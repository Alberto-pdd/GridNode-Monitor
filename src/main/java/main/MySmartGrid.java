package main;

import energy.Consumo;
import energy.RedEnergetica;

import java.util.ArrayList;
import java.util.List;

public class MySmartGrid {
	
    public static void main(String[] args) {
        RedEnergetica red = new RedEnergetica(
                Config.NUM_ZONAS,
                Config.CAPACIDAD_BATERIA,
                Config.NIVEL_INICIAL_BATERIA
        );

        List<Consumo> consumos = Consumo.consumosDesdeFichero(Config.FICHERO_CONSUMOS);
        System.out.println("Leidos " + consumos.size() + " consumos desde " + Config.FICHERO_CONSUMOS);

        List<Thread> lThread = new ArrayList<>();

        // Tramitamos los consumos de manera secuencial
        String resultado;
        for (Consumo c: consumos) {
        	ProcesarConsumo pc = new ProcesarConsumo(red, c);
            Thread t = new Thread(pc);
            lThread.add(t);
            t.start();
        }

        for(Thread t: lThread) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        red.imprimeAuditoria();
    }


}
