package com.swiftdrop.user.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.user.dto.response.UserProfileResponse;
import com.swiftdrop.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Profile of the signed-in user. Requires a valid access token (any role). */
    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userService.getProfile(UUID.fromString(currentUser.userId()));
    }
}
