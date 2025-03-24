package com.research.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        accountRequest.setTimestamp(System.currentTimeMillis());
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

    @KafkaListener(topics = "create-account-response-topic", containerFactory = "kafkaListenerContainerFactory")
    public void listenCreateAccountResponse(ConsumerRecord<String, String> record) {
        try {
            String message = record.value();

            if (isJson(message)) {
                ObjectMapper objectMapper = new ObjectMapper();
                AccountResponse response = objectMapper.readValue(message, AccountResponse.class);
                logger.info("Received AccountResponse - CorrelationId: {}, Status: {}", response.getCorrelationId(), response.getStatus());
            } else {
                logger.info("Received Non-JSON Message - {}", message);
            }
        } catch (Exception e) {
            logger.error("Error during message processing: {}", e.getMessage(), e);
        }
    }

    private boolean isJson(String message) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.readTree(message);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
