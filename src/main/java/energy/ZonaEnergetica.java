package energy;

import operators.CentroControl;
import operators.OperarioCarga;
import pcd.util.Ventana;
import storage.Bateria;
import storage.BateriaRenovable;

import java.awt.Color;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

import grpc.MonitorizacionGrpc;
import operators.OperarioRed;
import operators.RobotProductor;

import main.Config;

/**
 * Representa una unidad geográfica y operativa dentro de la red eléctrica.
 * Cada zona gestiona sus propios recursos de almacenamiento (baterías), 
 * generación (robots productores) y personal (operarios).
 */
public class ZonaEnergetica {
    
    // Identificación y Finanzas
    private final int idZona;
    private final CuentaEnergetica cuenta;
    private final RedEnergetica red;
    
    // Recursos de Energía (Baterías y Generación)
    private final Bateria bateria; // Batería rápida (Recurso compartido principal)
    private final BateriaRenovable bateriaSolar;
    private final BateriaRenovable bateriaEolica;
    private final RobotProductor robotSolar;
    private final RobotProductor robotEolica;
    
    // Gestión y Control
    private final CentroControl centroControl;
    private final Ventana ventana;
    
    // Comunicación y Sincronización Global
    private final MonitorizacionGrpc.MonitorizacionBlockingStub stub;
    private final CountDownLatch latchFin;
    private final CountDownLatch latchGrpc;
    
    // Mecanismos de Sincronización Local
    private Semaphore sOperarioRed;
    private CyclicBarrier bOperarioRed;
    private final Semaphore sConsumo = new Semaphore(Config.MAX_CONSUMOS);

    public ZonaEnergetica(int idZona, CuentaEnergetica cuenta, Bateria bateria, CentroControl centroControl,
            Ventana ventana, MonitorizacionGrpc.MonitorizacionBlockingStub stub, 
            CountDownLatch latchFin, CountDownLatch latchGrpc, RedEnergetica red) {
        
        this.idZona = idZona;
        this.cuenta = Objects.requireNonNull(cuenta);
        this.bateria = Objects.requireNonNull(bateria);
        this.centroControl = Objects.requireNonNull(centroControl);
        this.ventana = ventana;
        this.stub = stub;
        this.latchFin = latchFin;
        this.latchGrpc = latchGrpc;
        this.red = red;

        // Inicialización de recursos renovables y sus productores
        this.bateriaSolar = new BateriaRenovable(Config.CAPACIDAD_BATERIA, 0);
        this.bateriaEolica = new BateriaRenovable(Config.CAPACIDAD_BATERIA, 0);
        this.robotSolar = new RobotProductor("Solar", bateriaSolar);
        this.robotEolica = new RobotProductor("Eolica", bateriaEolica);

        // Arranque de la producción automática de energía
        robotSolar.start();
        robotEolica.start();

        inicializarPersonalZona();
    }

    /**
     * Configura y arranca los hilos de trabajo (Operarios) de la zona.
     */
    private void inicializarPersonalZona() {
        // Configuración de sincronización según el modo elegido
        if (Config.SYNC_MODE == 0) {
            sOperarioRed = new Semaphore(Config.NUMERO_OPERARIOS);
        } else {
            bOperarioRed = new CyclicBarrier(Config.NUMERO_OPERARIOS);
        }

        // Latch para asegurar que los operarios están listos antes de que el OperarioCarga empiece
        CountDownLatch latchOperarios = new CountDownLatch(Config.NUMERO_OPERARIOS);

        // Lanzamiento de los Operarios de Red
        for (int i = 0; i < Config.NUMERO_OPERARIOS; i++) {
            OperarioRed opRed = new OperarioRed(centroControl, this, sOperarioRed, bOperarioRed, 
                                               latchOperarios, stub, latchFin, latchGrpc);
            new Thread(opRed).start();
        }

        // Lanzamiento del Operario de Carga
        OperarioCarga opCarga = new OperarioCarga(this, latchOperarios);
        new Thread(opCarga).start();
    }

    // --- MÉTODOS DE GESTIÓN DE CONSUMOS ---

    /**
     * Recibe una solicitud de consumo y la deriva al centro de control
     * para que sea procesada por los operarios disponibles.
     */
    public String tramitarConsumo(Consumo c) {
        if (c.getZona() != idZona) {
            return "ERROR: consumo dirigido a zona " + c.getZona() + " pero tramitado en zona " + idZona;
        }

        ventana.traza(c.getIdConsumo() + " solicita: " + c.getTotalKWh() + " kWh", Color.BLUE);
        
        // Delegamos el trabajo al sistema de colas del Centro de Control
        return centroControl.addTrabajo(c);
    }

    // --- MÉTODOS DE ACCESO (GETTERS) ---

    public int getIdZona() { return idZona; }
    public CuentaEnergetica getCuenta() { return cuenta; }
    public RedEnergetica getRed() { return red; }
    public Bateria getBateria() { return bateria; }
    public BateriaRenovable getBateriaSolar() { return bateriaSolar; }
    public BateriaRenovable getBateriaEolica() { return bateriaEolica; }
    public CentroControl getCentroControl() { return centroControl; }
    public Semaphore getSConsumo() { return sConsumo; }
    public Ventana getVentana() { return ventana; }

    // --- MÉTODOS DE TRAZABILIDAD ---

    public void traza(String s) { ventana.traza(s); }
    public void traza(String s, Color color) { ventana.traza(s, color); }

    @Override
    public String toString() {
        return "ZonaEnergetica{id=" + idZona + ", balance=" + cuenta.getBalanceKWh() + "}";
    }
}
