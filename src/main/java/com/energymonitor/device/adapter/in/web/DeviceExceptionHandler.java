package com.energymonitor.device.adapter.in.web;

import com.energymonitor.device.application.exception.ApplianceTypeNotFoundException;
import com.energymonitor.device.application.exception.DeviceAlreadyLinkedException;
import com.energymonitor.device.application.exception.DeviceNotFoundException;
import com.energymonitor.device.application.exception.HomeNotFoundException;
import com.energymonitor.device.application.exception.HomeServiceUnavailableException;
import com.energymonitor.device.application.exception.NotHomeOwnerException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {HomeDeviceController.class, ApplianceTypeController.class})
public class DeviceExceptionHandler {

    @ExceptionHandler({DeviceNotFoundException.class, HomeNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(RuntimeException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(NotHomeOwnerException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleNotOwner(NotHomeOwnerException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(DeviceAlreadyLinkedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleAlreadyLinked(DeviceAlreadyLinkedException ex) {
        return Map.of("error", ex.getMessage());
    }

    /** Nothing is decided without knowing the caller's rights on the home. */
    @ExceptionHandler(HomeServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleHomeServiceUnavailable(HomeServiceUnavailableException ex) {
        return Map.of("error", "homes are temporarily unavailable, try again");
    }

    @ExceptionHandler({ApplianceTypeNotFoundException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(RuntimeException ex) {
        return Map.of("error", ex.getMessage());
    }
}
