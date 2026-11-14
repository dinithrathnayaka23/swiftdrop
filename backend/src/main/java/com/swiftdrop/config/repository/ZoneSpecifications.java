package com.swiftdrop.config.repository;

import com.swiftdrop.config.model.DeliveryZone;
import com.swiftdrop.config.model.ZoneCoverageArea;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks for the Service Areas list. Combine them with {@link Specification#and}. */
public final class ZoneSpecifications {

    private static final char ESCAPE = '\\';

    private ZoneSpecifications() {
    }

    /** Matches every zone; the starting point for combining filters. */
    public static Specification<DeliveryZone> any() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<DeliveryZone> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    /** Zone name, or any of its coverage rows' city, district or postal code, contains {@code text} (ignoring case). */
    public static Specification<DeliveryZone> matchesText(String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Subquery<Integer> coverage = query.subquery(Integer.class);
            Root<ZoneCoverageArea> area = coverage.from(ZoneCoverageArea.class);
            coverage.select(cb.literal(1)).where(
                    cb.equal(area.get("zone"), root),
                    cb.or(cb.like(cb.lower(area.get("city")), pattern, ESCAPE),
                            cb.like(cb.lower(area.get("district")), pattern, ESCAPE),
                            cb.like(cb.lower(area.get("postalCode")), pattern, ESCAPE)));
            return cb.or(cb.like(cb.lower(root.get("zoneName")), pattern, ESCAPE), cb.exists(coverage));
        };
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
