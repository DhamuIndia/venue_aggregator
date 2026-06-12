package com.staminal.venue.halls;

import org.springframework.stereotype.Service;

import com.staminal.venue.halls.Repository.HallsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HallsService {
    
    private final HallsRepository hallsRepository;
}

