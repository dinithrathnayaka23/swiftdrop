package com.swiftdrop.usermanagement.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.usermanagement.dto.response.UserStatsResponse;
import com.swiftdrop.usermanagement.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard numbers for the admin's User Management page. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/admin/users")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminUserStatsController {

    private final UserStatsService userStatsService;

    /** Account counts in total, per role and per status. */
    @GetMapping("/stats")
    public UserStatsResponse stats() {
        return userStatsService.stats();
    }
}
