package com.experimentms.controller;

import com.experimentms.security.AuthService;
import com.experimentms.service.Db;
import com.experimentms.util.Maps;
import com.experimentms.util.TimeUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/experimentData")
public class ExperimentDataController {
    private final Db db;
    private final AuthService auth;

    public ExperimentDataController(Db db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @GetMapping("/batches")
    public List<Map<String, Object>> batches(HttpServletRequest request) {
        auth.require(request, "experiment_data_analysis", "read");
        return db.query("SELECT b.*, (SELECT COUNT(*) FROM persons p WHERE p.batch_id = b.batch_id) person_count FROM batches b ORDER BY b.batch_id", null);
    }

    @GetMapping("/batches-with-data")
    public List<Map<String, Object>> batchesWithData(HttpServletRequest request) {
        auth.require(request, "experiment_data_analysis", "read");
        return db.query("SELECT DISTINCT b.*, (SELECT COUNT(*) FROM persons p WHERE p.batch_id = b.batch_id) person_count FROM batches b JOIN daily_experiment_data d ON b.batch_id = d.batch_id ORDER BY b.batch_id", null);
    }

    @GetMapping("/persons")
    public List<Map<String, Object>> persons(@RequestParam(required = false) Integer batch_id, HttpServletRequest request) {
        auth.require(request, "experiment_data_analysis", "read");
        if (batch_id == null) {
            return db.query("SELECT p.*, b.batch_number FROM persons p LEFT JOIN batches b ON p.batch_id = b.batch_id ORDER BY p.person_id", null);
        }
        return db.query("SELECT p.*, b.batch_number FROM persons p LEFT JOIN batches b ON p.batch_id = b.batch_id WHERE p.batch_id = :batch_id ORDER BY p.person_id", Maps.of("batch_id", batch_id));
    }

    @GetMapping("/persons-with-data")
    public List<Map<String, Object>> personsWithData(@RequestParam(required = false) Integer batch_id, HttpServletRequest request) {
        auth.require(request, "experiment_data_analysis", "read");
        if (batch_id == null) {
            return db.query("SELECT DISTINCT p.*, b.batch_number FROM persons p LEFT JOIN batches b ON p.batch_id = b.batch_id JOIN daily_experiment_data d ON p.person_id = d.person_id ORDER BY p.person_id", null);
        }
        return db.query("SELECT DISTINCT p.*, b.batch_number FROM persons p LEFT JOIN batches b ON p.batch_id = b.batch_id JOIN daily_experiment_data d ON p.person_id = d.person_id WHERE p.batch_id = :batch_id ORDER BY p.person_id", Maps.of("batch_id", batch_id));
    }

    @GetMapping("/data")
    public Map<String, Object> data(@RequestParam(required = false) Integer batch_id,
                                    @RequestParam(required = false) Integer person_id,
                                    HttpServletRequest request) {
        auth.require(request, "experiment_data_analysis", "read");
        StringBuilder sql = new StringBuilder("SELECT * FROM daily_experiment_data WHERE 1=1");
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        if (batch_id != null) {
            sql.append(" AND batch_id = :batch_id");
            params.put("batch_id", batch_id);
        }
        if (person_id != null) {
            sql.append(" AND person_id = :person_id");
            params.put("person_id", person_id);
        }
        sql.append(" ORDER BY experiment_day");
        List<Map<String, Object>> rows = db.query(sql.toString(), params);
        double mardTotal = 0.0;
        double pardTotal = 0.0;
        int mardCount = 0;
        int pardCount = 0;
        for (Map<String, Object> row : rows) {
            if (row.get("mard_value") instanceof Number) {
                mardTotal += ((Number) row.get("mard_value")).doubleValue();
                mardCount++;
            }
            if (row.get("pard_value") instanceof Number) {
                pardTotal += ((Number) row.get("pard_value")).doubleValue();
                pardCount++;
            }
        }
        return Maps.of(
                "daily_data", rows,
                "avg_mard", mardCount == 0 ? 0.0 : Math.round((mardTotal / mardCount) * 100000.0) / 100000.0,
                "avg_pard", pardCount == 0 ? 0.0 : Math.round((pardTotal / pardCount) * 100000.0) / 100000.0
        );
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        auth.require(request, "experiment_data_analysis", "write");
        int processed = 0;
        DataFormatter formatter = new DataFormatter();
        InputStream input = file.getInputStream();
        Workbook workbook = WorkbookFactory.create(input);
        Sheet sheet = workbook.getSheetAt(0);
        if (sheet.getPhysicalNumberOfRows() < 2) {
            workbook.close();
            return Maps.of("message", "文件导入成功", "processed_rows", 0);
        }
        Row header = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> indexes = headers(header, formatter);

        for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String batchNumber = cell(row, indexes, formatter, "批次号", "批次编号", "batch_number");
            String personName = cell(row, indexes, formatter, "姓名", "人员姓名", "person_name");
            String dayText = cell(row, indexes, formatter, "实验天数", "天数", "experiment_day");
            if (batchNumber == null || personName == null || dayText == null) {
                continue;
            }
            Map<String, Object> batch = db.row("SELECT batch_id FROM batches WHERE batch_number = :batch_number", Maps.of("batch_number", batchNumber));
            if (batch == null) {
                continue;
            }
            Map<String, Object> person = db.row("SELECT person_id FROM persons WHERE person_name = :name AND batch_id = :batch_id", Maps.of("name", personName, "batch_id", batch.get("batch_id")));
            if (person == null) {
                continue;
            }
            int experimentDay = (int) Double.parseDouble(dayText);
            Object mard = numberOrNull(cell(row, indexes, formatter, "MARD值", "MARD", "mard_value"));
            Object pard = numberOrNull(cell(row, indexes, formatter, "PARD值", "PARD", "pard_value"));
            String recordDateText = cell(row, indexes, formatter, "记录日期", "日期", "record_date");
            Date recordDate = recordDateText == null ? Date.valueOf(java.time.LocalDate.now()) : TimeUtil.sqlDate(recordDateText);

            Map<String, Object> existing = db.row("SELECT data_id FROM daily_experiment_data WHERE person_id = :person_id AND experiment_day = :day", Maps.of("person_id", person.get("person_id"), "day", experimentDay));
            if (existing == null) {
                db.insert("daily_experiment_data", Maps.of(
                        "person_id", person.get("person_id"),
                        "batch_id", batch.get("batch_id"),
                        "experiment_day", experimentDay,
                        "mard_value", mard,
                        "pard_value", pard,
                        "record_date", recordDate
                ), "person_id", "batch_id", "experiment_day", "mard_value", "pard_value", "record_date");
            } else {
                db.update("UPDATE daily_experiment_data SET batch_id = :batch_id, mard_value = :mard, pard_value = :pard, record_date = :record_date WHERE data_id = :id",
                        Maps.of("batch_id", batch.get("batch_id"), "mard", mard, "pard", pard, "record_date", recordDate, "id", existing.get("data_id")));
            }
            processed++;
        }
        workbook.close();
        return Maps.of("message", "文件导入成功", "processed_rows", processed);
    }

    private Map<String, Integer> headers(Row row, DataFormatter formatter) {
        Map<String, Integer> indexes = new LinkedHashMap<String, Integer>();
        if (row == null) {
            return indexes;
        }
        for (Cell cell : row) {
            indexes.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
        }
        return indexes;
    }

    private String cell(Row row, Map<String, Integer> indexes, DataFormatter formatter, String... names) {
        for (String name : names) {
            Integer index = indexes.get(name);
            if (index != null) {
                String value = formatter.formatCellValue(row.getCell(index)).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private Object numberOrNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return Double.parseDouble(text.trim());
    }
}
