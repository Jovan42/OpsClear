package com.opsclear.model;

public enum JobPriority {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** Higher number = higher urgency. Used for default sort ordering. */
    public int weight() {
        return switch (this) {
            case CRITICAL -> 3;
            case HIGH     -> 2;
            case MEDIUM   -> 1;
            case LOW      -> 0;
        };
    }
}
