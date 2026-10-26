package com.energymonitor.device.application.exception;

/**
 * Thrown when the home service, owner of the homes, does not answer whether the caller may use
 * one. Nothing is decided without that answer: the request is refused as unavailable (503).
 */
public class HomeServiceUnavailableException extends DeviceApplicationException {

    public HomeServiceUnavailableException(String message) {
        super(message);
    }

    public HomeServiceUnavailableException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
