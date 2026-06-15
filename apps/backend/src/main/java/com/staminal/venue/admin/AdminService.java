package com.staminal.venue.admin;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallsRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;

    private final VendorRepository vendorRepository;

    private final HallsRepository hallsRepository;

    public Admin createAdmin(Admin admin) {
        admin.setStatus("ACTIVE");
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());

        return adminRepository.save(admin);
    }

    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    public Admin getAdminById(Long id) {

        return adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
    }

    public Vendors approveVendor(Long vendorId) {

        Vendors vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setRejectionReason(null);

        return vendorRepository.save(vendor);
    }

    public Vendors rejectVendor(Long vendorId, String reason) {

        Vendors vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        vendor.setStatus(VendorStatus.REJECTED);
        vendor.setRejectionReason(reason);

        return vendorRepository.save(vendor);
    }

    public Halls approveHall(Long hallId) {

        Halls hall = hallsRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        hall.setStatus(HallStatus.APPROVED);
        hall.setRejectionReason(null);

        return hallsRepository.save(hall);
    }

    public Halls rejectHall(Long hallId, String reason) {

        Halls hall = hallsRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        hall.setStatus(HallStatus.REJECTED);
        hall.setRejectionReason(reason);

        return hallsRepository.save(hall);
    }

    public List<Vendors> getPendingVendors() {
        return vendorRepository.findByStatus(VendorStatus.PENDING);
    }

    public List<Halls> getPendingHalls() {
        return hallsRepository.findByStatus(HallStatus.PENDING);
    }

}
