package com.experimentms.controller;

import com.experimentms.exception.ApiException;
import com.experimentms.security.AuthService;
import com.experimentms.service.Db;
import com.experimentms.util.Maps;
import com.experimentms.util.Payload;
import com.experimentms.util.TimeUtil;
import org.springframework.transaction.annotation.Transactional;
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
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wearRecords")
public class WearRecordController {
    private static final String[] FIELDS = new String[]{
            "batch_id", "person_id", "sensor_id", "sensor_detail_id",
            "applicator_lot_no", "sensor_lot_no", "sensor_batch", "sensor_number", "transmitter_id",
            "wear_time", "wear_end_time", "wear_position", "user_name", "nickname",
            "abnormal_situation", "cause_analysis"
    };

    private final Db db;
    private final AuthService auth;

    public WearRecordController(Db db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @GetMapping("/used-sensors")
    public List<Integer> usedSensors(HttpServletRequest request) {
        auth.require(request, "wear_records", "read");
        List<Map<String, Object>> rows = db.query(
                "SELECT DISTINCT s.sensor_detail_id FROM wear_records wr JOIN sensors s ON wr.sensor_id = s.sensor_id WHERE wr.wear_end_time IS NULL AND s.sensor_detail_id IS NOT NULL",
                null
        );
        List<Integer> result = new ArrayList<Integer>();
        for (Map<String, Object> row : rows) {
            result.add(((Number) row.get("sensor_detail_id")).intValue());
        }
        return result;
    }

    @GetMapping("/")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int skip,
                                          @RequestParam(defaultValue = "1000") int limit,
                                          @RequestParam(required = false) Integer batch_id,
                                          @RequestParam(required = false) Integer person_id,
                                          @RequestParam(required = false) Integer sensor_id,
                                          HttpServletRequest request) {
        auth.require(request, "wear_records", "read");
        StringBuilder sql = baseSql();
        Map<String, Object> params = Maps.of("skip", skip, "limit", limit);
        if (batch_id != null) {
            sql.append(" AND wr.batch_id = :batch_id");
            params.put("batch_id", batch_id);
        }
        if (person_id != null) {
            sql.append(" AND wr.person_id = :person_id");
            params.put("person_id", person_id);
        }
        if (sensor_id != null) {
            sql.append(" AND wr.sensor_id = :sensor_id");
            params.put("sensor_id", sensor_id);
        }
        sql.append(" ORDER BY wr.wear_time DESC LIMIT :limit OFFSET :skip");
        List<Map<String, Object>> rows = db.query(sql.toString(), params);
        enrich(rows);
        return rows;
    }

