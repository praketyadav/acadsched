package com.acadsched.config;

import com.acadsched.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration - Role-Based Access Control
 * 
 * ADMIN: Full system access (manage data, generate timetables, create events, view grievances)
 * FACULTY: View timetables, view events (read-only)
 * STUDENT: View timetables, submit grievances, view events (limited)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // Public pages
                .requestMatchers("/", "/home", "/register", "/css/**", "/js/**", "/images/**").permitAll()
                
                // ADMIN ONLY - System configuration and management
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/admin-dashboard").hasRole("ADMIN")
                .requestMatchers("/timetable/generate").hasRole("ADMIN")
                .requestMatchers("/timetable/reschedule/**").hasRole("ADMIN")
                .requestMatchers("/events/new", "/events/*/edit", "/events/*/delete", "/events/*/publish").hasRole("ADMIN")
                .requestMatchers("/grievances/analytics").hasRole("ADMIN")
                .requestMatchers("/grievances/*/update-status", "/grievances/*/delete").hasRole("ADMIN")
                
                // FACULTY - View access
                .requestMatchers("/timetable", "/timetable/view").hasAnyRole("ADMIN", "FACULTY")
                .requestMatchers("/events", "/events/*").hasAnyRole("ADMIN", "FACULTY", "STUDENT")
                
                // STUDENT - Limited access
                .requestMatchers("/grievances", "/grievances/new").hasAnyRole("ADMIN", "STUDENT")
                
                // Authenticated users - Dashboard
                .requestMatchers("/dashboard").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/access-denied")
            );

        return http.build();
    }
}
