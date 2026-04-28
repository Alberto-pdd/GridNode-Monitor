package main;

import operators.ObtenerConsumoMaximo;
import energy.Consumo;
import energy.RedEnergetica;
import grpc.MonitorizacionGrpc;
import grpc.PreciosGrpc;
import grpc.PreciosProto;
import grpc.PreciosProto.DemandaRequest;
import grpc.PreciosProto.PreciosReply;
import grpc.PreciosProto.PreciosRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Orquestador principal de la aplicación MySmartGrid.
 * Coordina la lectura de datos, el lanzamiento de hilos de procesamiento,
 * el análisis avanzado de datos (RxJava, ForkJoin, Callable) y la comunicación gRPC.
 */
public class MySmartGrid {

    public static void main(String[] args) {
        
        // 1. CONFIGURACIÓN E INICIALIZACIÓN
        ManagedChannel channelMonitrizacion = ManagedChannelBuilder.forAddress("localhost", 9002)
            .usePlaintext()
            .build();

        MonitorizacionGrpc.MonitorizacionBlockingStub stub = MonitorizacionGrpc.newBlockingStub(channelMonitrizacion);
        
        RedEnergetica red = new RedEnergetica(
                Config.NUM_ZONAS,
                Config.CAPACIDAD_BATERIA,
                Config.NIVEL_INICIAL_BATERIA,
                stub
            );

        List<Consumo> consumos = inicializarConsumos(red, stub);

        // 2. SINCRONIZACIÓN: ESPERA A LA FINALIZACIÓN DE TRÁMITES LOCALES
        // Esto garantiza que los 50 trámites se impriman ANTES que el análisis.
        red.esperarTrabajosLocales();

        // 3. ANÁLISIS AVANZADO DE DATOS (RxJava, Callable, ForkJoin)
        ejecutarAnalisisAvanzado(consumos);

        // 4. AUDITORÍA FINAL Y FILTROS
        red.imprimeAuditoria();
        mostrarFiltrosYEstadisticas(consumos);

        // 5. RESPUESTAS DEL SERVIDOR gRPC
        // Se imprimen al final absoluto para evitar que se mezclen con la auditoría.
        System.out.println("\n--- FASE 4: RESPUESTAS gRPC (UNARY - anotarConsumo()) ---");
        red.imprimirRespuestasGrpc();

        // 6. BIDIRECCIONAL - PreciosSerice
        System.out.println("\n--- FASE 5: CÁLCULO DE PRECIOS (BIDIRECCIONAL - calcularPrecios()) ---");
        ManagedChannel channelPrecios = ManagedChannelBuilder.forAddress("localhost", 9004)
            .usePlaintext()
            .build();

        PreciosGrpc.PreciosStub asynPreciosStub = PreciosGrpc.newStub(channelPrecios);
        
        StreamObserver<PreciosReply> responsePreciosObserver = new StreamObserver<PreciosProto.PreciosReply>() {
            @Override
            public void onNext(PreciosReply reply) {
                // Se ejecuta cada vez que el servidor calcula un precio
                System.out.println("[gRPC Price] ID: " + reply.getIdConsumo() + " | Precio Total: " + String.format("%.2f", reply.getPrecio()) + "EUR");
            }
            @Override
            public void onError(Throwable t) {
                System.out.println("Servidor de Precios no disponible en el puerto 9004");
                System.out.println("Error en el servicio de precios: " + t.getMessage());
            }
            @Override
            public void onCompleted() {
                System.out.println("--- Todos los precios han sido calculados con éxito ---");
            }
        };

        // Iniciamos la conexión bidireccional
        StreamObserver<PreciosRequest> requestObserver = asynPreciosStub.calcularPrecios(responsePreciosObserver);

        try {
            for (Consumo c : consumos) {
                // Construcción manual para evitar colisiones de tipos entre Protos
                PreciosRequest request = PreciosRequest.newBuilder()
                    .setIdConsumo(c.getIdConsumo())
                    .setIdZona(c.getZona())
                    .addAllDemandas(c.getDemandas().stream().map(d -> 
                        grpc.PreciosProto.DemandaRequest.newBuilder()
                            .setIdTipo(d.getIdTipo().toString())
                            .setKWh(d.getKWh())
                            .build()
                    ).collect(Collectors.toList()))
                    .build();
                
                requestObserver.onNext(request);
            }
            
            // Avisamos que no hay más datos
            requestObserver.onCompleted();
            
            // Esperamos un máximo de 5 segundos a que lleguen las respuestas
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Error al enviar solicitudes de precios: " + e.getMessage());
        }
        

        // Cierre de los canales de comunicación
        channelMonitrizacion.shutdown();
        channelPrecios.shutdown();
    }

    /**
     * Carga los consumos y arranca los hilos de procesamiento según el modo configurado.
     */
    private static List<Consumo> inicializarConsumos(RedEnergetica red, MonitorizacionGrpc.MonitorizacionBlockingStub stub) {
        List<Consumo> listaConsumos = new ArrayList<>();
        
        System.out.println("--- FASE 1: PROCESAMIENTO DE TRÁMITES LOCALES ---");
        
        switch (Config.MODO_EJECUCION) {
            case 0:
                System.out.println("Modo Observables:");
                listaConsumos = Consumo.consumosDesdeFichero(Config.FICHERO_CONSUMOS);
                System.out.println("Leidos " + listaConsumos.size() + " consumos. Iniciando hilos...");
                ejecutarModoHilosDirectos(red, listaConsumos);
                break;

            case 1:
                System.out.println("Modo Observables:");
                listaConsumos = ejecutarModoObservable(red);
                break;

            case 2:
                int nucleos = Runtime.getRuntime().availableProcessors();
                System.out.println("Modo Executor con " + nucleos + " nucleos.");
                listaConsumos = Consumo.consumosDesdeFichero(Config.FICHERO_CONSUMOS);
                ejecutarModoExecutor(red, listaConsumos, nucleos);
                break;
        }
        return listaConsumos;
    }

