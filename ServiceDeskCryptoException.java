package com.atlassian.servicedesk.plugins.base.internal.api.util.security;

/**
 * Exception thrown on encryption or decryption failure.
 */
public class ServiceDeskCryptoException extends RuntimeException {

    public ServiceDeskCryptoException(final String message) {
        super(message);
    }
}
