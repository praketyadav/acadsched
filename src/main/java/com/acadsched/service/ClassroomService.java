package com.acadsched.service;

import com.acadsched.model.Classroom;
import com.acadsched.repository.ClassroomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomRepository classroomRepository;

    @Transactional
    public Classroom createClassroom(Classroom classroom) {
        return classroomRepository.save(classroom);
    }

    public List<Classroom> getAllClassrooms() {
        return classroomRepository.findAll();
    }

    public Optional<Classroom> getClassroomById(Long id) {
        return classroomRepository.findById(id);
    }

    public List<Classroom> getAvailableClassrooms() {
        return classroomRepository.findByAvailable(true);
    }

    public List<Classroom> getClassroomsByBuilding(String building) {
        return classroomRepository.findByBuilding(building);
    }

    @Transactional
    public Classroom updateClassroom(Long id, Classroom updatedClassroom) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found"));
        
        classroom.setRoomNumber(updatedClassroom.getRoomNumber());
        classroom.setBuilding(updatedClassroom.getBuilding());
        classroom.setCapacity(updatedClassroom.getCapacity());
        classroom.setType(updatedClassroom.getType());
        classroom.setAvailable(updatedClassroom.getAvailable());
        classroom.setEquipment(updatedClassroom.getEquipment());
        
        return classroomRepository.save(classroom);
    }

    @Transactional
    public void deleteClassroom(Long id) {
        classroomRepository.deleteById(id);
    }

    @Transactional
    public void toggleAvailability(Long id) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found"));
        classroom.setAvailable(!classroom.getAvailable());
        classroomRepository.save(classroom);
    }
}
