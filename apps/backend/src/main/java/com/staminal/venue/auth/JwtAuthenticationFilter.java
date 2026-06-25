package com.staminal.venue.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.staminal.venue.auth.service.JwtService;

import io.jsonwebtoken.JwtException;
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
                                && authHeader.startsWith("Bearer ")
                                && SecurityContextHolder.getContext().getAuthentication() == null) {

                        String token = authHeader.substring(7);
                        try {
                                if (jwtService.isAccessToken(token)) {
                                        String subject = jwtService.extractSubject(token);
                                        String role = jwtService.extractRole(token);

                                        List<SimpleGrantedAuthority> authorities = List.of(
                                                        new SimpleGrantedAuthority("ROLE_" + role));

                                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                                        subject,
                                                        null,
                                                        authorities);

                                        SecurityContextHolder
                                                        .getContext()
                                                        .setAuthentication(authentication);
                                }
                        } catch (JwtException | IllegalArgumentException exception) {
                                SecurityContextHolder.clearContext();
                        }
                }

                filterChain.doFilter(request, response);
        }
}
