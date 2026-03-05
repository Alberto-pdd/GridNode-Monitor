package operators;

import energy.Consumo;

public class TrabajoConsumo {

    private Consumo consumo;
    private String resultado;
    private boolean fin = false;

    public TrabajoConsumo(Consumo consumo) {
        this.consumo = consumo;
    }

    synchronized public void awaitResultado() {
        while (fin == false) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void setResultado(String resultado) {
        fin = true;
        this.resultado = resultado;
        notifyAll();
    }

    public Consumo getConsumo() {
        return consumo;
    }
}
