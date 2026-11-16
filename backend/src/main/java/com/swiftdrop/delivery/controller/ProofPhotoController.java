package com.swiftdrop.delivery.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.delivery.dto.response.ProofPhoto;
import com.swiftdrop.delivery.service.DeliveryProofService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proof-of-delivery photos. Any signed-in user may ask, but only the admin, the courier who took the
 * photo and the customer whose parcel it is get it; everyone else gets 404. Never cached or public.
 */
@RestController
@RequestMapping(ProofPhotoController.PATH)
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ProofPhotoController {

    static final String PATH = ApiPaths.API_V1 + "/proofs";

    private final DeliveryProofService deliveryProofService;

    @GetMapping("/{proofId}/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthenticatedUser caller,
                                        @PathVariable UUID proofId) {
        ProofPhoto photo = deliveryProofService.photo(proofId, caller);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("proof-" + proofId + ".jpg").build().toString())
                .body(photo.content());
    }
}
