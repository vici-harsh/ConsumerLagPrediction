package com.research.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private com.research.processing.RedisService redisService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "create-account-topic")
    public void listenCreateAccountRequest(String correlationId, Map<String, Object> request) {
        logger.info("Received Kafka message with correlationId: {}", correlationId);

        // Process the request (e.g., store in Redis)
        redisService.storeData(correlationId, request);

        // Send a success response back to the response topic
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("correlationId", correlationId);

        kafkaTemplate.send("create-account-response-topic", correlationId, response);
        logger.info("Sent success response for correlationId: {}", correlationId);
    }
}