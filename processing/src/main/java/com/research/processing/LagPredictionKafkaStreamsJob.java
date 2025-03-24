//package com.research.processing;
//
//import com.research.adapter.AccountRequest;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.apache.kafka.common.serialization.Serdes;
//import org.apache.kafka.streams.KafkaStreams;
//import org.apache.kafka.streams.StreamsBuilder;
//import org.apache.kafka.streams.kstream.Consumed;
//import org.apache.kafka.streams.kstream.Produced;
//import org.apache.kafka.streams.kstream.KStream;
//import redis.clients.jedis.Jedis;
//
//import java.util.Arrays;
//import java.util.Properties;
//
//public class LagPredictionKafkaStreamsJob {
//
//    private static LSTMModel lstmModel;
//    private static final String MODEL_PATH = "processing/src/main/resources/models/lstm_model.h5";
//    private static final long THRESHOLD = 1000;
//
//    private static final ObjectMapper objectMapper = new ObjectMapper(); // For JSON serialization
//
//    public static void main(String[] args) {
//        // Kafka Streams configuration
//        Properties props = new Properties();
//        props.put("bootstrap.servers", "localhost:9092");
//        props.put("application.id", "lag-prediction-streams-app");
//
//        // Initialize the LSTM model
//        lstmModel = new LSTMModel(MODEL_PATH);
//
//        // Define the processing topology
//        StreamsBuilder builder = new StreamsBuilder();
//        KStream<String, AccountRequest> sourceStream = builder.stream("create-account-topic", Consumed.with(Serdes.String(), new AccountRequestSerde()));
//
//        sourceStream.mapValues(request -> {
//            // Log the raw Kafka message for debugging
//            System.out.println("Raw Kafka message: " + request);
//
//            // Extract timestamp from the request (assuming request has a timestamp field)
//            long messageTimestamp = request.getTimestamp(); // Assuming getTimestamp() method exists
//            long currentTime = System.currentTimeMillis();
//            long lag = currentTime - messageTimestamp;
//
//            // Ensure lag is always positive
//            if (lag < 100) {
//                lag = 100;
//            }
//
//            // Simulate lag by adding an artificial delay
//            long simulatedLag = lag + (long) (Math.random() * 2000); // Add random delay between 0 and 2000 ms
//            System.out.println("Actual Lag: " + lag + " ms");
//            System.out.println("Simulated Lag: " + simulatedLag + " ms");
//
//            // Use the simulated lag for further processing
//            lag = simulatedLag;
//
//            // Prepare input sequence for the LSTM model
//            long[] lagSequence = {lag - 100, lag - 50, lag};
//            System.out.println("Lag Sequence: " + Arrays.toString(lagSequence));
//
//            // Check if the model is trained
//            if (lstmModel.isModelLoaded()) {
//                // Simulate predicted lag with a random value (for testing purposes)
//                long predictedLag = lag + (long) (Math.random() * 500); // Add random variation to the lag
//                System.out.println("Predicted lag: " + predictedLag + " ms");
//
//                // Simulate training (no actual training, just logging)
//                System.out.println("Simulated training with lag sequence: " + Arrays.toString(lagSequence) + ", actual lag: " + lag);
//            } else {
//                // Simulate predicted lag with a random value (for testing purposes)
//                long predictedLag = lag + (long) (Math.random() * 500); // Add random variation to the lag
//                System.out.println("Simulated Predicted lag: " + predictedLag + " ms");
//
//                // Simulate training (no actual training, just logging)
//                System.out.println("Simulated training with lag sequence: " + Arrays.toString(lagSequence) + ", actual lag: " + lag);
//
//                // Save the model after initial training (simulated)
//                System.out.println("Simulated model saved to: " + MODEL_PATH);
//            }
//
//            // Include timestamp in the predicted lag value
//            return String.format("%d,%d", currentTime, lag);
//        }).to("processed-topic", Produced.with(Serdes.String(), Serdes.String()));
//
//        // Start the Kafka Streams application
//        KafkaStreams streams = new KafkaStreams(builder.build(), props);
//        streams.start();
//
//        // Add shutdown hook to gracefully close the application
//        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
//
//        // Redis integration (store predicted lag in Redis)
//        Jedis jedis = new Jedis("localhost", 6379);
//        sourceStream.mapValues(request -> {
//            // Convert the AccountRequest object to a JSON string
//            try {
//                return objectMapper.writeValueAsString(request);
//            } catch (Exception e) {
//                System.err.println("Failed to serialize AccountRequest: " + e.getMessage());
//                return "{}"; // Return an empty JSON object as a fallback
//            }
//        }).foreach((key, value) -> {
//            String redisKey = "predicted_lag:" + System.currentTimeMillis();
//            jedis.set(redisKey, value); // Store the JSON string in Redis
//            System.out.println("Stored predicted lag in Redis: " + redisKey + " -> " + value);
//
//            // Trigger alerts if the predicted lag exceeds the threshold
//            try {
//                long predictedLag = Long.parseLong(value.split(",")[1]); // Extract the lag value
//                if (predictedLag > THRESHOLD) {
//                    SlackNotifier.sendAlert("Predicted Lag Alert: " + predictedLag + " ms");
//                }
//            } catch (Exception e) {
//                System.err.println("Failed to extract predicted lag: " + e.getMessage());
//            }
//        });
//
//        // Periodically save the model (e.g., every 1000 records)
//        sourceStream.foreach((key, value) -> {
//            if (lstmModel.getTrainingCount() % 1000 == 0) {
//                System.out.println("Saving model after " + lstmModel.getTrainingCount() + " training iterations");
//                ModelPersistence.saveModel(lstmModel, MODEL_PATH);
//            }
//        });
//    }
//}