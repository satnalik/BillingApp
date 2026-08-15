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
