package com.acadsched.service;

import com.acadsched.model.Subject;
import com.acadsched.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    @Transactional
    public Subject createSubject(Subject subject) {
        return subjectRepository.save(subject);
    }

    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }

    public Optional<Subject> getSubjectById(Long id) {
        return subjectRepository.findById(id);
    }

    public List<Subject> getSubjectsByDepartment(String department) {
        return subjectRepository.findByDepartment(department);
    }

    public List<Subject> getSubjectsBySemester(String semester) {
        return subjectRepository.findBySemester(semester);
    }

    @Transactional
    public Subject updateSubject(Long id, Subject updatedSubject) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        
        subject.setName(updatedSubject.getName());
        subject.setCredits(updatedSubject.getCredits());
        subject.setDepartment(updatedSubject.getDepartment());
        subject.setSemester(updatedSubject.getSemester());
        subject.setEligibleFaculty(updatedSubject.getEligibleFaculty());
        subject.setPriority(updatedSubject.getPriority());
        subject.setType(updatedSubject.getType());
        
        return subjectRepository.save(subject);
    }

    @Transactional
    public void deleteSubject(Long id) {
        subjectRepository.deleteById(id);
    }
}
