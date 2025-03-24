package com.research.processing;

import com.research.adapter.AccountRequest;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class LagPredictionService {
    private static final Logger logger = LoggerFactory.getLogger(LagPredictionService.class);

    private static final long LAG_THRESHOLD = 1000;
    private static final long RETRAIN_INTERVAL = 300000; // 5 minutes
    private static final int TRAINING_WINDOW_SIZE = 30;
    static final int REDIS_TTL_SECONDS = 900; // 15 minutes

    private final LSTMModel lstmModel;
    private final JedisPool jedisPool;
    private KafkaStreams streams;
    private Queue<Long> lagWindow = new LinkedList<>();
    private long lastRetrainTime = System.currentTimeMillis();

    @Autowired
    public LagPredictionService(LSTMModel lstmModel, JedisPool jedisPool) {
        this.lstmModel = lstmModel;
        this.jedisPool = jedisPool;
    }

    @PostConstruct
    public void start() {
        logger.info("======== Starting LagPredictionService ========");

        // Enhanced Redis connection test
        try (Jedis jedis = jedisPool.getResource()) {
            String pingResponse = jedis.ping();
            logger.info("Redis connection test: {}", pingResponse);

            // Test write/read with more detailed logging
            String testKey = "lag:test:" + System.currentTimeMillis();
            Map<String, String> testData = Map.of(
                    "test", "value",
                    "timestamp", String.valueOf(System.currentTimeMillis())
            );
            jedis.hset(testKey, testData);
            Map<String, String> readData = jedis.hgetAll(testKey);
            logger.info("Redis test write/read - Key: {}, Data: {}", testKey, readData);
            jedis.del(testKey);
        } catch (Exception e) {
            logger.error("Redis connection failed", e);
            throw new RuntimeException("Redis connection failed", e);
        }

        // Enhanced Kafka Streams configuration
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("application.id", "lstm-lag-prediction-service");
        props.put("commit.interval.ms", "5000");
        props.put("cache.max.bytes.buffering", "0");
        props.put("reconnect.backoff.ms", "1000");
        props.put("reconnect.backoff.max.ms", "10000");
        props.put("retries", "3");
        props.put("metadata.max.age.ms", "30000");
        props.put("default.api.timeout.ms", "30000");

        StreamsBuilder builder = new StreamsBuilder();

        // Enhanced stream processing with better logging
        KStream<String, AccountRequest> stream = builder.stream(
                        "create-account-topic",
                        Consumed.with(Serdes.String(), new AccountRequestSerde())
                )
                .peek((key, value) -> logger.info("RAW KAFKA MESSAGE RECEIVED - Key: {}, Timestamp: {}",
                        key, value.getTimestamp()))
                .filter((key, value) -> {
                    if (value == null || value.getTimestamp() <= 0) {
                        logger.warn("Invalid message received - Key: {}", key);
                        return false;
                    }
                    return true;
                });

        KStream<String, String> processedStream = stream
                .peek((key, value) -> logger.debug("Processing record - Key: {}", key))
                .mapValues(this::processRecord)
                .filter((key, value) -> value != null && !value.contains("ERROR"));

        // Send to both topics
        processedStream.to("create-account-response-topic", Produced.with(Serdes.String(), Serdes.String()));
        processedStream.to("lag-predictions", Produced.with(Serdes.String(), Serdes.String()));

        streams = new KafkaStreams(builder.build(), props);

        // Enhanced exception handling
        streams.setUncaughtExceptionHandler(ex -> {
            logger.error("Kafka Streams error: {}", ex.getMessage(), ex);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });

        streams.setStateListener((newState, oldState) -> {
            logger.info("State changed from {} to {}", oldState, newState);
            if (newState == KafkaStreams.State.ERROR) {
                logger.error("Streams entered ERROR state");
            }
        });

        streams.start();
        logger.info("======== LagPredictionService Started Successfully ========");
    }

    private String processRecord(AccountRequest request) {
        long startTime = System.currentTimeMillis();
        String correlationId = request.getCorrelationId();
        logger.info("PROCESSING START - CorrelationID: {}", correlationId);

        try {
            long messageTimestamp = request.getTimestamp();
            long currentTime = System.currentTimeMillis();
            long lag = calculateLag(messageTimestamp, currentTime);

            logger.info("Processing record {} - Calculated lag: {}ms", correlationId, lag);

            long predictedLag = predictLag(lag);
            storeInRedis(correlationId, lag, predictedLag);
            handleModelTraining(lag);
            checkForAlerts(lag);

            String result = formatOutput(correlationId, currentTime, lag, predictedLag);
            logger.info("Processing completed in {}ms - Result: {}",
                    System.currentTimeMillis() - startTime, result);
            return result;
        } catch (Exception e) {
            logger.error("Error processing record {}", correlationId, e);
            return String.format("%s|ERROR|%d", correlationId, System.currentTimeMillis());
        }
    }

    private long calculateLag(long messageTs, long currentTs) {
        long lag = currentTs - messageTs;
        logger.debug("Calculating lag - MessageTS: {}, CurrentTS: {}, Lag: {}",
                messageTs, currentTs, lag);
        return Math.max(lag, 100);
    }

    private long predictLag(long currentLag) {
        try {
            long[] sequence = {currentLag-100, currentLag-50, currentLag};
            logger.debug("Predicting with sequence: {}", Arrays.toString(sequence));
            long prediction = lstmModel.predict(sequence);
            logger.debug("Prediction result: {}", prediction);
            return prediction;
        } catch (Exception e) {
            logger.error("Prediction failed, using current lag as fallback", e);
            return currentLag;
        }
    }

    private void storeInRedis(String key, long lag, long predictedLag) {
        long timestamp = System.currentTimeMillis();
        String redisKey = String.format("lag:metrics:%d", timestamp);

        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> fields = new HashMap<>();
            fields.put("actual", String.valueOf(lag));
            fields.put("predicted", String.valueOf(predictedLag));
            fields.put("timestamp", String.valueOf(timestamp));
            fields.put("correlationId", key);

            jedis.hset(redisKey, fields);
            jedis.expire(redisKey, REDIS_TTL_SECONDS);

            logger.info("REDIS WRITE SUCCESS - Key: {}, Actual: {}, Predicted: {}",
                    redisKey, lag, predictedLag);
        } catch (Exception e) {
            logger.error("REDIS WRITE FAILED - Key: {} - Error: {}", redisKey, e.getMessage(), e);
            throw new RuntimeException("Redis write failed", e);
        }
    }

    private void handleModelTraining(long lag) {
        if (lag <= 100) {
            logger.warn("Skipping training - Invalid lag value: {}", lag);
            return;
        }

        lagWindow.add(lag);
        if (lagWindow.size() > TRAINING_WINDOW_SIZE) {
            lagWindow.poll();
        }

        if (System.currentTimeMillis() - lastRetrainTime > RETRAIN_INTERVAL
                || lagWindow.size() == TRAINING_WINDOW_SIZE) {
            logger.info("Initiating model training with {} samples", lagWindow.size());
            trainModel();
            lastRetrainTime = System.currentTimeMillis();
        }
    }

    private void trainModel() {
        if (lagWindow.size() < 3) {
            logger.warn("Not enough data for training ({} samples)", lagWindow.size());
            return;
        }

        List<Long> trainingData = new ArrayList<>(lagWindow);
        long[] sequence = new long[3];

        for(int i=2; i<trainingData.size(); i++) {
            sequence[0] = trainingData.get(i-2);
            sequence[1] = trainingData.get(i-1);
            sequence[2] = trainingData.get(i);
            logger.debug("Training with sequence: {}", Arrays.toString(sequence));
            lstmModel.train(sequence, sequence[2]);
        }

        lstmModel.saveModel();
        logger.info("Model retrained successfully with {} samples", trainingData.size());
    }

    private String formatOutput(String key, long timestamp, long lag, long predictedLag) {
        return String.format("%s|%d|%d|%d", key, timestamp, lag, predictedLag);
    }

    private void checkForAlerts(long lag) {
        if (lag > LAG_THRESHOLD) {
            logger.warn("ALERT: High lag detected - {}ms", lag);
            try {
                SlackNotifier.sendAlert("Lag Alert: " + lag + "ms");
            } catch (Exception e) {
                logger.error("Failed to send Slack alert", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        logger.info("======== Stopping LagPredictionService ========");
        if (streams != null) {
            streams.close();
        }
        if (jedisPool != null) {
            jedisPool.close();
        }
        logger.info("======== LagPredictionService Stopped ========");
    }
}