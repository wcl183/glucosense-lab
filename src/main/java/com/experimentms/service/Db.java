package com.experimentms.service;

import com.experimentms.exception.ApiException;
import com.experimentms.util.TimeUtil;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class Db {
    private final NamedParameterJdbcTemplate jdbc;

    public Db(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> query(String sql, Map<String, ?> params) {
        return normalize(jdbc.queryForList(sql, params == null ? new LinkedHashMap<String, Object>() : params));
    }

    public Map<String, Object> row(String sql, Map<String, ?> params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> requireRow(String sql, Map<String, ?> params, String message) {
        Map<String, Object> row = row(sql, params);
        if (row == null) {
            throw new ApiException(404, message);
        }
        return row;
    }

    public int update(String sql, Map<String, ?> params) {
        return jdbc.update(sql, params == null ? new LinkedHashMap<String, Object>() : params);
    }

    public Number insert(String table, Map<String, Object> data, String... allowedFields) {
        List<String> allowed = Arrays.asList(allowedFields);
        List<String> columns = new ArrayList<String>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (String field : allowed) {
            if (data.containsKey(field)) {
                columns.add(field);
                params.addValue(field, data.get(field));
            }
        }
        if (columns.isEmpty()) {
            throw new ApiException(400, "缺少可写字段");
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        StringBuilder values = new StringBuilder(" VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append(columns.get(i));
            values.append(":").append(columns.get(i));
        }
        sql.append(")").append(values).append(")");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql.toString(), params, keyHolder);
        return keyHolder.getKey();
    }

    public int updateById(String table, String idColumn, Object id, Map<String, Object> data, String... allowedFields) {
        List<String> sets = new ArrayList<String>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (String field : allowedFields) {
            if (data.containsKey(field)) {
                sets.add(field + " = :" + field);
                params.addValue(field, data.get(field));
            }
        }
        if (sets.isEmpty()) {
            return 0;
        }
        params.addValue("id", id);
        return jdbc.update("UPDATE " + table + " SET " + join(sets) + " WHERE " + idColumn + " = :id", params);
    }

    public int count(String sql, Map<String, ?> params) {
        Integer value = jdbc.queryForObject(sql, params == null ? new LinkedHashMap<String, Object>() : params, Integer.class);
        return value == null ? 0 : value;
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object value = TimeUtil.normalize(entry.getValue());
                if (value instanceof BigDecimal) {
                    value = ((BigDecimal) value).doubleValue();
                }
                item.put(entry.getKey(), value);
            }
            result.add(item);
        }
        return result;
    }
}
