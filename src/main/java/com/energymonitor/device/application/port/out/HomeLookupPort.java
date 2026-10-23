package com.energymonitor.device.application.port.out;

/**
 * Port to ask the Home context what a caller may do in a home. Homes and their members belong to
 * energy-monitor-backend, so this service never keeps a copy of them.
 */
public interface HomeLookupPort {

    /**
     * @param userId the caller, from the access token
     * @param homeId the home in the path
     * @return whether the home exists and the caller belongs to it or owns it
     * @throws com.energymonitor.device.application.exception.HomeServiceUnavailableException when
     *         energy-monitor-backend does not answer
     */
    HomeAccess access(String userId, String homeId);

    /**
     * What a caller may do in a home. A member of a home that does not exist cannot be, so
     * {@code member} and {@code owner} are only true when {@code exists} is.
     *
     * @param exists whether the home exists
     * @param member whether the caller belongs to it, as owner or member
     * @param owner  whether the caller owns it
     */
    record HomeAccess(boolean exists, boolean member, boolean owner) {

        /** Neither the home nor any right on it. */
        public static final HomeAccess NONE = new HomeAccess(false, false, false);
    }
}
