package operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import energy.Consumo;
import energy.Demanda;
import energy.ZonaEnergetica;

public class CentroControl {
    private ZonaEnergetica zona;
    private List<TrabajoConsumo> colaConsumos = new ArrayList<>();

    public CentroControl() {

    }

    synchronized public void addTrabajo(Consumo c) {
        TrabajoConsumo tc = new TrabajoConsumo(c);
        colaConsumos.add(tc);
        notifyAll();
    }

    synchronized public Consumo getTrabajo() {
        while (colaConsumos.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        TrabajoConsumo tc = colaConsumos.remove(0);
        tc.setResultado("OK");
        return tc.getConsumo();
    }

    public void setZona(ZonaEnergetica zona) {
        this.zona = zona;
    }

    public int getIdZona() {
        return zona.getIdZona();
    }

    public String enviarTrabajo(Consumo c) {

        Random r = new Random();
        // Simulamos un tiempo de tramitación del consumo
        try {
            Thread.sleep(r.nextInt(200));
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Calcular el consumo

        double cRapida = 0.0;
        double cSolar = 0.0;
        double cEolica = 0.0;

        for (Demanda demanda : c.getDemandas()) {
            if (demanda.getIdTipo().equals("RAPIDA")) {
                cRapida = cRapida + demanda.getKWh();
            }
            if (demanda.getIdTipo().equals("SOLAR")) {
                cSolar = cSolar + demanda.getKWh();
            }
            if (demanda.getIdTipo().equals("EOLICA")) {
                cEolica = cEolica + demanda.getKWh();
            }
        }

        // Dar la energia

        if (cRapida > 0) {
            synchronized (zona.getBateria()) {
                while (!zona.getBateria().puedeSuministrar(cRapida)) {
                    try {
                        zona.getBateria().puedeSuministrar(cRapida);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            zona.getBateria().suministra(cRapida);
        }
        if (cSolar > 0) {
            zona.getBateriaSolar().esperarEnergia(cSolar);
            zona.getBateriaSolar().suministra(cSolar);
        }
        if (cEolica > 0) {
            zona.getBateriaEolica().esperarEnergia(cEolica);
            zona.getBateriaEolica().suministra(cEolica);
        }

        zona.getCuenta().anotaConsumoDet(cRapida, cSolar, cEolica);

        double total = c.getTotalKWh();
        double suministrado = cRapida + cSolar + cEolica;

        // String completo;

        // if (cRapida > 0) {
        // completo = "Rapida: " + fmt(cRapida) + " ";
        // }
        // if (cSolar > 0) {
        // completo = "Solar: " + fmt(cSolar) + " ";
        // }
        // if (cEolica > 0) {
        // completo = "Eolica: " + fmt(cEolica) + " ";
        // }

        StringBuilder completo = new StringBuilder("OK: ");
        if (cRapida > 0) {
            completo.append("Rapida = ").append(fmt(cRapida)).append(" ");
        }
        if (cSolar > 0) {
            completo.append("Solar = ").append(fmt(cSolar)).append(" ");
        }
        if (cEolica > 0) {
            completo.append("Eolica = ").append(fmt(cEolica)).append(" ");
        }

        String resultado = (suministrado >= total)
                ? "OK: " + completo.toString() + "- Total: " + fmt(total) + " kWh"
                : "PARCIAL: " + completo.toString() + "- suministrados " + fmt(suministrado) + " kWh (faltan "
                        + fmt(total - suministrado) + " kWh)";

        traza("Trabajo completado para: " + c.getIdConsumo());

        // String resultado = completo.toString() + "- Total = " + fmt(c.getTotalKWh())
        // + " Kwh";

        return resultado;
    }

    private String fmt(double x) {
        return String.format(java.util.Locale.ROOT, "%.2f", x);
    }

    public void traza(String msg) {
        System.out.println("[CentroControl Z" + zona.getIdZona() + "] " + msg);
    }
}
