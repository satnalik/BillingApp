package com.pahal.billingApp.api;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.entity.Customer;
import com.pahal.billingApp.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class CustomerControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;

    @Test
    void getCustomers_returnsCurrentTenantCustomersAndSupportsSearch() throws Exception {
        TenantContext.setCurrentTenant("Customer-Tenant-A");
        try {
            saveCustomer("Ravi Kumar", "9000011111");
            saveCustomer("Anita Sharma", "9888811111");
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant("Customer-Tenant-B");
        try {
            saveCustomer("Other Customer", "9000099999");
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant("Customer-Tenant-A");
        try {
            mockMvc.perform(get("/api/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name=='Ravi Kumar')]").exists())
                    .andExpect(jsonPath("$[?(@.name=='Anita Sharma')]").exists())
                    .andExpect(jsonPath("$[?(@.name=='Other Customer')]").doesNotExist());

            mockMvc.perform(get("/api/customers").param("q", "900001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name=='Ravi Kumar')]").exists())
                    .andExpect(jsonPath("$[?(@.name=='Anita Sharma')]").doesNotExist());
        } finally {
            TenantContext.clear();
        }
    }

    private void saveCustomer(String name, String contactNumber) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setContactNumber(contactNumber);
        customerRepository.save(customer);
    }
}
