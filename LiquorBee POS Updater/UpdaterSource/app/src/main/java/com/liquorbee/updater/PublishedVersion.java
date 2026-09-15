package com.liquorbee.updater;

public final class PublishedVersion {
    public final long code;
    public final String label;

    public PublishedVersion(long code, String label) {
        if (code <= 0) throw new IllegalArgumentException("Build number must be positive");
        this.code = code;
        this.label = label;
    }
}
