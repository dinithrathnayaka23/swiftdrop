package com.swiftdrop.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.delivery.dto.request.DeliverRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.ProofPhoto;
import com.swiftdrop.delivery.service.DeliveryProofService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

class ProofControllersTest {

    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final AuthenticatedUser COURIER = new AuthenticatedUser(COURIER_ID.toString(), Set.of(Role.COURIER));

    private final DeliveryProofService service = mock(DeliveryProofService.class);

    @Test
    void deliveringIsForCouriersAndUsesTheSignedInCourier() {
        assertThat(CourierProofController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('COURIER')");
        UUID parcelId = UUID.randomUUID();
        DeliverRequest request = new DeliverRequest("Kasun Perera", null, null);
        CourierDeliveryDetailResponse detail = mock(CourierDeliveryDetailResponse.class);
        when(service.deliver(parcelId, COURIER_ID, request)).thenReturn(detail);

        assertThat(new CourierProofController(service).deliver(COURIER, parcelId, request)).isSameAs(detail);
    }

    @Test
    void thePhotoIsNeverCachedOrSniffedAndShownInline() {
        assertThat(ProofPhotoController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("isAuthenticated()");
        assertThat(ProofPhotoController.PATH).isEqualTo("/api/v1/proofs");
        UUID proofId = UUID.randomUUID();
        byte[] jpeg = {1, 2, 3};
        when(service.photo(proofId, COURIER)).thenReturn(new ProofPhoto(jpeg, "image/jpeg"));

        ResponseEntity<byte[]> response = new ProofPhotoController(service).photo(COURIER, proofId);

        assertThat(response.getBody()).isEqualTo(jpeg);
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(headers.getCacheControl()).isEqualTo("private, no-store");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getContentDisposition().getType()).isEqualTo("inline");
    }
}
