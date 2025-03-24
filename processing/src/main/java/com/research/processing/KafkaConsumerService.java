package com.research.processing;

import com.research.adapter.AccountRequest;
import com.research.adapter.AccountResponse;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private RedisService redisService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Handle lag data for monitoring
    @KafkaListener(topics = "consumer-lag", groupId = "lag-monitor")
    public void consume(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);

            // Safe way to get values with defaults
            long timestamp = jsonNode.path("timestamp").asLong();  // Changed from get() to path()
            double actualLag = jsonNode.path("actualLag").asDouble();
            double predictedLag = jsonNode.path("predictedLag").asDouble(0);  // Default value 0 if missing

            // Store data
            String consumerGroupId = "lag-monitor";
            redisService.saveLagData(consumerGroupId, timestamp, "actual", actualLag);

            if (predictedLag > 0) {  // Only store if we got a valid prediction
                redisService.saveLagData(consumerGroupId, timestamp, "predicted", predictedLag);
            }

        } catch (Exception e) {
            logger.error("Failed to process lag monitoring message", e);
        }
    }

    // Handle account creation requests
    @KafkaListener(topics = "create-account-topic", groupId = "consumer-group-1",
            containerFactory = "kafkaListenerContainerFactory")
    public void listenCreateAccountRequest(ConsumerRecord<String, AccountRequest> record) {
        try {
            String correlationId = record.key();
            AccountRequest request = record.value();

            if (request == null) {
                logger.error("Deserialization failed. Received null AccountRequest for correlationId: {}", correlationId);
                return;
            }

            logger.info("Received Kafka message with correlationId: {}, Timestamp: {}", correlationId, request.getTimestamp());

            redisService.storeData(correlationId, request);

            AccountResponse response = new AccountResponse();
            response.setCorrelationId(correlationId);
            response.setStatus("success");
            response.setMessage("Account created successfully");

            kafkaTemplate.send("create-account-response-topic", correlationId, response);
            logger.info("Sent success response for correlationId: {}", correlationId);
        } catch (Exception e) {
            logger.error("Error processing Kafka message: {}", e.getMessage(), e);

            AccountResponse errorResponse = new AccountResponse();
            errorResponse.setCorrelationId(record.key());
            errorResponse.setStatus("failure");
            errorResponse.setMessage("Account creation failed due to internal error");

            kafkaTemplate.send("create-account-response-topic", record.key(), errorResponse);
        }
    }
}
