package main;

import energy.Consumo;
import energy.RedEnergetica;

public class ProcesarConsumo implements Runnable{

    private RedEnergetica red;
    private Consumo c;

    public ProcesarConsumo (RedEnergetica red, Consumo c) {
        this.red = red;
        this.c = c;
    }

    @Override
    public void run() {
        String resultado = red.getZona(c.getZona()).tramitarConsumo(c);
        red.getZona(c.getZona()).getVentana().traza (c.getIdConsumo()+ " - Tramitado: "+resultado);
    }
}
