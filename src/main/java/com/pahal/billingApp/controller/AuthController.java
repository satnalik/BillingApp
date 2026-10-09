package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.LoginRequest;
import com.pahal.billingApp.dto.ChangePasswordRequest;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.enums.Role;
import com.pahal.billingApp.repository.UserRepository;
import com.pahal.billingApp.security.CustomUserDetails;
import com.pahal.billingApp.service.JwtService;
import com.pahal.billingApp.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

//@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication API", description = "Endpoints for user authentication and password management")
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserService userService;

    @Operation(summary = "User Login", description = "Authenticate user and return JWT token along with user data")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> user = userRepository.findByUserId(request.getUserId());
        if (user.isPresent()) {
            User userData = user.get();
            if (userData.isActive() && passwordEncoder.matches(request.getPassword(), userData.getPassword())
                    && userData.getUserId().equals(request.getUserId())) {
                String userTenantId = userData.getTenantId();
                Role role = userData.getRole();
                String token = jwtService.generateToken(request.getUserId(), userTenantId, role);
//                return ResponseEntity.ok(Collections.singletonMap("token", token));
//                return new ResponseEntity<>("User login successful",HttpStatus.OK);
                Map userMap = new HashMap();
                userMap.put("token", token);
                userMap.put("UserData", userData);
                return new ResponseEntity<>(userMap,HttpStatus.OK);

            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Invalid userId or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);

    }

    @Operation(summary = "Change Password", description = "Allows the authenticated user to change their own password after verifying the current password.")
    @PostMapping("/change-password")
    public ResponseEntity<?> updateUserPasswordOnFirstLogin(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) {
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Authentication is required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Request body is required."));
        }
        boolean changed = userService.changePasswordForUserId(
                principal.getUsername(), principal.getTenantId(), request.getCurrentPassword(), request.getNewPassword());
        if (!changed) {
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Current password is incorrect.");
            return ResponseEntity.badRequest().body(body);
        }

        return new ResponseEntity<>("Password Updated Successfully.", HttpStatus.OK);
    }

}
