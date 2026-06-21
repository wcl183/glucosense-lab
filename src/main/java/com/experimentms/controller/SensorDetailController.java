package com.experimentms.controller;

import com.experimentms.exception.ApiException;
import com.experimentms.security.AuthService;
import com.experimentms.service.Db;
import com.experimentms.util.Maps;
import com.experimentms.util.Payload;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sensorDetails")
public class SensorDetailController {
    private static final String[] FIELDS = new String[]{"sterilization_date", "test_number", "probe_number", "value_0", "value_2", "value_5", "value_25", "sensitivity", "r_value", "destination", "remarks", "created_time"};

    private final Db db;
    private final AuthService auth;

    public SensorDetailController(Db db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @GetMapping("/")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int skip,
                                          @RequestParam(defaultValue = "100") int limit,
                                          @RequestParam(required = false) String test_number,
                                          @RequestParam(required = false) String probe_number,
                                          HttpServletRequest request) {
        auth.require(request, "sensor_details", "read");
        StringBuilder sql = new StringBuilder("SELECT * FROM sensor_details WHERE 1=1");
        Map<String, Object> params = Maps.of("skip", skip, "limit", limit);
        if (test_number != null && !test_number.isEmpty()) {
            sql.append(" AND test_number LIKE :test_number");
            params.put("test_number", "%" + test_number + "%");
        }
        if (probe_number != null && !probe_number.isEmpty()) {
            sql.append(" AND probe_number LIKE :probe_number");
            params.put("probe_number", "%" + probe_number + "%");
        }
        sql.append(" ORDER BY sensor_detail_id LIMIT :limit OFFSET :skip");
        return db.query(sql.toString(), params);
    }

    @GetMapping("/used-sensor-details")
    public List<Integer> used(HttpServletRequest request) {
        auth.require(request, "sensor_details", "read");
        List<Map<String, Object>> rows = db.query("SELECT DISTINCT sensor_detail_id FROM sensors WHERE sensor_detail_id IS NOT NULL", null);
        List<Integer> result = new ArrayList<Integer>();
        for (Map<String, Object> row : rows) {
            result.add(((Number) row.get("sensor_detail_id")).intValue());
        }
        return result;
    }

