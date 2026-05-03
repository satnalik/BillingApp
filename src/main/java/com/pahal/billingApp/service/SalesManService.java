package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.repository.SalesManRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SalesManService {


    @Autowired
    private SalesManRepository salesManRepository;

    public String addNewSalesMan(Salesman salesman){
        salesManRepository.save(salesman);
        return salesman.getEmployeeId();
    }

    public List<Salesman> getAllSalesmenByTenant(String tenantId) {
        return salesManRepository.findByTenantId(tenantId);
    }

    @CacheEvict(cacheNames = "reports", allEntries = true)
    public Salesman setActiveStatus(String employeeId, String tenantId, boolean active) {
        Salesman salesman = salesManRepository.findByEmployeeIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new RuntimeException("Salesman not found"));
        salesman.setActive(active);
        return salesManRepository.save(salesman);
    }
}
