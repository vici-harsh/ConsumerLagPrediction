package com.research.processing;

import com.research.adapter.AccountRequest;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class AccountRequestSerde implements Serde<AccountRequest> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Serializer<AccountRequest> serializer() {
        return new AccountRequestSerializer();
    }

    @Override
    public Deserializer<AccountRequest> deserializer() {
        return new AccountRequestDeserializer();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No configuration needed
    }

    @Override
    public void close() {
        // No resources to close
    }

    class AccountRequestSerializer implements Serializer<AccountRequest> {
        @Override
        public byte[] serialize(String topic, AccountRequest data) {
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize AccountRequest", e);
            }
        }

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            // No configuration needed
        }

        @Override
        public void close() {
            // No resources to close
        }
    }

    class AccountRequestDeserializer implements Deserializer<AccountRequest> {
        @Override
        public AccountRequest deserialize(String topic, byte[] data) {
            try {
                return objectMapper.readValue(data, AccountRequest.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize AccountRequest", e);
            }
        }

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            // No configuration needed
        }

        @Override
        public void close() {
            // No resources to close
        }
    }
}