package grpc.client;

import java.util.Iterator;

import grpc.MonitorizacionGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import grpc.MonitorizacionProto.DemandaReply;
import grpc.MonitorizacionProto.DemandaRequest;
import grpc.MonitorizacionProto.DireccionReply;
import grpc.MonitorizacionProto.DireccionRequest;
import main.Config;

public class ClienteConsultor {
    public static void main(String[] args) {
        System.out.println("Cliente Consultor iniciado...");
        
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9002)
            .usePlaintext()
            .build();

        MonitorizacionGrpc.MonitorizacionBlockingStub stub = MonitorizacionGrpc.newBlockingStub(channel);
        MonitorizacionGrpc.MonitorizacionStub asyncStub = MonitorizacionGrpc.newStub(channel); 

        int numZonas = Config.NUM_ZONAS; 

        
        try {  
            System.out.println("\n------------- Mostrando los IDs con Demanda Solar -------------\n");
            for(int i = 0; i < numZonas; i++) {
                DemandaRequest demandaRequest = DemandaRequest.newBuilder()
                    .setIdZona(i)
                    .build();

                Iterator<DemandaReply> iterator = stub.demandaSolar(demandaRequest);

                while (iterator.hasNext()) {
                    DemandaReply reply = iterator.next();

                    System.out.println("ID Recibido: " + reply.getIdConsumo());
                }
            }

            System.out.println("\n------------- Mostrando las Direcciones con Registros de Demanda Solar -------------\n");
        
            StreamObserver<DireccionReply> direccionesReply = new StreamObserver<DireccionReply>() {
                @Override
                public void onNext(DireccionReply reply) {
                    System.out.println("Las Direcciones con registros de Demanda Solar son: " + reply.getTotal());
                }

                @Override
                public void onError(Throwable t) {
                    t.printStackTrace();
                }

                @Override
                public void onCompleted() {
                    System.out.println("Completado");
                }
            };

            StreamObserver<DireccionRequest> requestObserver = asyncStub.consumosDireccion(direccionesReply);

            String[] calles = {"Avenida de la Universidad", "Berna, 11", "Gran Via, 22", "Caceres, 5", "Ruta de la Plata, 9", "Calle falsa 123"};
            
            for (String calle : calles) {
                requestObserver.onNext(DireccionRequest.newBuilder().setDireccion(calle).build());
            }
            
            // IMPORTANTE: se termina el flujo que se envia
            requestObserver.onCompleted();


        } finally {

            // Esperamos un poco para que se procesen las respuestas antes de cerrar el canal
            try {
                Thread.sleep(500);
            } catch (Exception e) {}

            channel.shutdown();

            System.out.println("\n------------------------------------------------------------------------------------");
        }
    }
}
