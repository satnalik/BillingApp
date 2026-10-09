package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.StoreProfileDTO;
import com.pahal.billingApp.entity.StoreProfile;
import com.pahal.billingApp.repository.StoreProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProfileService {
    private final StoreProfileRepository repository;

    public StoreProfileService(StoreProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StoreProfileDTO getForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant is required");
        }
        return repository.findById(tenantId.trim())
                .map(this::toDto)
                .orElseGet(() -> defaultProfile(tenantId.trim()));
    }

    @Transactional
    public StoreProfileDTO saveForTenant(String tenantId, StoreProfileDTO request) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant is required");
        }
        if (request == null) throw new IllegalArgumentException("Store profile is required");

        String storeName = normalize(request.getStoreName());
        if (storeName == null) throw new IllegalArgumentException("Store name is required");

        StoreProfile profile = repository.findById(tenantId.trim()).orElseGet(StoreProfile::new);
        profile.setTenantId(tenantId.trim());
        profile.setStoreName(requireMax(storeName, 160, "Store name"));
        profile.setAddress(optionalMax(request.getAddress(), 1000, "Address"));
        profile.setPhoneNumber(optionalMax(request.getPhoneNumber(), 40, "Phone number"));
        profile.setGstin(optionalMax(request.getGstin(), 20, "GSTIN"));
        profile.setInvoiceHeader(defaultValue(optionalMax(request.getInvoiceHeader(), 120, "Invoice header"), "INVOICE"));
        profile.setInvoiceFooter(defaultValue(optionalMax(request.getInvoiceFooter(), 500, "Invoice footer"), "Thank you!"));
        return toDto(repository.save(profile));
    }

    private StoreProfileDTO defaultProfile(String tenantId) {
        StoreProfileDTO dto = new StoreProfileDTO();
        dto.setStoreName(tenantId);
        dto.setInvoiceHeader("INVOICE");
        dto.setInvoiceFooter("Thank you!");
        return dto;
    }

    private StoreProfileDTO toDto(StoreProfile profile) {
        StoreProfileDTO dto = new StoreProfileDTO();
        dto.setStoreName(profile.getStoreName());
        dto.setAddress(profile.getAddress());
        dto.setPhoneNumber(profile.getPhoneNumber());
        dto.setGstin(profile.getGstin());
        dto.setInvoiceHeader(defaultValue(profile.getInvoiceHeader(), "INVOICE"));
        dto.setInvoiceFooter(defaultValue(profile.getInvoiceFooter(), "Thank you!"));
        return dto;
    }

    private String optionalMax(String value, int max, String label) {
        String normalized = normalize(value);
        return normalized == null ? null : requireMax(normalized, max, label);
    }

    private String requireMax(String value, int max, String label) {
        if (value.length() > max) throw new IllegalArgumentException(label + " must be at most " + max + " characters");
        return value;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
