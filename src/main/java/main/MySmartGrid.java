package main;

import operators.ObtenerConsumoMaximo;
import energy.Consumo;
import energy.RedEnergetica;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MySmartGrid {

    public static void main(String[] args) {
        RedEnergetica red = new RedEnergetica(
                Config.NUM_ZONAS,
                Config.CAPACIDAD_BATERIA,
                Config.NIVEL_INICIAL_BATERIA);

        List<Consumo> consumos = null;
        final List<Consumo>[] consumosWrapper = new List[1];

        switch (Config.MODO_EJECUCION) {
            case 0:
                consumos = Consumo.consumosDesdeFichero(Config.FICHERO_CONSUMOS);
                System.out.println("Leidos " + consumos.size() + " consumos desde " + Config.FICHERO_CONSUMOS);

                List<Thread> lThread = new ArrayList<>();

                for (Consumo c : consumos) {
                    ProcesarConsumo pc = new ProcesarConsumo(red, c);
                    Thread t = new Thread(pc);
                    lThread.add(t);
                    t.start();
                }

                for (Thread t : lThread) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                break;

            case 1:
                System.out.println("Modo Observable:");
                consumosWrapper[0] = new ArrayList<>();
                consumos = consumosWrapper[0];
                List<Thread> lThreadObs = new ArrayList<>();
                Consumo.consumosDesdeFicheroObservable(Config.FICHERO_CONSUMOS)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.computation())
                        .flatMap(c -> {
                            consumosWrapper[0].add(c);
                            Thread t = new Thread(new ProcesarConsumo(red, c));
                            lThreadObs.add(t);
                            t.start();
                            return Observable.just(c);
                        })
                        .blockingSubscribe();

                for (Thread t : lThreadObs) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            case 2:
                int nucleos = Runtime.getRuntime().availableProcessors();
                System.out.println("Modo Executor con " + nucleos + " nucleos disponibles");

                ExecutorService executor = Executors.newFixedThreadPool(nucleos);

                consumos = Consumo.consumosDesdeFichero(Config.FICHERO_CONSUMOS);
                System.out.println("Leidos " + consumos.size() + " consumos desde " + Config.FICHERO_CONSUMOS);

                for (Consumo c : consumos) {
                    executor.submit(new ProcesarConsumo(red, c));
                }

                executor.shutdown();
                
                try {
                    executor.awaitTermination(1, TimeUnit.HOURS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                break;
        }

        // BLOQUE 20 KWH
        Observable<Consumo> consumoObservable = Observable.fromIterable(consumos)
                .subscribeOn(Schedulers.computation());

        consumoObservable.reduce(0.0, (acc, c) -> acc + c.getTotalKWh())
                .subscribe(total -> System.out
                        .println("[Thread " + Thread.currentThread().getName() + "] Suma total: " + total + " kWh"));

        consumoObservable.filter(c -> c.getTotalKWh() > 20.0)
                .subscribe(c -> System.out.println("[Thread " + Thread.currentThread().getName() + "] Consumo > 20kWh: "
                        + c.getIdConsumo() + " = " + c.getTotalKWh() + " kWh"));
        // BLOQUE 20 KWH

        // Obtener consumo máximo usando Callable
        ExecutorService maxExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<Consumo> futureMax = maxExecutor.submit(new ObtenerConsumoMaximo(consumos));
            Consumo max = futureMax.get();
            if (max != null) {
                System.out.println("Consumo mas alto detectado (Callable): ID=" + max.getIdConsumo() + 
                                   ", Cantidad=" + max.getTotalKWh() + " kWh");
            }
        } catch (Exception e) {
            System.err.println("Error al obtener el consumo maximo: " + e.getMessage());
        } finally {
            maxExecutor.shutdown();
        }

        red.imprimeAuditoria();

        System.out.println("Consumo de Kwh < 5");
        consumos.parallelStream().filter(c -> c.getTotalKWh() < 5.0).map(Consumo::getIdConsumo)
                .forEach(System.out::println);

        double maxC = consumos.parallelStream().mapToDouble(Consumo::getTotalKWh).max().orElse(0.0);
        System.out.println("Consumo máximo: " + maxC);

        String direccion1 = "Sagitario, 24";
        boolean encontrado1 = consumos.parallelStream().anyMatch(c -> c.getDireccion().equals(direccion1));

        String direccion2 = "Berna, 11";
        boolean encontrado2 = consumos.parallelStream().anyMatch(c -> c.getDireccion().equals(direccion2));

        if (encontrado1) {
            System.out.println("Direccion Encontrada");
        } else {
            System.out.println("Direccion NO encontrada");
        }

        if (encontrado2) {
            System.out.println("Direccion Encontrada");
        } else {
            System.out.println("Direccion NO encontrada");
        }
    }
}
