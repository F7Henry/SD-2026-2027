import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UDPClient {

    public static void main(String args[]) {
        DatagramSocket aSocket = null;
        Scanner sc = new Scanner(System.in);
        int nextSeq = 1;

        try {
            aSocket = new DatagramSocket();
            InetAddress aHost = InetAddress.getByName("localhost");
            int serverPort = 6789;

            while (true) {
                System.out.println("Modo (auto/manual/sair):");
                String mode = sc.nextLine().trim().toLowerCase();

                if (mode.equals("sair") || mode.equals("exit") || mode.equals("fim")) {
                    break;
                }

                int seq = nextSeq;
                if (mode.equals("manual") || mode.equals("m")) {
                    System.out.print("Numero de sequencia: ");
                    String input = sc.nextLine().trim();
                    try {
                        seq = Integer.parseInt(input);
                    } catch (NumberFormatException e) {
                        System.out.println("Numero invalido. Tente novamente.");
                        continue;
                    }
                } else if (mode.equals("auto") || mode.equals("a") || mode.isEmpty()) {
                    seq = nextSeq;
                    nextSeq++;
                } else {
                    System.out.println("Modo invalido.");
                    continue;
                }

                System.out.print("Mensagem: ");
                String message = sc.nextLine();
                if (message.equalsIgnoreCase("sair") || message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("fim")) {
                    break;
                }

                String payload = seq + "," + message;
                byte[] m = payload.getBytes(StandardCharsets.UTF_8);
                DatagramPacket request = new DatagramPacket(m, m.length, aHost, serverPort);
                aSocket.send(request);

                byte[] buffer = new byte[1000];
                DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                aSocket.receive(reply);

                String response = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8);

                if (response.startsWith("waitingfor,")) {
                    System.out.println("WAITING FOR " + response.substring("waitingfor,".length()));
                } else {
                    System.out.println("ECHO: " + response);
                }
            }

        } catch (SocketException e) { System.out.println("Socket: " + e.getMessage());
        } catch (IOException e)     { System.out.println("IO: " + e.getMessage());
        } finally {
            sc.close();
            if (aSocket != null) aSocket.close();
        }
    }
}