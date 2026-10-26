import java.net.*;
import java.io.*;
import java.util.Scanner;

public class UDPClient {

    public static void main(String args[]) {
        DatagramSocket aSocket = null;


        try {
            aSocket = new DatagramSocket();

            InetAddress aHost = InetAddress.getByName("localhost");
            int serverPort = 6789;

            Scanner scanner = new Scanner(System.in);
            String message;

            while (true) {
                System.out.print("Mensagem (ou 'sair'): ");
                message = scanner.nextLine();

                if (message.equalsIgnoreCase("sair")) {
                    break;
                }

                byte[] m = message.getBytes();
                DatagramPacket request = new DatagramPacket(m, m.length, aHost, serverPort);
                aSocket.send(request);

                byte[] buffer = new byte[1000];
                DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                aSocket.receive(reply);

                System.out.println("Reply: " + new String(reply.getData()));
            }

        } catch (SocketException e) { System.out.println("Socket: " + e.getMessage());
        } catch (IOException e)     { System.out.println("IO: " + e.getMessage());
        } finally { if (aSocket != null) aSocket.close(); }
    }
}