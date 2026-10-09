package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.Customer;
import com.pahal.billingApp.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void ensureBillCustomer(String name, String contactInfo) {
        String tenant = StockService.requireTenant();
        if (contactInfo == null || contactInfo.isBlank()) return;
        String contact = contactInfo.trim();
        if (contact.length() > 32) throw new IllegalArgumentException("Customer contact must be at most 32 characters.");
        String displayName = name == null || name.isBlank() ? null : name.trim();
        customerRepository.ensureBillCustomer(tenant, contact, displayName, java.time.LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Customer> getCustomers(String q) {
        if (q == null || q.isBlank()) {
            return customerRepository.findAllByOrderByCreatedAtDesc();
        }

        String search = q.trim();
        return customerRepository
                .findByNameContainingIgnoreCaseOrContactNumberContainingIgnoreCaseOrderByCreatedAtDesc(search, search);
    }
}
