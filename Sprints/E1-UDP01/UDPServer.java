import java.net.*;
import java.io.*;

public class UDPServer {

    public static void main(String args[]) {
        DatagramSocket aSocket = null;

        try {
            aSocket = new DatagramSocket(6789);
            byte[] buffer = new byte[1000];
            String resposta;
            int L = 0;

            while (true) {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                aSocket.receive(request);

                String mensagem = new String(
                        request.getData(),
                        0,
                        request.getLength()
                );

                String[] partes = mensagem.split(",", 2);
                int numero;
                DatagramPacket reply;

                if (partes.length < 2){
                    resposta = "erro no formato da mensagem";
                    byte[] dadosResposta = resposta.getBytes();
                    reply = new DatagramPacket(
                            dadosResposta,
                            dadosResposta.length,
                            request.getAddress(),
                            request.getPort()
                    );
                    aSocket.send(reply);
                    continue;
                }

                try {
                    numero = Integer.parseInt(partes[0]);
                }
                catch (NumberFormatException e) {
                    resposta = "erro no formato da mensagem";
                    byte[] dadosResposta = resposta.getBytes();
                    reply = new DatagramPacket(
                            dadosResposta,
                            dadosResposta.length,
                            request.getAddress(),
                            request.getPort()
                    );
                    aSocket.send(reply);
                    continue;
                }




                if (numero == L + 1) {

                    reply = new DatagramPacket(request.getData(),
                            request.getLength(), request.getAddress(), request.getPort());
                    L = numero;
                }
                else {
                    resposta = "waitingfor," + (L + 1);
                    byte[] dadosResposta = resposta.getBytes();
                    reply = new DatagramPacket(
                            dadosResposta,
                            dadosResposta.length,
                            request.getAddress(),
                            request.getPort()
                    );
                }
                aSocket.send(reply);
            }
        } catch (SocketException e) { System.out.println("Socket: " + e.getMessage());
        } catch (IOException e)     { System.out.println("IO: " + e.getMessage());
        } finally { if (aSocket != null) aSocket.close(); }
    }
}