package com.research.processing;

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

    // Initialize the LSTM model with the model path
    private static LSTMModel lstmModel;
    private static final String MODEL_PATH = "processing/src/main/resources/models/lstm_model.h5";

    // Define a threshold for predicted lag (e.g., 1000 ms)
    private static final long THRESHOLD = 1000;

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
            // Extract timestamp and calculate lag
            long messageTimestamp = Long.parseLong(record.split(",")[0]); // Example: "timestamp,data"
            long currentTime = System.currentTimeMillis();
            long lag = currentTime - messageTimestamp;

            // Prepare input sequence for the LSTM model (e.g., last 3 lag values)
            long[] lagSequence = {lag - 100, lag - 50, lag};

            // Check if the model is trained
            if (lstmModel.isModelLoaded()) {
                // Predict lag using the LSTM model
                long predictedLag = lstmModel.predictLag(lagSequence);
                System.out.println("Predicted lag: " + predictedLag + " ms");

                // Train the model with the new data
                System.out.println("Training model with lag sequence: " + Arrays.toString(lagSequence) + ", actual lag: " + lag);
                lstmModel.train(lagSequence, lag);
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