package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.model.ParcelStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelStatusRepository extends JpaRepository<ParcelStatus, String> {
}
