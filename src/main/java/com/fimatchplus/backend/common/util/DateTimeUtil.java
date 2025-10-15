package com.fimatchplus.backend.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtil {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private DateTimeUtil() {
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(KOREA_ZONE);
    }

    public static boolean isMarketOpen() {
        LocalDateTime now = now();
        int hour = now.getHour();
        int minute = now.getMinute();

        if (hour < 9 || hour > 15) {
            return false;
        }
        if (hour == 9 && minute < 0) {
            return false;
        }
        if (hour == 15 && minute > 30) {
            return false;
        }

        return true;
    }

    public static String getNextCloseTime() {
        LocalDateTime now = now();
        LocalDateTime nextClose = now.withHour(15).withMinute(30).withSecond(0).withNano(0);

        if (now.isAfter(nextClose)) {
            nextClose = nextClose.plusDays(1);
        }

        return formatDateTime(nextClose);
    }
}


