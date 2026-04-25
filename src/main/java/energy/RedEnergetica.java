package energy;

import operators.CentroControl;
import pcd.util.Ventana;
import storage.Bateria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import grpc.MonitorizacionGrpc;
import main.Config;

/**
 * Clase que representa la red energética global.
 * Gestiona el conjunto de zonas, los centros de control y la sincronización
 * de los informes finales de auditoría y gRPC.
 */
public class RedEnergetica {
    private final List<ZonaEnergetica> zonas;
    private final List<CentroControl> centros;
    private final MonitorizacionGrpc.MonitorizacionBlockingStub stub;
    
    // Latches para controlar el flujo de impresión en consola
    private final CountDownLatch latchFin;
    private final CountDownLatch latchGrpc;
    
    // Lista segura para hilos que almacena las respuestas del servidor gRPC
    private final List<String> respuestasGrpc = new CopyOnWriteArrayList<>();

    public RedEnergetica(int numZonas, double capacidadBateria, double nivelInicialBateria, MonitorizacionGrpc.MonitorizacionBlockingStub stub) {
        if (numZonas <= 0) {
            throw new IllegalArgumentException("numZonas debe ser > 0");
        }

        this.stub = stub;
        // Inicializamos los latches con el número total de consumos esperados
        this.latchFin = new CountDownLatch(Config.NUM_CONSUMOS_A_GENERAR);
        this.latchGrpc = new CountDownLatch(Config.NUM_CONSUMOS_A_GENERAR);
        
        List<ZonaEnergetica> tmpZonas = new ArrayList<>(numZonas);
        List<CentroControl> tmpCentros = new ArrayList<>(numZonas);

        // Modularizamos la creación de cada zona energética
        for (int i = 0; i < numZonas; i++) {
            inicializarComponentesZona(i, capacidadBateria, nivelInicialBateria, tmpZonas, tmpCentros);
        }

        this.zonas = Collections.unmodifiableList(tmpZonas);
        this.centros = Collections.unmodifiableList(tmpCentros);
    }

    /**
     * Crea e interconecta los componentes de una zona individual.
     */
    private void inicializarComponentesZona(int id, double capacidad, double nivel, 
                                          List<ZonaEnergetica> listaZonas, List<CentroControl> listaCentros) {
        
        CentroControl cc = new CentroControl();
        CuentaEnergetica cuenta = new CuentaEnergetica(0.0);
        Bateria bateria = new Bateria(capacidad, nivel);
        
        // Configuración de la interfaz visual de la zona
        Ventana ventanaZona = new Ventana(id * Config.TAMAÑO_VENTANA, 1, Config.TAMAÑO_VENTANA,
                Config.TAMAÑO_VENTANA, "Zona " + id);
        bateria.setVentana(ventanaZona);

        ZonaEnergetica zona = new ZonaEnergetica(id, cuenta, bateria, cc, ventanaZona, stub, latchFin, latchGrpc, this);
        cc.setZona(zona);

        listaZonas.add(zona);
        listaCentros.add(cc);
    }

    // --- MÉTODOS DE ACCESO ---

    public List<ZonaEnergetica> getZonas() {
        return zonas;
    }

    public ZonaEnergetica getZona(int idZona) {
        return zonas.get(idZona);
    }

    public CentroControl getCentroControl(int idZona) {
        return centros.get(idZona);
    }

    // --- MÉTODOS DE AUDITORÍA Y CÁLCULO ---

    public double auditoriaBalanceTotal() {
        double sum = 0.0;
        for (ZonaEnergetica z : zonas) {
            sum += z.getCuenta().getBalanceKWh();
        }
        return sum;
    }

    public double auditoriaEnergiaDisponibleTotal() {
        return zonas.stream().mapToDouble(
                z -> z.getBateria().getNivelActualKWh() + 
                     z.getBateriaSolar().getNivelActualKWh() + 
                     z.getBateriaEolica().getNivelActualKWh())
                .sum();
    }

    // --- GESTIÓN DE SINCRONIZACIÓN Y RESPUESTAS ---

    public void guardarRespuestaGrpc(String msg) {
        respuestasGrpc.add(msg);
    }

    public void esperarTrabajosLocales() {
        try {
            latchFin.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void esperarGrpc() {
        try {
            latchGrpc.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void imprimirRespuestasGrpc() {
        System.out.println("");
        for (String msg : respuestasGrpc) {
            System.out.println(msg);
        }
    }

    /**
     * Imprime el informe detallado de la red.
     * Bloquea la ejecución hasta que todos los trámites locales hayan finalizado.
     */
    public void imprimeAuditoria() {
        esperarTrabajosLocales();

        System.out.println("\n------------------------------------------------------- AUDITORIA RED -------------------------------------------------------\n");

        // Usamos parallelStream para agilizar el cálculo en redes con muchas zonas
        zonas.parallelStream().forEach(z -> {
            String info = "Zona " + z.getIdZona() + 
                          " -> Consumidos = " + fmt(z.getCuenta().getBalanceKWh()) + " kWh" +
                          " | Bateria Rápida = " + fmt(z.getBateria().getNivelActualKWh()) + " kWh" +
                          " --- Bateria Solar = " + fmt(z.getBateriaSolar().getNivelActualKWh()) + " kWh" +
                          " --- Bateria Eolica = " + fmt(z.getBateriaEolica().getNivelActualKWh()) + " kWh";
            System.out.println(info);
        });

        System.out.println("Consumo total: " + fmt(auditoriaBalanceTotal()) + " kWh");
        System.out.println("Energia disponible total: " + fmt(auditoriaEnergiaDisponibleTotal()) + " kWh");
        System.out.println("======================================\n");
    }

    /**
     * Formateador auxiliar para mantener la precisión de los decimales en el informe.
     */
    private String fmt(double x) {
        return String.format(java.util.Locale.ROOT, "%.2f", x);
    }
}
