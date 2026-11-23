package tcp01;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class TCPClient {
    public static void main(String[] args) {
        String hostname = "localhost";
        int serverPort = 7896;
        Socket s = null;

        try {
            s = new Socket(hostname, serverPort);

            // 1. Criar ObjectOutputStream e enviar o cabeçalho imediatamente
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            out.flush();

            // 2. Criar DataInputStream para receber a resposta textual
            DataInputStream in = new DataInputStream(s.getInputStream());

            // 3. Juntar Person com Place
            Place place = new Place("3500-606", "Viseu");
            Person p = new Person("Ana Maria", place, 2001);
            System.out.println("A enviar objeto: " + p);

            out.writeObject(p);
            out.flush();

            // 4. Ler e imprimir a resposta enviada pelo servidor
            String resposta = in.readUTF();
            System.out.println("Resposta do servidor (localidade): " + resposta);

        } catch (EOFException e) {
            System.err.println("Servidor encerrou a ligação (EOF): " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Erro no cliente: " + e.getMessage());
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar o socket do cliente: " + e.getMessage());
                }
            }
        }
    }
}