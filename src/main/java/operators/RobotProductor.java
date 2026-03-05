package operators;

import storage.BateriaRenovable;

public class RobotProductor extends Thread {
    String tipoRobot;
    BateriaRenovable bateria;

    public RobotProductor(String tipoRobot, BateriaRenovable bateria) {
        this.tipoRobot = tipoRobot;
        this.bateria = bateria;
    }

    public void run() {
        while (true) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            bateria.carga(1.0);
        }
    }

}
