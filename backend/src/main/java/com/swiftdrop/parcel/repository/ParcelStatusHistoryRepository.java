package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.model.ParcelStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {

    @EntityGraph(attributePaths = {"previousStatus", "status"})
    List<ParcelStatusHistory> findByParcelIdOrderByChangedAtAsc(UUID parcelId);

    long countByParcelId(UUID parcelId);
}
