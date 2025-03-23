package com.research.processing;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class SlackNotifier {
    private static final String SLACK_WEBHOOK_URL = "https://hooks.slack.com/services/your/webhook/url";

    public static void sendAlert(String message) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SLACK_WEBHOOK_URL);
            String payload = String.format("{\"text\":\"%s\"}", message);

            // Set headers and payload
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(payload));

            // Send the request
            HttpResponse response = httpClient.execute(httpPost);
            System.out.println("Slack alert sent: " + response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}