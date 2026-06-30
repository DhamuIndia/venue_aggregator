package com.staminal.venue.reviews;

import java.time.Instant;

import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.users.Entity.User;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

public class Review {
    @Id
@GeneratedValue
private Long id;

@ManyToOne
private Enquiry enquiry;

@ManyToOne
private User customer;

private Integer rating;

private String title;

private String comment;

private Boolean verifiedService;

private Boolean active;

private Instant createdAt;

private Instant updatedAt;

public Long getId() {
    return id;
}

public void setId(Long id) {
    this.id = id;
}

public Enquiry getEnquiry() {
    return enquiry;
}

public void setEnquiry(Enquiry enquiry) {
    this.enquiry = enquiry;
}

public User getCustomer() {
    return customer;
}

public void setCustomer(User customer) {
    this.customer = customer;
}

public Integer getRating() {
    return rating;
}

public void setRating(Integer rating) {
    this.rating = rating;
}

public String getTitle() {
    return title;
}

public void setTitle(String title) {
    this.title = title;
}

public String getComment() {
    return comment;
}

public void setComment(String comment) {
    this.comment = comment;
}

public Boolean getVerifiedService() {
    return verifiedService;
}

public void setVerifiedService(Boolean verifiedService) {
    this.verifiedService = verifiedService;
}

public Boolean getActive() {
    return active;
}

public void setActive(Boolean active) {
    this.active = active;
}

public Instant getCreatedAt() {
    return createdAt;
}

public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
}

public Instant getUpdatedAt() {
    return updatedAt;
}

public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
}

}
