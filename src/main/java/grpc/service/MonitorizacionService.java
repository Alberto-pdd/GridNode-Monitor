package grpc.service;

import java.util.ArrayList;
import java.util.List;

import energy.Demanda;
import grpc.MonitorizacionGrpc.MonitorizacionImplBase;
import grpc.MonitorizacionProto.ConsumoReply;
import grpc.MonitorizacionProto.ConsumoRequest;
import grpc.MonitorizacionProto.DemandaReply;
import grpc.MonitorizacionProto.DemandaRequest;
import grpc.MonitorizacionProto.DireccionReply;
import grpc.MonitorizacionProto.DireccionRequest;
import io.grpc.Server;
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
        imprimirLogServidorUnary(request);

        // 2. Procesamiento de datos y actualización de acumuladores
        ConsumoReply respuesta = procesarYRegistrar(request);
    
        // 3. Envío de la respuesta al cliente y cierre del canal
        responseObserver.onNext(respuesta);
        responseObserver.onCompleted();
    }

    public void demandaSolar(DemandaRequest request, StreamObserver<DemandaReply> responseObserver) {
        //1. Mostrar info de trazabilidad en Servidor
        imprimirLogServidorServerStream(request);

        // 2. Procesamiento de datos y envios de respuesta al cliente + mostrar consumos en Servidor
        serverStreamingProcesarDatos(request, responseObserver);
         
        // 3. Cierre del canal
        responseObserver.onCompleted();
    }

    // Este método no devuelve un resultado, devuelve un objeto que reacciona a los datos del cliente.
    public StreamObserver<DireccionRequest> consumosDireccion(StreamObserver<DireccionReply> responseObserver) {
        
        // Retornamos un "escuchador" de direcciones
        return new StreamObserver<DireccionRequest>() {
            
            // El contador es local a esta conexión específica
            int direccionesRegistranSolar = 0;
            
            public void onNext(DireccionRequest direccionRequest) {
                // Se ejecuta CADA VEZ que el cliente envía UNA dirección
                // Flag de direccion con solar
                boolean direccionRegistrada = false;

                imprimirLogServidorClientStraming(direccionRequest);

                synchronized (historialConsumos) {
                    for (ConsumoRequest consumoRequest : historialConsumos) {
                        if (consumoRequest.getDireccion().equals(direccionRequest.getDireccion()) && consumoRequest.getSolar()) {
                            direccionesRegistranSolar++;
                            direccionRegistrada = true;
                            System.out.println("Direccion [" + direccionRequest.getDireccion() + "] registra demanda de energia solar con identificador -> [" + consumoRequest.getIdConsumo() + "]");
                            break; // Si encontramos un registro solar para esta dirección, no necesitamos seguir buscando
                        } 
                    } 

                    if (!direccionRegistrada) {
                        System.out.println("Direccion [" + direccionRequest.getDireccion() + "] NO registra demanda de energia solar.");
                    }
                }
            }
            
            public void onCompleted() {
                // Se ejecuta cuando el cliente TERMINA de enviar su lista
                // Aquí construyes el DireccionReply con el 'contador' y lo envías
                DireccionReply reply = DireccionReply.newBuilder()
                    .setTotal(direccionesRegistranSolar)
                    .build();

                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
            
            public void onError(Throwable t) { 
                // Manejo de errores de conexión
                t.fillInStackTrace();
            }
        };
    }



    // ----------------------- METODOS Unary -----------------------

    /**
     * Muestra por pantalla los detalles de la petición recibida.
     */
    private void imprimirLogServidorUnary(ConsumoRequest request) {
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

    private void imprimirLogServidorServerStream(DemandaRequest request) {
        System.out.println("[gRPC Server] Consultando historial solar para Zona: " + request.getIdZona());        
    }

    private void serverStreamingProcesarDatos(DemandaRequest request, StreamObserver<DemandaReply> responseObserver) {
        int idZona = request.getIdZona();
        synchronized (historialConsumos) {
            for (ConsumoRequest consumoRequest : historialConsumos) {
                        String idConsumo = (idZona == consumoRequest.getIdZona() && consumoRequest.getSolar()) 
                                            ? consumoRequest.getIdConsumo() : null;

                if(idConsumo != null) {
                    DemandaReply reply = DemandaReply.newBuilder()
                        .setIdConsumo(idConsumo)
                        .build();

                        responseObserver.onNext(reply);
                        System.out.println("  [Stream] Enviando ID: " + idConsumo);
                } 
            } 
        }
    }

    // ----------------------- METODOS Client Streaming -----------------------

    private void imprimirLogServidorClientStraming(DireccionRequest request) {
        System.out.println("[gRPC Server] Consultando registros de demanda solar para la Direccion [" + request.getDireccion() + "]");
    }
}
