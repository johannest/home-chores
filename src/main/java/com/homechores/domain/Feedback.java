package com.homechores.domain;

/** A member's reaction to doing a chore. */
public enum Feedback {
    HATE("😖", "Hate it"),
    OK("🙂", "OK"),
    LOVE("😍", "More of these");

    private final String emoji;
    private final String label;

    Feedback(String emoji, String label) {
        this.emoji = emoji;
        this.label = label;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getLabel() {
        return label;
    }
}
