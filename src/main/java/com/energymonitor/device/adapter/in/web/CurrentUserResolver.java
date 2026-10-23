package com.energymonitor.device.adapter.in.web;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated caller of the device endpoints.
 *
 * <p>The caller is the subject the security service put in the access token; homes and their
 * members are asked to energy-monitor-backend with that identifier.
 */
@Component
public class CurrentUserResolver {

    /**
     * @return the {@code sub} claim of the access token, which is the caller's {@code id_user}
     * @throws AuthenticationCredentialsNotFoundException when the request carries no validated JWT (401)
     */
    public String resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AuthenticationCredentialsNotFoundException("no authenticated user in the request");
        }
        String subject = token.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("the access token carries no subject");
        }
        return subject;
    }
}
