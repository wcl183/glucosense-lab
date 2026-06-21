package com.experimentms.controller;

import com.experimentms.exception.ApiException;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataController {
    private final Db db;
    private final AuthService auth;
    private final NamedParameterJdbcTemplate userJdbc;
    private final NamedParameterJdbcTemplate deviceJdbc;
    private final NamedParameterJdbcTemplate sensorDataJdbc;

    public SensorDataController(
            Db db,
            AuthService auth,
            @Qualifier("userJdbc") NamedParameterJdbcTemplate userJdbc,
            @Qualifier("deviceJdbc") NamedParameterJdbcTemplate deviceJdbc,
            @Qualifier("sensorDataJdbc") NamedParameterJdbcTemplate sensorDataJdbc
    ) {
        this.db = db;
        this.auth = auth;
        this.userJdbc = userJdbc;
        this.deviceJdbc = deviceJdbc;
        this.sensorDataJdbc = sensorDataJdbc;
    }

    @GetMapping("/{userId}/overall")
    public Map<String, Object> overall(@PathVariable("userId") long userId, HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        return Maps.of("readings", sensorReadings(userId, null));
    }

    @GetMapping("/{userId}/available-dates")
    public Map<String, Object> availableDates(@PathVariable("userId") long userId, HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        List<Map<String, Object>> devices = devicesForUser(userId);
        Set<String> dates = new LinkedHashSet<String>();
        String wearTime = null;
        for (Map<String, Object> device : devices) {
            if (wearTime == null && device.get("wear_time") != null) {
                wearTime = String.valueOf(TimeUtil.normalize(device.get("wear_time")));
            }
            LocalDateTime start = device.get("wear_time") == null ? null : TimeUtil.parseDateTime(device.get("wear_time"));
            LocalDateTime end = start == null ? null : start.plusDays(15);
            StringBuilder sql = new StringBuilder("SELECT DISTINCT DATE(index_time) date_only FROM cgm_sensor WHERE sensor_num = :sensor_num");
            Map<String, Object> params = Maps.of("sensor_num", device.get("sensor_num"));
            if (start != null) {
                sql.append(" AND index_time >= :start_time AND index_time < :end_time");
                params.put("start_time", java.sql.Timestamp.valueOf(start));
                params.put("end_time", java.sql.Timestamp.valueOf(end));
            }
            for (Map<String, Object> row : normalize(sensorDataJdbc.queryForList(sql.toString(), params))) {
                if (row.get("date_only") != null) {
                    dates.add(String.valueOf(row.get("date_only")));
                }
            }
        }
        List<String> sorted = new ArrayList<String>(dates);
        Collections.sort(sorted);
        return Maps.of("dates", sorted, "wear_time", wearTime);
    }

    @GetMapping("/{userId}/data")
    public Map<String, Object> dataByDate(@PathVariable("userId") long userId,
                                          @RequestParam("date") String date,
                                          HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        return Maps.of("date", date, "readings", sensorReadings(userId, date));
    }

    @GetMapping("/user-by-phone/{phoneNumber}")
    public Map<String, Object> userByPhone(@PathVariable("phoneNumber") String phoneNumber, HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        Map<String, Object> user = first(userJdbc, "SELECT user_id, user_name, nick_name, phonenumber FROM sys_user WHERE phonenumber = :phone", Maps.of("phone", phoneNumber));
        if (user == null) {
            throw new ApiException(404, "未找到手机号为 " + phoneNumber + " 的用户");
        }
        return user;
    }

    @GetMapping("/user-by-name/{userName}")
    public Map<String, Object> userByName(@PathVariable("userName") String userName, HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        Map<String, Object> user = first(userJdbc, "SELECT user_id, user_name, nick_name, phonenumber FROM sys_user WHERE user_name = :name OR nick_name = :name", Maps.of("name", userName));
        if (user == null) {
            throw new ApiException(404, "未找到用户名为 " + userName + " 的用户");
        }
        return user;
    }

    @GetMapping("/search-users-by-name/{userName}")
    public Map<String, Object> searchUsersByName(@PathVariable("userName") String userName, HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        return Maps.of("users", normalize(userJdbc.queryForList(
                "SELECT user_id, user_name, nick_name, phonenumber FROM sys_user WHERE user_name LIKE :name OR nick_name LIKE :name ORDER BY user_id LIMIT 50",
                Maps.of("name", "%" + userName + "%")
        )));
    }

    @GetMapping("/batches")
    public Map<String, Object> batches(HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        return Maps.of("batches", db.query(
                "SELECT DISTINCT b.* FROM batches b JOIN wear_records wr ON b.batch_id = wr.batch_id ORDER BY b.batch_id DESC",
                null
        ));
    }

    @GetMapping("/batch/{batchId}/persons")
    public Map<String, Object> persons(@PathVariable("batchId") int batchId, HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        return Maps.of("persons", db.query(
                "SELECT DISTINCT p.*, b.batch_number FROM persons p JOIN wear_records wr ON p.person_id = wr.person_id LEFT JOIN batches b ON p.batch_id = b.batch_id WHERE wr.batch_id = :batch_id ORDER BY p.person_id",
                Maps.of("batch_id", batchId)
        ));
    }

    @GetMapping("/batch/{batchId}/person/{personId}/wear-records")
    public Map<String, Object> wearRecords(@PathVariable("batchId") int batchId,
                                           @PathVariable("personId") int personId,
                                           HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        return Maps.of("wear_records", wearRecordsByBatchPerson(batchId, personId));
    }

    @GetMapping("/nickname/{nickname}/data")
    public Map<String, Object> dataByNickname(@PathVariable("nickname") String nickname,
                                              @RequestParam(required = false) String date,
                                              HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        Map<String, Object> user = first(userJdbc,
                "SELECT user_id, user_name, nick_name, phonenumber FROM sys_user WHERE user_name = :name OR nick_name = :name",
                Maps.of("name", nickname));
        if (user == null) {
            return Maps.of("data", new ArrayList<Map<String, Object>>(), "user_info", null);
        }
        return Maps.of("data", sensorReadings(((Number) user.get("user_id")).longValue(), date), "user_info", user);
    }

    @GetMapping("/batch/{batchId}/person/{personId}/competitor-data")
    public Map<String, Object> competitorData(@PathVariable("batchId") int batchId,
                                              @PathVariable("personId") int personId,
                                              HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        return Maps.of("competitor_data", latestCompetitorData(batchId, personId));
    }

    @GetMapping("/batch/{batchId}/person/{personId}/finger-blood-data")
    public Map<String, Object> fingerBloodData(@PathVariable("batchId") int batchId,
                                               @PathVariable("personId") int personId,
                                               HttpServletRequest request) {
        auth.require(request, "sensor_data_visualization", "read");
        List<Map<String, Object>> rows = db.query(
                "SELECT DATE_FORMAT(collection_time, '%H:%i') time, collection_time, blood_glucose_value glucose, 'finger_blood' data_type FROM finger_blood_files WHERE batch_id = :batch_id AND person_id = :person_id ORDER BY collection_time",
                Maps.of("batch_id", batchId, "person_id", personId)
        );
        return Maps.of("finger_blood_data", rows);
    }

    @GetMapping("/download-latest/{batchId}/{personId}")
    public ResponseEntity<ByteArrayResource> downloadLatest(@PathVariable("batchId") int batchId,
                                                            @PathVariable("personId") int personId,
                                                            @RequestParam(required = false) String nickname,
                                                            HttpServletRequest request) throws Exception {
        auth.require(request, "sensor_data_visualization", "read");
        List<Map<String, Object>> wearRecords = wearRecordsByBatchPerson(batchId, personId);
        List<Map<String, Object>> readings = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> record : wearRecords) {
            Object recordNickname = nickname != null ? nickname : record.get("nickname");
            if (recordNickname != null) {
                Map<String, Object> user = first(userJdbc,
                        "SELECT user_id, user_name, nick_name, phonenumber FROM sys_user WHERE user_name = :name OR nick_name = :name",
                        Maps.of("name", recordNickname));
                if (user != null) {
                    readings.addAll(sensorReadings(((Number) user.get("user_id")).longValue(), null));
                }
            }
        }
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("sensor_data");
        Row header = sheet.createRow(0);
        String[] columns = new String[]{"time", "glucose", "original_value", "current", "sensor_num", "nickname"};
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
        for (int i = 0; i < readings.size(); i++) {
            Map<String, Object> item = readings.get(i);
            Row row = sheet.createRow(i + 1);
            for (int c = 0; c < columns.length; c++) {
                Object value = item.get(columns[c]);
                row.createCell(c).setCellValue(value == null ? "" : String.valueOf(value));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        String filename = URLEncoder.encode("sensor_data_" + batchId + "_" + personId + ".xlsx", "UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(out.toByteArray()));
    }

    private List<Map<String, Object>> devicesForUser(long userId) {
        return normalize(deviceJdbc.queryForList(
                "SELECT user_id, sensor_num, wear_time, status FROM cgm_device WHERE user_id = :user_id ORDER BY wear_time",
                Maps.of("user_id", userId)
        ));
    }

    private List<Map<String, Object>> sensorReadings(long userId, String date) {
        List<Map<String, Object>> devices = devicesForUser(userId);
        List<Map<String, Object>> readings = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> device : devices) {
            StringBuilder sql = new StringBuilder(
                    "SELECT DATE_FORMAT(index_time, '%H:%i') time, index_time, glu_value glucose, original_value, current, sensor_num " +
                            "FROM cgm_sensor WHERE user_id = :user_id AND sensor_num = :sensor_num"
            );
            Map<String, Object> params = Maps.of("user_id", userId, "sensor_num", device.get("sensor_num"));
            if (date != null) {
                LocalDate day = TimeUtil.parseDate(date);
                sql.append(" AND index_time >= :start_time AND index_time < :end_time");
                params.put("start_time", java.sql.Timestamp.valueOf(LocalDateTime.of(day, LocalTime.MIDNIGHT)));
                params.put("end_time", java.sql.Timestamp.valueOf(LocalDateTime.of(day.plusDays(1), LocalTime.MIDNIGHT)));
            }
            sql.append(" ORDER BY index_time");
            List<Map<String, Object>> rows = normalize(sensorDataJdbc.queryForList(sql.toString(), params));
            for (Map<String, Object> row : rows) {
                row.put("current", row.get("current"));
                row.put("nickname", device.get("sensor_num"));
                readings.add(row);
            }
        }
        Collections.sort(readings, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                return String.valueOf(o1.get("index_time")).compareTo(String.valueOf(o2.get("index_time")));
            }
        });
        return readings;
    }

    private List<Map<String, Object>> wearRecordsByBatchPerson(int batchId, int personId) {
        return db.query(
                "SELECT wr.*, p.person_name, b.batch_number, sd.test_number, sd.probe_number " +
                        "FROM wear_records wr " +
                        "LEFT JOIN persons p ON wr.person_id = p.person_id " +
                        "LEFT JOIN batches b ON wr.batch_id = b.batch_id " +
                        "LEFT JOIN sensor_details sd ON wr.sensor_detail_id = sd.sensor_detail_id " +
                        "WHERE wr.batch_id = :batch_id AND wr.person_id = :person_id ORDER BY wr.wear_time",
                Maps.of("batch_id", batchId, "person_id", personId)
        );
    }

    private List<Map<String, Object>> latestCompetitorData(int batchId, int personId) {
        Map<String, Object> file = db.row(
                "SELECT * FROM competitor_files WHERE batch_id = :batch_id AND person_id = :person_id ORDER BY upload_time DESC, competitor_file_id DESC LIMIT 1",
                Maps.of("batch_id", batchId, "person_id", personId)
        );
        if (file == null || file.get("file_path") == null) {
            return new ArrayList<Map<String, Object>>();
        }
        Path path = Paths.get(String.valueOf(file.get("file_path"))).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return new ArrayList<Map<String, Object>>();
        }
        return parseCompetitorFile(path);
    }

    private List<Map<String, Object>> parseCompetitorFile(Path path) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        try {
            InputStream input = Files.newInputStream(path);
            Workbook workbook = WorkbookFactory.create(input);
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> headers = new LinkedHashMap<String, Integer>();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header != null) {
                for (Cell cell : header) {
                    headers.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
                }
            }
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String time = firstCell(row, headers, formatter, "time", "时间", "检测时间", "采集时间");
                String glucose = firstCell(row, headers, formatter, "glucose", "血糖", "血糖值", "葡萄糖");
                if (time == null || glucose == null) {
                    continue;
                }
                result.add(Maps.of(
                        "time", time,
                        "glucose", Double.parseDouble(glucose),
                        "data_type", "competitor"
                ));
            }
            workbook.close();
        } catch (Exception ignored) {
            return new ArrayList<Map<String, Object>>();
        }
        return result;
    }

    private String firstCell(Row row, Map<String, Integer> headers, DataFormatter formatter, String... names) {
        for (String name : names) {
            Integer index = headers.get(name);
            if (index != null) {
                String value = formatter.formatCellValue(row.getCell(index)).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private Map<String, Object> first(NamedParameterJdbcTemplate jdbc, String sql, Map<String, Object> params) {
        List<Map<String, Object>> rows = normalize(jdbc.queryForList(sql, params));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object value = TimeUtil.normalize(entry.getValue());
                if (value instanceof Number) {
                    item.put(entry.getKey(), value);
                } else {
                    item.put(entry.getKey(), value);
                }
            }
            result.add(item);
        }
        return result;
    }
}
