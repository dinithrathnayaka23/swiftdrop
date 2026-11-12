package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.request.UpdateMerchantProfileRequest;
import com.swiftdrop.merchant.dto.response.MerchantProfileResponse;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantProfileServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @InjectMocks private MerchantProfileService profileService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setBusinessName("BrightMart");
        merchant.setRegistrationNo("PV00234567");
        merchant.setContactEmail("old@brightmart.lk");
        merchant.setContactPhone("+94711111111");
        merchant.setBusinessAddress("1 Old Road, Colombo 03");
        merchant.setApprovalStatus(MerchantApprovalStatus.APPROVED);
    }

    @Test
    void returnsTheMerchantsProfile() {
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));

        MerchantProfileResponse profile = profileService.get(merchant.getId());

        assertThat(profile.businessName()).isEqualTo("BrightMart");
        assertThat(profile.registrationNo()).isEqualTo("PV00234567");
        assertThat(profile.approvalStatus()).isEqualTo(MerchantApprovalStatus.APPROVED);
    }

    @Test
    void updatesContactDetailsInTheirStoredFormButNotTheVerifiedDetails() {
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(merchant)).thenReturn(merchant);

        MerchantProfileResponse profile = profileService.update(merchant.getId(), new UpdateMerchantProfileRequest(
                "  Orders@BrightMart.LK ", "+94772222222", "  25 New Street, Kandy  "));

        assertThat(profile.contactEmail()).isEqualTo("orders@brightmart.lk");
        assertThat(profile.contactPhone()).isEqualTo("+94772222222");
        assertThat(profile.businessAddress()).isEqualTo("25 New Street, Kandy");
        assertThat(profile.businessName()).isEqualTo("BrightMart");
        assertThat(profile.registrationNo()).isEqualTo("PV00234567");
    }

    @Test
    void unknownMerchantIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(merchantRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.get(unknown)).isInstanceOf(NotFoundException.class);
    }
}
