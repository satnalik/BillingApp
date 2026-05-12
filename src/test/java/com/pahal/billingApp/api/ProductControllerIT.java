package com.pahal.billingApp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.SupplierRepository;
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
    @Autowired SupplierRepository supplierRepository;

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
        create.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
        create.setStockQuantity(5.0);
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
        update.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
        update.setStockQuantity(99.0);

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

    @Test
    void createProduct_linksSupplierById() throws Exception {
        TenantContext.setCurrentTenant(TENANT);
        try {
            Supplier supplier = new Supplier();
            supplier.setName("ABC Traders");
            supplier.setSupplierCode("ABC");
            supplier = supplierRepository.save(supplier);

            Product product = new Product();
            product.setBarcode("SUP-123456");
            product.setName("Cotton Shirt");
            product.setSellingPrice(799.0);
            product.setPrice(799.0);
            product.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            product.setStockQuantity(20.0);
            product.setCategory("Shirts");

            Supplier supplierRef = new Supplier();
            supplierRef.setId(supplier.getId());
            product.setSupplier(supplierRef);

            mockMvc.perform(
                            post("/api/products")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(product))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.supplier.id").value(supplier.getId()))
                    .andExpect(jsonPath("$.supplierName").value("ABC Traders"));
        } finally {
            TenantContext.clear();
        }
    }
}
