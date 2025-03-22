package com.research.processing;

import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
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
                model = KerasModelImport.importKerasSequentialModelAndWeights(modelPath);
                System.out.println("Model loaded successfully from: " + modelPath);
            } else {
                throw new IOException("Model file not found at: " + modelPath);
            }
        } catch (Exception e) {
            System.err.println("Failed to load model: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Predict lag using the LSTM model
    public long predictLag(long[] lagSequence) {
        if (model == null) {
            throw new IllegalStateException("Model is not loaded.");
        }

        // Convert the lag sequence to an INDArray (input for the model)
        INDArray input = Nd4j.create(lagSequence).reshape(1, lagSequence.length, 1); // Shape: (batchSize, sequenceLength, numFeatures)
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
        INDArray input = Nd4j.create(lagSequence).reshape(1, lagSequence.length, 1); // Shape: (batchSize, sequenceLength, numFeatures)
        INDArray label = Nd4j.create(new float[]{actualLag}).reshape(1, 1); // Shape: (batchSize, outputSize)

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
            File file = new File(savePath);
            ModelSerializer.writeModel(model, file, true);
            System.out.println("Model saved successfully to: " + savePath);
        } catch (IOException e) {
            System.err.println("Failed to save model: " + e.getMessage());
            e.printStackTrace();
        }
    }
}