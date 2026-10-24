package com.energymonitor.device.adapter.out.home;

import com.energymonitor.device.application.exception.HomeServiceUnavailableException;
import com.energymonitor.device.application.port.out.HomeLookupPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks energy-monitor-backend {@code GET /internal/homes/{homeId}/access?userId=...}, which answers
 * 200 with {@code {"exists":..,"member":..,"owner":..}}. Anything else, or no answer within the
 * timeouts, is reported as unavailable: a device is never linked or listed without knowing the
 * caller may.
 */
public class HttpHomeAccessAdapter implements HomeLookupPort {

    private final RestClient backend;

    /**
     * @param backend client with the backend's base URL, the timeouts and the internal token
     */
    public HttpHomeAccessAdapter(RestClient backend) {
        this.backend = backend;
    }

    @Override
    public HomeAccess access(String userId, String homeId) {
        if (userId == null || homeId == null) {
            return HomeAccess.NONE;
        }
        try {
            return backend.get()
                    .uri("/internal/homes/{homeId}/access?userId={userId}", homeId, userId)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                            throw new HomeServiceUnavailableException(
                                    "home access answered " + response.getStatusCode().value());
                        }
                        Answer answer = response.bodyTo(Answer.class);
                        if (answer == null) {
                            throw new HomeServiceUnavailableException("home access answered no body");
                        }
                        return answer.toAccess();
                    });
        } catch (RestClientException unreachable) {
            throw new HomeServiceUnavailableException("home access failed", unreachable);
        }
    }

    /** The body of the answer. */
    record Answer(boolean exists, boolean member, boolean owner) {

        HomeAccess toAccess() {
            return new HomeAccess(exists, exists && member, exists && member && owner);
        }
    }
}
