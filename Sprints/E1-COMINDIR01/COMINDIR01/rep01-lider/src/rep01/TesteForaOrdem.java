
package rep01;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

public class TesteForaOrdem {

    public static void main(String[] args) throws Exception {
        InetAddress group = InetAddress.getByName("230.0.0.1");

        try (MulticastSocket socket = new MulticastSocket()) {
            String mensagem = "isto-nao-e-um-registo";

            byte[] data = mensagem.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                group,
                6789
            );

            socket.send(packet);

            System.out.println("Mensagem inválida enviada: " + mensagem);
        }
    }
}
