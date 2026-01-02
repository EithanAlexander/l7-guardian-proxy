package com.l7guardian.proxy.infrastructure.forward;

public class ProxyForwardingException extends RuntimeException {

    public ProxyForwardingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProxyForwardingException(String message) {
        super(message);
    }
}
