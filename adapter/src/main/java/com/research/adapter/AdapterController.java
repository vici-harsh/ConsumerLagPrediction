package com.research.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/accounts")
public class AdapterController {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Map<String, CompletableFuture<String>> responseMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(AdapterController.class);

    @PostMapping("/createAccount")
    public ResponseEntity<String> createAccount(@Valid @RequestBody AccountRequest accountRequest) {
        String correlationId = UUID.randomUUID().toString();
        logger.info("Received create account request with correlationId: {}", correlationId);

        accountRequest.setCorrelationId(correlationId);
        kafkaTemplate.send("create-account-topic", correlationId, accountRequest);
        logger.info("Sent request to Kafka with correlationId: {}", correlationId);

        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        responseMap.put(correlationId, responseFuture);

        try {
            String response = responseFuture.get(30, TimeUnit.SECONDS); // Timeout after 30 seconds
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error while waiting for response: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error creating account: " + e.getMessage());
        } finally {
            responseMap.remove(correlationId);
        }
    }

    @KafkaListener(topics = "create-account-response-topic", containerFactory = "accountResponseKafkaListenerContainerFactory")
    public void listenCreateAccountResponse(ConsumerRecord<String, AccountResponse> record) {
        try {
            String correlationId = record.key();
            AccountResponse response = record.value();
            logger.info("Received Kafka response for correlationId: {}, Status: {}", correlationId, response.getStatus());

            CompletableFuture<String> responseFuture = responseMap.get(correlationId);
            if (responseFuture != null) {
                responseFuture.complete(response.getStatus());
            } else {
                logger.warn("No pending request found for correlationId: {}", correlationId);
            }
        } catch (ClassCastException e) {
            logger.error("ClassCastException: Failed to deserialize AccountResponse. Check the consumer configuration.", e);
        } catch (Exception e) {
            logger.error("Unexpected error while processing Kafka response: ", e);
        }
    }

}
