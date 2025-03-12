package com.research.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/accounts")
public class AdapterController {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Map<String, CompletableFuture<String>> responseMap = new HashMap<>();


    // Endpoint to create an account
    @PostMapping("/createAccount")
    public ResponseEntity<String> createAccount(@RequestBody Map<String, String> accountRequest, HttpSession session) {
        String correlationId = UUID.randomUUID().toString();
        session.setAttribute("correlationId", correlationId);

        // Add correlation ID to the request
        accountRequest.put("correlationId", correlationId);

        // Send request to Kafka
        kafkaTemplate.send("create-account-topic", correlationId, accountRequest);

        // Wait for response from Kafka
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        responseMap.put(correlationId, responseFuture);

        try {
            String response = responseFuture.get(); // Block until response is received
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error creating account: " + e.getMessage());
        } finally {
            responseMap.remove(correlationId);
        }
    }

    // Endpoint to fetch account details
    @PostMapping("/fetchDetail")
    public ResponseEntity<String> fetchDetail(@RequestBody Map<String, String> fetchRequest, HttpSession session) {
        String correlationId = UUID.randomUUID().toString();
        session.setAttribute("correlationId", correlationId);

        // Add correlation ID to the request
        fetchRequest.put("correlationId", correlationId);

        // Send request to Kafka
        kafkaTemplate.send("fetch-detail-topic", correlationId, fetchRequest);

        // Wait for response from Kafka
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        responseMap.put(correlationId, responseFuture);

        try {
            String response = responseFuture.get(); // Block until response is received
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching account details: " + e.getMessage());
        } finally {
            responseMap.remove(correlationId);
        }
    }

    // Kafka listener for responses
    @KafkaListener(topics = "create-account-response-topic")
    public void listenCreateAccountResponse(String correlationId, String response) {
        CompletableFuture<String> responseFuture = responseMap.get(correlationId);
        if (responseFuture != null) {
            responseFuture.complete(response); // Complete the future with the response
        }
    }

    @KafkaListener(topics = "fetch-detail-response-topic")
    public void listenFetchDetailResponse(String correlationId, String response) {
        CompletableFuture<String> responseFuture = responseMap.get(correlationId);
        if (responseFuture != null) {
            responseFuture.complete(response); // Complete the future with the response
        }
    }

    @PostMapping("/request")
    public ResponseEntity<String> handleRequest(@RequestBody String requestBody, HttpSession session) {
        String correlationId = UUID.randomUUID().toString();
        session.setAttribute("correlationId", correlationId);

        // Send request to Kafka
        kafkaTemplate.send("request-topic", correlationId, requestBody);

        // Wait for response from Kafka
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        responseMap.put(correlationId, responseFuture);

        try {
            String response = responseFuture.get(); // Block until response is received
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing request");
        } finally {
            responseMap.remove(correlationId);
        }
    }

    @KafkaListener(topics = "response-topic")
    public void listenResponse(String correlationId, String response) {
        CompletableFuture<String> responseFuture = responseMap.get(correlationId);
        if (responseFuture != null) {
            responseFuture.complete(response); // Complete the future with the response
        }
    }
}