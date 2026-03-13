package com.acadsched.controller;

import com.acadsched.model.Grievance;
import com.acadsched.model.User;
import com.acadsched.service.GrievanceService;
import com.acadsched.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Grievance Controller - Manages student grievances
 * Role Permissions:
 * - ADMIN: View all grievances, update status, view analytics
 * - STUDENT: Submit grievances, view own grievances
 * - FACULTY: Cannot access (optional: view summary)
 */
@Controller
@RequestMapping("/grievances")
@RequiredArgsConstructor
public class GrievanceController {

    private final GrievanceService grievanceService;
    private final UserService userService;

    /**
     * List grievances based on role
     * - Admin: sees all grievances
     * - Student: sees only their own grievances
     */
    @GetMapping
    public String listGrievances(Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        List<Grievance> grievances;
        if (user.getRole() == User.Role.ADMIN) {
            // Admin sees all grievances
            grievances = grievanceService.getAllGrievances();
        } else if (user.getRole() == User.Role.STUDENT) {
            // Student sees only their own grievances
            grievances = grievanceService.getGrievancesByStudent(user.getId());
        } else {
            // Faculty cannot access grievances (or redirect to limited view)
            return "redirect:/dashboard?error=unauthorized";
        }
        
        model.addAttribute("grievances", grievances);
        model.addAttribute("userRole", user.getRole());
        return "grievances/list";
    }

    /**
     * Show create grievance form - STUDENT ONLY
     */
    @GetMapping("/new")
    public String showCreateForm(Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // STUDENT ONLY - Submit grievances
        if (user.getRole() != User.Role.STUDENT) {
            return "redirect:/grievances?error=only_students_can_submit";
        }
        
        model.addAttribute("grievance", new Grievance());
        return "grievances/create";
    }

    /**
     * Create new grievance - STUDENT ONLY
     */
    @PostMapping("/new")
    public String createGrievance(@Valid @ModelAttribute Grievance grievance,
                                 BindingResult result,
                                 Authentication authentication,
                                 Model model) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // STUDENT ONLY - Submit grievances
        if (user.getRole() != User.Role.STUDENT) {
            return "redirect:/grievances?error=only_students_can_submit";
        }
        
        if (result.hasErrors()) {
            return "grievances/create";
        }

        grievance.setStudent(user);
        grievanceService.createGrievance(grievance);
        return "redirect:/grievances?success=submitted";
    }

    /**
     * View analytics - ADMIN ONLY
     */
    @GetMapping("/analytics")
    public String viewAnalytics(Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - View analytics
        if (user.getRole() != User.Role.ADMIN) {
            return "redirect:/grievances?error=unauthorized";
        }
        
        Map<String, Long> categoryAnalytics = grievanceService.getCategoryAnalytics();
        Map<String, Long> statusAnalytics = grievanceService.getStatusAnalytics();
        
        model.addAttribute("categoryAnalytics", categoryAnalytics);
        model.addAttribute("statusAnalytics", statusAnalytics);
        return "grievances/analytics";
    }

    /**
     * Update grievance status - ADMIN ONLY
     */
    @PostMapping("/{id}/update-status")
    public String updateStatus(@PathVariable Long id,
                              @RequestParam Grievance.GrievanceStatus status,
                              @RequestParam String response,
                              Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - Update status
        if (user.getRole() != User.Role.ADMIN) {
            return "redirect:/grievances?error=unauthorized";
        }
        
        grievanceService.updateGrievanceStatus(id, status, response);
        return "redirect:/grievances?success=updated";
    }

    /**
     * Delete grievance - ADMIN ONLY
     */
    @PostMapping("/{id}/delete")
    public String deleteGrievance(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - Delete grievances
        if (user.getRole() != User.Role.ADMIN) {
            return "redirect:/grievances?error=unauthorized";
        }
        
        grievanceService.deleteGrievance(id);
        return "redirect:/grievances?success=deleted";
    }
}
