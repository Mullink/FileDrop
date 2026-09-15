package com.liquorbee.updater;

public final class ReleaseCheck {
    public final PublishedVersion published;
    public final String error;
    public final long checkedAt;

    public ReleaseCheck(PublishedVersion published, String error, long checkedAt) {
        this.published = published;
        this.error = error;
        this.checkedAt = checkedAt;
    }

    public boolean failed() { return published == null; }
}