    @PostMapping("/")
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "wear_records", "write");
        Map<String, Object> batch = db.requireRow("SELECT batch_id, batch_number FROM batches WHERE batch_id = :id", Maps.of("id", body.get("batch_id")), "指定的批次不存在");
        Map<String, Object> person = db.requireRow("SELECT person_id, person_name FROM persons WHERE person_id = :id", Maps.of("id", body.get("person_id")), "指定的人员不存在");
        Map<String, Object> sensor = db.requireRow("SELECT sensor_id, sensor_detail_id FROM sensors WHERE sensor_id = :id", Maps.of("id", body.get("sensor_id")), "指定的传感器不存在");
        if (db.count("SELECT COUNT(*) FROM wear_records WHERE sensor_id = :sensor_id AND wear_end_time IS NULL", Maps.of("sensor_id", body.get("sensor_id"))) > 0) {
            throw new ApiException(400, "此传感器已被佩戴且未结束");
        }
        checkPositionConflict(null, body);

        Map<String, Object> data = Payload.withDates(Payload.pick(body, FIELDS), "wear_time", "wear_end_time");
        if (data.get("wear_time") == null) {
            data.put("wear_time", Date.valueOf(LocalDate.now()));
        }
        data.put("nickname", body.get("nickname") == null ? person.get("person_name") : body.get("nickname"));
        data.put("user_name", person.get("person_name"));
        if (sensor.get("sensor_detail_id") != null) {
            data.put("sensor_detail_id", sensor.get("sensor_detail_id"));
        }
        Number id = db.insert("wear_records", data, FIELDS);
        Map<String, Object> result = getByIdInternal(id);
        result.put("batch_number", batch.get("batch_number"));
        return result;
    }

    @GetMapping("/{wearRecordId}")
    public Map<String, Object> get(@PathVariable("wearRecordId") int wearRecordId, HttpServletRequest request) {
        auth.require(request, "wear_records", "read");
        return getByIdInternal(wearRecordId);
    }

    @PutMapping("/{wearRecordId}")
    @Transactional
    public Map<String, Object> update(@PathVariable("wearRecordId") int wearRecordId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "wear_records", "write");
        db.requireRow("SELECT wear_record_id FROM wear_records WHERE wear_record_id = :id", Maps.of("id", wearRecordId), "佩戴记录不存在");
        Map<String, Object> person = db.requireRow("SELECT person_id, person_name FROM persons WHERE person_id = :id", Maps.of("id", body.get("person_id")), "指定的人员不存在");
        db.requireRow("SELECT batch_id FROM batches WHERE batch_id = :id", Maps.of("id", body.get("batch_id")), "指定的批次不存在");
        Map<String, Object> sensor = db.requireRow("SELECT sensor_id, sensor_detail_id FROM sensors WHERE sensor_id = :id", Maps.of("id", body.get("sensor_id")), "指定的传感器不存在");
        if (db.count("SELECT COUNT(*) FROM wear_records WHERE sensor_id = :sensor_id AND wear_end_time IS NULL AND wear_record_id <> :id", Maps.of("sensor_id", body.get("sensor_id"), "id", wearRecordId)) > 0) {
            throw new ApiException(400, "此传感器已被其他人员佩戴且未结束");
        }
        checkPositionConflict(wearRecordId, body);

        Map<String, Object> data = Payload.withDates(Payload.pick(body, FIELDS), "wear_time", "wear_end_time");
        data.put("nickname", body.get("nickname") == null ? person.get("person_name") : body.get("nickname"));
        data.put("user_name", person.get("person_name"));
        data.put("sensor_detail_id", sensor.get("sensor_detail_id"));
        db.updateById("wear_records", "wear_record_id", wearRecordId, data, FIELDS);

        if (data.get("wear_end_time") != null) {
            LocalDate endDate = TimeUtil.parseDate(data.get("wear_end_time"));
            db.update("UPDATE sensors SET end_time = :end_time, end_reason = :end_reason WHERE sensor_id = :sensor_id",
                    Maps.of("end_time", Timestamp.valueOf(endDate.atStartOfDay()), "end_reason", data.get("cause_analysis"), "sensor_id", body.get("sensor_id")));
        } else if (data.containsKey("wear_end_time")) {
            db.update("UPDATE sensors SET end_time = NULL, end_reason = NULL WHERE sensor_id = :sensor_id", Maps.of("sensor_id", body.get("sensor_id")));
        }
        return getByIdInternal(wearRecordId);
    }

    @DeleteMapping("/{wearRecordId}")
    @Transactional
    public Map<String, Object> delete(@PathVariable("wearRecordId") int wearRecordId, HttpServletRequest request) {
        auth.require(request, "wear_records", "delete");
        Map<String, Object> wearRecord = db.requireRow("SELECT wear_record_id, sensor_id FROM wear_records WHERE wear_record_id = :id", Maps.of("id", wearRecordId), "佩戴记录不存在");
        db.update("UPDATE sensors SET end_time = :end_time, end_reason = :reason WHERE sensor_id = :sensor_id",
                Maps.of("end_time", Timestamp.valueOf(LocalDateTime.now()), "reason", "佩戴记录已删除", "sensor_id", wearRecord.get("sensor_id")));
        db.update("DELETE FROM wear_records WHERE wear_record_id = :id", Maps.of("id", wearRecordId));
        return Maps.of("message", "佩戴记录删除成功");
    }

    private StringBuilder baseSql() {
        return new StringBuilder(
                "SELECT wr.*, p.person_name, b.batch_number, " +
                        "COALESCE(sd_sensor.sensor_detail_id, sd_record.sensor_detail_id) effective_sensor_detail_id, " +
                        "COALESCE(sd_sensor.test_number, sd_record.test_number) test_number, " +
                        "COALESCE(sd_sensor.probe_number, sd_record.probe_number) probe_number " +
                        "FROM wear_records wr " +
                        "LEFT JOIN batches b ON wr.batch_id = b.batch_id " +
                        "LEFT JOIN persons p ON wr.person_id = p.person_id " +
                        "LEFT JOIN sensors s ON wr.sensor_id = s.sensor_id " +
                        "LEFT JOIN sensor_details sd_sensor ON s.sensor_detail_id = sd_sensor.sensor_detail_id " +
                        "LEFT JOIN sensor_details sd_record ON wr.sensor_detail_id = sd_record.sensor_detail_id " +
                        "WHERE 1=1"
        );
    }

    private Map<String, Object> getByIdInternal(Object id) {
        List<Map<String, Object>> rows = db.query(baseSql().append(" AND wr.wear_record_id = :id").toString(), Maps.of("id", id));
        if (rows.isEmpty()) {
            throw new ApiException(404, "佩戴记录不存在");
        }
        enrich(rows);
        return rows.get(0);
    }

    private void enrich(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object detailId = row.get("effective_sensor_detail_id");
            Map<String, Object> detail = detailId == null ? null : db.row("SELECT * FROM sensor_details WHERE sensor_detail_id = :id", Maps.of("id", detailId));
            row.put("sensor_detail", detail);
            if (detailId != null) {
                row.put("sensor_detail_id", detailId);
            }
        }
    }

    private void checkPositionConflict(Integer currentRecordId, Map<String, Object> body) {
        Object position = body.get("wear_position");
        if (position == null || String.valueOf(position).trim().isEmpty()) {
            return;
        }
        Map<String, Object> params = Maps.of("person_id", body.get("person_id"), "wear_position", position, "id", currentRecordId);
        String sql = "SELECT COUNT(*) FROM wear_records WHERE person_id = :person_id AND wear_position = :wear_position AND wear_end_time IS NULL";
        if (currentRecordId != null) {
            sql += " AND wear_record_id <> :id";
        }
        if (db.count(sql, params) > 0) {
            throw new ApiException(400, "该人员已在\"" + position + "\"位置佩戴其他传感器");
        }
    }
}
