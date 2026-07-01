package com.staminal.venue.availability.Dto;

public class AvailabilitySummary {

    private Integer availableDays;

    private Integer bookedDays;

    private Integer totalDays;

    public Integer getAvailableDays() {
        return availableDays;
    }

    public void setAvailableDays(Integer availableDays) {
        this.availableDays = availableDays;
    }

    public Integer getBookedDays() {
        return bookedDays;
    }

    public void setBookedDays(Integer bookedDays) {
        this.bookedDays = bookedDays;
    }

    public Integer getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(Integer totalDays) {
        this.totalDays = totalDays;
    }

}