package com.l7guardian.proxy.core.exception;

public class RequestPayloadTooLargeException extends RuntimeException {

    public RequestPayloadTooLargeException(String message) {
        super(message);
    }
}
