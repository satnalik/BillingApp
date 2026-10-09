package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            insert into customers (tenant_id, contact_number, name, created_at)
            values (:tenantId, :contact, :name, :createdAt)
            on conflict (tenant_id, contact_number) do update
              set name = case when customers.name is null or btrim(customers.name) = ''
                              then coalesce(excluded.name, customers.name) else customers.name end
            """, nativeQuery = true)
    int ensureBillCustomer(@org.springframework.data.repository.query.Param("tenantId") String tenantId,
                          @org.springframework.data.repository.query.Param("contact") String contact,
                          @org.springframework.data.repository.query.Param("name") String name,
                          @org.springframework.data.repository.query.Param("createdAt") java.time.LocalDateTime createdAt);

    Optional<Customer> findByContactNumber(String contactNumber);

    List<Customer> findAllByOrderByCreatedAtDesc();

    List<Customer> findByNameContainingIgnoreCaseOrContactNumberContainingIgnoreCaseOrderByCreatedAtDesc(
            String name,
            String contactNumber);
}
