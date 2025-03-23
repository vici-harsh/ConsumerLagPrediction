package com.research.processing;

import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.deeplearning4j.util.ModelSerializer;

import java.io.File;
import java.io.IOException;

public class LSTMModel {
    private MultiLayerNetwork model;
    private String modelPath;
    private int trainingCount = 0; // Track the number of training iterations

    public LSTMModel(String modelPath) {
        this.modelPath = modelPath;
        loadModel();
    }

    // Load the pre-trained LSTM model
    private void loadModel() {
        try {
            if (new File(modelPath).exists()) {
                model = ModelSerializer.restoreMultiLayerNetwork(modelPath);
                System.out.println("Model loaded successfully from: " + modelPath);
            } else {
                System.out.println("Model file not found at: " + modelPath + ". Initializing new model.");
                initializeModel();
            }
        } catch (Exception e) {
            System.err.println("Failed to load model: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Initialize a new LSTM model
    private void initializeModel() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .weightInit(WeightInit.XAVIER)
                .list()
                .layer(0, new LSTM.Builder()
                        .nIn(3) // Input size (e.g., lag sequence length)
                        .nOut(10) // Number of LSTM units
                        .activation(Activation.TANH)
                        .build())
                .layer(1, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(10) // Input size from the previous LSTM layer
                        .nOut(1) // Output size (e.g., predicted lag)
                        .activation(Activation.IDENTITY)
                        .build())
                .build();

        model = new MultiLayerNetwork(conf);
        model.init();
        System.out.println("New LSTM model initialized.");
    }

    // Check if the model is loaded
    public boolean isModelLoaded() {
        return model != null;
    }

    // Predict lag using the LSTM model
    public long predictLag(long[] lagSequence) {
        if (model == null) {
            throw new IllegalStateException("Model is not loaded.");
        }

        // Convert the lag sequence to an INDArray (input for the model)
        INDArray input = Nd4j.create(new float[][]{
                {lagSequence[0], lagSequence[1], lagSequence[2]}
        }).reshape(1, 3, 1); // Shape: (batchSize, sequenceLength, numFeatures)

        INDArray output = model.output(input);

        // Return the predicted lag (assuming the output is a single value)
        return (long) output.getDouble(0);
    }

    // Train the model with new data (online learning)
    public void train(long[] lagSequence, long actualLag) {
        if (model == null) {
            throw new IllegalStateException("Model is not loaded.");
        }

        // Convert the lag sequence and actual lag to INDArrays
        INDArray input = Nd4j.create(new float[][]{
                {lagSequence[0], lagSequence[1], lagSequence[2]}
        }).reshape(1, 3, 1); // Shape: (batchSize, sequenceLength, numFeatures)

        INDArray label = Nd4j.create(new float[]{actualLag}).reshape(1, 1, 1); // Shape: (batchSize, outputSize, 1)

        // Train the model with the new data
        model.fit(input, label);
        trainingCount++; // Increment training count
    }

    public int getTrainingCount() {
        return trainingCount;
    }

    // Save the updated model to disk
    public void saveModel(String savePath) {
        try {
            ModelSerializer.writeModel(model, savePath, true);
            System.out.println("Model saved successfully to: " + savePath);
        } catch (IOException e) {
            System.err.println("Failed to save model: " + e.getMessage());
            e.printStackTrace();
        }
    }
}