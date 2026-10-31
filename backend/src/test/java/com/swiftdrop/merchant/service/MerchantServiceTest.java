package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.NewMerchant;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.notification.service.EmailService;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private UserService userService;
    @Mock private EmailService emailService;

    private MerchantService merchantService;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, userService, emailService, "http://localhost:3000");
    }

    private static Merchant pendingMerchant() {
        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setBusinessName("BrightMart");
        merchant.setRegistrationNo("PV00234567");
        merchant.setContactEmail("sunil@brightmart.lk");
        merchant.setRegisteredByUserId(UUID.randomUUID());
        merchant.setApprovalStatus(MerchantApprovalStatus.PENDING);
        return merchant;
    }

    @ParameterizedTest
    @CsvSource({"pv 00234567, PV00234567", "PB-12345678, PB12345678", "ga00000001, GA00000001"})
    void registrationNumbersAreStoredInOneStandardForm(String input, String expected) {
        assertThat(MerchantService.normalizeRegistrationNo(input)).isEqualTo(expected);
    }

    @Test
    void duplicateCheckUsesTheStandardForm() {
        when(merchantRepository.existsByRegistrationNo("PV00234567")).thenReturn(true);

        assertThat(merchantService.registrationNoExists("pv 0023-4567")).isTrue();
    }

    @Test
    void registersAPendingMerchantWithCleanedUpDetails() {
        UUID userId = UUID.randomUUID();
        when(merchantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        MerchantResponse response = merchantService.registerPending(new NewMerchant(userId, " BrightMart ",
                "pv 00234567", "Sunil@BrightMart.lk", "+94771234567", " 12 Galle Road "));

        ArgumentCaptor<Merchant> saved = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).saveAndFlush(saved.capture());
        Merchant merchant = saved.getValue();
        assertThat(merchant.getBusinessName()).isEqualTo("BrightMart");
        assertThat(merchant.getRegistrationNo()).isEqualTo("PV00234567");
        assertThat(merchant.getContactEmail()).isEqualTo("sunil@brightmart.lk");
        assertThat(merchant.getBusinessAddress()).isEqualTo("12 Galle Road");
        assertThat(merchant.getRegisteredByUserId()).isEqualTo(userId);
        assertThat(response.approvalStatus()).isEqualTo(MerchantApprovalStatus.PENDING);
    }

    @Test
    void approvingActivatesTheAccountAndEmailsTheMerchant() {
        Merchant merchant = pendingMerchant();
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));

        MerchantResponse response = merchantService.approve(merchant.getId(), adminId);

        assertThat(response.approvalStatus()).isEqualTo(MerchantApprovalStatus.APPROVED);
        assertThat(merchant.getReviewedByUserId()).isEqualTo(adminId);
        assertThat(merchant.getReviewedAt()).isNotNull();
        verify(userService).changeAccountStatus(merchant.getRegisteredByUserId(), AccountStatus.ACTIVE);
        verify(emailService).send(eq("sunil@brightmart.lk"), contains("approved"),
                contains("http://localhost:3000/login"));
    }

    @Test
    void rejectingDeactivatesTheAccountAndSendsTheReason() {
        Merchant merchant = pendingMerchant();
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));

        merchantService.reject(merchant.getId(), adminId, "  Registration number could not be verified ");

        assertThat(merchant.getApprovalStatus()).isEqualTo(MerchantApprovalStatus.REJECTED);
        assertThat(merchant.getRejectionReason()).isEqualTo("Registration number could not be verified");
        verify(userService).changeAccountStatus(merchant.getRegisteredByUserId(), AccountStatus.DEACTIVATED);
        verify(emailService).send(eq("sunil@brightmart.lk"), anyString(),
                contains("Reason: Registration number could not be verified"));
    }

    @Test
    void onlyPendingMerchantsCanBeReviewed() {
        Merchant merchant = pendingMerchant();
        merchant.setApprovalStatus(MerchantApprovalStatus.APPROVED);
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> merchantService.reject(merchant.getId(), adminId, "late"))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(userService, emailService);
    }

    @Test
    void unknownMerchant() {
        UUID id = UUID.randomUUID();
        when(merchantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.approve(id, adminId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsMerchantsByStatus() {
        when(merchantRepository.findByApprovalStatusOrderByCreatedAtAsc(MerchantApprovalStatus.PENDING))
                .thenReturn(List.of(pendingMerchant()));

        assertThat(merchantService.listByStatus(MerchantApprovalStatus.PENDING)).singleElement()
                .extracting(MerchantResponse::businessName).isEqualTo("BrightMart");
    }
}
