package com.pahal.billingApp.controller;


import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.repository.SalesManRepository;
import com.pahal.billingApp.security.CustomUserDetails;
import com.pahal.billingApp.service.SalesManService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/salesman")
public class SalesManController {

    @Autowired
    private SalesManService salesManService;

    @PostMapping(path = "/addsalesman")
    public ResponseEntity<?> addNewSalesMan(@RequestBody Salesman salesman){
        salesManService.addNewSalesMan(salesman);
        return  new ResponseEntity<>("SalesMan created successfully. Employee_ID is: "+salesman.getEmployeeId(), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<?> getSalesmen(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
        }
        // Extract tenantId from your custom UserDetails
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String tenantId = userDetails.getTenantId();

        List<Salesman> list = salesManService.getAllSalesmenByTenant(tenantId);
        return ResponseEntity.ok(list);
    }
}
