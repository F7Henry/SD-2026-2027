package tcp01;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

public class Connection extends Thread {
    private Socket clientSocket;

    public Connection(Socket aClientSocket) {
        clientSocket = aClientSocket;
        this.start();
    }

    @Override
    public void run() {
        try {
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            Object obj = in.readObject();

            if (obj instanceof Person) {
                Person person = (Person) obj;
                System.out.println("Servidor recebeu o objeto: " + person);
                out.writeUTF(person.getPlace().getLocality());
                out.flush();
            } else {
                out.writeUTF("Erro: Objeto recebido não é do tipo Person.");
                out.flush();
            }

        } catch (ClassNotFoundException e) {
            System.err.println("Classe não encontrada na desserialização: " + e.getMessage());
        } catch (EOFException e) {
            System.err.println("Cliente encerrou a ligação (EOF): " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Erro de I/O na Connection: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Erro ao fechar o socket do cliente: " + e.getMessage());
            }
        }
    }
}