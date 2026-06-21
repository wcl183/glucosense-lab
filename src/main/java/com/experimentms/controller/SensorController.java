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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sensors")
public class SensorController {
    private static final String[] FIELDS = new String[]{"person_id", "batch_id", "sensor_detail_id", "sensor_lot_no", "sensor_batch", "sensor_number", "transmitter_id", "start_time", "end_time", "end_reason"};
    private final Db db;
    private final AuthService auth;

    public SensorController(Db db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @GetMapping("/")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int skip,
                                          @RequestParam(defaultValue = "100") int limit,
                                          @RequestParam(required = false) Integer batch_id,
                                          @RequestParam(required = false) Integer person_id,
                                          HttpServletRequest request) {
        auth.require(request, "sensor_data", "read");
        StringBuilder sql = new StringBuilder("SELECT s.*, p.person_name, b.batch_number FROM sensors s JOIN persons p ON s.person_id = p.person_id JOIN batches b ON s.batch_id = b.batch_id WHERE 1=1");
        Map<String, Object> params = Maps.of("skip", skip, "limit", limit);
        if (batch_id != null) {
            sql.append(" AND s.batch_id = :batch_id");
            params.put("batch_id", batch_id);
        }
        if (person_id != null) {
            sql.append(" AND s.person_id = :person_id");
            params.put("person_id", person_id);
        }
        sql.append(" ORDER BY s.sensor_id LIMIT :limit OFFSET :skip");
        return db.query(sql.toString(), params);
    }

    @GetMapping("/used-sensors")
    public List<Integer> used(HttpServletRequest request) {
        auth.require(request, "sensor_data", "read");
        List<Map<String, Object>> rows = db.query("SELECT DISTINCT sensor_id FROM wear_records WHERE sensor_id IS NOT NULL", null);
        List<Integer> result = new ArrayList<Integer>();
        for (Map<String, Object> row : rows) {
            result.add(((Number) row.get("sensor_id")).intValue());
        }
        return result;
    }

    @PostMapping("/")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "sensor_data", "write");
        validateBatchAndPerson(body.get("batch_id"), body.get("person_id"));
        Map<String, Object> data = Payload.withDateTimes(Payload.pick(body, FIELDS), "start_time", "end_time");
        Number id = db.insert("sensors", data, FIELDS);
        return getByIdInternal(id);
    }

    @GetMapping("/{sensorId}")
    public Map<String, Object> get(@PathVariable("sensorId") int sensorId, HttpServletRequest request) {
        auth.require(request, "sensor_data", "read");
        return getByIdInternal(sensorId);
    }

    @PutMapping("/{sensorId}")
    public Map<String, Object> update(@PathVariable("sensorId") int sensorId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "sensor_data", "write");
        db.requireRow("SELECT sensor_id FROM sensors WHERE sensor_id = :id", Maps.of("id", sensorId), "传感器不存在");
        validateBatchAndPerson(body.get("batch_id"), body.get("person_id"));
        Map<String, Object> data = Payload.withDateTimes(Payload.pick(body, FIELDS), "start_time", "end_time");
        db.updateById("sensors", "sensor_id", sensorId, data, FIELDS);
        return getByIdInternal(sensorId);
    }

    @DeleteMapping("/{sensorId}")
    public Map<String, Object> delete(@PathVariable("sensorId") int sensorId, HttpServletRequest request) {
        auth.require(request, "sensor_data", "delete");
        db.requireRow("SELECT sensor_id FROM sensors WHERE sensor_id = :id", Maps.of("id", sensorId), "传感器不存在");
        if (db.count("SELECT COUNT(*) FROM wear_records WHERE sensor_id = :id", Maps.of("id", sensorId)) > 0) {
            throw new ApiException(400, "该传感器已有佩戴记录，无法删除。请先删除相关的佩戴记录后再尝试删除传感器。");
        }
        db.update("DELETE FROM sensors WHERE sensor_id = :id", Maps.of("id", sensorId));
        return Maps.of("message", "传感器记录删除成功");
    }

    private Map<String, Object> getByIdInternal(Object id) {
        return db.requireRow(
                "SELECT s.*, p.person_name, b.batch_number FROM sensors s LEFT JOIN persons p ON s.person_id = p.person_id LEFT JOIN batches b ON s.batch_id = b.batch_id WHERE s.sensor_id = :id",
                Maps.of("id", id),
                "传感器不存在"
        );
    }

    private void validateBatchAndPerson(Object batchId, Object personId) {
        db.requireRow("SELECT batch_id FROM batches WHERE batch_id = :id", Maps.of("id", batchId), "指定的批次不存在");
        db.requireRow("SELECT person_id FROM persons WHERE person_id = :id", Maps.of("id", personId), "指定的人员不存在");
    }
}
