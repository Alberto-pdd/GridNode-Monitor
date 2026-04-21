package operators;

import java.util.concurrent.CountDownLatch;
import energy.ZonaEnergetica;
import main.Config;

public class OperarioCarga implements Runnable {

    ZonaEnergetica zonaEnergetica;
    CountDownLatch latch;

    public OperarioCarga(ZonaEnergetica z, CountDownLatch latch) {
        this.zonaEnergetica = z;
        this.latch = latch;
    }

    @Override
    public void run() {
        if (latch != null) {
            try {
                System.out.println("[OperarioCarga Z" + zonaEnergetica.getIdZona() + "] Esperando a que todos los operarios de red inicien...");
                latch.await();
                System.out.println("[OperarioCarga Z" + zonaEnergetica.getIdZona() + "] ¡Todos los operarios listos! Comenzando carga de baterías.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        while (true) {
            zonaEnergetica.getBateria().carga(Config.CANTIDAD_DE_RECARGA);
        }
    }
}
