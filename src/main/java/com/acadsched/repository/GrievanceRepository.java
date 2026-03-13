package com.acadsched.repository;

import com.acadsched.model.Grievance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface GrievanceRepository extends JpaRepository<Grievance, Long> {
    List<Grievance> findByStudentId(Long studentId);
    List<Grievance> findByStatus(Grievance.GrievanceStatus status);
    List<Grievance> findByCategory(Grievance.GrievanceCategory category);
    List<Grievance> findByOrderByCreatedAtDesc();
    
    @Query("SELECT g.category as category, COUNT(g) as count FROM Grievance g GROUP BY g.category")
    List<Map<String, Object>> countByCategory();
    
    @Query("SELECT g.status as status, COUNT(g) as count FROM Grievance g GROUP BY g.status")
    List<Map<String, Object>> countByStatus();
}
