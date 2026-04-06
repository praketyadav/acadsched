package com.acadsched.dto;

import com.acadsched.model.ClassGroup;
import com.acadsched.model.Subject;
import com.acadsched.model.Subject.SubjectType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transient wrapper representing a single unassigned session — the output of
 * the Subject Allocation phase and the input to the Scheduling (CSP) phase.
 *
 * Faculty Pool Model:
 * - {@code eligibleFacultyIds}: list of faculty IDs eligible to teach this session.
 *   The scheduler dynamically selects one based on availability + load balancing.
 * - {@code assignedFacultyId}: filled by the scheduler after dynamic selection.
 *
 * Lab-block linking:
 * - {@code linkedGroupId}: non-null for PRACTICAL/consecutive sessions that
 *   MUST be placed in adjacent time-slots on the same day.
 * - {@code blockSize}: total number of consecutive 1-hour slots in this block.
 * - {@code blockIndex}: 0-based position within a linked block.
 * - {@code requiresLabRoom}: true when the session needs a LABORATORY room.
 */
@Getter
@Setter
@Builder
public class SessionDTO {

    /** Unique identifier for this session (transient, not persisted). */
    @Builder.Default
    private final String sessionId = UUID.randomUUID().toString();

    // ── Subject metadata ────────────────────────────────────────────────────
    private Subject subject;
    private ClassGroup classGroup;
    private String semester;
    private SubjectType sessionType;

    // ── Faculty Pool (replaces single Faculty reference) ────────────────────
    /** IDs of all faculty members eligible to teach this session. */
    @Builder.Default
    private List<Long> eligibleFacultyIds = new ArrayList<>();

    /** Faculty ID dynamically assigned by the scheduler (null until placed). */
    private Long assignedFacultyId;

    // ── Lab-block linking ───────────────────────────────────────────────────
    private String linkedGroupId;

    @Builder.Default
    private int blockSize = 1;

    @Builder.Default
    private int blockIndex = 0;

    // ── Room requirement hint ───────────────────────────────────────────────
    @Builder.Default
    private boolean requiresLabRoom = false;

    // ── Scheduling output (filled later by the CSP engine) ──────────────────
    private String dayOfWeek;
    private String timeSlot;

    // ── Priority (inherited from Subject) ───────────────────────────────────
    @Builder.Default
    private int priority = 1;

    /** True if this session is part of a consecutive lab/practical block. */
    public boolean isLinked() {
        return linkedGroupId != null;
    }

    /** True if the scheduler has already placed this session. */
    public boolean isAssigned() {
        return dayOfWeek != null && timeSlot != null;
    }

    @Override
    public String toString() {
        return String.format("Session[%s | %s | block=%d/%d | linked=%s | faculty=%s | slot=%s %s]",
                subject != null ? subject.getSubjectCode() : "?",
                sessionType,
                blockIndex + 1, blockSize,
                linkedGroupId != null ? linkedGroupId.substring(0, 8) : "none",
                assignedFacultyId != null ? assignedFacultyId : "pool:" + eligibleFacultyIds.size(),
                dayOfWeek != null ? dayOfWeek : "unset",
                timeSlot != null ? timeSlot : "");
    }
}
