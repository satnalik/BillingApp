package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.LoginRequest;
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
            if (passwordEncoder.matches(request.getPassword(), userData.getPassword())
                    && userData.getUserId().equals(request.getUserId())) {
                String userTenantId = userData.getTenantId();
                Role role = userData.getRole();
                String token = jwtService.generateToken(request.getUserId(), userTenantId, role);
                // return new ResponseEntity<>("User login successful",HttpStatus.OK);
                Map userMap = new HashMap();
                userMap.put("token", token);
                userMap.put("UserData", userData);
                return ResponseEntity.ok(userMap);

            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Invalid userId or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);

    }

    @Operation(summary = "Change Password on First Login", description = "Allows users to change their password on first login. The userId can be provided either via JWT authentication or as a query parameter.")
    @PostMapping("/change-password")
    public ResponseEntity<?> updateUserPasswordOnFirstLogin(
            @RequestParam String password,
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String resolvedUserId = principal != null ? principal.getUsername() : userId;
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            Map<String, Object> body = new HashMap<>();
            body.put("message", "userId is required (either via JWT auth or userId query param).");
            return ResponseEntity.badRequest().body(body);
        }

        boolean changed = userService.changePasswordForUserId(resolvedUserId, password);
        if (!changed) {
            Map<String, Object> body = new HashMap<>();
            body.put("message", "User not found with userId: " + resolvedUserId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        return new ResponseEntity<>("Password Updated Successfully.", HttpStatus.OK);
    }

}
