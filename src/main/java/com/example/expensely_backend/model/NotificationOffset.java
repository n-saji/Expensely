package com.example.expensely_backend.model;

public enum NotificationOffset {
    FIVE_MINUTES(5),
    THIRTY_MINUTES(30),
    ONE_HOUR(60),
    ONE_DAY(1440),
    THREE_DAYS(4320),
    SEVEN_DAYS(10080);

    private final int minutes;

    NotificationOffset(int minutes) {
        this.minutes = minutes;
    }

    public int getMinutes() {
        return minutes;
    }
}
