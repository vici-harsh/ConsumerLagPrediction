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

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private RedisService redisService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "create-account-topic", groupId = "consumer-group-1")
    public void listenCreateAccountRequest(ConsumerRecord<String, AccountRequest> record) {
        try {
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
