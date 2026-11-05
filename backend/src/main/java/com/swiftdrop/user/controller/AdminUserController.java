package com.swiftdrop.user.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.dto.request.ChangeAccountStatusRequest;
import com.swiftdrop.user.dto.request.ChangeRoleRequest;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Every account, for the admin: the User Management screen. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/admin/users")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    /** Accounts, newest first, filtered by name/email/phone text, role and status. */
    @GetMapping
    public PagedModel<UserSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return new PagedModel<>(userService.listUsers(new UserSearch(search, role, status), PageRequest.of(page, size)));
    }

    @GetMapping("/{userId}")
    public UserSummary get(@PathVariable UUID userId) {
        return userService.getUser(userId);
    }

    /** Changes the role between customer and courier; the user signs in again with the new role. */
    @PatchMapping("/{userId}/role")
    public UserSummary changeRole(@PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request) {
        return userService.changeRole(userId, request.role());
    }

    /** Activates, suspends or deactivates the account; suspending or deactivating ends the user's sessions. */
    @PatchMapping("/{userId}/status")
    public UserSummary changeStatus(@PathVariable UUID userId,
                                    @Valid @RequestBody ChangeAccountStatusRequest request) {
        return userService.changeAccountStatusAsAdmin(userId, request.status());
    }
}
