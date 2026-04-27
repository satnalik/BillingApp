package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Salesman;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesManRepository extends JpaRepository<Salesman, Integer> {

    // Fetch all salesmen for a specific store
    List<Salesman> findByTenantId(String tenantId);
}
