package com.swiftdrop.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.merchant.dto.request.UpdateMerchantProfileRequest;
import com.swiftdrop.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.service.MerchantAccessService;
import com.swiftdrop.merchant.service.MerchantProfileService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class MerchantProfileControllerTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock private MerchantAccessService merchantAccessService;
    @Mock private MerchantProfileService profileService;
    @InjectMocks private MerchantProfileController controller;

    private final UUID userId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.MERCHANT));
    private final UpdateMerchantProfileRequest validRequest =
            new UpdateMerchantProfileRequest("orders@brightmart.lk", "+94772222222", "25 New Street, Kandy");

    @Test
    void onlyMerchantsMayUseIt() {
        assertThat(MerchantProfileController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('MERCHANT')");
    }

    @Test
    void readsAndUpdatesOnlyTheSignedInUsersMerchant() {
        when(merchantAccessService.requireApprovedMerchant(userId)).thenReturn(merchantId);

        controller.get(user);
        controller.update(validRequest, user);

        verify(profileService).get(merchantId);
        verify(profileService).update(merchantId, validRequest);
    }

    @Test
    void merchantThatIsNotApprovedCannotSeeItsProfile() {
        when(merchantAccessService.requireApprovedMerchant(userId))
                .thenThrow(MerchantNotApprovedException.forStatus(MerchantApprovalStatus.SUSPENDED));

        assertThatThrownBy(() -> controller.get(user)).isInstanceOf(MerchantNotApprovedException.class);
        verifyNoInteractions(profileService);
    }

    @Test
    void validRequestPassesValidation() {
        assertThat(VALIDATOR.validate(validRequest)).isEmpty();
    }

    @Test
    void invalidContactDetailsAreRejected() {
        UpdateMerchantProfileRequest invalid = new UpdateMerchantProfileRequest("not-an-email", "0771234567", "short");

        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("contactEmail", "contactPhone", "businessAddress");
    }

    @Test
    void contactDetailsStayOutOfLogs() {
        assertThat(validRequest.toString()).doesNotContain("brightmart", "+9477");
    }
}
