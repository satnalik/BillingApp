package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.repository.SalesManRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
}
