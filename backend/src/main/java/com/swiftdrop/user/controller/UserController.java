package com.swiftdrop.user.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.user.dto.request.UpdateProfileRequest;
import com.swiftdrop.user.dto.response.UserProfileResponse;
import com.swiftdrop.user.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** Updates the signed-in user's own name and phone number (any role). */
    @PatchMapping("/me")
    public UserProfileResponse updateMe(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(UUID.fromString(currentUser.userId()), request.fullName(), request.phone());
    }
}
