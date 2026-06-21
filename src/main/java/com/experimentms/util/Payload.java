package com.experimentms.util;

import com.experimentms.exception.ApiException;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Payload {
    private Payload() {
    }

    public static Map<String, Object> pick(Map<String, Object> body, String... fields) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (body == null) {
            return result;
        }
        for (String field : fields) {
            if (body.containsKey(field)) {
                result.put(field, body.get(field));
            }
        }
        return result;
    }

    public static Map<String, Object> withDateTimes(Map<String, Object> body, String... fields) {
        if (body == null) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>(body);
        for (String field : fields) {
            if (result.containsKey(field)) {
                Timestamp value = TimeUtil.timestamp(result.get(field));
                result.put(field, value);
            }
        }
        return result;
    }

    public static Map<String, Object> withDates(Map<String, Object> body, String... fields) {
        if (body == null) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>(body);
        for (String field : fields) {
            if (result.containsKey(field)) {
                Date value = TimeUtil.sqlDate(result.get(field));
                result.put(field, value);
            }
        }
        return result;
    }

    public static Integer intValue(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        if (value == null || "".equals(String.valueOf(value).trim())) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    public static int requiredInt(Map<String, Object> body, String field) {
        Integer value = intValue(body, field);
        if (value == null) {
            throw new ApiException(400, "缺少字段: " + field);
        }
        return value;
    }

    public static String stringValue(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        return value == null ? null : String.valueOf(value);
    }
}
