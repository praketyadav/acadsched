package com.acadsched.controller;

import com.acadsched.model.Classroom;
import com.acadsched.model.Faculty;
import com.acadsched.model.Subject;
import com.acadsched.service.ClassroomService;
import com.acadsched.service.FacultyService;
import com.acadsched.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FacultyService facultyService;
    private final SubjectService subjectService;
    private final ClassroomService classroomService;

    // =============== FACULTY MANAGEMENT ===============
    
    @GetMapping("/faculty")
    public String listFaculty(Model model) {
        model.addAttribute("faculties", facultyService.getAllFaculty());
        return "admin/faculty-list";
    }

    @GetMapping("/faculty/new")
    public String showFacultyForm(Model model) {
        model.addAttribute("faculty", new Faculty());
        return "admin/faculty-form";
    }

    @PostMapping("/faculty/new")
    public String createFaculty(@Valid @ModelAttribute Faculty faculty, 
                               BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "admin/faculty-form";
        }
        try {
            facultyService.createFaculty(faculty);
            return "redirect:/admin/faculty?success";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "admin/faculty-form";
        }
    }

    @GetMapping("/faculty/{id}/edit")
    public String showEditFacultyForm(@PathVariable Long id, Model model) {
        Faculty faculty = facultyService.getFacultyById(id)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found"));
        model.addAttribute("faculty", faculty);
        return "admin/faculty-form";
    }

    @PostMapping("/faculty/{id}/edit")
    public String updateFaculty(@PathVariable Long id, 
                               @Valid @ModelAttribute Faculty faculty,
                               BindingResult result) {
        if (result.hasErrors()) {
            return "admin/faculty-form";
        }
        facultyService.updateFaculty(id, faculty);
        return "redirect:/admin/faculty?updated";
    }

    @PostMapping("/faculty/{id}/delete")
    public String deleteFaculty(@PathVariable Long id) {
        facultyService.deleteFaculty(id);
        return "redirect:/admin/faculty?deleted";
    }

    @PostMapping("/faculty/{id}/toggle")
    public String toggleFacultyAvailability(@PathVariable Long id) {
        facultyService.toggleAvailability(id);
        return "redirect:/admin/faculty";
    }

    // =============== SUBJECT MANAGEMENT ===============
    
    @GetMapping("/subjects")
    public String listSubjects(Model model) {
        model.addAttribute("subjects", subjectService.getAllSubjects());
        return "admin/subject-list";
    }

    @GetMapping("/subjects/new")
    public String showSubjectForm(Model model) {
        model.addAttribute("subject", new Subject());
        model.addAttribute("faculties", facultyService.getAllFaculty());
        return "admin/subject-form";
    }

    @PostMapping("/subjects/new")
    public String createSubject(@Valid @ModelAttribute Subject subject, 
                               BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("faculties", facultyService.getAllFaculty());
            return "admin/subject-form";
        }
        subjectService.createSubject(subject);
        return "redirect:/admin/subjects?success";
    }

    @GetMapping("/subjects/{id}/edit")
    public String showEditSubjectForm(@PathVariable Long id, Model model) {
        Subject subject = subjectService.getSubjectById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        model.addAttribute("subject", subject);
        model.addAttribute("faculties", facultyService.getAllFaculty());
        return "admin/subject-form";
    }

    @PostMapping("/subjects/{id}/edit")
    public String updateSubject(@PathVariable Long id, 
                               @Valid @ModelAttribute Subject subject,
                               BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("faculties", facultyService.getAllFaculty());
            return "admin/subject-form";
        }
        subjectService.updateSubject(id, subject);
        return "redirect:/admin/subjects?updated";
    }

    @PostMapping("/subjects/{id}/delete")
    public String deleteSubject(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return "redirect:/admin/subjects?deleted";
    }

    // =============== CLASSROOM MANAGEMENT ===============
    
    @GetMapping("/classrooms")
    public String listClassrooms(Model model) {
        model.addAttribute("classrooms", classroomService.getAllClassrooms());
        return "admin/classroom-list";
    }

    @GetMapping("/classrooms/new")
    public String showClassroomForm(Model model) {
        model.addAttribute("classroom", new Classroom());
        return "admin/classroom-form";
    }

    @PostMapping("/classrooms/new")
    public String createClassroom(@Valid @ModelAttribute Classroom classroom, 
                                 BindingResult result) {
        if (result.hasErrors()) {
            return "admin/classroom-form";
        }
        classroomService.createClassroom(classroom);
        return "redirect:/admin/classrooms?success";
    }

    @GetMapping("/classrooms/{id}/edit")
    public String showEditClassroomForm(@PathVariable Long id, Model model) {
        Classroom classroom = classroomService.getClassroomById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found"));
        model.addAttribute("classroom", classroom);
        return "admin/classroom-form";
    }

    @PostMapping("/classrooms/{id}/edit")
    public String updateClassroom(@PathVariable Long id, 
                                 @Valid @ModelAttribute Classroom classroom,
                                 BindingResult result) {
        if (result.hasErrors()) {
            return "admin/classroom-form";
        }
        classroomService.updateClassroom(id, classroom);
        return "redirect:/admin/classrooms?updated";
    }

    @PostMapping("/classrooms/{id}/delete")
    public String deleteClassroom(@PathVariable Long id) {
        classroomService.deleteClassroom(id);
        return "redirect:/admin/classrooms?deleted";
    }

    @PostMapping("/classrooms/{id}/toggle")
    public String toggleClassroomAvailability(@PathVariable Long id) {
        classroomService.toggleAvailability(id);
        return "redirect:/admin/classrooms";
    }
}
