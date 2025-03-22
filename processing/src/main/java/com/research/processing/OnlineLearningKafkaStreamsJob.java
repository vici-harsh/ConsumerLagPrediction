package com.research.processing;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Properties;

public class OnlineLearningKafkaStreamsJob {

    private static OnlineLearningModel model = new OnlineLearningModel();

    public static void main(String[] args) {
        // Kafka Streams configuration
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("application.id", "online-learning-streams-app");

        // Define the processing topology
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> sourceStream = builder.stream("create-account-topic", Consumed.with(Serdes.String(), Serdes.String()));

        sourceStream.mapValues(record -> {
            // Extract timestamp and calculate lag
            long messageTimestamp = Long.parseLong(record.split(",")[0]); // Example: "timestamp,data"
            long currentTime = System.currentTimeMillis();
            long lag = currentTime - messageTimestamp;

            // Train the model with the new data point
            model.train(lag);

            // Predict lag using the updated model
            long predictedLag = model.predict(lag);

            // Return the predicted lag as a string
            return "Predicted Lag: " + predictedLag + " ms";
        }).to("processed-topic", Produced.with(Serdes.String(), Serdes.String()));

        // Start the Kafka Streams application
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        // Add shutdown hook to gracefully close the application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}