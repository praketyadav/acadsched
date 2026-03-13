package com.acadsched.controller;

import com.acadsched.model.Timetable;
import com.acadsched.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final SchedulingService schedulingService;

    @GetMapping
    public String viewTimetable(Model model) {
        return "timetable/view";
    }

    @GetMapping("/generate")
    public String showGenerateForm(Model model) {
        return "timetable/generate";
    }

    @PostMapping("/generate")
    public String generateTimetable(@RequestParam String semester, 
                                   @RequestParam String section, 
                                   Model model) {
        try {
            List<Timetable> schedule = schedulingService.generateTimetable(semester, section);
            model.addAttribute("schedule", schedule);
            model.addAttribute("message", "Timetable generated successfully!");
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
}
