package com.example.expensely_backend.utils;

import java.time.LocalDateTime;

public class FormatDate {
    public static LocalDateTime formatStartDate(LocalDateTime date){
        if (date == null) {
            return LocalDateTime.of(1970,1,1,0,0);
        }else{
            return date.withHour(0).withMinute(0).withSecond(0);
        }
    }
    public static LocalDateTime formatEndDate(LocalDateTime date){
        if (date == null) {
            return LocalDateTime.now();
        }else{
            return date.withHour(23).withMinute(59).withSecond(59);
        }
    }
}
