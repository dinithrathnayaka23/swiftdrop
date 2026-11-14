package com.swiftdrop.usermanagement.service;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.usermanagement.dto.response.UserStatsResponse;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Account counts for the admin dashboard. Built only on the user module's public {@link UserApi}:
 * each count is the total of a one-row page, so no account data is loaded.
 */
@Service
@RequiredArgsConstructor
public class UserStatsService {

    private static final PageRequest COUNT_ONLY = PageRequest.of(0, 1);

    private final UserApi userApi;

    public UserStatsResponse stats() {
        Map<Role, Long> byRole = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            byRole.put(role, count(new UserSearch(null, role, null)));
        }
        Map<AccountStatus, Long> byStatus = new EnumMap<>(AccountStatus.class);
        for (AccountStatus status : AccountStatus.values()) {
            byStatus.put(status, count(new UserSearch(null, null, status)));
        }
        return new UserStatsResponse(count(UserSearch.all()),
                Collections.unmodifiableMap(byRole), Collections.unmodifiableMap(byStatus));
    }

    private long count(UserSearch search) {
        return userApi.listUsers(search, COUNT_ONLY).getTotalElements();
    }
}
