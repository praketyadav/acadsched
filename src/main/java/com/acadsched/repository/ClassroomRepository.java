package com.acadsched.repository;

import com.acadsched.model.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    Optional<Classroom> findByRoomNumber(String roomNumber);
    List<Classroom> findByBuilding(String building);
    List<Classroom> findByAvailable(Boolean available);
    List<Classroom> findByType(Classroom.RoomType type);
    List<Classroom> findByCapacityGreaterThanEqual(Integer capacity);
}
