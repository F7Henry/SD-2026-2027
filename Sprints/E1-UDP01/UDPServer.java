import java.net.*;
import java.io.*;
import java.util.HashMap;

public class UDPServer {

    public static HashMap<Integer, String> mapaRecebidos = new HashMap<>();

    public static int processDeliveredMessages(
            int nLastMessageInOrder,
            int nCurrentMessage,
            String currentMessage
    ) {
        //coisas
        if (nCurrentMessage == nLastMessageInOrder + 1) {
            //coisas
            System.out.println("Mensagem entregue: " + currentMessage);
            nLastMessageInOrder++;
        }
        else if (nCurrentMessage > nLastMessageInOrder + 1) {
            //coisas
            System.out.println("Fora de ordem: " + currentMessage);
            System.out.println("  > Aguardando mensagem de número: " + (nLastMessageInOrder + 1));
            mapaRecebidos.putIfAbsent(nCurrentMessage, currentMessage);
        }
        else {
            //coisas
            System.out.println("Mensagem já recebida!");
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



                int novoL = processDeliveredMessages(L, numero, mensagem);
                if (novoL != L) {
                    L = novoL;
                    while (mapaRecebidos.containsKey(L + 1)) {
                        int proximoNumero = L + 1;
                        String proximaMensagem = mapaRecebidos.remove(proximoNumero);
                        L = processDeliveredMessages(L, proximoNumero, proximaMensagem);
                    }
                    resposta = "delivered," + L;
                }
                else {
                    resposta = "waitingfor," + (L + 1);
                }

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