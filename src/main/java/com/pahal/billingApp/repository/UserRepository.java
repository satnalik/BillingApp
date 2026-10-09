package com.pahal.billingApp.repository;

import com.pahal.billingApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByTenantIdAndActiveTrue(String tenantId);
    java.util.List<User> findByTenantIdOrderByUserId(String tenantId);
    @org.springframework.data.jpa.repository.Query("select distinct u.tenantId from User u where u.tenantId is not null")
    java.util.List<String> findDistinctTenantIds();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.userId = :userId and u.tenantId = :tenantId")
    Optional<User> lockCashier(@org.springframework.data.repository.query.Param("userId") String userId,
                             @org.springframework.data.repository.query.Param("tenantId") String tenantId);

    Optional<User> findByUserId(String userId);
    Optional<User> findByUserIdAndTenantId(String userId, String tenantId);
    boolean existsByUserId(String userId);
    boolean existsByTenantId(String tenantId);
}
