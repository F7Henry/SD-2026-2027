
package rep02;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.Base64;

public class IpfsPubSub {

    private final String api;
    private final HttpClient client = HttpClient.newHttpClient();

    public IpfsPubSub(String api) {
        this.api = api.endsWith("/")
                ? api.substring(0, api.length() - 1)
                : api;
    }

    private String encodeTopic(String topic) {
        return "u" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(topic.getBytes(StandardCharsets.UTF_8));
    }

    public void publish(String topic, String message)
            throws IOException, InterruptedException {

        String encodedTopic = URLEncoder.encode(
                encodeTopic(topic), StandardCharsets.UTF_8);

        String url = api + "/pubsub/pub?arg=" + encodedTopic;

        String boundary = "----JavaBoundary7MA4YWxk";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"data\"; filename=\"message.txt\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n"
                + message + "\r\n"
                + "--" + boundary + "--\r\n";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Origin", "http://127.0.0.1:5001")
                .header("Content-Type",
                        "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(
                        body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new IOException(
                    "Erro ao publicar: " + response.statusCode()
                    + " - " + response.body());
        }
    }

    public void subscribe(String topic, Consumer<String> onMessage)
            throws IOException, InterruptedException {

        String encodedTopic = URLEncoder.encode(
                encodeTopic(topic), StandardCharsets.UTF_8);

        String url = api + "/pubsub/sub?arg=" + encodedTopic;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Origin", "http://127.0.0.1:5001")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException(
                    "Erro na subscriÃ§Ã£o: " + response.statusCode());
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        response.body(), StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String data = extractData(line);

                if (data == null) {
                    continue;
                }

                try {
                    if (data.startsWith("u")) data = data.substring(1);
                    onMessage.accept(new String(Base64.getUrlDecoder().decode(data),
                            StandardCharsets.UTF_8));

                } catch (IllegalArgumentException e) {
                    System.out.println(
                            "Mensagem PubSub com codificaÃ§Ã£o invÃ¡lida.");
                }
            }
        }
    }

    private String extractData(String jsonLine) {
        String marker = "\"data\":\"";
        int start = jsonLine.indexOf(marker);

        if (start < 0) {
            return null;
        }

        start += marker.length();
        int end = jsonLine.indexOf('"', start);

        if (end < 0) {
            return null;
        }

        return jsonLine.substring(start, end);
    }
}
