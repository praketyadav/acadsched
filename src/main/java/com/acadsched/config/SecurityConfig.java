package com.acadsched.config;

import com.acadsched.security.CustomAccessDeniedHandler;
import com.acadsched.security.CustomUserDetailsService;
import com.acadsched.security.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security Configuration — Role-Based Access Control (Principle of Least Privilege)
 *
 * ADMIN:   Full system access (manage data, generate/edit timetables, events, grievances)
 * FACULTY: View timetables (own schedule auto-loaded), view events (read-only)
 * STUDENT: View own timetable (auto-filtered to ClassGroup), submit grievances, view events
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAccessDeniedHandler accessDeniedHandler;

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
                // ── Public pages & static resources ─────────────────────
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/home", "/register", "/login").permitAll()

                // ── ADMIN ONLY — System configuration & management ──────
                .requestMatchers("/admin/**").hasRole(RoleConstants.ADMIN)
                .requestMatchers("/admin-dashboard").hasRole(RoleConstants.ADMIN)

                // ── TIMETABLE — Granular path mapping ───────────────────
                // Viewing: All authenticated roles
                .requestMatchers(HttpMethod.GET, "/timetable").hasAnyRole(
                        RoleConstants.ADMIN, RoleConstants.FACULTY, RoleConstants.STUDENT)
                .requestMatchers(HttpMethod.GET, "/timetable/view", "/timetable/view/**").hasAnyRole(
                        RoleConstants.ADMIN, RoleConstants.FACULTY, RoleConstants.STUDENT)

                // API endpoints (AJAX data loading for grid): All authenticated roles
                .requestMatchers(HttpMethod.GET, "/timetable/api/**").hasAnyRole(
                        RoleConstants.ADMIN, RoleConstants.FACULTY, RoleConstants.STUDENT)

                // Generation: ADMIN only
                .requestMatchers("/timetable/generate").hasRole(RoleConstants.ADMIN)

                // Mutation endpoints (POST/PUT/DELETE): ADMIN only
                .requestMatchers(HttpMethod.POST, "/timetable/**").hasRole(RoleConstants.ADMIN)
                .requestMatchers(HttpMethod.PUT, "/timetable/**").hasRole(RoleConstants.ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/timetable/**").hasRole(RoleConstants.ADMIN)

                // ── EVENTS ──────────────────────────────────────────────
                .requestMatchers("/events/new", "/events/*/edit", "/events/*/delete", "/events/*/publish")
                        .hasRole(RoleConstants.ADMIN)
                .requestMatchers("/events", "/events/*").hasAnyRole(
                        RoleConstants.ADMIN, RoleConstants.FACULTY, RoleConstants.STUDENT)

                // ── GRIEVANCES ──────────────────────────────────────────
                .requestMatchers("/grievances/analytics").hasRole(RoleConstants.ADMIN)
                .requestMatchers("/grievances/*/update-status", "/grievances/*/delete")
                        .hasRole(RoleConstants.ADMIN)
                .requestMatchers("/grievances", "/grievances/new").hasAnyRole(
                        RoleConstants.ADMIN, RoleConstants.STUDENT)

                // ── Dashboard & Root: any authenticated user ───────────────────
                .requestMatchers("/dashboard", "/").authenticated()

                // ── Catch-all ───────────────────────────────────────────
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
                .accessDeniedHandler(accessDeniedHandler)
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/admin/faculty/import"),
                    new AntPathRequestMatcher("/admin/subjects/import"),
                    new AntPathRequestMatcher("/admin/classrooms/import"),
                    new AntPathRequestMatcher("/timetable/reschedule/**")
                )
            );

        return http.build();
    }
}
