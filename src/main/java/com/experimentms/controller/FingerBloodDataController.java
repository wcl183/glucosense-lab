package com.experimentms.controller;

import com.experimentms.exception.ApiException;
import com.experimentms.security.AuthService;
import com.experimentms.service.ActivityLogService;
import com.experimentms.service.Db;
import com.experimentms.util.Maps;
import com.experimentms.util.Payload;
import com.experimentms.util.TimeUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fingerBloodData")
public class FingerBloodDataController {
    private final Db db;
    private final AuthService auth;
    private final ActivityLogService activityLog;

    public FingerBloodDataController(Db db, AuthService auth, ActivityLogService activityLog) {
        this.db = db;
        this.auth = auth;
        this.activityLog = activityLog;
    }

    @GetMapping("/")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int skip,
                                          @RequestParam(defaultValue = "100") int limit,
                                          @RequestParam(required = false) Integer batch_id,
                                          @RequestParam(required = false) Integer person_id,
                                          @RequestParam(required = false) String start_time,
                                          @RequestParam(required = false) String end_time,
                                          HttpServletRequest request) {
        auth.require(request, "finger_blood_data", "read");
        return queryFingerBlood(skip, limit, batch_id, person_id, start_time, end_time);
    }

    @PostMapping("/")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "finger_blood_data", "write");
        validateBatchAndPerson(body.get("batch_id"), body.get("person_id"));
        Map<String, Object> data = Payload.withDateTimes(Payload.pick(body, "person_id", "batch_id", "collection_time", "blood_glucose_value"), "collection_time");
        Number id = db.insert("finger_blood_files", data, "person_id", "batch_id", "collection_time", "blood_glucose_value");
        return getByIdInternal(id);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<ByteArrayResource> export(@RequestParam(required = false) Integer batch_id,
                                                    @RequestParam(required = false) Integer person_id,
                                                    @RequestParam(required = false) String start_time,
                                                    @RequestParam(required = false) String end_time,
                                                    HttpServletRequest request) throws Exception {
        auth.require(request, "finger_blood_data", "read");
        List<Map<String, Object>> rows = queryFingerBlood(0, 100000, batch_id, person_id, start_time, end_time);
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("finger_blood_data");
        Row header = sheet.createRow(0);
        String[] columns = new String[]{"数据ID", "人员姓名", "批次编号", "采集时间", "血糖值"};
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> item = rows.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(String.valueOf(item.get("finger_blood_file_id")));
            row.createCell(1).setCellValue(String.valueOf(item.get("person_name")));
            row.createCell(2).setCellValue(String.valueOf(item.get("batch_number")));
            row.createCell(3).setCellValue(String.valueOf(item.get("collection_time")));
            row.createCell(4).setCellValue(item.get("blood_glucose_value") == null ? "" : String.valueOf(item.get("blood_glucose_value")));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        activityLog.log("导出指尖血数据", "导出了" + rows.size() + "条指尖血数据");
        String filename = URLEncoder.encode("指尖血数据导出_" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx", "UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(out.toByteArray()));
    }

    @PostMapping("/batch-delete")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> user = auth.require(request, "finger_blood_data", "delete");
        List<Object> ids = (List<Object>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(400, "请提供要删除的数据ID列表");
        }
        int deleted = db.update("DELETE FROM finger_blood_files WHERE finger_blood_file_id IN (:ids)", Maps.of("ids", ids));
        activityLog.log("批量删除指尖血数据", "批量删除了" + deleted + "条指尖血数据", user.get("user_id"));
        return Maps.of("deleted_count", deleted, "message", "成功删除" + deleted + "条指尖血数据");
    }

    @GetMapping("/{dataId}")
    public Map<String, Object> get(@PathVariable("dataId") int dataId, HttpServletRequest request) {
        auth.require(request, "finger_blood_data", "read");
        return getByIdInternal(dataId);
    }

    @PutMapping("/{dataId}")
    public Map<String, Object> update(@PathVariable("dataId") int dataId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "finger_blood_data", "write");
        db.requireRow("SELECT finger_blood_file_id FROM finger_blood_files WHERE finger_blood_file_id = :id", Maps.of("id", dataId), "数据不存在");
        validateBatchAndPerson(body.get("batch_id"), body.get("person_id"));
        Map<String, Object> data = Payload.withDateTimes(Payload.pick(body, "person_id", "batch_id", "collection_time", "blood_glucose_value"), "collection_time");
        db.updateById("finger_blood_files", "finger_blood_file_id", dataId, data, "person_id", "batch_id", "collection_time", "blood_glucose_value");
        return getByIdInternal(dataId);
    }

    @DeleteMapping("/{dataId}")
    public Map<String, Object> delete(@PathVariable("dataId") int dataId, HttpServletRequest request) {
        auth.require(request, "finger_blood_data", "delete");
        int deleted = db.update("DELETE FROM finger_blood_files WHERE finger_blood_file_id = :id", Maps.of("id", dataId));
        if (deleted == 0) {
            throw new ApiException(404, "数据不存在");
        }
        return Maps.of("message", "数据删除成功");
    }

    private List<Map<String, Object>> queryFingerBlood(int skip, int limit, Integer batchId, Integer personId, String startTime, String endTime) {
        StringBuilder sql = new StringBuilder("SELECT f.*, p.person_name, b.batch_number FROM finger_blood_files f JOIN persons p ON f.person_id = p.person_id JOIN batches b ON f.batch_id = b.batch_id WHERE 1=1");
        Map<String, Object> params = Maps.of("skip", skip, "limit", limit);
        if (batchId != null) {
            sql.append(" AND f.batch_id = :batch_id");
            params.put("batch_id", batchId);
        }
        if (personId != null) {
            sql.append(" AND f.person_id = :person_id");
            params.put("person_id", personId);
        }
        if (startTime != null) {
            sql.append(" AND f.collection_time >= :start_time");
            params.put("start_time", TimeUtil.timestamp(startTime));
        }
        if (endTime != null) {
            sql.append(" AND f.collection_time <= :end_time");
            params.put("end_time", TimeUtil.timestamp(endTime));
        }
        sql.append(" ORDER BY f.collection_time DESC LIMIT :limit OFFSET :skip");
        return db.query(sql.toString(), params);
    }

    private Map<String, Object> getByIdInternal(Object id) {
        return db.requireRow(
                "SELECT f.*, p.person_name, b.batch_number FROM finger_blood_files f LEFT JOIN persons p ON f.person_id = p.person_id LEFT JOIN batches b ON f.batch_id = b.batch_id WHERE f.finger_blood_file_id = :id",
                Maps.of("id", id),
                "数据不存在"
        );
    }

    private void validateBatchAndPerson(Object batchId, Object personId) {
        db.requireRow("SELECT batch_id FROM batches WHERE batch_id = :id", Maps.of("id", batchId), "指定的批次不存在");
        db.requireRow("SELECT person_id FROM persons WHERE person_id = :id", Maps.of("id", personId), "指定的人员不存在");
    }
}
