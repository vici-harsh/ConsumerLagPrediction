package com.research.processing;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.KStream;
import redis.clients.jedis.Jedis;

import java.util.Properties;

public class LagPredictionKafkaStreamsJob {

    // Initialize the LSTM model with the model path
    private static LSTMModel lstmModel = new LSTMModel("processing/src/main/resources/models/lstm_model.h5");

    public static void main(String[] args) {
        // Kafka Streams configuration
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("application.id", "lag-prediction-streams-app");

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

            // Predict lag using the LSTM model
            long predictedLag = lstmModel.predictLag(lagSequence);

            // Train the model with the new data
            lstmModel.train(lagSequence, lag);

            // Return the predicted lag as a string
            return "Predicted Lag: " + predictedLag + " ms";
        }).to("processed-topic", Produced.with(Serdes.String(), Serdes.String()));

        // Start the Kafka Streams application
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        // Add shutdown hook to gracefully close the application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

        // Redis integration (optional: write predicted lag to Redis)
        Jedis jedis = new Jedis("localhost", 6379);
        sourceStream.foreach((key, value) -> {
            jedis.set(key, value); // Store predicted lag in Redis
        });

        // Periodically save the model (e.g., every 1000 records)
        sourceStream.foreach((key, value) -> {
            if (lstmModel.getTrainingCount() % 1000 == 0) {
                ModelPersistence.saveModel(lstmModel, "processing/src/main/resources/models/lstm_model_updated.h5");
            }
        });
    }
}