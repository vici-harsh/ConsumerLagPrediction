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
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/accounts")
public class AdapterController {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Map<String, CompletableFuture<String>> responseMap = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(AdapterController.class);
    // Endpoint to create an account
    @PostMapping("/createAccount")
    public ResponseEntity<String> createAccount(@Valid @RequestBody AccountRequest accountRequest, HttpSession session) {
        String correlationId = UUID.randomUUID().toString();

        logger.info("Received create account request with correlationId: {}", correlationId);
        session.setAttribute("correlationId", correlationId);

        // Add correlation ID to the request
        accountRequest.setCorrelationId(correlationId);

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


    // Kafka listener for responses
    @KafkaListener(topics = "create-account-response-topic")
    public void listenCreateAccountResponse(String correlationId, Map<String, Object> responseMap) {
        CompletableFuture<String> responseFuture = this.responseMap.get(correlationId);
        logger.info("Received Kafka response for correlationId: {}", correlationId);
        if (responseFuture != null) {
            try {
                String status = (String) responseMap.get("status");
                if (status != null) {
                    responseFuture.complete(status);
                } else {
                    responseFuture.complete("error: status not found in response");
                }
            } catch (Exception e) {
                responseFuture.complete("error: invalid response format");
            }
        }
    }

}