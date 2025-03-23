package com.research.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.KStream;
import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.Properties;

public class LagPredictionKafkaStreamsJob {

    private static LSTMModel lstmModel;
    private static final String MODEL_PATH = "processing/src/main/resources/models/lstm_model.h5";
    private static final long THRESHOLD = 1000;

    private static final ObjectMapper objectMapper = new ObjectMapper(); // For JSON deserialization

    public static void main(String[] args) {
        // Kafka Streams configuration
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("application.id", "lag-prediction-streams-app");

        // Initialize the LSTM model
        lstmModel = new LSTMModel(MODEL_PATH);

        // Define the processing topology
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> sourceStream = builder.stream("create-account-topic", Consumed.with(Serdes.String(), Serdes.String()));

        sourceStream.mapValues(record -> {
            // Log the raw Kafka message for debugging
            System.out.println("Raw Kafka message: " + record);

            try {
                // Deserialize the JSON message into a TimestampedAccountRequest object
                TimestampedAccountRequest timestampedRequest = objectMapper.readValue(record, TimestampedAccountRequest.class);

                // Extract timestamp and calculate lag
                long messageTimestamp = timestampedRequest.getTimestamp();
                long currentTime = System.currentTimeMillis();
                long lag = currentTime - messageTimestamp;

                // Simulate lag by adding an artificial delay
                long simulatedLag = lag + (long) (Math.random() * 2000); // Add random delay between 0 and 2000 ms
                System.out.println("Actual Lag: " + lag + " ms");
                System.out.println("Simulated Lag: " + simulatedLag + " ms");

                // Use the simulated lag for further processing
                lag = simulatedLag;

                // Prepare input sequence for the LSTM model
                long[] lagSequence = {lag - 100, lag - 50, lag};
                System.out.println("Lag Sequence: " + Arrays.toString(lagSequence));

                // Check if the model is trained
                if (lstmModel.isModelLoaded()) {
                    // Predict lag using the LSTM model
                    long predictedLag = lstmModel.predictLag(lagSequence);
                    System.out.println("Predicted lag: " + predictedLag + " ms");

                    // Train the model with the new data
                    if (lag > 0) {
                        System.out.println("Training model with lag sequence: " + Arrays.toString(lagSequence) + ", actual lag: " + lag);
                        lstmModel.train(lagSequence, lag);
                    } else {
                        System.out.println("Skipping training due to zero lag");
                    }
                } else {
                    // Train the model with the new data (initial training)
                    System.out.println("Initial training with lag sequence: " + Arrays.toString(lagSequence) + ", actual lag: " + lag);
                    lstmModel.train(lagSequence, lag);

                    // Save the model after initial training
                    ModelPersistence.saveModel(lstmModel, MODEL_PATH);
                    System.out.println("Model saved to: " + MODEL_PATH);
                }

                // Include timestamp in the predicted lag value
                return String.format("%d,%d", currentTime, lag);

            } catch (Exception e) {
                System.err.println("Failed to deserialize Kafka message: " + e.getMessage());
                return "Invalid message format";
            }
        }).to("processed-topic", Produced.with(Serdes.String(), Serdes.String()));

        // Start the Kafka Streams application
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        // Add shutdown hook to gracefully close the application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

        // Redis integration (store predicted lag in Redis)
        Jedis jedis = new Jedis("localhost", 6379);
        sourceStream.foreach((key, value) -> {
            String redisKey = "predicted_lag:" + System.currentTimeMillis();
            jedis.set(redisKey, value); // Store predicted lag in Redis
            System.out.println("Stored predicted lag in Redis: " + redisKey + " -> " + value);

            // Trigger alerts if the predicted lag exceeds the threshold
            long predictedLag = Long.parseLong(value.split(",")[1]);
            if (predictedLag > THRESHOLD) {
                SlackNotifier.sendAlert("Predicted Lag Alert: " + predictedLag + " ms");
            }
        });

        // Periodically save the model (e.g., every 1000 records)
        sourceStream.foreach((key, value) -> {
            if (lstmModel.getTrainingCount() % 1000 == 0) {
                System.out.println("Saving model after " + lstmModel.getTrainingCount() + " training iterations");
                ModelPersistence.saveModel(lstmModel, MODEL_PATH);
            }
        });
    }
}