package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.StoreProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreProfileRepository extends JpaRepository<StoreProfile, String> {
}
