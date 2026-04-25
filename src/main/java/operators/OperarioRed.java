package operators;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

import energy.Consumo;
import energy.Demanda;
import energy.ZonaEnergetica;
import grpc.MonitorizacionGrpc;
import grpc.MonitorizacionProto.ConsumoReply;
import grpc.MonitorizacionProto.ConsumoRequest;
import main.Config;

/**
 * Clase que representa a un operario encargado de gestionar los consumos de una zona.
 * Se encarga de procesar las demandas de energía, interactuar con las baterías y
 * notificar los resultados al servidor central mediante gRPC.
 */
public class OperarioRed implements Runnable {

    private final CentroControl centroControl;
    private final ZonaEnergetica zonaEnergetica;
    private final Semaphore semaphore;
    private final CyclicBarrier barrier;
    private final CountDownLatch latch;
    private final MonitorizacionGrpc.MonitorizacionBlockingStub stub;
    private final CountDownLatch latchFin;
    private final CountDownLatch latchGrpc;
    
    private boolean primero = true;

    // Acumuladores temporales para el ciclo actual
    private double cSolar = 0;
    private double cEolico = 0;
    private double cRapido = 0;

    public OperarioRed(CentroControl centroControl, ZonaEnergetica zona, Semaphore semaphore, 
                      CyclicBarrier barrier, CountDownLatch latch, 
                      MonitorizacionGrpc.MonitorizacionBlockingStub stub, 
                      CountDownLatch latchFin, CountDownLatch latchGrpc) {
        this.centroControl = centroControl;
        this.zonaEnergetica = zona;
        this.semaphore = semaphore;
        this.barrier = barrier;
        this.latch = latch;
        this.stub = stub;
        this.latchFin = latchFin;
        this.latchGrpc = latchGrpc;
    }

    @Override
    public void run() {
        while (true) {
            sincronizarCiclo();

            Consumo consumo = centroControl.getTrabajo();
            if (consumo == null) continue;

            try {
                // Control de acceso a los recursos de la zona
                zonaEnergetica.getSConsumo().acquire();
                
                procesarConsumo(consumo);
                
                zonaEnergetica.getSConsumo().release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            finalizarIteracion();
        }
    }

    /**
     * Gestiona la sincronización inicial de los hilos según el modo configurado.
     */
    private void sincronizarCiclo() {
        try {
            if (Config.SYNC_MODE == 0 && semaphore != null) {
                semaphore.acquire();
            } else if (Config.SYNC_MODE == 1 && barrier != null) {
                barrier.await();
            }

            // Notificamos al sistema que el hilo ha arrancado (solo la primera vez)
            if (primero && latch != null) {
                latch.countDown();
                primero = false;
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Orquestador de la lógica de procesamiento de un consumo individual.
     */
    private void procesarConsumo(Consumo consumo) {
        reiniciarAcumuladores();
        
        // 1. Clasificación de la demanda
        clasificarDemandas(consumo);

        // 2. Bloqueo hasta que haya energía disponible
        esperarSuministro();

        // 3. Consumo efectivo de las baterías
        ejecutarSuministro();

        // 4. Registro local (Traza)
        centroControl.traza("Trabajo completado para: " + consumo.getIdConsumo());
        
        // Notificamos que el trámite local ha terminado (para la auditoría)
        if (latchFin != null) latchFin.countDown();

        // 5. Notificación al servidor central vía gRPC
        comunicarServidor(consumo);
    }

    private void reiniciarAcumuladores() {
        cSolar = 0;
        cEolico = 0;
        cRapido = 0;
    }

    private void clasificarDemandas(Consumo consumo) {
        for (Demanda demanda : consumo.getDemandas()) {
            switch (demanda.getIdTipo()) {
                case "SOLAR":   cSolar += demanda.getKWh(); break;
                case "EOLICA":  cEolico += demanda.getKWh(); break;
                case "RAPIDA":  cRapido += demanda.getKWh(); break;
            }
        }
    }

    /**
     * Gestión de bloqueos. Nota: La batería Rápida usa wait/notify internamente,
     * por lo que requiere sincronización explícita aquí.
     */
    private void esperarSuministro() {
        if (cSolar > 0)  zonaEnergetica.getBateriaSolar().esperarEnergia(cSolar);
        if (cEolico > 0) zonaEnergetica.getBateriaEolica().esperarEnergia(cEolico);
        
        if (cRapido > 0) {
            synchronized (zonaEnergetica.getBateria()) {
                while (!zonaEnergetica.getBateria().puedeSuministrar(cRapido)) {
                    try {
                        zonaEnergetica.getBateria().wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private void ejecutarSuministro() {
        if (cSolar > 0)  zonaEnergetica.getBateriaSolar().suministra(cSolar);
        if (cEolico > 0) zonaEnergetica.getBateriaEolica().suministra(cEolico);
        if (cRapido > 0) zonaEnergetica.getBateria().suministra(cRapido);
    }

    /**
     * Envía la información al servidor gRPC y gestiona la respuesta asíncrona.
     */
    private void comunicarServidor(Consumo consumo) {
        ConsumoRequest request = ConsumoRequest.newBuilder()
            .setIdConsumo(consumo.getIdConsumo())
            .setIdZona(zonaEnergetica.getIdZona())
            .setKWh(consumo.getTotalKWh())
            .setDireccion(consumo.getDireccion())
            .setSolar(cSolar > 0)
            .build();

        // Llamada bloqueante al servidor
        ConsumoReply reply = stub.anotarConsumo(request);

        // Almacenamos el mensaje para imprimirlo en el bloque final de la consola
        // Usamos concatenación simple para mayor legibilidad del código
        String msg = "Respuesta Servidor -> Total Red gRPC: " + reply.getTotalRed();
        zonaEnergetica.getRed().guardarRespuestaGrpc(msg);
            
        // Notificamos que la respuesta del servidor ha llegado
        if (latchGrpc != null) latchGrpc.countDown();
    }


    private void finalizarIteracion() {
        // Si usamos semáforo, liberamos el permiso para la siguiente ronda
        if (Config.SYNC_MODE == 0 && semaphore != null) {
            semaphore.release();
        }
    }
}

