package com.pahal.billingApp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pahal.billingApp.dto.AddProductBarcodeRequest;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.repository.ProductBarcodeRepository;
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
    @Autowired ProductBarcodeRepository productBarcodeRepository;
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

    @Test
    void createProduct_withoutBarcodeAndFilterProductsBySupplier() throws Exception {
        TenantContext.setCurrentTenant(TENANT);
        try {
            Supplier supplier = new Supplier();
            supplier.setName("No Barcode Supplier");
            supplier.setSupplierCode("NBS");
            supplier = supplierRepository.save(supplier);

            Supplier otherSupplier = new Supplier();
            otherSupplier.setName("Other Supplier");
            otherSupplier.setSupplierCode("OTH");
            otherSupplier = supplierRepository.save(otherSupplier);

            Product product = new Product();
            product.setName("Barcode Later Product");
            product.setSellingPrice(199.0);
            product.setPrice(199.0);
            product.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            product.setStockQuantity(0.0);

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
                    .andExpect(jsonPath("$.supplierName").value("No Barcode Supplier"));

            Product otherProduct = new Product();
            otherProduct.setName("Other Product");
            otherProduct.setSellingPrice(299.0);
            otherProduct.setPrice(299.0);
            otherProduct.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            otherProduct.setStockQuantity(0.0);
            otherProduct.setSupplier(otherSupplier);
            TenantContext.setCurrentTenant(TENANT);
            productRepository.save(otherProduct);

            TenantContext.setCurrentTenant(TENANT);
            mockMvc.perform(get("/api/products").param("supplierId", supplier.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name=='Barcode Later Product')]").exists())
                    .andExpect(jsonPath("$[?(@.name=='Other Product')]").doesNotExist());

            TenantContext.setCurrentTenant(TENANT);
            mockMvc.perform(get("/api/products/search")
                            .param("supplierId", supplier.getId().toString())
                            .param("name", "Barcode Later"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name=='Barcode Later Product')]").exists())
                    .andExpect(jsonPath("$[?(@.name=='Other Product')]").doesNotExist());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void getByBarcode_withSupplierIdReturnsOnlyMatchingSupplierProduct() throws Exception {
        TenantContext.setCurrentTenant(TENANT);
        try {
            Supplier supplier = new Supplier();
            supplier.setName("Barcode Supplier");
            supplier.setSupplierCode("BCS");
            supplier = supplierRepository.save(supplier);

            Supplier otherSupplier = new Supplier();
            otherSupplier.setName("Wrong Supplier");
            otherSupplier.setSupplierCode("WRG");
            otherSupplier = supplierRepository.save(otherSupplier);

            Product product = new Product();
            product.setBarcode("8901234567890");
            product.setName("Packed Grocery Item");
            product.setSellingPrice(49.0);
            product.setPrice(49.0);
            product.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            product.setStockQuantity(5.0);
            product.setSupplier(supplier);
            product.setSupplierName(supplier.getName());
            productRepository.save(product);

            mockMvc.perform(get("/api/products/barcode/{barcode}", "8901234567890")
                            .param("supplierId", supplier.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(product.getId()))
                    .andExpect(jsonPath("$.supplier.id").value(supplier.getId()));

            mockMvc.perform(get("/api/products/barcode/{barcode}", "8901234567890")
                            .param("supplierId", otherSupplier.getId().toString()))
                    .andExpect(status().isNotFound());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void addBarcode_linksAdditionalBarcodeToSameProduct() throws Exception {
        TenantContext.setCurrentTenant(TENANT);
        try {
            Product product = new Product();
            product.setBarcode("BISCUIT-BOX-OLD");
            product.setName("Biscuit Pack");
            product.setSellingPrice(5.0);
            product.setPrice(5.0);
            product.setItemType(com.pahal.billingApp.enums.ItemType.PACKAGE);
            product.setStockQuantity(10.0);

            String productJson = mockMvc.perform(
                            post("/api/products")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(product))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.barcode").value("BISCUIT-BOX-OLD"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            long productId = objectMapper.readTree(productJson).get("id").asLong();

            AddProductBarcodeRequest barcodeRequest = new AddProductBarcodeRequest();
            barcodeRequest.setBarcode("BISCUIT-BOX-NEW");
            barcodeRequest.setQuantityPerScan(10.0);

            TenantContext.setCurrentTenant(TENANT);
            mockMvc.perform(
                            post("/api/products/{productId}/barcodes", productId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(barcodeRequest))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.barcode").value("BISCUIT-BOX-NEW"))
                    .andExpect(jsonPath("$.quantityPerScan").value(10.0));

            TenantContext.setCurrentTenant(TENANT);
            mockMvc.perform(get("/api/products/barcode/{barcode}", "BISCUIT-BOX-NEW"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId))
                    .andExpect(jsonPath("$.name").value("Biscuit Pack"));

            TenantContext.setCurrentTenant(TENANT);
            assertThat(productBarcodeRepository.findByBarcode("BISCUIT-BOX-NEW"))
                    .isPresent()
                    .get()
                    .extracting(barcode -> barcode.getProduct().getId())
                    .isEqualTo(productId);
        } finally {
            TenantContext.clear();
        }
    }
}
