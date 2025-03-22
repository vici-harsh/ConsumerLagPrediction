package com.research.processing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SlackNotifier {
    private static final String SLACK_WEBHOOK_URL = "https://hooks.slack.com/services/your/webhook/url";

    public static void sendAlert(String message) {
        String payload = String.format("{\"text\":\"%s\"}", message);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SLACK_WEBHOOK_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Slack alert sent: " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}