    @PostMapping("/")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "sensor_details", "write");
        checkDuplicate(body.get("test_number"), body.get("probe_number"), null);
        Map<String, Object> data = Payload.withDates(Payload.pick(body, FIELDS), "sterilization_date");
        data.put("created_time", Timestamp.valueOf(LocalDateTime.now()));
        Number id = db.insert("sensor_details", data, FIELDS);
        return getByIdInternal(id);
    }

    @PostMapping("/batch")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchCreate(@RequestBody List<Map<String, Object>> body, HttpServletRequest request) {
        auth.require(request, "sensor_details", "write");
        if (body == null) {
            body = new ArrayList<Map<String, Object>>();
        }
        List<Map<String, Object>> created = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : body) {
            checkDuplicate(item.get("test_number"), item.get("probe_number"), null);
        }
        for (Map<String, Object> item : body) {
            Map<String, Object> data = Payload.withDates(Payload.pick(item, FIELDS), "sterilization_date");
            data.put("created_time", Timestamp.valueOf(LocalDateTime.now()));
            Number id = db.insert("sensor_details", data, FIELDS);
            created.add(getByIdInternal(id));
        }
        return Maps.of("message", "成功创建 " + created.size() + " 条传感器详细信息", "created_count", created.size(), "data", created);
    }

    @PostMapping("/check-duplicates")
    public Map<String, Object> duplicates(@RequestParam(required = false) String test_number,
                                          @RequestParam(required = false) String probe_number,
                                          @RequestParam(required = false) Integer exclude_id,
                                          HttpServletRequest request) {
        auth.require(request, "sensor_details", "read");
        List<Map<String, Object>> duplicates = new ArrayList<Map<String, Object>>();
        if (test_number != null && db.count("SELECT COUNT(*) FROM sensor_details WHERE test_number = :value AND (:exclude_id IS NULL OR sensor_detail_id <> :exclude_id)", Maps.of("value", test_number, "exclude_id", exclude_id)) > 0) {
            Map<String, Object> existing = db.row("SELECT sensor_detail_id FROM sensor_details WHERE test_number = :value AND (:exclude_id IS NULL OR sensor_detail_id <> :exclude_id) LIMIT 1", Maps.of("value", test_number, "exclude_id", exclude_id));
            duplicates.add(Maps.of("field", "test_number", "value", test_number, "existing_id", existing.get("sensor_detail_id")));
        }
        if (probe_number != null && db.count("SELECT COUNT(*) FROM sensor_details WHERE probe_number = :value AND (:exclude_id IS NULL OR sensor_detail_id <> :exclude_id)", Maps.of("value", probe_number, "exclude_id", exclude_id)) > 0) {
            Map<String, Object> existing = db.row("SELECT sensor_detail_id FROM sensor_details WHERE probe_number = :value AND (:exclude_id IS NULL OR sensor_detail_id <> :exclude_id) LIMIT 1", Maps.of("value", probe_number, "exclude_id", exclude_id));
            duplicates.add(Maps.of("field", "probe_number", "value", probe_number, "existing_id", existing.get("sensor_detail_id")));
        }
        return Maps.of("has_duplicates", !duplicates.isEmpty(), "duplicates", duplicates);
    }

    @PostMapping("/batch-delete")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "sensor_details", "delete");
        List<Object> ids = (List<Object>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(400, "请提供要删除的数据ID列表");
        }
        if (db.count("SELECT COUNT(*) FROM sensors WHERE sensor_detail_id IN (:ids)", Maps.of("ids", ids)) > 0) {
            throw new ApiException(400, "传感器详细信息正在被传感器管理模块使用，无法删除");
        }
        int deleted = db.update("DELETE FROM sensor_details WHERE sensor_detail_id IN (:ids)", Maps.of("ids", ids));
        return Maps.of("deleted_count", deleted, "message", "成功删除" + deleted + "条传感器详细信息记录");
    }

    @GetMapping("/{sensorDetailId}")
    public Map<String, Object> get(@PathVariable("sensorDetailId") int sensorDetailId, HttpServletRequest request) {
        auth.require(request, "sensor_details", "read");
        return getByIdInternal(sensorDetailId);
    }

    @PutMapping("/{sensorDetailId}")
    public Map<String, Object> update(@PathVariable("sensorDetailId") int sensorDetailId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "sensor_details", "write");
        db.requireRow("SELECT sensor_detail_id FROM sensor_details WHERE sensor_detail_id = :id", Maps.of("id", sensorDetailId), "传感器详细信息不存在");
        checkDuplicate(body.get("test_number"), body.get("probe_number"), sensorDetailId);
        Map<String, Object> data = Payload.withDates(Payload.pick(body, FIELDS), "sterilization_date");
        db.updateById("sensor_details", "sensor_detail_id", sensorDetailId, data, FIELDS);
        return getByIdInternal(sensorDetailId);
    }

    @DeleteMapping("/{sensorDetailId}")
    public Map<String, Object> delete(@PathVariable("sensorDetailId") int sensorDetailId, HttpServletRequest request) {
        auth.require(request, "sensor_details", "delete");
        db.requireRow("SELECT sensor_detail_id FROM sensor_details WHERE sensor_detail_id = :id", Maps.of("id", sensorDetailId), "传感器详细信息不存在");
        if (db.count("SELECT COUNT(*) FROM sensors WHERE sensor_detail_id = :id", Maps.of("id", sensorDetailId)) > 0) {
            throw new ApiException(400, "该传感器详细信息正在被传感器管理模块使用，无法删除");
        }
        db.update("DELETE FROM sensor_details WHERE sensor_detail_id = :id", Maps.of("id", sensorDetailId));
        return Maps.of("message", "传感器详细信息记录删除成功");
    }

    private Map<String, Object> getByIdInternal(Object id) {
        return db.requireRow("SELECT * FROM sensor_details WHERE sensor_detail_id = :id", Maps.of("id", id), "传感器详细信息不存在");
    }

    private void checkDuplicate(Object testNumber, Object probeNumber, Integer excludeId) {
        Map<String, Object> params = Maps.of("exclude_id", excludeId);
        if (testNumber != null) {
            params.put("value", testNumber);
            if (db.count("SELECT COUNT(*) FROM sensor_details WHERE test_number = :value AND (:exclude_id IS NULL OR sensor_detail_id <> :exclude_id)", params) > 0) {
                throw new ApiException(400, "测试编号已存在");
            }
        }
        if (probeNumber != null) {
            params.put("value", probeNumber);
            if (db.count("SELECT COUNT(*) FROM sensor_details WHERE probe_number = :value AND (:exclude_id IS NULL OR sensor_detail_id <> :exclude_id)", params) > 0) {
                throw new ApiException(400, "探针编号已存在");
            }
        }
    }
}
