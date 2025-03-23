package com.research.processing.config;

import com.research.adapter.AccountRequest;
import com.research.processing.LSTMModel;
import com.research.processing.ModelPersistence;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
@EnableKafka
public class KafkaConfig {

    // Consumer Configuration
    @Bean
    public ConsumerFactory<String, AccountRequest> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group-1");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.research.adapter");
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new JsonDeserializer<>(AccountRequest.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AccountRequest> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AccountRequest> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // Error Handling
        DefaultErrorHandler errorHandler = new DefaultErrorHandler();
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // Producer Configuration
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Kafka Streams Configuration
    @Bean
    public KafkaStreams kafkaStreams() {
        // Kafka Streams configuration
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("application.id", "lag-prediction-streams-app");

        // Define the processing topology
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> sourceStream = builder.stream("create-account-topic", Consumed.with(Serdes.String(), Serdes.String()));

        // Initialize the LSTM model
        LSTMModel lstmModel = new LSTMModel("processing/src/main/resources/models/lstm_model.h5");

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
                ModelPersistence.saveModel(lstmModel, "processing/src/main/resources/models/lstm_model_updated.h5");
                System.out.println("Model saved to: processing/src/main/resources/models/lstm_model_updated.h5");
            }

            // Include timestamp in the predicted lag value
            return String.format("%d,%d", currentTime, lag);
        }).to("processed-topic", Produced.with(Serdes.String(), Serdes.String()));

        // Build the Kafka Streams application
        KafkaStreams streams = new KafkaStreams(builder.build(), props);

        // Start the Kafka Streams application
        streams.start();

        // Add shutdown hook to gracefully close the application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

        return streams;
    }
}