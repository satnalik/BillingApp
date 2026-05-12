package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplierService {

    @Autowired
    private SupplierRepository supplierRepository;

    public Supplier createSupplier(Supplier supplier) {
        normalizeSupplier(supplier);
        return supplierRepository.save(supplier);
    }

    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public List<Supplier> searchSuppliers(String name) {
        return supplierRepository.findByNameContainingIgnoreCase(name);
    }

    public Supplier updateSupplier(Long id, Supplier request, String tenantId) {
        Supplier supplier = supplierRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        supplier.setName(request.getName());
        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhoneNumber(request.getPhoneNumber());
        supplier.setEmail(request.getEmail());
        supplier.setGstNumber(request.getGstNumber());
        supplier.setAddress(request.getAddress());
        supplier.setActive(request.isActive());

        normalizeSupplier(supplier);
        return supplierRepository.save(supplier);
    }

    public Supplier setActiveStatus(Long id, String tenantId, boolean active) {
        Supplier supplier = supplierRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        supplier.setActive(active);
        return supplierRepository.save(supplier);
    }

    private void normalizeSupplier(Supplier supplier) {
        if (supplier.getName() != null) {
            supplier.setName(supplier.getName().trim());
        }
        if (supplier.getSupplierCode() != null) {
            supplier.setSupplierCode(supplier.getSupplierCode().trim().toUpperCase());
        }
    }
}
