package com.acadsched.repository;

import com.acadsched.model.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Timetable entity
 * 
 * Key Queries:
 * - Faculty view: findByFacultyId (SELECT * FROM timetable WHERE faculty_id = ?)
 * - Student view: findByClassGroupId (SELECT * FROM timetable WHERE class_group_id = ?)
 * - Conflict detection: findByDayAndTimeSlot
 */
@Repository
public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    
    // Query by semester
    List<Timetable> findBySemester(String semester);
    
    // Query by class group (for student view)
    List<Timetable> findByClassGroupId(Long classGroupId);
    
    // Query by semester and class group
    List<Timetable> findBySemesterAndClassGroupId(String semester, Long classGroupId);
    
    // Query by faculty (for faculty view)
    // SELECT * FROM timetable WHERE faculty_id = ?
    List<Timetable> findByFacultyId(Long facultyId);
    
    // Query by classroom
    List<Timetable> findByClassroomId(Long classroomId);
    
    // Query by day
    List<Timetable> findByDayOfWeek(String dayOfWeek);
    
    // Find by day and time slot (for conflict detection)
    @Query("SELECT t FROM Timetable t WHERE t.dayOfWeek = :day AND t.timeSlot = :slot")
    List<Timetable> findByDayAndTimeSlot(@Param("day") String day, @Param("slot") String slot);
    
    // Check faculty conflicts
    @Query("SELECT t FROM Timetable t WHERE t.faculty.id = :facultyId AND t.dayOfWeek = :day AND t.timeSlot = :slot")
    List<Timetable> findByFacultyAndDayAndTimeSlot(@Param("facultyId") Long facultyId, 
                                                     @Param("day") String day, 
                                                     @Param("slot") String slot);
    
    // Check classroom conflicts
    @Query("SELECT t FROM Timetable t WHERE t.classroom.id = :classroomId AND t.dayOfWeek = :day AND t.timeSlot = :slot")
    List<Timetable> findByClassroomAndDayAndTimeSlot(@Param("classroomId") Long classroomId, 
                                                       @Param("day") String day, 
                                                       @Param("slot") String slot);
    
    // Check class group conflicts
    @Query("SELECT t FROM Timetable t WHERE t.classGroup.id = :classGroupId AND t.dayOfWeek = :day AND t.timeSlot = :slot")
    List<Timetable> findByClassGroupAndDayAndTimeSlot(@Param("classGroupId") Long classGroupId,
                                                        @Param("day") String day,
                                                        @Param("slot") String slot);
}
