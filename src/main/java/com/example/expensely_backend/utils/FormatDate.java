package com.example.expensely_backend.utils;

import java.time.LocalDateTime;

public class FormatDate {
    public static LocalDateTime formatStartDate(LocalDateTime date, boolean keepThisYear){
        if (date == null) {
            int year = 1970;
            if( keepThisYear ) year = LocalDateTime.now().getYear();

            return LocalDateTime.of(year,1,1,0,0);
        }else{
            return date.withHour(0).withMinute(0).withSecond(0);
        }
    }
    public static LocalDateTime formatEndDate(LocalDateTime date){
        if (date == null) {
            return LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        }else{
            return date.withHour(23).withMinute(59).withSecond(59);
        }
    }
}
