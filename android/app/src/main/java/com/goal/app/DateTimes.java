package com.goal.app;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public final class DateTimes {
    private DateTimes() {}

    public static String nowIso() {
        return OffsetDateTime.now().toString();
    }

    public static long parseMillis(String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    public static String clock(int seconds) {
        int safe = Math.max(0, seconds);
        int minutes = safe / 60;
        int secs = safe % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, secs);
    }

    public static String dateOnly(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate().toString();
        } catch (Exception ignored) {
            return iso;
        }
    }

    public static String dueText(String dueDate) {
        if (dueDate == null || dueDate.isEmpty()) return "没有截止日期";
        return dueDate;
    }
}
