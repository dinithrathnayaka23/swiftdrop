package com.swiftdrop.usermanagement.dto.response;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.model.AccountStatus;
import java.util.Map;

/**
 * Account counts for the User Management dashboard cards. Every role and status is always present
 * (with 0 when there are none), in enum order.
 *
 * @param total    all accounts
 * @param byRole   accounts per role
 * @param byStatus accounts per account status
 */
public record UserStatsResponse(
        long total,
        Map<Role, Long> byRole,
        Map<AccountStatus, Long> byStatus) {
}