    private static void ejecutarModoHilosDirectos(RedEnergetica red, List<Consumo> consumos) {
        List<Thread> hilos = new ArrayList<>();
        for (Consumo c : consumos) {
            Thread t = new Thread(new ProcesarConsumo(red, c));
            hilos.add(t);
            t.start();
            pausaEscalonada(20);
        }
        esperarHilos(hilos);
    }

    private static List<Consumo> ejecutarModoObservable(RedEnergetica red) {
        List<Consumo> lista = new ArrayList<>();
        List<Thread> hilos = new ArrayList<>();
        
        Consumo.consumosDesdeFicheroObservable(Config.FICHERO_CONSUMOS)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .flatMap(c -> {
                lista.add(c);
                Thread t = new Thread(new ProcesarConsumo(red, c));
                hilos.add(t);
                t.start();
                pausaEscalonada(20);
                return Observable.just(c);
            })
            .blockingSubscribe();

        esperarHilos(hilos);
        return lista;
    }

    private static void ejecutarModoExecutor(RedEnergetica red, List<Consumo> consumos, int nucleos) {
        ExecutorService executor = Executors.newFixedThreadPool(nucleos);
        for (Consumo c : consumos) {
            executor.submit(new ProcesarConsumo(red, c));
            pausaEscalonada(20);
        }
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ejecuta las tres técnicas de procesamiento avanzado solicitadas.
     */
    private static void ejecutarAnalisisAvanzado(List<Consumo> consumos) {
        System.out.println("\n--- FASE 2: ANÁLISIS AVANZADO DE DATOS ---");

        // A. CALLABLE: Obtener el consumo máximo
        ejecutarAnalisisCallable(consumos);

        // B. FORK-JOIN: Filtrado de consumos pesados (>20kWh)
        ejecutarAnalisisForkJoin(consumos);
    }

    private static void ejecutarAnalisisCallable(List<Consumo> consumos) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Consumo> future = executor.submit(new ObtenerConsumoMaximo(consumos));
            Consumo max = future.get();
            if (max != null) {
                System.out.println("Resultado Callable -> Consumo mas alto: " + max.getIdConsumo() + 
                                   " (" + max.getTotalKWh() + " kWh)");
            }
        } catch (Exception e) {
            System.err.println("Error en Callable: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private static void ejecutarAnalisisForkJoin(List<Consumo> consumos) {
        ForkJoinPool pool = new ForkJoinPool();
        List<Consumo> filtrados = pool.invoke(new TareaFiltrado(consumos));
        System.out.println("Resultado ForkJoin -> Consumos > 20kWh encontrados: " + filtrados.size());
        for (Consumo c : filtrados) {
            System.out.println(" - ID: " + c.getIdConsumo() + " | " + c.getTotalKWh() + " kWh");
        }
    }

    /**
     * Muestra filtros adicionales y comprobación de direcciones.
     */
    private static void mostrarFiltrosYEstadisticas(List<Consumo> consumos) {
        System.out.println("\n--- FASE 3: FILTROS Y BÚSQUEDAS ---");
        
        System.out.println("Consumos menores a 5 kWh:");
        consumos.parallelStream()
                .filter(c -> c.getTotalKWh() < 5.0)
                .map(Consumo::getIdConsumo)
                .forEach(System.out::println);

        double max = consumos.parallelStream()
                            .mapToDouble(Consumo::getTotalKWh)
                            .max().orElse(0.0);
        System.out.println("Consumo maximo global: " + max + " kWh");

        comprobarDireccion(consumos, "Sagitario, 24");
        comprobarDireccion(consumos, "Berna, 11");
    }

    private static void comprobarDireccion(List<Consumo> consumos, String direccion) {
        boolean existe = consumos.parallelStream().anyMatch(c -> c.getDireccion().equals(direccion));
        if (existe) {
            System.out.println("Direccion Encontrada: " + direccion);
        } else {
            System.out.println("Direccion NO encontrada: " + direccion);
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private static void pausaEscalonada(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void esperarHilos(List<Thread> hilos) {
        for (Thread t : hilos) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /**
     * Tarea ForkJoin recursiva para filtrar consumos pesados (>20kWh).
     */
    static class TareaFiltrado extends RecursiveTask<List<Consumo>> {
        private final List<Consumo> lista;

        TareaFiltrado(List<Consumo> lista) {
            this.lista = lista;
        }

        @Override
        protected List<Consumo> compute() {
            if (lista.size() < 10) {
                return lista.stream()
                        .filter(c -> c.getTotalKWh() > 20.0)
                        .collect(Collectors.toList());
            }

            int mid = lista.size() / 2;
            TareaFiltrado t1 = new TareaFiltrado(lista.subList(0, mid));
            TareaFiltrado t2 = new TareaFiltrado(lista.subList(mid, lista.size()));

            invokeAll(t1, t2);

            List<Consumo> result = new ArrayList<>(t1.join());
            result.addAll(t2.join());
            return result;
        }
    }
}
