package com.staminal.venue.admin;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.auth.service.JwtService;
// import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.vendors.Catering.VendorCateringDetails;
import com.staminal.venue.vendors.Catering.VendorCateringRepository;
import com.staminal.venue.vendors.Dj.VendorDjDetails;
import com.staminal.venue.vendors.Dj.VendorDjRepository;
import com.staminal.venue.vendors.Dto.VendorResponse;
// import com.staminal.venue.halls.Entity.Halls;
// import com.staminal.venue.halls.Repository.HallsRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Hall.VendorHallDetails;
import com.staminal.venue.vendors.Hall.VendorHallRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;
    private final VendorRepository vendorRepository;
    private final JwtService jwtService;
    private final VendorHallRepository vendorHallRepository;
    private final VendorDjRepository vendorDjRepository;
    private final VendorCateringRepository vendorCateringRepository;

    // private final HallsRepository hallsRepository;

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

    public VendorResponse approveVendor(Long vendorId) {

        Vendors vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setRejectionReason(null);

        Vendors savedVendor = vendorRepository.save(vendor);

        VendorResponse response = new VendorResponse();

        response.setId(savedVendor.getId());
        response.setVendorName(savedVendor.getVendorName());
        response.setBusinessName(savedVendor.getBusinessName());
        response.setDescription(savedVendor.getDescription());
        response.setCoverImageUrl(savedVendor.getCoverImageUrl());
        response.setCity(savedVendor.getCity());
        response.setArea(savedVendor.getArea());
        response.setContactNumber(savedVendor.getContactNumber());
        response.setWhatsAppNumber(savedVendor.getWhatsAppNumber());
        response.setStatus(savedVendor.getStatus().name());

        return response;
    }

    public VendorResponse rejectVendor(Long vendorId, String reason) {

        Vendors vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        vendor.setStatus(VendorStatus.REJECTED);
        vendor.setRejectionReason(reason);

        Vendors savedVendor = vendorRepository.save(vendor);

        VendorResponse response = new VendorResponse();

        response.setId(savedVendor.getId());
        response.setVendorName(savedVendor.getVendorName());
        response.setBusinessName(savedVendor.getBusinessName());
        response.setDescription(savedVendor.getDescription());
        response.setCoverImageUrl(savedVendor.getCoverImageUrl());
        response.setCity(savedVendor.getCity());
        response.setArea(savedVendor.getArea());
        response.setContactNumber(savedVendor.getContactNumber());
        response.setWhatsAppNumber(savedVendor.getWhatsAppNumber());
        response.setStatus(savedVendor.getStatus().name());

        return response;
    }

    public List<VendorResponse> getPendingVendors() {

        List<Vendors> vendors = vendorRepository.findByStatus(VendorStatus.PENDING);

        return vendors.stream()
                .map(vendor -> {

                    VendorResponse response = new VendorResponse();

                    response.setId(vendor.getId());
                    response.setVendorName(vendor.getVendorName());
                    response.setBusinessName(vendor.getBusinessName());
                    response.setDescription(vendor.getDescription());
                    response.setCoverImageUrl(vendor.getCoverImageUrl());
                    response.setCity(vendor.getCity());
                    response.setArea(vendor.getArea());
                    response.setContactNumber(vendor.getContactNumber());
                    response.setWhatsAppNumber(vendor.getWhatsAppNumber());
                    response.setStatus(vendor.getStatus().name());

                    return response;
                })
                .toList();
    }

    public AdminLoginResponse login(
            AdminLoginRequest request) {

        Admin admin = adminRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (!admin.getPasswordHash()
                .equals(request.getPassword())) {

            throw new RuntimeException(
                    "Invalid password");
        }

        AdminLoginResponse response = new AdminLoginResponse();

        String token = jwtService.generateToken(admin.getEmail(), "ADMIN");

        response.setMessage("Login Successful");
        response.setToken(token);

        return response;
    }

    public VendorHallDetails approveHall(Long hallId) {

        VendorHallDetails hall = vendorHallRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        hall.setStatus(VendorStatus.APPROVED);
        hall.setRejectionReason(null);

        return vendorHallRepository.save(hall);
    }

    public VendorHallDetails rejectHall(Long hallId, String reason) {

        VendorHallDetails hall = vendorHallRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        hall.setStatus(VendorStatus.REJECTED);
        hall.setRejectionReason(reason);

        return vendorHallRepository.save(hall);
    }

    public List<VendorHallDetails> getPendingHalls() {

        return vendorHallRepository.findByStatus(
                VendorStatus.PENDING);
    }

    public VendorDjDetails approveDj(Long djId) {

        VendorDjDetails dj = vendorDjRepository.findById(djId)
                .orElseThrow(() -> new RuntimeException("DJ not found"));

        dj.setStatus(VendorStatus.APPROVED);
        dj.setRejectionReason(null);

        return vendorDjRepository.save(dj);
    }

    public VendorDjDetails rejectDj(Long djId, String reason) {

        VendorDjDetails dj = vendorDjRepository.findById(djId)
                .orElseThrow(() -> new RuntimeException("DJ not found"));

        dj.setStatus(VendorStatus.REJECTED);
        dj.setRejectionReason(reason);

        return vendorDjRepository.save(dj);
    }

    public List<VendorDjDetails> getPendingDj() {

        return vendorDjRepository.findByStatus(
                VendorStatus.PENDING);
    }

    public VendorCateringDetails approveCatering(Long cateringId) {
        VendorCateringDetails catering = vendorCateringRepository.findById(cateringId)
                .orElseThrow(() -> new RuntimeException("Catering not found"));

        catering.setStatus(VendorStatus.APPROVED);
        catering.setRejectionReason(null);
        return vendorCateringRepository.save(catering);
    }

    public VendorCateringDetails rejectCatering(Long cateringId, String reason) {

        VendorCateringDetails catering = vendorCateringRepository.findById(cateringId)
                .orElseThrow(() -> new RuntimeException("Catering not found"));

        catering.setStatus(VendorStatus.REJECTED);
        catering.setRejectionReason(reason);

        return vendorCateringRepository.save(catering);
    }

    public List<VendorCateringDetails> getPendingCatering() {

        return vendorCateringRepository.findByStatus(
                VendorStatus.PENDING);
    }

}
