package tcp01;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
    public static void main(String[] args) {
        int serverPort = 7896;

        try (ServerSocket listenSocket = new ServerSocket(serverPort)) {
            System.out.println("Servidor de escuta iniciado na porta " + serverPort + "...");

            while (true) {
                // Bloqueia à espera de um novo cliente
                Socket clientSocket = listenSocket.accept();
                System.out.println("Conexão aceite de: " + clientSocket.getRemoteSocketAddress());

                // Delega o processamento do cliente para uma thread dedicada
                new Connection(clientSocket);
            }
        } catch (IOException e) {
            System.err.println("Erro de escuta no ServerSocket: " + e.getMessage());
        }
    }
}