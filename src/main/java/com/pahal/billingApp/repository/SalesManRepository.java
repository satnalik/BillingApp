package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Salesman;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesManRepository extends JpaRepository<Salesman, String> {

    // Fetch all salesmen for a specific store
    List<Salesman> findByTenantId(String tenantId);

    Optional<Salesman> findByEmployeeIdAndTenantId(String employeeId, String tenantId);
}
