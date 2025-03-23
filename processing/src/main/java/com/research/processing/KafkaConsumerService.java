package com.research.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.adapter.AccountRequest;
import com.research.adapter.AccountResponse;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private RedisService redisService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper; // Inject ObjectMapper for JSON serialization

    @KafkaListener(topics = "create-account-topic", groupId = "consumer-group-1")
    public void listenCreateAccountRequest(ConsumerRecord<String, AccountRequest> record) {
        try {
            // Simulate random processing delay (0 to 2 seconds)
            int randomDelay = (int) (Math.random() * 2000);
            Thread.sleep(randomDelay);
            String correlationId = record.key();
            AccountRequest request = record.value();

            logger.info("Received Kafka message with correlationId: {}", correlationId);

            // Process and store in Redis
            redisService.storeData(correlationId, request);

            // Create AccountResponse
            AccountResponse response = new AccountResponse();
            response.setCorrelationId(correlationId);
            response.setStatus("success");
            response.setMessage("Account created successfully");

            // Send response back to adapter
            kafkaTemplate.send("create-account-response-topic", correlationId, response);
            logger.info("Sent success response for correlationId: {}", correlationId);

            // Create a JSON object that includes the timestamp and the AccountRequest
            long timestamp = System.currentTimeMillis();
            String messageWithTimestamp = objectMapper.writeValueAsString(new TimestampedAccountRequest(timestamp, request));

            // Send the message to the create-account-topic
            kafkaTemplate.send("create-account-topic", correlationId, messageWithTimestamp);
            logger.info("Sent message with timestamp to create-account-topic: {}", messageWithTimestamp);

        } catch (Exception e) {
            logger.error("Error processing Kafka message: {}", e.getMessage(), e);

            // Send error response
            AccountResponse errorResponse = new AccountResponse();
            errorResponse.setCorrelationId(record.key());
            errorResponse.setStatus("failure");
            errorResponse.setMessage("Account creation failed due to internal error");

            kafkaTemplate.send("create-account-response-topic", record.key(), errorResponse);
        }
    }
}