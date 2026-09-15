package com.liquorbee.updater;

public enum OpeningResult {
    CHECKING(R.string.result_checking),
    CHECK_FAILED(R.string.result_check_failed),
    POS_MISSING(R.string.result_pos_missing),
    CURRENT(R.string.result_current),
    PAUSED(R.string.result_paused),
    OPENING_DISABLED(R.string.result_opening_disabled),
    PERMISSION_REQUIRED(R.string.result_permission_required),
    SCREEN_ASLEEP(R.string.result_screen_asleep),
    SCREEN_LOCKED(R.string.result_screen_locked),
    DISMISSED_TODAY(R.string.result_dismissed_today),
    ATTEMPT_WAIT(R.string.result_attempt_wait),
    OPEN_REQUESTED(R.string.result_open_requested),
    OPENED(R.string.result_opened),
    OPEN_REJECTED(R.string.result_open_rejected),
    SERVICE_REJECTED(R.string.result_service_rejected),
    TIMED_OUT(R.string.result_timed_out);

    public final int message;
    OpeningResult(int message) { this.message = message; }
}
