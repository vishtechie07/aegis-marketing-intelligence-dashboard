package com.aegis.config;

public class ApiKeyNotConfiguredException extends RuntimeException {
    public ApiKeyNotConfiguredException(String message) {
        super(message);
    }
}
