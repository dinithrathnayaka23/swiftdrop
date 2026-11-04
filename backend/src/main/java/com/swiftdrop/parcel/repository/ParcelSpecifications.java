package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.model.Parcel;
import jakarta.persistence.criteria.Expression;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks for the filtered parcel lists. Combine them with {@link Specification#and}. */
public final class ParcelSpecifications {

    private ParcelSpecifications() {
    }

    /** Matches every parcel; the starting point for combining filters. */
    public static Specification<Parcel> any() {
        return (root, query, cb) -> cb.conjunction();
    }

    /** Matches no parcel. */
    public static Specification<Parcel> none() {
        return (root, query, cb) -> cb.disjunction();
    }

    public static Specification<Parcel> idIn(Collection<UUID> ids) {
        return ids.isEmpty() ? none() : (root, query, cb) -> root.get("id").in(ids);
    }

    public static Specification<Parcel> idNotIn(Collection<UUID> ids) {
        return ids.isEmpty() ? any() : (root, query, cb) -> cb.not(root.get("id").in(ids));
    }

    public static Specification<Parcel> ownedByCustomer(UUID customerUserId) {
        return (root, query, cb) -> cb.equal(root.get("customerUserId"), customerUserId);
    }

    public static Specification<Parcel> hasStatus(Collection<String> statusCodes) {
        return (root, query, cb) -> root.get("currentStatus").get("statusCode").in(statusCodes);
    }

    /** {@code from} inclusive, {@code to} exclusive; either may be {@code null}. */
    public static Specification<Parcel> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            Expression<Instant> createdAt = root.get("createdAt");
            if (from != null && to != null) {
                return cb.and(cb.greaterThanOrEqualTo(createdAt, from), cb.lessThan(createdAt, to));
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(createdAt, from);
            }
            return to != null ? cb.lessThan(createdAt, to) : cb.conjunction();
        };
    }

    /** Delivery city, ignoring case. */
    public static Specification<Parcel> deliveredTo(String city) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("dropoffAddress").get("city")),
                city.trim().toLowerCase(Locale.ROOT));
    }

    /** Part of the tracking number, delivery city or merchant order reference (customer list). */
    public static Specification<Parcel> customerSearch(String term) {
        String pattern = likePattern(term);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("trackingNumber")), pattern, '\\'),
                cb.like(cb.lower(root.get("dropoffAddress").get("city")), pattern, '\\'),
                cb.like(cb.lower(root.get("externalOrderReference")), pattern, '\\'));
    }

    /**
     * Part of the tracking number or recipient name, or one of {@code merchantIds} (the merchants
     * whose name matched the term) for the admin list.
     */
    public static Specification<Parcel> adminSearch(String term, Collection<UUID> merchantIds) {
        String pattern = likePattern(term);
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> any = new ArrayList<>();
            any.add(cb.like(cb.lower(root.get("trackingNumber")), pattern, '\\'));
            any.add(cb.like(cb.lower(root.get("dropoffAddress").get("contactName")), pattern, '\\'));
            if (!merchantIds.isEmpty()) {
                any.add(root.get("merchantId").in(merchantIds));
            }
            return cb.or(any.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    /** "%term%" in lower case, with LIKE wildcards in the term escaped by a backslash. */
    static String likePattern(String term) {
        String escaped = term.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
