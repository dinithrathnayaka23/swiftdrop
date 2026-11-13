package com.swiftdrop.dispatch.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * The result of a bulk assignment: the parcels given to the courier and the ones that were not,
 * each with the reason. Returned with 200 even when every parcel failed.
 */
public record BulkAssignResponse(List<AssignmentResponse> assigned, List<Failure> failed) {

    public record Failure(UUID parcelId, String reason) {
    }
}
