package com.pahal.billingApp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ProductControllerIT {

    private static final String TENANT = "Tenant-A";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductRepository productRepository;

    @Test
    void upsertByBarcode_createsThenUpdatesStock() throws Exception {
        String barcode = "123456";
        TenantContext.setCurrentTenant(TENANT);
        try {

        Product create = new Product();
        create.setBarcode(barcode);
        create.setName("Dove");
        create.setSellingPrice(10.0);
        create.setPrice(10.0);
        create.setStockQuantity(5);
        create.setCategory("FMCG");

        mockMvc.perform(
                        post("/api/products/barcode")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(create))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.barcode").value(barcode))
                .andExpect(jsonPath("$.stockQuantity").value(5));

        Product update = new Product();
        update.setBarcode(barcode);
        update.setName("Dove (Updated)");
        update.setSellingPrice(12.5);
        update.setPrice(12.5);
        update.setStockQuantity(99);

        mockMvc.perform(
                        post("/api/products/barcode")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value(barcode))
                .andExpect(jsonPath("$.name").value("Dove (Updated)"))
                .andExpect(jsonPath("$.stockQuantity").value(99));

        // Verify only one product exists for this barcode (unique per tenant).
        TenantContext.setCurrentTenant(TENANT);
        try {
            assertThat(productRepository.findByBarcode(barcode)).isNotNull();
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(get("/api/products")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.barcode=='" + barcode + "')]").exists());
        } finally {
            TenantContext.clear();
        }
    }
}
