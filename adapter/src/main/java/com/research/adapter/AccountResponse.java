package com.research.adapter;

import java.util.Objects;

public class AccountResponse {

    private String correlationId;
    private String status;
    private String message;

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AccountResponse that = (AccountResponse) o;
        return Objects.equals(correlationId, that.correlationId) && Objects.equals(status, that.status) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, status, message);
    }

    @Override
    public String toString() {
        return "AccountResponse{" +
                "correlationId='" + correlationId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
