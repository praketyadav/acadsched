package com.acadsched.service;

import com.acadsched.model.Grievance;
import com.acadsched.repository.GrievanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GrievanceService {

    private final GrievanceRepository grievanceRepository;

    @Transactional
    public Grievance createGrievance(Grievance grievance) {
        return grievanceRepository.save(grievance);
    }

    public List<Grievance> getAllGrievances() {
        return grievanceRepository.findByOrderByCreatedAtDesc();
    }

    public List<Grievance> getGrievancesByStudent(Long studentId) {
        return grievanceRepository.findByStudentId(studentId);
    }

    public List<Grievance> getGrievancesByStatus(Grievance.GrievanceStatus status) {
        return grievanceRepository.findByStatus(status);
    }

    public List<Grievance> getGrievancesByCategory(Grievance.GrievanceCategory category) {
        return grievanceRepository.findByCategory(category);
    }

    @Transactional
    public Grievance updateGrievanceStatus(Long id, Grievance.GrievanceStatus status, String response) {
        Grievance grievance = grievanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Grievance not found"));
        
        grievance.setStatus(status);
        grievance.setResponse(response);
        
        if (status == Grievance.GrievanceStatus.RESOLVED) {
            grievance.setResolvedAt(LocalDateTime.now());
        }
        
        return grievanceRepository.save(grievance);
    }

    public Map<String, Long> getCategoryAnalytics() {
        Map<String, Long> analytics = new HashMap<>();
        for (Grievance.GrievanceCategory category : Grievance.GrievanceCategory.values()) {
            long count = grievanceRepository.findByCategory(category).size();
            analytics.put(category.name(), count);
        }
        return analytics;
    }

    public Map<String, Long> getStatusAnalytics() {
        Map<String, Long> analytics = new HashMap<>();
        for (Grievance.GrievanceStatus status : Grievance.GrievanceStatus.values()) {
            long count = grievanceRepository.findByStatus(status).size();
            analytics.put(status.name(), count);
        }
        return analytics;
    }

    @Transactional
    public void deleteGrievance(Long id) {
        grievanceRepository.deleteById(id);
    }
}
