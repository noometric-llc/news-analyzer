package org.newsanalyzer.model;

/**
 * Status of a per-article processing step (extraction or bias detection).
 * Tracked separately per step so partial ingestion failures remain diagnosable.
 */
public enum ArticleStatus {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    ArticleStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ArticleStatus fromValue(String value) {
        for (ArticleStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown article status: " + value);
    }
}
