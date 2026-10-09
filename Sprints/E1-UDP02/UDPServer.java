import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.ArrayList;

public class UDPServer {

    public static HashMap<Integer, String> mapaRecebidos = new HashMap<>();
    public static ArrayList<String> mensagensEntregues = new ArrayList<>();

    public static int processDeliveredMessages(
            int nLastMessageInOrder,
            int nCurrentMessage,
            String currentMessage
    ) {
        //coisas
        if (nCurrentMessage == nLastMessageInOrder + 1) {
            System.out.println("Mensagem entregue: " + currentMessage);
            mensagensEntregues.add(currentMessage);
            nLastMessageInOrder++;

            // entrega em cascata
            while (mapaRecebidos.containsKey(nLastMessageInOrder + 1)) {
                int proximoNumero = nLastMessageInOrder + 1;
                String proximaMensagem = mapaRecebidos.remove(proximoNumero);

                System.out.println(">Hashmap entregue: " + proximaMensagem);
                mensagensEntregues.add(proximaMensagem);
                nLastMessageInOrder++;
            }
        }
        else if (nCurrentMessage > nLastMessageInOrder + 1) {
            System.out.println(">Fora de ordem: " + currentMessage);
            mapaRecebidos.putIfAbsent(nCurrentMessage, currentMessage);
        }
        else {
            System.out.println(">Mensagem já recebida: " + currentMessage);
        }
        return nLastMessageInOrder;
    }

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



                L = processDeliveredMessages(L, numero, mensagem);

                resposta = "waitingfor," + (L + 1);

                byte[] dadosResposta = resposta.getBytes();
                reply = new DatagramPacket(
                        dadosResposta,
                        dadosResposta.length,
                        request.getAddress(),
                        request.getPort()
                );

                aSocket.send(reply);
            }
        } catch (SocketException e) { System.out.println("Socket: " + e.getMessage());
        } catch (IOException e)     { System.out.println("IO: " + e.getMessage());
        } finally { if (aSocket != null) aSocket.close(); }
    }
}