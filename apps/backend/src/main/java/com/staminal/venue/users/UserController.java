package com.staminal.venue.users;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Dj.VendorDjDetails;
import com.staminal.venue.vendors.Hall.VendorHallDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    @GetMapping
    public List<User> getAllUsers() {

        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {

        return userService.getUserById(id);
    }

    @GetMapping("/phone/{phone}")
    public User getUserByPhone(@PathVariable String phone) {

        return userService.getUserByPhone(phone);
    }

    @GetMapping("/halls")
    public List<VendorHallDetails> getAllHalls() {

        return userService.getAllHalls();
    }

    @GetMapping("/djs")
    public List<VendorDjDetails> getAllDjs(){
        return userService.getAllDj();
    }
}