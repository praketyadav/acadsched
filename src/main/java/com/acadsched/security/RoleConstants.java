package com.acadsched.security;

/**
 * Centralized role constants — eliminates hardcoded role strings.
 *
 * Use the bare names (ADMIN, FACULTY, STUDENT) when comparing against
 * the User.Role enum, and the ROLE_* variants when working with
 * Spring Security's GrantedAuthority / hasRole() checks.
 */
public final class RoleConstants {

    private RoleConstants() { /* utility class */ }

    // ── Bare role names (match User.Role enum values) ──────────────────
    public static final String ADMIN   = "ADMIN";
    public static final String FACULTY = "FACULTY";
    public static final String STUDENT = "STUDENT";

    // ── Spring Security prefixed roles ─────────────────────────────────
    public static final String ROLE_ADMIN   = "ROLE_ADMIN";
    public static final String ROLE_FACULTY = "ROLE_FACULTY";
    public static final String ROLE_STUDENT = "ROLE_STUDENT";
}
