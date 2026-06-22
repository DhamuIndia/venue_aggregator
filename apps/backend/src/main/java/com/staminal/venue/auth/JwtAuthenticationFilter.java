package com.staminal.venue.auth;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.staminal.venue.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtService jwtService;

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain)
                        throws ServletException, IOException {

                String authHeader = request.getHeader("Authorization");

                if (authHeader != null
                                && authHeader.startsWith("Bearer ")) {

                        String token = authHeader.substring(7);
                        String email = jwtService.extractEmail(token);
                        String role = jwtService.extractRole(token);

                        List<SimpleGrantedAuthority> authorities = List.of(
                                        new SimpleGrantedAuthority(
                                                        "ROLE_" + role));

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        authorities);

                        SecurityContextHolder
                                        .getContext()
                                        .setAuthentication(authentication);
                }

                filterChain.doFilter(request, response);
        }
}