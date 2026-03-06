package storage;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import pcd.util.Ventana;

public class BateriaRenovable {
    Lock lock = new ReentrantLock();
    Condition c = lock.newCondition();
    private final double capacidadMaxKWh;
    private double nivelActualKWh;
    Ventana v;

    public BateriaRenovable(double capacidadMaxKWh, double nivelInicialKWh) {
        if (capacidadMaxKWh <= 0)
            throw new IllegalArgumentException("capacidadMaxKWh debe ser > 0");
        if (nivelInicialKWh < 0)
            throw new IllegalArgumentException("nivelInicialKWh debe ser >= 0");
        this.capacidadMaxKWh = capacidadMaxKWh;
        this.nivelActualKWh = Math.min(nivelInicialKWh, capacidadMaxKWh);
    }

    public double getCapacidadMaxKWh() {
        return capacidadMaxKWh;
    }

    public double getNivelActualKWh() {
        return nivelActualKWh;
    }

    public boolean puedeSuministrar(double kWh) {
        return kWh <= nivelActualKWh;
    }

    public double suministra(double kWh) {
        lock.lock();
        if (kWh <= 0)
            return 0.0;

        while (nivelActualKWh == 0) {
            try {
                wait();
            } catch (Exception e) {
                e.getStackTrace();
            }
        }

        // double suministrado = Math.min(kWh, nivelActualKWh);
        double suministrado = kWh;
        nivelActualKWh -= suministrado;
        lock.unlock();
        return suministrado;
    }

    public void carga(double kWh) {
        lock.lock();
        if (kWh <= 0)
            return;
        while ((nivelActualKWh + kWh) > capacidadMaxKWh) {
            try {
                c.await();
            } catch (Exception e) {
                e.getStackTrace();
            }
        }
        c.signal();
        nivelActualKWh = Math.min(capacidadMaxKWh, nivelActualKWh + kWh);
        lock.unlock();
    }

    public void setVentana(Ventana _v) {
        v = _v;
    }

    @Override
    public String toString() {
        return "Bateria{capacidadMaxKWh=" + capacidadMaxKWh + ", nivelActualKWh=" + nivelActualKWh + "}";
    }

    public void esperarEnergia(double kwh) {
        lock.lock();
        try {
            while (nivelActualKWh < kwh) {
                c.await();
            }
        } catch (Exception e) {
            e.getStackTrace();
        }
        lock.unlock();
    }
}
