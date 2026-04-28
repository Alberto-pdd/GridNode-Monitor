package grpc.service;

import grpc.PreciosGrpc.PreciosImplBase;
import grpc.PreciosProto.PreciosReply;
import grpc.PreciosProto.PreciosRequest;
import io.grpc.stub.StreamObserver;


public class PreciosService extends PreciosImplBase {
    @Override
    public StreamObserver<PreciosRequest> calcularPrecios(StreamObserver<PreciosReply> responseObserver) {
        return super.calcularPrecios(responseObserver);
    }
}
