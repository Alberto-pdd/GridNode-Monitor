package energy;

import operators.CentroControl;
import operators.OperarioCarga;
import pcd.util.Ventana;
import storage.Bateria;
import storage.BateriaRenovable;

import java.awt.Color;
import java.util.Objects;
import operators.OperarioRed;
import operators.RobotProductor;

import main.Config;

public class ZonaEnergetica {
    private final int idZona;
    private final CuentaEnergetica cuenta;
    private final Bateria bateria;
    private final CentroControl centroControl;
    private Ventana v;
    private BateriaRenovable bateriaSolar;
    private BateriaRenovable bateriaEolica;
    private RobotProductor robotSolar;
    private RobotProductor robotEolica;

    public ZonaEnergetica(int idZona, CuentaEnergetica cuenta, Bateria bateria, CentroControl centroControl,
            Ventana _v) {
        this.idZona = idZona;
        this.cuenta = Objects.requireNonNull(cuenta);
        this.bateria = Objects.requireNonNull(bateria);
        this.centroControl = Objects.requireNonNull(centroControl);
        this.v = _v;
        this.bateriaSolar = new BateriaRenovable(Config.CAPACIDAD_BATERIA, 0);
        this.bateriaEolica = new BateriaRenovable(Config.CAPACIDAD_BATERIA, 0);
        this.robotSolar = new RobotProductor("Solar", bateriaSolar);
        this.robotEolica = new RobotProductor("Eolica", bateriaEolica);

        robotSolar.start();
        robotEolica.start();

        for (int i = 0; i < Config.NUMERO_OPERARIOS; i++) {
            OperarioRed opRed = new OperarioRed(centroControl, this);
            Thread tOpRed = new Thread(opRed);
            tOpRed.start();
        }

        OperarioCarga opCarga = new OperarioCarga(this);
        Thread tOpCarga = new Thread(opCarga);
        tOpCarga.start();
    }

    public int getIdZona() {
        return idZona;
    }

    public CuentaEnergetica getCuenta() {
        return cuenta;
    }

    public Bateria getBateria() {
        return bateria;
    }

    public CentroControl getCentroControl() {
        return centroControl;
    }

    public String tramitarConsumo(Consumo c) {

        if (c.getZona() != idZona) {
            return "ERROR: consumo dirigido a zona " + c.getZona() + " pero tramitado en zona " + idZona;
        }

        v.traza(c.getIdConsumo() + " solicita: " + c.getTotalKWh(), Color.BLUE);
        String resultado = centroControl.enviarTrabajo(c);

        return resultado;
    }

    @Override
    public String toString() {
        return "ZonaEnergetica{idZona=" + idZona + ", cuenta=" + cuenta + ", bateria=" + bateria + "}";
    }

    public Ventana getVentana() {
        return v;
    }

    public void traza(String s) {
        v.traza(s);
    }

    public void traza(String s, Color color) {
        v.traza(s, color);
    }

}
