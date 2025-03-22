package com.research.processing;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.Properties;

public class LagPredictionFlinkJob {
    public static void main(String[] args) throws Exception {
        // Set up the Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Kafka properties for consumer
        Properties consumerProps = new Properties();
        consumerProps.setProperty("bootstrap.servers", "localhost:9092"); // Kafka broker address
        consumerProps.setProperty("group.id", "flink-consumer-group");   // Consumer group ID

        // Create Kafka consumer
        FlinkKafkaConsumer<String> kafkaConsumer = new FlinkKafkaConsumer<>(
                "create-account-topic", // Kafka topic to consume from
                new SimpleStringSchema(), // Deserialization schema
                consumerProps
        );

        // Add Kafka consumer as a source to the Flink environment
        DataStream<String> kafkaStream = env.addSource(kafkaConsumer);

        // Process the Kafka stream (e.g., calculate consumer lag)
        DataStream<String> processedStream = kafkaStream.map(record -> {
            // Parse the Kafka message (assuming it contains a timestamp)
            long messageTimestamp = Long.parseLong(record.split(",")[0]); // Example: "timestamp,data"
            long currentTime = System.currentTimeMillis();
            long lag = currentTime - messageTimestamp;

            // Return the lag as a string
            return "Lag: " + lag + " ms";
        });

        // Kafka properties for producer
        Properties producerProps = new Properties();
        producerProps.setProperty("bootstrap.servers", "localhost:9092");

        // Create Kafka producer
        FlinkKafkaProducer<String> kafkaProducer = new FlinkKafkaProducer<>(
                "processed-topic", // Kafka topic to produce to
                new SimpleStringSchema(),
                producerProps
        );

        // Send the processed data back to Kafka
        processedStream.addSink(kafkaProducer);

        // Execute the Flink job
        env.execute("Lag Prediction Flink Job");
    }
}