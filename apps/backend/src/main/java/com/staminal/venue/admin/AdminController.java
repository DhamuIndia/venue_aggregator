package com.staminal.venue.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.vendors.Entity.Vendors;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping
    public Admin creatAdmin(@RequestBody Admin admin){
        return adminService.createAdmin(admin);
    }

    @GetMapping
    public List<Admin> getAllAdmins() {
        return adminService.getAllAdmins();
    }

    @GetMapping("/{id}")
    public Admin getAdminById(@PathVariable Long id) {
        return adminService.getAdminById(id);
    }

    @PutMapping("/vendors/{id}/approve")
    public Vendors approveVendor(@PathVariable Long id) {

        return adminService.approveVendor(id);
    }

    @PutMapping("/vendors/{id}/reject")
    public Vendors rejectVendor(
            @PathVariable Long id,
            @RequestBody RejectRequest request) {

        return adminService.rejectVendor(
                id,
                request.getReason());
    }

    // @PutMapping("/halls/{id}/approve")
    // public Halls approveHall(@PathVariable Long id) {

    //     return adminService.approveHall(id);
    // }

    // @PutMapping("/halls/{id}/reject")
    // public Halls rejectHall(
    //         @PathVariable Long id,
    //         @RequestBody RejectRequest request) {

    //     return adminService.rejectHall(
    //             id,
    //             request.getReason());
    // }

    // @GetMapping("/vendors/pending")
    // public List<Vendors> getPendingVendors() {
    //     return adminService.getPendingVendors();
    // }

    // @GetMapping("/halls/pending")
    // public List<Halls> getPendingHalls() {
    //     return adminService.getPendingHalls();
    // }
}
