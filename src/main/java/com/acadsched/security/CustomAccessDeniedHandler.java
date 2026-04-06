package com.acadsched.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom AccessDeniedHandler that routes users based on their role:
 *   - STUDENT  → /dashboard?accessDenied=true  (friendly redirect)
 *   - Others   → /access-denied                (generic error page)
 */
@Component
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        String uri = request.getRequestURI();

        log.warn("Access Denied: User '{}' attempted to access unauthorized path '{}'", username, uri);

        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleConstants.ROLE_STUDENT))) {
            // Students get a friendly redirect back to their dashboard
            response.sendRedirect(request.getContextPath() + "/dashboard?accessDenied=true");
        } else {
            // Admin / Faculty / unknown → standard access-denied page
            response.sendRedirect(request.getContextPath() + "/access-denied");
        }
    }
}
