package grpc.service;

import java.util.ArrayList;
import java.util.List;
import grpc.MonitorizacionGrpc.MonitorizacionImplBase;
import grpc.MonitorizacionProto.ConsumoReply;
import grpc.MonitorizacionProto.ConsumoRequest;
import io.grpc.stub.StreamObserver;
import main.Config;

/**
 * Servicio gRPC para la monitorización centralizada de la red energética.
 * Este servidor centraliza los datos de consumo de todas las zonas y mantiene
 * un registro histórico y acumulado de la energía consumida.
 */
public class MonitorizacionService extends MonitorizacionImplBase {
    
    // Lista para almacenar todos los registros recibidos (Historial)
    private final List<ConsumoRequest> historialConsumos = new ArrayList<>();
    
    // Acumuladores globales para la red y específicos por zona
    private double acumuladoRed = 0.0;
    private final double[] acumuladosZonas = new double[Config.NUM_ZONAS];

    /**
     * RPC UNARIO: Recibe un registro de consumo de un Operario de Red,
     * actualiza los totales globales y devuelve el estado actual de la red.
     * 
     * Nota de Concurrencia: Se utiliza un bloque sincronizado para asegurar que
     * las actualizaciones de los acumuladores sean atómicas, evitando condiciones
     * de carrera cuando múltiples hilos de diferentes zonas notifican simultáneamente.
     */
    @Override
    public void anotarConsumo(ConsumoRequest request, StreamObserver<ConsumoReply> responseObserver) {
        
        // 1. Mostrar información en la consola del servidor para trazabilidad
        imprimirLogServidor(request);

        // 2. Procesamiento de datos y actualización de acumuladores
        ConsumoReply respuesta = procesarYRegistrar(request);
    
        // 3. Envío de la respuesta al cliente y cierre del canal
        responseObserver.onNext(respuesta);
        responseObserver.onCompleted();
    }

    // TODO: Implementar demandaSolar (Server Streaming)
    // public void demandaSolar(...) { }

    // TODO: Implementar consumosDireccion (Client Streaming)
    // public void consumosDireccion(...) { }



    // ----------------------- METODOS Unary -----------------------

    /**
     * Muestra por pantalla los detalles de la petición recibida.
     */
    private void imprimirLogServidor(ConsumoRequest request) {
        String solarTag = request.getSolar() ? "[SOLAR]" : "[OTRO]";
        String mensaje = "[gRPC Server] Registro: " + request.getIdConsumo() + 
                         " | Zona: " + request.getIdZona() + 
                         " | " + request.getKWh() + " kWh | " + solarTag;
        System.out.println(mensaje);
    }

    /**
     * Realiza la actualización de los datos compartidos de forma segura.
     * @return Objeto ConsumoReply con los totales actualizados.
     */
    private synchronized ConsumoReply procesarYRegistrar(ConsumoRequest request) {
        int idZona = request.getIdZona();
        double kWh = request.getKWh();

        // Registro en historial y actualización de acumuladores
        historialConsumos.add(request);
        acumuladoRed += kWh;
        acumuladosZonas[idZona] += kWh;

        // Construcción de la respuesta con el estado actual
        return ConsumoReply.newBuilder()
            .setIdZona(idZona)
            .setTotalRed(acumuladoRed)
            .setTotalZona(acumuladosZonas[idZona])
            .build();
    }
    

    // ----------------------- METODOS Server Streaming -----------------------


    // ----------------------- METODOS Client Streaming -----------------------


}
