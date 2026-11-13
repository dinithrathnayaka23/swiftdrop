package com.swiftdrop.dispatch.mapper;

import com.swiftdrop.dispatch.dto.response.AssignmentResponse;
import com.swiftdrop.dispatch.model.DeliveryAssignment;

public final class AssignmentMapper {

    private AssignmentMapper() {
    }

    /** Reads the (lazy) courier's account id: call it inside the transaction that loaded the assignment. */
    public static AssignmentResponse toResponse(DeliveryAssignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getParcelId(),
                assignment.getCourier().getId(),
                assignment.getCourier().getUserId(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedByUserId(),
                assignment.getAssignedAt(),
                assignment.getEndedAt(),
                assignment.getReassignmentReason());
    }
}
