package com.acadsched.controller;

import com.acadsched.model.Event;
import com.acadsched.model.User;
import com.acadsched.service.EventService;
import com.acadsched.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Event Controller - Manages campus events
 * Role Permissions:
 * - ADMIN: Create, Edit, Delete, Publish events
 * - FACULTY: View events only
 * - STUDENT: View events only
 */
@Controller
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserService userService;

    /**
     * List upcoming published events (accessible to all authenticated users)
     */
    @GetMapping
    public String listEvents(Model model) {
        List<Event> events = eventService.getUpcomingEvents();
        model.addAttribute("events", events);
        return "events/list";
    }

    /**
     * List all events (Admin sees all, others see only published)
     */
    @GetMapping("/all")
    public String listAllEvents(Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        List<Event> events;
        if (user.getRole() == User.Role.ADMIN) {
            events = eventService.getAllEvents();
        } else {
            events = eventService.getPublishedEvents();
        }
        
        model.addAttribute("events", events);
        return "events/list";
    }

    /**
     * Show create event form - ADMIN ONLY
     */
    @GetMapping("/new")
    public String showCreateForm(Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - Create events
        if (user.getRole() != User.Role.ADMIN) {
            model.addAttribute("error", "Only administrators can create events");
            return "redirect:/events?error=unauthorized";
        }
        
        model.addAttribute("event", new Event());
        return "events/create";
    }

    /**
     * Create new event - ADMIN ONLY
     */
    @PostMapping("/new")
    public String createEvent(@Valid @ModelAttribute Event event,
                            BindingResult result,
                            Authentication authentication,
                            Model model) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - Create events
        if (user.getRole() != User.Role.ADMIN) {
            return "redirect:/events?error=unauthorized";
        }
        
        if (result.hasErrors()) {
            return "events/create";
        }

        event.setOrganizer(user);
        eventService.createEvent(event);
        return "redirect:/events/all?success=created";
    }

    /**
     * View event details (accessible to all authenticated users)
     */
    @GetMapping("/{id}")
    public String viewEvent(@PathVariable Long id, Model model) {
        Event event = eventService.getEventById(id);
        model.addAttribute("event", event);
        return "events/view";
    }

    /**
     * Show edit form - ADMIN ONLY
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - Edit events
        if (user.getRole() != User.Role.ADMIN) {
            return "redirect:/events?error=unauthorized";
        }
        
        Event event = eventService.getEventById(id);
        model.addAttribute("event", event);
        return "events/edit";
    }

    /**
     * Update event - ADMIN ONLY
     */
    @PostMapping("/{id}/edit")
    public String updateEvent(@PathVariable Long id,
                            @Valid @ModelAttribute Event event,
                            BindingResult result,
                            Authentication authentication,
                            Model model) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - Edit events
        if (user.getRole() != User.Role.ADMIN) {
            return "redirect:/events?error=unauthorized";
        }
        
        if (result.hasErrors()) {
            return "events/edit";
        }

        eventService.updateEvent(id, event);
        return "redirect:/events/" + id + "?success=updated";
    }

    /**
     * Publish event - ADMIN ONLY
     */
    @PostMapping("/{id}/publish")
    public String publishEvent(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - Publish events
        if (user.getRole() != User.Role.ADMIN) {
            return "redirect:/events?error=unauthorized";
        }
        
        eventService.publishEvent(id);
        return "redirect:/events/" + id + "?success=published";
    }

    /**
     * Delete event - ADMIN ONLY
     */
    @PostMapping("/{id}/delete")
    public String deleteEvent(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByUsername(authentication.getName()).orElseThrow();
        
        // ADMIN ONLY - Delete events
        if (user.getRole() != User.Role.ADMIN) {
            return "redirect:/events?error=unauthorized";
        }
        
        eventService.deleteEvent(id);
        return "redirect:/events/all?success=deleted";
    }
}
