package operators;

import energy.Consumo;
import energy.ZonaEnergetica;

public class OperarioRed implements Runnable {

    CentroControl centroControl;
    Consumo consumo;
    Double total;
    ZonaEnergetica zonaEnergetica;

    public OperarioRed(CentroControl centroControl, ZonaEnergetica zona) {
        this.centroControl = centroControl;
        this.zonaEnergetica = zona;
        this.total = 0.0;
    }

    @Override
    public void run() {
        while (true) {
            consumo = centroControl.getTrabajo();
            total = total + consumo.getTotalKWh();

            double suministro = zonaEnergetica.getBateria().suministra(total);

            zonaEnergetica.getCuenta().anotaConsumo(suministro);
        }
    }
}
