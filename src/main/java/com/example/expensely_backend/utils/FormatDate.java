package com.example.expensely_backend.utils;

import java.time.LocalDateTime;
import java.util.Objects;

public class FormatDate {
    public static LocalDateTime formatStartDate(LocalDateTime date, boolean keepThisYear) {
        if (date == null) {
            int year = 1970;
            if (keepThisYear) year = LocalDateTime.now().getYear();

            return LocalDateTime.of(year, 1, 1, 0, 0);
        } else {
            return date.withHour(0).withMinute(0).withSecond(0);
        }
    }

    public static LocalDateTime formatEndDate(LocalDateTime date) {
        return Objects.requireNonNullElseGet(date, LocalDateTime::now).withHour(23).withMinute(59).withSecond(59);
    }
}
