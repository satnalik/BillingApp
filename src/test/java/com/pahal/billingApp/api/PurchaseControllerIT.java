package com.pahal.billingApp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.dto.CancelPurchaseBillRequest;
import com.pahal.billingApp.dto.CreatePurchaseBillItemRequest;
import com.pahal.billingApp.dto.CreatePurchaseBillRequest;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.enums.ItemType;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PurchaseControllerIT {

    private static final String TENANT = "Tenant-Purchase";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ProductRepository productRepository;

    @Test
    void createPurchaseBill_increasesProductStockAndReturnsDetails() throws Exception {
        Supplier supplier = new Supplier();
        supplier.setName("ABC Traders");
        supplier.setSupplierCode("ABC");

        Product product = new Product();
        product.setBarcode("ABC-P1");
        product.setName("Cotton Shirt");
        product.setItemType(ItemType.PACKAGE);
        product.setStockQuantity(10.0);
        product.setSellingPrice(700.0);
        product.setPrice(700.0);

        TenantContext.setCurrentTenant(TENANT);
        try {
            supplier = supplierRepository.save(supplier);
            product = productRepository.save(product);
        } finally {
            TenantContext.clear();
        }

        CreatePurchaseBillItemRequest item = new CreatePurchaseBillItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(15.0);
        item.setPurchasePrice(400.0);
        item.setSellingPrice(799.0);

        CreatePurchaseBillRequest request = new CreatePurchaseBillRequest();
        request.setSupplierId(supplier.getId());
        request.setBillNumber("ABC-458");
        request.setBillDate(LocalDate.of(2026, 5, 11));
        request.setItems(List.of(item));
        request.setPaidAmount(1000.0);
        request.setPaymentMethod(PaymentMethod.UPI);
        request.setPaymentReference("upi-123");

        TenantContext.setCurrentTenant(TENANT);
        Long purchaseId;
        try {
            String json = mockMvc.perform(
                            post("/api/purchases")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.supplierId").value(supplier.getId()))
                    .andExpect(jsonPath("$.supplierName").value("ABC Traders"))
                    .andExpect(jsonPath("$.totalAmount").value(6000.0))
                    .andExpect(jsonPath("$.paidAmount").value(1000.0))
                    .andExpect(jsonPath("$.dueAmount").value(5000.0))
                    .andExpect(jsonPath("$.items[0].productId").value(product.getId()))
                    .andExpect(jsonPath("$.items[0].quantity").value(15.0))
                    .andExpect(jsonPath("$.payments[0].method").value("UPI"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            purchaseId = objectMapper.readTree(json).get("id").asLong();
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant(TENANT);
        try {
            Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStockQuantity()).isEqualTo(25.0);
            assertThat(updatedProduct.getCostPrice()).isEqualTo(400.0);
            assertThat(updatedProduct.getSellingPrice()).isEqualTo(799.0);

            mockMvc.perform(get("/api/purchases/{id}", purchaseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.billNumber").value("ABC-458"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.items[0].productName").value("Cotton Shirt"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void cancelPurchaseBill_reversesProductStockAndMarksCancelled() throws Exception {
        Supplier supplier = new Supplier();
        supplier.setName("XYZ Textiles");
        supplier.setSupplierCode("XYZ");

        Product product = new Product();
        product.setBarcode("XYZ-P1");
        product.setName("Jeans");
        product.setItemType(ItemType.PACKAGE);
        product.setStockQuantity(10.0);
        product.setSellingPrice(1200.0);
        product.setPrice(1200.0);

        TenantContext.setCurrentTenant(TENANT);
        try {
            supplier = supplierRepository.save(supplier);
            product = productRepository.save(product);
        } finally {
            TenantContext.clear();
        }

        CreatePurchaseBillItemRequest item = new CreatePurchaseBillItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(15.0);
        item.setPurchasePrice(700.0);

        CreatePurchaseBillRequest request = new CreatePurchaseBillRequest();
        request.setSupplierId(supplier.getId());
        request.setBillNumber("XYZ-100");
        request.setItems(List.of(item));

        Long purchaseId;
        TenantContext.setCurrentTenant(TENANT);
        try {
            String json = mockMvc.perform(
                            post("/api/purchases")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            purchaseId = objectMapper.readTree(json).get("id").asLong();
        } finally {
            TenantContext.clear();
        }

        CancelPurchaseBillRequest cancelRequest = new CancelPurchaseBillRequest();
        cancelRequest.setReason("Wrong quantity entered");

        TenantContext.setCurrentTenant(TENANT);
        try {
            mockMvc.perform(
                            patch("/api/purchases/{id}/cancel", purchaseId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(cancelRequest))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancelReason").value("Wrong quantity entered"))
                    .andExpect(jsonPath("$.cancelledAt").isString());

            Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStockQuantity()).isEqualTo(10.0);
        } finally {
            TenantContext.clear();
        }
    }
}
