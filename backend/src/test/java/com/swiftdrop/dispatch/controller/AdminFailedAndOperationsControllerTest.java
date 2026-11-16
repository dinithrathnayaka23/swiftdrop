package com.swiftdrop.dispatch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.dispatch.dto.request.CloseDeliveryRequest;
import com.swiftdrop.dispatch.dto.request.FailedDeliveryScope;
import com.swiftdrop.dispatch.dto.request.RetryDeliveryRequest;
import com.swiftdrop.dispatch.dto.response.FailedDecisionResponse;
import com.swiftdrop.dispatch.service.FailedDeliveryService;
import com.swiftdrop.dispatch.service.OperationsService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AdminFailedAndOperationsControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(ADMIN_ID.toString(),
            Set.of(Role.DISPATCHER_ADMIN));
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock private FailedDeliveryService failedDeliveryService;
    @Mock private OperationsService operationsService;
    @InjectMocks private AdminFailedDeliveryController failedController;
    @InjectMocks private AdminOperationsController operationsController;

    private static Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void onlyTheDispatcherAdminMayUseThem() {
        assertThat(AdminFailedDeliveryController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
        assertThat(AdminOperationsController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void theScopeIsAttentionOrClosedOnly() {
        when(failedDeliveryService.list(FailedDeliveryScope.CLOSED, 0, 20)).thenReturn(Page.empty());

        failedController.list("closed", 0, 20);
        verify(failedDeliveryService).list(FailedDeliveryScope.CLOSED, 0, 20);
        assertThat(AdminFailedDeliveryController.scopeOf("ATTENTION")).isEqualTo(FailedDeliveryScope.ATTENTION);

        assertThatThrownBy(() -> failedController.list("ALL", 0, 20)).isInstanceOf(BadRequestException.class)
                .hasMessage("scope must be ATTENTION or CLOSED");
        assertThatThrownBy(() -> failedController.list("nope", 0, 20)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void pagesAreCappedAtFifty() {
        assertThatThrownBy(() -> failedController.list("ATTENTION", 0, 51)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> failedController.list("ATTENTION", -1, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> operationsController.activeDeliveries(0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> operationsController.activeDeliveries(0, 51)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(failedDeliveryService, operationsService);
    }

    @Test
    void retryAndCloseActAsTheSignedInAdmin() {
        when(failedDeliveryService.retry(PARCEL_ID, "Tomorrow", ADMIN_ID))
                .thenReturn(new FailedDecisionResponse(PARCEL_ID, ParcelStatusCode.CREATED));
        when(failedDeliveryService.close(PARCEL_ID, "Gone", ADMIN_ID))
                .thenReturn(new FailedDecisionResponse(PARCEL_ID, ParcelStatusCode.FAILED));

        assertThat(failedController.retry(PARCEL_ID, new RetryDeliveryRequest("Tomorrow"), ADMIN).status())
                .isEqualTo(ParcelStatusCode.CREATED);
        assertThat(failedController.close(PARCEL_ID, new CloseDeliveryRequest("Gone"), ADMIN).status())
                .isEqualTo(ParcelStatusCode.FAILED);
    }

    @Test
    void retryNeedsNoBody() {
        failedController.retry(PARCEL_ID, null, ADMIN);

        verify(failedDeliveryService).retry(PARCEL_ID, null, ADMIN_ID);
    }

    @Test
    void closeNeedsAReasonOfAtMost500AndARetryNoteIsAtMost500() {
        assertThat(invalidFields(new CloseDeliveryRequest("Address does not exist"))).isEmpty();
        assertThat(invalidFields(new CloseDeliveryRequest("  "))).containsExactly("reason");
        assertThat(invalidFields(new CloseDeliveryRequest(null))).containsExactly("reason");
        assertThat(invalidFields(new CloseDeliveryRequest("x".repeat(501)))).containsExactly("reason");
        assertThat(invalidFields(new RetryDeliveryRequest(null))).isEmpty();
        assertThat(invalidFields(new RetryDeliveryRequest("x".repeat(501)))).containsExactly("note");
    }
}
