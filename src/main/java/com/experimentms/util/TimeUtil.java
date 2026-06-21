package com.experimentms.util;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TimeUtil() {
    }

    public static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof Date) {
            return ((Date) value).toLocalDate().format(DATE);
        }
        if (value instanceof java.util.Date) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZONE);
            return dateTime.format(DATE_TIME);
        }
        return value;
    }

    public static LocalDate parseDate(Object value) {
        if (value == null || "".equals(String.valueOf(value).trim())) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZONE).toLocalDate();
        }
        String text = String.valueOf(value).trim();
        if (text.length() >= 10) {
            return LocalDate.parse(text.substring(0, 10), DATE);
        }
        return LocalDate.parse(text, DATE);
    }

    public static LocalDateTime parseDateTime(Object value) {
        if (value == null || "".equals(String.valueOf(value).trim())) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof Date) {
            return LocalDateTime.of(((Date) value).toLocalDate(), LocalTime.MIDNIGHT);
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZONE);
        }
        String text = String.valueOf(value).trim().replace('T', ' ');
        if (text.length() == 10) {
            return LocalDateTime.of(LocalDate.parse(text, DATE), LocalTime.MIDNIGHT);
        }
        if (text.length() == 16) {
            text = text + ":00";
        }
        if (text.length() > 19) {
            text = text.substring(0, 19);
        }
        return LocalDateTime.parse(text, DATE_TIME);
    }

    public static Timestamp timestamp(Object value) {
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    public static Date sqlDate(Object value) {
        LocalDate date = parseDate(value);
        return date == null ? null : Date.valueOf(date);
    }
}
