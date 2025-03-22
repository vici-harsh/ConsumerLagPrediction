package com.research.processing;

import com.yahoo.labs.samoa.instances.DenseInstance; // Use DenseInstance instead of Instance
import com.yahoo.labs.samoa.instances.Instance;
import moa.classifiers.trees.HoeffdingTree;
import moa.core.InstanceExample;

public class OnlineLearningModel {
    private HoeffdingTree model;

    public OnlineLearningModel() {
        // Initialize the Hoeffding Tree model
        model = new HoeffdingTree();
        model.prepareForUse();
    }

    public void train(long lag) {
        // Create a new instance with the lag value using DenseInstance
        Instance instance = new DenseInstance(1); // Use DenseInstance
        instance.setValue(0, lag);

        // Train the model with the new instance
        model.trainOnInstance(new InstanceExample(instance));
    }

    public long predict(long lag) {
        // Create a new instance with the lag value using DenseInstance
        Instance instance = new DenseInstance(1); // Use DenseInstance
        instance.setValue(0, lag);

        // Predict the lag using the model
        double prediction = model.getVotesForInstance(instance)[0];
        return (long) prediction;
    }
}