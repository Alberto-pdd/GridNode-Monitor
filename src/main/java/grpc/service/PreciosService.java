package grpc.service;

import grpc.PreciosGrpc.PreciosImplBase;
import grpc.PreciosProto.PreciosReply;
import grpc.PreciosProto.PreciosRequest;
import io.grpc.stub.StreamObserver;
import grpc.PreciosProto.DemandaRequest;


public class PreciosService extends PreciosImplBase {

    double precioRapida = 0.15;
    double precioEolica = 0.10;
    double precioSolar = 0.08;

    @Override
    public StreamObserver<PreciosRequest> calcularPrecios(StreamObserver<PreciosReply> responseObserver) {
        return new StreamObserver<PreciosRequest>() {
            @Override
            public void onNext(PreciosRequest request) {
                System.out.println("Calculando precio para Consumo ID: " + request.getIdConsumo());
                
                double precioTotal = 0;

                for (DemandaRequest demandaRequest : request.getDemandasList()) {
                    if (demandaRequest.getIdTipo().equalsIgnoreCase("SOLAR")) {
                        precioTotal = precioTotal + demandaRequest.getKWh()*precioSolar;
                    }
                    else if (demandaRequest.getIdTipo().equalsIgnoreCase("EOLICA")) {
                        precioTotal = precioTotal + demandaRequest.getKWh()*precioEolica;
                    }
                    else {
                        // Si es de cualquier otro tipo, se entiende como "Rapida"
                        precioTotal = precioTotal + demandaRequest.getKWh()*precioRapida;
                    }
                }

                PreciosReply reply = PreciosReply.newBuilder()
                    .setIdConsumo(request.getIdConsumo())
                    .setPrecio(precioTotal)
                    .build();

                responseObserver.onNext(reply);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }
        };
    }
}
