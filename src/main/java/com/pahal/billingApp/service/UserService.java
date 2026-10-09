package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.UserDetailsDTO;
import com.pahal.billingApp.dto.CreateUserRequest;
import com.pahal.billingApp.dto.ProvisionTenantOwnerRequest;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.enums.Role;
import com.pahal.billingApp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired private com.pahal.billingApp.licensing.TenantLicenseService licenses;
    @Autowired private com.pahal.billingApp.repository.CashierShiftRepository shifts;

    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;


    @Transactional
    public UserDetailsDTO addStaffUser(CreateUserRequest request, String tenantId) {
        if (request == null) throw new IllegalArgumentException("Request is required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Tenant is required");
        Role role = request.getRole();
        if (role != Role.ROLE_CASHIER && role != Role.ROLE_MANAGER) {
            throw new IllegalArgumentException("Staff role must be CASHIER or MANAGER");
        }
        licenses.requireUserCapacity(tenantId);
        return createUser(request.getUserId(), request.getPassword(), request.getName(), role, tenantId);
    }

    @Transactional
    public UserDetailsDTO provisionTenantOwner(ProvisionTenantOwnerRequest request, String tenantId) {
        if (request == null) throw new IllegalArgumentException("Request is required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Tenant is required");
        licenses.lockTenant(tenantId);
        if (userRepository.existsByTenantId(tenantId)) {
            throw new IllegalArgumentException("A user is already provisioned for this tenant");
        }
        if (request.getLicenseFile() == null || request.getLicenseFile().isBlank())
            throw new IllegalArgumentException("A signed license file is required for a new tenant.");
        licenses.importLicense(tenantId, request.getLicenseFile());
        licenses.requireUserCapacity(tenantId);
        return createUser(request.getUserId(), request.getPassword(), request.getName(), Role.ROLE_ADMIN, tenantId);
    }

    /** Only the packaged local installer uses this stricter, one-store, first-account operation. */
    @Transactional
    public synchronized UserDetailsDTO provisionFirstDesktopOwner(ProvisionTenantOwnerRequest request, String tenantId) {
        licenses.lockTenant("__desktop_first_owner__");
        if (userRepository.count() != 0) {
            throw new IllegalArgumentException("First-store setup has already been completed");
        }
        return provisionTenantOwner(request, tenantId);
    }

    public boolean isDesktopFirstStoreSetupRequired() {
        return userRepository.count() == 0;
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> staff(String tenantId) {
        return userRepository.findByTenantIdOrderByUserId(tenantId).stream().map(u ->
                java.util.Map.<String, Object>of("userId", u.getUserId(), "name", u.getName() == null ? "" : u.getName(),
                        "role", u.getRole().name(), "active", u.isActive())).toList();
    }
    @Transactional
    public void activeStaff(String userId, String tenantId, boolean active) {
        licenses.lockTenant(tenantId);
        var user = userRepository.lockCashier(userId, tenantId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (user.isActive() == active) return;
        if (!active && user.getRole() == Role.ROLE_ADMIN) throw new IllegalArgumentException("The tenant administrator cannot be deactivated.");
        if (!active && shifts.findFirstByTenantIdAndCashierUserIdAndClosedAtIsNullOrderByIdDesc(tenantId, userId).isPresent())
            throw new IllegalArgumentException("Close this user's shift before deactivating the account.");
        if (active) licenses.requireUserCapacity(tenantId);
        user.setActive(active); userRepository.save(user); licenses.record(tenantId, "USER_UPDATED", userId + " active=" + active);
    }

    private UserDetailsDTO createUser(String userId, String password, String name, Role role, String tenantId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("User ID is required");
        if (password == null || password.length() < 5) {
            throw new IllegalArgumentException("Password must be at least 5 characters");
        }
        if (userRepository.existsByUserId(userId.trim())) {
            throw new IllegalArgumentException("User ID is already in use");
        }
        User user = new User();
        user.setUserId(userId.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name != null ? name.trim() : null);
        user.setRole(role);
        user.setTenantId(tenantId.trim());
        user.set_FirstTimeLogin(true);
        User savedUser = userRepository.save(user);

        UserDetailsDTO dto = new UserDetailsDTO();
        dto.setId(savedUser.getId());
        dto.setUserId(savedUser.getUserId());
        dto.setRole(savedUser.getRole());
        dto.setTenantId(savedUser.getTenantId());

        return dto;
    }

    public void changePassword(String newPassword,User user){
        user.setPassword(passwordEncoder.encode(newPassword));
        user.set_FirstTimeLogin(false);
        userRepository.save(user);

    }

    public boolean changePasswordForUserId(String userId, String tenantId, String currentPassword, String newPassword) {
        Optional<User> userOpt = userRepository.findByUserIdAndTenantId(userId, tenantId);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }
        if (newPassword == null || newPassword.length() < 12) {
            throw new IllegalArgumentException("New password must be at least 12 characters");
        }
        changePassword(newPassword, user);
        return true;
    }
}
