package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.CourierAvailability;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks for the filtered courier list. Combine them with {@link Specification#and}. */
public final class CourierSpecifications {

    private static final char ESCAPE = '\\';

    private CourierSpecifications() {
    }

    /** Matches every courier; the starting point for combining filters. */
    public static Specification<Courier> any() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Courier> hasAvailability(CourierAvailability availability) {
        return (root, query, cb) -> cb.equal(root.get("availabilityStatus"), availability);
    }

    public static Specification<Courier> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    /** Vehicle type, ignoring case. */
    public static Specification<Courier> hasVehicleType(String vehicleType) {
        String wanted = vehicleType.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("vehicleType")), wanted);
    }

    /** Couriers whose service areas include the zone. */
    public static Specification<Courier> coversZone(UUID zoneId) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Courier, UUID> zones = root.join("serviceAreaIds");
            return cb.equal(zones, zoneId);
        };
    }

    /**
     * Couriers whose account is one of {@code userIds} (the accounts whose name, email or phone
     * matched, found through the user module) or whose vehicle registration contains {@code text}.
     */
    public static Specification<Courier> matchesText(Collection<UUID> userIds, String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Predicate registration = cb.like(cb.lower(root.get("vehicleRegistration")), pattern, ESCAPE);
            return userIds.isEmpty() ? registration : cb.or(registration, root.get("userId").in(userIds));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
