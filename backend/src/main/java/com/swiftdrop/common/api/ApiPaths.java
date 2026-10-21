package com.swiftdrop.common.api;

/**
 * Central place for API path prefixes. Controllers should build their
 * {@code @RequestMapping} values from these constants so a future version bump
 * (e.g. /api/v2) is a one-line change.
 */
public final class ApiPaths {

    public static final String API_V1 = "/api/v1";

    public static final String AUTH = API_V1 + "/auth";
    public static final String PUBLIC = API_V1 + "/public";
    public static final String PUBLIC_TRACKING = PUBLIC + "/tracking";
    public static final String HEALTH = API_V1 + "/health";

    private ApiPaths() {
    }
}
