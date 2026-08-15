package com.pahal.billingApp.controller;

import com.pahal.billingApp.entity.Customer;
import com.pahal.billingApp.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customer API", description = "Endpoints for customer lookup")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Operation(summary = "Get Customers", description = "Returns customers captured while creating bills. Optional q searches by name or contact number.")
    @GetMapping
    public List<Customer> getCustomers(@RequestParam(required = false) String q) {
        return customerService.getCustomers(q);
    }
}
