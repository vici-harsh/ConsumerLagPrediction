package com.research.adapter;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public class AccountRequest implements Serializable {

    public AccountRequest() {}

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Correlation ID is required")
    private String correlationId;

    private long timestamp; // Add timestamp field

    // Constructor
    public AccountRequest(String username, String password) {
        this.username = username;
        this.password = password;
        this.timestamp = System.currentTimeMillis(); // Initialize timestamp with current time
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    // Getter for timestamp
    public long getTimestamp() {
        return timestamp;
    }

    // Setter for timestamp
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AccountRequest{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}