package com.acadsched.service;

import com.acadsched.model.Faculty;
import com.acadsched.repository.FacultyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FacultyService {

    private final FacultyRepository facultyRepository;

    @Transactional
    public Faculty createFaculty(Faculty faculty) {
        if (facultyRepository.existsByFacultyId(faculty.getFacultyId())) {
            throw new IllegalArgumentException("Faculty ID already exists");
        }
        if (facultyRepository.existsByEmail(faculty.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        return facultyRepository.save(faculty);
    }

    public List<Faculty> getAllFaculty() {
        return facultyRepository.findAll();
    }

    public Optional<Faculty> getFacultyById(Long id) {
        return facultyRepository.findById(id);
    }

    public List<Faculty> getFacultyByDepartment(String department) {
        return facultyRepository.findByDepartment(department);
    }

    public List<Faculty> getAvailableFaculty() {
        return facultyRepository.findByAvailable(true);
    }

    @Transactional
    public Faculty updateFaculty(Long id, Faculty updatedFaculty) {
        Faculty faculty = facultyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found"));
        
        faculty.setName(updatedFaculty.getName());
        faculty.setDepartment(updatedFaculty.getDepartment());
        faculty.setEmail(updatedFaculty.getEmail());
        faculty.setPhoneNumber(updatedFaculty.getPhoneNumber());
        faculty.setAvailable(updatedFaculty.getAvailable());
        
        return facultyRepository.save(faculty);
    }

    @Transactional
    public void deleteFaculty(Long id) {
        facultyRepository.deleteById(id);
    }

    @Transactional
    public void toggleAvailability(Long id) {
        Faculty faculty = facultyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found"));
        faculty.setAvailable(!faculty.getAvailable());
        facultyRepository.save(faculty);
    }
}
