package com.acadsched.service;

import com.acadsched.model.Event;
import com.acadsched.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public List<Event> getPublishedEvents() {
        return eventRepository.findByPublished(true);
    }

    public List<Event> getUpcomingEvents() {
        return eventRepository.findByPublishedAndEventDateAfterOrderByEventDateAsc(
                true, LocalDateTime.now());
    }

    public List<Event> getEventsByType(Event.EventType type) {
        return eventRepository.findByType(type);
    }

    public List<Event> getEventsByOrganizer(Long organizerId) {
        return eventRepository.findByOrganizerId(organizerId);
    }

    public Event getEventById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
    }

    @Transactional
    public Event updateEvent(Long id, Event updatedEvent) {
        Event event = getEventById(id);
        
        event.setTitle(updatedEvent.getTitle());
        event.setDescription(updatedEvent.getDescription());
        event.setEventDate(updatedEvent.getEventDate());
        event.setVenue(updatedEvent.getVenue());
        event.setType(updatedEvent.getType());
        event.setPublished(updatedEvent.getPublished());
        
        return eventRepository.save(event);
    }

    @Transactional
    public void publishEvent(Long id) {
        Event event = getEventById(id);
        event.setPublished(true);
        eventRepository.save(event);
    }

    @Transactional
    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
    }
}
