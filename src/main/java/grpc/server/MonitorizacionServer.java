package grpc.server;

import grpc.service.MonitorizacionService;

import java.io.IOException;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class MonitorizacionServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Creamos el servidor
        Server server = ServerBuilder.forPort(9002)
                .addService(new MonitorizacionService())
                .build();

        // 2. Lo iniciamos
        System.out.println("Servidor de Monitorizacion encendido en el puerto 9002...");
        server.start();

        // 3. Hacemos que no se cierre
        server.awaitTermination();
    }
}