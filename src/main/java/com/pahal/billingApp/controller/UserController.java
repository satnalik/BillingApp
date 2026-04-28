package com.pahal.billingApp.controller;


import com.pahal.billingApp.dto.UserDetailsDTO;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.repository.UserRepository;
import com.pahal.billingApp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/adduser")
public class UserController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<?> addNewUser(@RequestBody User user){
        UserDetailsDTO createdUser = userService.addUser(user);
        return ResponseEntity.ok(createdUser);
    }
}
