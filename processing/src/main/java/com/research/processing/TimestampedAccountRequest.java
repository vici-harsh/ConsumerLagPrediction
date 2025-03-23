package com.research.processing;

import com.research.adapter.AccountRequest;

public class TimestampedAccountRequest {
    private long timestamp;
    private AccountRequest accountRequest;

    // No-argument constructor (required for Jackson)
    public TimestampedAccountRequest() {
    }

    // Constructor with arguments (optional)
    public TimestampedAccountRequest(long timestamp, AccountRequest accountRequest) {
        this.timestamp = timestamp;
        this.accountRequest = accountRequest;
    }

    // Getters and setters (required for Jackson)
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public AccountRequest getAccountRequest() {
        return accountRequest;
    }

    public void setAccountRequest(AccountRequest accountRequest) {
        this.accountRequest = accountRequest;
    }

    @Override
    public String toString() {
        return "TimestampedAccountRequest{" +
                "timestamp=" + timestamp +
                ", accountRequest=" + accountRequest +
                '}';
    }
}