package com.swiftdrop.user.mapper;

import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.dto.response.UserProfileResponse;
import com.swiftdrop.user.model.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserAccount toAccount(User user) {
        return new UserAccount(user.getId(), user.getFullName(), user.getEmail(), user.getPasswordHash(),
                user.getGoogleSubject(), user.getRole(), user.getAccountStatus(), user.getEmailVerifiedAt() != null);
    }

    public static UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRole(), user.getAccountStatus(), user.getCreatedAt());
    }
}
