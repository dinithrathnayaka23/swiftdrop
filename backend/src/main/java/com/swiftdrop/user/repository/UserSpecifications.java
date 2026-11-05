package com.swiftdrop.user.repository;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.model.User;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filters for the user list. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Accounts matching every filter that is set in the search. */
    public static Specification<User> matching(UserSearch search) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (search.text() != null && !search.text().isBlank()) {
            spec = spec.and(containsText(search.text()));
        }
        if (search.role() != null) {
            spec = spec.and(hasRole(search.role()));
        }
        if (search.status() != null) {
            spec = spec.and(hasStatus(search.status()));
        }
        return spec;
    }

    static Specification<User> containsText(String text) {
        String pattern = likePattern(text);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern, '\\'),
                cb.like(cb.lower(root.get("email")), pattern, '\\'),
                cb.like(cb.lower(root.get("phone")), pattern, '\\'));
    }

    static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    static Specification<User> hasStatus(AccountStatus status) {
        return (root, query, cb) -> cb.equal(root.get("accountStatus"), status);
    }

    /** "%text%" in lower case, with LIKE wildcards in the text escaped by a backslash. */
    static String likePattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
