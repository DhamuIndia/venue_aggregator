package com.staminal.venue.admin;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.vendors.Catering.VendorCateringDetails;
import com.staminal.venue.vendors.Dj.VendorDjDetails;
import com.staminal.venue.vendors.Dto.VendorResponse;
import com.staminal.venue.vendors.Hall.VendorHallDetails;

// import com.staminal.venue.halls.Entity.Halls;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping
    public Admin creatAdmin(@RequestBody Admin admin) {
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

    @PostMapping("/login")
    public AdminLoginResponse login(@RequestBody AdminLoginRequest request) {
        return adminService.login(request);
    }

    @GetMapping("/vendors/pending")
    public List<VendorResponse> getPendingVendors() {
        return adminService.getPendingVendors();
    }

    @PutMapping("/vendors/{id}/approve")
    public VendorResponse approveVendor(@PathVariable Long id) {
        return adminService.approveVendor(id);
    }

    @PutMapping("/vendors/{id}/reject")
    public VendorResponse rejectVendor(@PathVariable Long id, @RequestBody RejectRequest request) {
        return adminService.rejectVendor(id, request.getReason());
    }

    @PutMapping("/halls/{id}/approve")
    public VendorHallDetails approveHall(@PathVariable Long id) {
        return adminService.approveHall(id);
    }

    @PutMapping("/halls/{id}/reject")
    public VendorHallDetails rejectHall(@PathVariable Long id, @RequestBody RejectRequest request) {
        return adminService.rejectHall(id, request.getReason());
    }

    @GetMapping("/halls/pending")
    public List<VendorHallDetails> getPendingHalls() {
        return adminService.getPendingHalls();
    }

    @PutMapping("/dj/{id}/approve")
    public VendorDjDetails approveDj(@PathVariable Long id) {
        return adminService.approveDj(id);
    }

    @PutMapping("/dj/{id}/reject")
    public VendorDjDetails rejectDj(@PathVariable Long id, @RequestBody RejectRequest request) {
        return adminService.rejectDj(id, request.getReason());
    }

    @GetMapping("/dj/pending")
    public List<VendorDjDetails> getPendingDj() {
        return adminService.getPendingDj();
    }

    @PutMapping("/catering/{id}/approve")
    public VendorCateringDetails approveCatering(@PathVariable Long id) {
        return adminService.approveCatering(id);
    }

    @PutMapping("/catering/{id}/reject")
    public VendorCateringDetails rejectCatering(@PathVariable Long id, @RequestBody RejectRequest request) {
        return adminService.rejectCatering(id, request.getReason());
    }

    @GetMapping("/catering/pending")
    public List<VendorCateringDetails> getPendingCatering() {
        return adminService.getPendingCatering();
    }

}
