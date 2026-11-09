package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.model.AssignmentStatus;
import java.util.UUID;

/** Number of one courier's assignments with one status, from {@link DeliveryAssignmentRepository#countWorkload}. */
public record WorkloadCount(UUID courierId, AssignmentStatus status, long total) {
}
