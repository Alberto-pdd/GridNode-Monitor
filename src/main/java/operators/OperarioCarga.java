package operators;

import energy.ZonaEnergetica;
import main.Config;

public class OperarioCarga implements Runnable {

    ZonaEnergetica zonaEnergetica;

    public OperarioCarga(ZonaEnergetica z) {
        this.zonaEnergetica = z;
    }

    @Override
    public void run() {
        while (true) {
            zonaEnergetica.getBateria().carga(Config.CANTIDAD_DE_RECARGA);
        }
    }
}
