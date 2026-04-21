package operators;

import java.util.concurrent.Semaphore;

import energy.Consumo;
import energy.Demanda;
import energy.ZonaEnergetica;

public class OperarioRed implements Runnable {

    CentroControl centroControl;
    Consumo consumo;
    Double total;
    ZonaEnergetica zonaEnergetica;
    Semaphore semaphore;

    public OperarioRed(CentroControl centroControl, ZonaEnergetica zona, Semaphore semaphore) {
        this.centroControl = centroControl;
        this.zonaEnergetica = zona;
        this.total = 0.0;
        this.semaphore = semaphore;
    }

    @Override
    public void run() {
        while (true) {
            double cSolar = 0;
            double cEolico = 0;
            double cRapido = 0;

            consumo = centroControl.getTrabajo();

            try {
                zonaEnergetica.getSConsumo().acquire();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            // SUMAR CONSUMOS
            for (Demanda demanda : consumo.getDemandas()) {
                if (demanda.getIdTipo().equals("SOLAR")) {
                    cSolar = cSolar + demanda.getKWh();
                }
                if (demanda.getIdTipo().equals("EOLICA")) {
                    cEolico = cEolico + demanda.getKWh();
                }
                if (demanda.getIdTipo().equals("RAPIDA")) {
                    cRapido = cRapido + demanda.getKWh();
                }
            }

            // BLOQUEOS
            if (cSolar > 0) {
                zonaEnergetica.getBateriaSolar().esperarEnergia(cSolar);
            }

            if (cEolico > 0) {
                zonaEnergetica.getBateriaEolica().esperarEnergia(cEolico);
            }

            if (cRapido > 0) {
                synchronized (zonaEnergetica.getBateria()) {
                    while (zonaEnergetica.getBateria().puedeSuministrar(cRapido) == false) {
                        try {
                            zonaEnergetica.getBateria().wait();
                        } catch (Exception e) {
                            e.getStackTrace();
                        }
                    }
                }
            }

            // SUMINISTRAR
            if (cSolar > 0) {
                zonaEnergetica.getBateriaSolar().suministra(cSolar);
            }

            if (cEolico > 0) {
                zonaEnergetica.getBateriaEolica().suministra(cEolico);
            }

            if (cRapido > 0) {
                zonaEnergetica.getBateria().suministra(cRapido);
            }

            // FINALIZACION
            zonaEnergetica.getCuenta().anotaConsumoDet(cRapido, cSolar, cEolico);
            System.out.println(consumo.getIdConsumo() + "Tramitado OK: Total: " + consumo.getTotalKWh());
            System.out.println(consumo.getIdConsumo() + "Tramitado OK: Solar: " + cSolar);
            System.out.println(consumo.getIdConsumo() + "Tramitado OK: Eolica: " + cEolico);
            System.out.println(consumo.getIdConsumo() + "Tramitado OK: Rapido: " + cRapido);

            zonaEnergetica.getSConsumo().release();
        }
    }
}
