package com.research.processing;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

public class ModelPersistence {
    public static void saveModel(LSTMModel model, String filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(model);
            System.out.println("Model saved to " + filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}