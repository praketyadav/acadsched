package com.acadsched.controller;

import com.acadsched.model.User;
import com.acadsched.service.*;
import com.acadsched.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final UserService userService;
    private final FacultyRepository facultyRepository;
    private final SubjectRepository subjectRepository;
    private final ClassroomRepository classroomRepository;
    private final TimetableRepository timetableRepository;
    private final GrievanceRepository grievanceRepository;
    private final EventRepository eventRepository;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user, 
                              BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "register";
        }

        try {
            userService.createUser(user);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Add statistics for admin dashboard
        model.addAttribute("facultyCount", facultyRepository.count());
        model.addAttribute("subjectCount", subjectRepository.count());
        model.addAttribute("classroomCount", classroomRepository.count());
        model.addAttribute("timetableCount", timetableRepository.count());
        model.addAttribute("availableFacultyCount", facultyRepository.findByAvailable(true).size());
        model.addAttribute("availableClassroomCount", classroomRepository.findByAvailable(true).size());
        model.addAttribute("pendingGrievanceCount", grievanceRepository.findByStatus(
                com.acadsched.model.Grievance.GrievanceStatus.PENDING).size());
        model.addAttribute("upcomingEventCount", eventRepository.findByEventDateAfter(LocalDateTime.now()).size());
        
        return "dashboard";
    }
    
    @GetMapping("/admin-dashboard")
    public String adminDashboard(Model model) {
        // Add comprehensive statistics
        model.addAttribute("facultyCount", facultyRepository.count());
        model.addAttribute("subjectCount", subjectRepository.count());
        model.addAttribute("classroomCount", classroomRepository.count());
        model.addAttribute("timetableCount", timetableRepository.count());
        model.addAttribute("availableFacultyCount", facultyRepository.findByAvailable(true).size());
        model.addAttribute("availableClassroomCount", classroomRepository.findByAvailable(true).size());
        model.addAttribute("pendingGrievanceCount", grievanceRepository.findByStatus(
                com.acadsched.model.Grievance.GrievanceStatus.PENDING).size());
        model.addAttribute("upcomingEventCount", eventRepository.findByEventDateAfter(LocalDateTime.now()).size());
        
        return "admin-dashboard";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}
