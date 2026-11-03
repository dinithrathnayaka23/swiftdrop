package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.model.ParcelStatusHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {
}
