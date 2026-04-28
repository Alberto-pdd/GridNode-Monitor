package grpc.server;

import java.io.IOException;

import grpc.service.PreciosService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class PreciosServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Creamos el servidor
        Server server = ServerBuilder.forPort(9004)
                .addService(new PreciosService())
                .build();

        // 2. Lo iniciamos
        System.out.println("Servidor de Precios encendido en el puerto 9004...");
        server.start();

        // 3. Hacemos que no se cierre
        server.awaitTermination();
    }
}
