package com.staminal.venue.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                                .cors(cors -> {
                                })
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exceptionHandling -> exceptionHandling
                                                .authenticationEntryPoint(
                                                                (request, response, authException) -> writeProblem(
                                                                                response,
                                                                                HttpStatus.UNAUTHORIZED,
                                                                                "Authentication required"))
                                                .accessDeniedHandler((request, response,
                                                                accessDeniedException) -> writeProblem(
                                                                                response,
                                                                                HttpStatus.FORBIDDEN,
                                                                                "Access denied")))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/v1/auth/register",
                                                                "/v1/auth/login",
                                                                "/v1/auth/refresh")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST, "/v1/vendor/login")
                                                .permitAll()
                                                .requestMatchers(
                                                                "/actuator/health",
                                                                "/docs",
                                                                "/docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/v1/public/**")
                                                .permitAll()
                                                .requestMatchers(
                                                                "/admin",
                                                                "/admin/login",
                                                                "/vendors",
                                                                "/vendor-dj",
                                                                "/vendor-hall",
                                                                "/vendor-decoration",
                                                                "/vendor-catering",
                                                                "/vendor-makeup",
                                                                "/vendors/login",
                                                                "/users/halls",
                                                                "/users/djs",
                                                                "/users/photography",
                                                                "/users/decoration",
                                                                "/users/catering")
                                                .permitAll()
                                                .requestMatchers("/v1/admin/**", "/admin/**")
                                                .hasAnyRole("ADMIN", "SUPER_ADMIN")
                                                .requestMatchers("/v1/vendor/**", "/vendor/**")
                                                .hasRole("VENDOR")
                                                .requestMatchers("/v1/owner/**").hasRole("HALL_OWNER")
                                                .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                                                .anyRequest().authenticated())
                                .formLogin(form -> form.disable())
                                .httpBasic(httpBasic -> httpBasic.disable())
                                .addFilterBefore(
                                                jwtAuthenticationFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource(
                        @Value("${app.cors.allowed-origins:http://localhost:3001,http://localhost:3000}") String allowedOrigins) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isBlank())
                                .toList());
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
                configuration.setExposedHeaders(List.of("Location"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        private void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
                response.setStatus(status.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("""
                                {"status":%d,"title":"%s","detail":"%s"}
                                """.formatted(status.value(), status.getReasonPhrase(), detail).trim());
        }
}
