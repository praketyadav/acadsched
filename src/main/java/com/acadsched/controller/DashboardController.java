package com.acadsched.controller;

import com.acadsched.repository.*;
import com.acadsched.security.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final FacultyRepository facultyRepository;
    private final SubjectRepository subjectRepository;
    private final ClassroomRepository classroomRepository;
    private final TimetableRepository timetableRepository;
    private final GrievanceRepository grievanceRepository;
    private final EventRepository eventRepository;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model, Authentication authentication) {
        
        // Populate stats for admin/faculty/student views (optional for non-admin, but safe)
        model.addAttribute("facultyCount", facultyRepository.count());
        model.addAttribute("subjectCount", subjectRepository.count());
        model.addAttribute("classroomCount", classroomRepository.count());
        model.addAttribute("timetableCount", timetableRepository.count());
        model.addAttribute("availableFacultyCount", facultyRepository.findByAvailable(true).size());
        model.addAttribute("availableClassroomCount", classroomRepository.findByAvailable(true).size());
        model.addAttribute("pendingGrievanceCount", grievanceRepository.findByStatus(
                com.acadsched.model.Grievance.GrievanceStatus.PENDING).size());
        model.addAttribute("upcomingEventCount", eventRepository.findByEventDateAfter(LocalDateTime.now()).size());

        if (authentication == null) {
            return "redirect:/login";
        }

        // Check assigned roles for redirection
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleConstants.ROLE_ADMIN));
        boolean isFaculty = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleConstants.ROLE_FACULTY));
        boolean isStudent = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleConstants.ROLE_STUDENT));

        if (isAdmin) {
            return "admin-dashboard";
        } else if (isFaculty) {
            return "faculty-dashboard";
        } else if (isStudent) {
            return "student-portal";
        }

        return "redirect:/login";
    }
}
