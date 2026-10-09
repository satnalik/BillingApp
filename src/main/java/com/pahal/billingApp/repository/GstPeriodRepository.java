package com.pahal.billingApp.repository;
import com.pahal.billingApp.entity.GstPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GstPeriodRepository extends JpaRepository<GstPeriod, Long> {
    Optional<GstPeriod> findByTenantIdAndPeriodMonth(String tenant, String month);
    List<GstPeriod> findByTenantIdOrderByPeriodMonthDesc(String tenant);
}
