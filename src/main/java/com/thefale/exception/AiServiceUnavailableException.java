package com.thefale.exception;

/**
 * Thrown when all configured live AI providers are unavailable, unconfigured, or fail to return a valid response.
 */
public class AiServiceUnavailableException extends RuntimeException {
    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

