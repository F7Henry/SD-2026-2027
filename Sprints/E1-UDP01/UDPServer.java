import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class UDPServer {

    public static void main(String args[]) {
        DatagramSocket aSocket = null;
        int L = 0;

        try {
            aSocket = new DatagramSocket(6789);
            byte[] buffer = new byte[1000];

            while (true) {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                aSocket.receive(request);

                String raw = new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);
                String response;

                try {
                    int comma = raw.indexOf(',');
                    if (comma <= 0 || comma == raw.length() - 1) {
                        throw new IllegalArgumentException("Mensagem mal formada");
                    }

                    String seqText = raw.substring(0, comma).trim();
                    int seq = Integer.parseInt(seqText);
                    String message = raw.substring(comma + 1);

                    if (seq == L + 1) {
                        response = message;
                        L = seq;
                    } else {
                        response = "waitingfor," + (L + 1);
                    }
                } catch (Exception e) {
                    response = "waitingfor," + (L + 1);
                }

                byte[] replyBytes = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(replyBytes, replyBytes.length,
                        request.getAddress(), request.getPort());
                aSocket.send(reply);
            }
        } catch (SocketException e) { System.out.println("Socket: " + e.getMessage());
        } catch (IOException e)     { System.out.println("IO: " + e.getMessage());
        } finally { if (aSocket != null) aSocket.close(); }
    }
}