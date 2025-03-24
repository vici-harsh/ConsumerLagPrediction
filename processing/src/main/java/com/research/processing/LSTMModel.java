package com.research.processing;

import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class LSTMModel {
    private MultiLayerNetwork model;
    private final String modelPath;
    private static final Logger logger = LoggerFactory.getLogger(LSTMModel.class);

    public LSTMModel(String modelPath) {
        this.modelPath = modelPath;
        initializeModel();
    }

    public boolean isModelLoaded() {
        return model != null;
    }

    private void initializeModel() {
        try {
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                logger.info("Loading existing model from {}", modelPath);
                model = ModelSerializer.restoreMultiLayerNetwork(modelPath);
            } else {
                logger.warn("Model file not found. Building new model.");
                buildNewModel();
                modelFile.getParentFile().mkdirs(); // Ensure directory exists
                saveModel();
            }
        } catch (IOException e) {
            logger.error("Model initialization failed", e);
            throw new RuntimeException("Failed to initialize model", e);
        }
    }

    private void buildNewModel() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new Adam(0.0001)) // Adjusted learning rate
                .list()
                .layer(new LSTM.Builder()
                        .nIn(3)
                        .nOut(100) // Increased number of LSTM units
                        .activation(Activation.TANH)
                        .dropOut(0.5) // Added dropout
                        .build())
                .layer(new LSTM.Builder()
                        .nIn(100)
                        .nOut(50)
                        .activation(Activation.TANH)
                        .dropOut(0.5) // Added dropout
                        .build())
                .layer(new RnnOutputLayer.Builder()
                        .nIn(50)
                        .nOut(1)
                        .lossFunction(LossFunctions.LossFunction.MSE)
                        .activation(Activation.IDENTITY)
                        .build())
                .build();

        model = new MultiLayerNetwork(conf);
        model.init();
        logger.info("New LSTM model initialized with configuration: {}", conf.toJson());
    }

    public long predict(long[] sequence) {


        logger.info("Starting prediction for input: {}", Arrays.toString(sequence));
        try {
            // Normalize the input sequence
            double[] normalizedSequence = normalizeSequence(sequence);
            logger.info("Normalized sequence: {}", Arrays.toString(normalizedSequence));

            // Convert sequence to INDArray and reshape to (1, 3, 1)
            INDArray input = Nd4j.create(normalizedSequence).reshape(1, 3, 1); // Shape: (batchSize, sequenceLength, numFeatures)
            logger.info("Input shape: {}", Arrays.toString(input.shape()));

            INDArray output = model.output(input);
            long result = (long) output.getDouble(0);
            logger.info("Prediction result: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("Prediction failed", e);
            throw new RuntimeException("Prediction failed", e);
        }
    }

    public void train(long[] sequence, long actual) {
        logger.info("Starting training for input: {} with label: {}", Arrays.toString(sequence), actual);
        try {
            // Normalize the input sequence
            double[] normalizedSequence = normalizeSequence(sequence);
            logger.info("Normalized sequence: {}", Arrays.toString(normalizedSequence));

            // Convert sequence to INDArray and reshape to (1, 3, 1)
            INDArray input = Nd4j.create(normalizedSequence).reshape(1, 3, 1); // Shape: (batchSize, sequenceLength, numFeatures)
            INDArray label = Nd4j.create(new float[]{actual}).reshape(1, 1, 1); // Shape: (batchSize, outputSize, 1)

            model.fit(input, label);
            logger.info("Training completed successfully.");
        } catch (Exception e) {
            logger.error("Training failed", e);
            throw new RuntimeException("Training failed", e);
        }
    }

    public void saveModel() {
        try {
            ModelSerializer.writeModel(model, modelPath, true);
            logger.info("Model saved successfully to {}", modelPath);
        } catch (IOException e) {
            logger.error("Failed to save model", e);
            throw new RuntimeException("Failed to save model", e);
        }
    }

    private double[] normalizeSequence(long[] sequence) {
        double[] normalizedSequence = new double[sequence.length];
        long max = Arrays.stream(sequence).max().getAsLong();
        for (int i = 0; i < sequence.length; i++) {
            normalizedSequence[i] = sequence[i] / (double) max; // Scale to [0, 1]
        }
        return normalizedSequence;
    }

}