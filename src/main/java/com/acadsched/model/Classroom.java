package com.acadsched.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "classrooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Room number is required")
    @Column(unique = true, nullable = false)
    private String roomNumber;

    @NotBlank(message = "Building is required")
    private String building;

    @Min(value = 1, message = "Capacity must be at least 1")
    @Column(nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    private RoomType type;

    @Column(nullable = false)
    private Boolean available = true;

    private String equipment; // Projector, Smart Board, etc.

    public enum RoomType {
        LECTURE_HALL,
        LABORATORY,
        SEMINAR_ROOM,
        TUTORIAL_ROOM,
        AUDITORIUM
    }
}
