package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.UserDetailsDTO;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.repository.UserRepository;
import com.pahal.billingApp.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/users")
@Tag(name = "User API", description = "Endpoints for managing users, including creation and retrieval of user details")
public class UserController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

    @Operation(summary = "Add New User", description = "Creates a new user with the provided details. The user will be associated with the tenant from the JWT token.")
    @PostMapping("/adduser")
    public ResponseEntity<?> addNewUser(@RequestBody User user) {
        UserDetailsDTO createdUser = userService.addUser(user);
        return ResponseEntity.ok(createdUser);
    }
}
