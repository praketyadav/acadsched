package com.acadsched.controller;

import com.acadsched.model.ClassGroup;
import com.acadsched.model.Faculty;
import com.acadsched.model.Timetable;
import com.acadsched.model.User;
import com.acadsched.security.RoleConstants;
import com.acadsched.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/timetable")
@RequiredArgsConstructor
@Slf4j
public class TimetableController {

    private final SchedulingService schedulingService;
    private final TimetableService timetableService;
    private final FacultyService facultyService;
    private final ClassroomService classroomService;
    private final UserService userService;
    private final com.acadsched.repository.ClassGroupRepository classGroupRepository;

    // ═══════════════════════════════════════════════════════════════════
    //  VIEW TIMETABLE — Role-Aware Landing Page
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping
    public String viewTimetable(Model model, Authentication authentication) {
        String username = authentication.getName();
        String role = extractRole(authentication);

        switch (role) {
            case RoleConstants.STUDENT -> {
                return handleStudentView(model, username);
            }
            case RoleConstants.FACULTY -> {
                return handleFacultyView(model, username);
            }
            default -> {
                // ADMIN — full management view
                model.addAttribute("isStudent", false);
                model.addAttribute("isFaculty", false);
                model.addAttribute("isAdmin", true);
                model.addAttribute("faculties", facultyService.getAllFaculty());
                model.addAttribute("classrooms", classroomService.getAllClassrooms());
                model.addAttribute("availableTimetables", schedulingService.getAvailableTimetables());
                return "timetable/view";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  VIEW BY SEMESTER/SECTION — With Student Guard
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/view")
    public String viewBySemesterSection(@RequestParam String semester,
                                        @RequestParam String section,
                                        Model model,
                                        Authentication authentication) {
        String username = authentication.getName();
        String role = extractRole(authentication);

        // ── STUDENT GUARD: Cannot browse other sections ─────────────
        if (RoleConstants.STUDENT.equals(role)) {
            return handleStudentView(model, username);
        }

        // ── FACULTY GUARD: Redirect to their schedule ───────────────
        if (RoleConstants.FACULTY.equals(role)) {
            return handleFacultyView(model, username);
        }

        // ── ADMIN: Standard semester/section view ───────────────────
        List<Timetable> schedule = schedulingService.getTimetableBySemesterAndSection(semester, section);
        model.addAttribute("schedule", schedule);
        model.addAttribute("semester", semester);
        model.addAttribute("section", section);
        model.addAttribute("faculties", facultyService.getAllFaculty());
        model.addAttribute("classrooms", classroomService.getAllClassrooms());
        model.addAttribute("availableTimetables", schedulingService.getAvailableTimetables());
        model.addAttribute("isStudent", false);
        model.addAttribute("isFaculty", false);
        model.addAttribute("isAdmin", true);
        return "timetable/view";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  STUDENT VIEW HELPER
    // ═══════════════════════════════════════════════════════════════════

    private String handleStudentView(Model model, String username) {
        model.addAttribute("isStudent", true);
        model.addAttribute("isFaculty", false);
        model.addAttribute("isAdmin", false);

        Optional<ClassGroup> classGroupOpt = timetableService.findClassGroupForStudent(username);

        if (classGroupOpt.isEmpty()) {
            // Student not assigned to any class group
            model.addAttribute("notAssigned", true);
            log.warn("Student '{}' attempted timetable view but has no ClassGroup", username);
            return "timetable/view";
        }

        ClassGroup classGroup = classGroupOpt.get();
        List<Timetable> schedule = timetableService.getStudentTimetableByUsername(username);

        model.addAttribute("notAssigned", false);
        model.addAttribute("schedule", schedule);
        model.addAttribute("studentClassGroup", classGroup);
        model.addAttribute("semester", schedule.isEmpty() ? null : schedule.get(0).getSemester());
        model.addAttribute("section", classGroup.getSection());

        log.info("Student '{}' viewing timetable for ClassGroup '{}' ({} entries)",
                username, classGroup.getName(), schedule.size());
        return "timetable/view";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FACULTY VIEW HELPER
    // ═══════════════════════════════════════════════════════════════════

    private String handleFacultyView(Model model, String username) {
        model.addAttribute("isStudent", false);
        model.addAttribute("isFaculty", true);
        model.addAttribute("isAdmin", false);

        // Look up the Faculty entity linked to this user
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User account not found.");
            return "timetable/view";
        }

        User user = userOpt.get();
        Faculty faculty = user.getFaculty();

        if (faculty == null) {
            // Faculty user not linked to a Faculty entity
            model.addAttribute("notAssigned", true);
            model.addAttribute("notAssignedMessage",
                    "Your account is not linked to a faculty profile. Please contact your administrator.");
            log.warn("Faculty user '{}' has no linked Faculty entity", username);
            return "timetable/view";
        }

        List<Timetable> schedule = schedulingService.getFacultyTimetable(faculty.getId());

        model.addAttribute("notAssigned", false);
        model.addAttribute("schedule", schedule);
        model.addAttribute("facultyProfile", faculty);
        model.addAttribute("semester", schedule.isEmpty() ? null : schedule.get(0).getSemester());
        model.addAttribute("section", schedule.isEmpty() ? null : schedule.get(0).getClassGroup().getSection());

        log.info("Faculty '{}' viewing their timetable ({} entries)",
                faculty.getName(), schedule.size());
        return "timetable/view";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ADMIN ONLY — Generate Timetable
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/generate")
    public String showGenerateForm(Model model) {
        model.addAttribute("classGroups", classGroupRepository.findAll());
        return "timetable/generate";
    }

    @PostMapping("/generate")
    public String generateTimetable(@RequestParam String semester,
                                    @RequestParam String section,
                                    @RequestParam(defaultValue = "1") int studentGapHours,
                                    @RequestParam(defaultValue = "false") boolean enforceLunchBreak,
                                    Model model) {
        try {
            List<Timetable> schedule = schedulingService.generateTimetable(
                    semester, section, studentGapHours, enforceLunchBreak);
            model.addAttribute("schedule", schedule);
            model.addAttribute("semester", semester);
            model.addAttribute("section", section);
            model.addAttribute("message",
                    "Timetable generated successfully! " + schedule.size() + " sessions across "
                    + schedule.stream().map(Timetable::getDayOfWeek).distinct().count() + " days.");
            model.addAttribute("faculties", facultyService.getAllFaculty());
            model.addAttribute("classrooms", classroomService.getAllClassrooms());
            model.addAttribute("isStudent", false);
            model.addAttribute("isFaculty", false);
            model.addAttribute("isAdmin", true);
            return "timetable/view";
        } catch (Exception e) {
            model.addAttribute("error", "Error generating timetable: " + e.getMessage());
            return "timetable/generate";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ADMIN ONLY — Reschedule / Faculty Leave / Admin Override
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/faculty/{facultyId}")
    public String viewFacultyTimetable(@PathVariable Long facultyId, Model model) {
        List<Timetable> schedule = schedulingService.getFacultyTimetable(facultyId);
        model.addAttribute("schedule", schedule);
        return "timetable/faculty-view";
    }

    @PostMapping("/reschedule/{id}")
    public String rescheduleSession(@PathVariable Long id,
                                    @RequestParam String newDay,
                                    @RequestParam String newTimeSlot,
                                    Model model) {
        try {
            schedulingService.rescheduleSession(id, newDay, newTimeSlot);
            model.addAttribute("message", "Session rescheduled successfully!");
        } catch (Exception e) {
            model.addAttribute("error", "Error rescheduling: " + e.getMessage());
        }
        return "redirect:/timetable";
    }

    /**
     * Handle faculty leave — dynamically reschedule affected classes.
     * Returns JSON so the frontend can show results without a full page reload.
     */
    @PostMapping("/faculty-leave")
    @ResponseBody
    public Map<String, Object> handleFacultyLeave(@RequestParam Long facultyId,
                                                   @RequestParam String leaveDay,
                                                   @RequestParam(required = false) String leaveTimeSlot,
                                                   @RequestParam(defaultValue = "1") int studentGapHours) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<String> actions = schedulingService.handleFacultyLeave(
                    facultyId, leaveDay, leaveTimeSlot, studentGapHours);

            List<Timetable> entries = schedulingService.getFacultyTimetable(facultyId);
            String semester = entries.isEmpty() ? null : entries.get(0).getSemester();
            String section  = entries.isEmpty() ? null : entries.get(0).getClassGroup().getSection();

            result.put("success", true);
            result.put("message", "Faculty leave processed. " + actions.size() + " class(es) handled.");
            result.put("actions", actions);
            result.put("semester", semester);
            result.put("section", section);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error processing faculty leave: " + e.getMessage());
            result.put("actions", List.of());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ADMIN OVERRIDE — Manual timetable editing with conflict validation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Admin overrides a single timetable entry.
     * Returns JSON: { saved: bool, conflicts: [...], entry: {...} }
     */
    @PostMapping("/admin-override")
    @ResponseBody
    public Map<String, Object> adminOverride(@RequestParam Long entryId,
                                              @RequestParam Long newFacultyId,
                                              @RequestParam Long newClassroomId,
                                              @RequestParam String newDay,
                                              @RequestParam String newTimeSlot,
                                              @RequestParam(defaultValue = "false") boolean force,
                                              @RequestParam(defaultValue = "1") int studentGapHours) {
        try {
            return schedulingService.adminOverride(entryId, newFacultyId, newClassroomId,
                    newDay, newTimeSlot, force, studentGapHours);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("saved", false);
            err.put("conflicts", List.of("Error: " + e.getMessage()));
            return err;
        }
    }

    /** API: List all classrooms (for modal dropdown). */
    @GetMapping("/api/classrooms")
    @ResponseBody
    public List<Map<String, Object>> getClassroomsJson() {
        return classroomService.getAllClassrooms().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("roomNumber", c.getRoomNumber());
                    m.put("building", c.getBuilding());
                    m.put("type", c.getType().name());
                    return m;
                })
                .toList();
    }

    /** API: List all faculty (for modal dropdown). */
    @GetMapping("/api/faculties")
    @ResponseBody
    public List<Map<String, Object>> getFacultiesJson() {
        return facultyService.getAllFaculty().stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("name", f.getName());
                    m.put("department", f.getDepartment());
                    return m;
                })
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extract the bare role name (ADMIN/FACULTY/STUDENT) from the Authentication.
     */
    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))  // strip "ROLE_"
                .findFirst()
                .orElse("");
    }
}
