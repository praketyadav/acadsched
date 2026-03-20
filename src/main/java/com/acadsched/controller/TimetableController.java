package com.acadsched.controller;

import com.acadsched.model.Classroom;
import com.acadsched.model.Faculty;
import com.acadsched.model.Timetable;
import com.acadsched.service.ClassroomService;
import com.acadsched.service.FacultyService;
import com.acadsched.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final SchedulingService schedulingService;
    private final FacultyService facultyService;
    private final ClassroomService classroomService;

    @GetMapping
    public String viewTimetable(Model model) {
        model.addAttribute("faculties", facultyService.getAllFaculty());
        model.addAttribute("classrooms", classroomService.getAllClassrooms());
        model.addAttribute("availableTimetables", schedulingService.getAvailableTimetables());
        return "timetable/view";
    }

    @GetMapping("/generate")
    public String showGenerateForm(Model model) {
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
            return "timetable/view";
        } catch (Exception e) {
            model.addAttribute("error", "Error generating timetable: " + e.getMessage());
            return "timetable/generate";
        }
    }

    @GetMapping("/view")
    public String viewBySemesterSection(@RequestParam String semester,
                                        @RequestParam String section,
                                        Model model) {
        List<Timetable> schedule = schedulingService.getTimetableBySemesterAndSection(semester, section);
        model.addAttribute("schedule", schedule);
        model.addAttribute("semester", semester);
        model.addAttribute("section", section);
        model.addAttribute("faculties", facultyService.getAllFaculty());
        model.addAttribute("classrooms", classroomService.getAllClassrooms());
        return "timetable/view";
    }

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
}

