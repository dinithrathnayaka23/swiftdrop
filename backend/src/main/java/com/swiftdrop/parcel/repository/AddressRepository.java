package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.model.Address;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {
}
