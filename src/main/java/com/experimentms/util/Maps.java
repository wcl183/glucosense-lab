package com.experimentms.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Maps {
    private Maps() {
    }

    public static Map<String, Object> of(Object... values) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
