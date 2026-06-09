package backend.dto;

/**
 * Frontend-friendly table occupancy state.
 */
public enum TableStatus {
    EMPTY,
    PARTIAL,
    NEAR_FULL,
    FULL,
    RELEASING_SOON
}
