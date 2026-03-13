package com.acadsched.repository;

import com.acadsched.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByPublished(Boolean published);
    List<Event> findByType(Event.EventType type);
    List<Event> findByOrganizerId(Long organizerId);
    List<Event> findByEventDateAfter(LocalDateTime date);
    List<Event> findByEventDateBefore(LocalDateTime date);
    List<Event> findByPublishedAndEventDateAfterOrderByEventDateAsc(Boolean published, LocalDateTime date);
}
