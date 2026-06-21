package com.experimentms.controller;

import com.experimentms.exception.ApiException;
import com.experimentms.security.AuthService;
import com.experimentms.service.ActivityLogService;
import com.experimentms.service.Db;
import com.experimentms.util.Maps;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/competitorFiles")
public class CompetitorFileController {
    private final Db db;
    private final AuthService auth;
    private final ActivityLogService activityLog;

    @Value("${app.storage.uploads-dir:uploads}")
    private String uploadsDir;

    private Path competitorUploadDir;

    public CompetitorFileController(Db db, AuthService auth, ActivityLogService activityLog) {
        this.db = db;
        this.auth = auth;
        this.activityLog = activityLog;
    }

    @PostConstruct
    public void init() throws Exception {
        competitorUploadDir = Paths.get(uploadsDir, "competitor_files").toAbsolutePath().normalize();
        Files.createDirectories(competitorUploadDir);
    }

    @GetMapping("/")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int skip,
                                          @RequestParam(defaultValue = "100") int limit,
                                          @RequestParam(required = false) Integer batch_id,
                                          @RequestParam(required = false) Integer person_id,
                                          HttpServletRequest request) {
        auth.require(request, "competitor_data", "read");
        StringBuilder sql = new StringBuilder("SELECT cf.*, p.person_name, b.batch_number FROM competitor_files cf JOIN persons p ON cf.person_id = p.person_id JOIN batches b ON cf.batch_id = b.batch_id WHERE 1=1");
        Map<String, Object> params = Maps.of("skip", skip, "limit", limit);
        if (batch_id != null) {
            sql.append(" AND cf.batch_id = :batch_id");
            params.put("batch_id", batch_id);
        }
        if (person_id != null) {
            sql.append(" AND cf.person_id = :person_id");
            params.put("person_id", person_id);
        }
        sql.append(" ORDER BY cf.competitor_file_id DESC LIMIT :limit OFFSET :skip");
        List<Map<String, Object>> rows = db.query(sql.toString(), params);
        enrich(rows);
        return rows;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("batch_id") int batchId,
                                      @RequestParam("person_id") int personId,
                                      @RequestParam("file") MultipartFile file,
                                      HttpServletRequest request) throws Exception {
        auth.require(request, "competitor_data", "write");
        db.requireRow("SELECT batch_id FROM batches WHERE batch_id = :id", Maps.of("id", batchId), "指定的批次不存在");
        db.requireRow("SELECT person_id FROM persons WHERE person_id = :id", Maps.of("id", personId), "指定的人员不存在");
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new ApiException(400, "文件名不能为空");
        }
        String original = Paths.get(file.getOriginalFilename()).getFileName().toString();
        String savedName = UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_" + original;
        Path target = competitorUploadDir.resolve(savedName).normalize();
        file.transferTo(target.toFile());
        Number id = db.insert("competitor_files", Maps.of(
                "person_id", personId,
                "batch_id", batchId,
                "file_path", target.toString(),
                "upload_time", Timestamp.valueOf(LocalDateTime.now())
        ), "person_id", "batch_id", "file_path", "upload_time");
        Map<String, Object> row = getByIdInternal(id);
        row.put("filename", original);
        row.put("file_size", Files.size(target));
        return row;
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable("fileId") int fileId, HttpServletRequest request) throws Exception {
        auth.require(request, "competitor_data", "read");
        Map<String, Object> row = db.requireRow("SELECT * FROM competitor_files WHERE competitor_file_id = :id", Maps.of("id", fileId), "文件不存在");
        Path path = Paths.get(String.valueOf(row.get("file_path"))).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ApiException(404, "文件已被删除或移动");
        }
        String filename = displayName(path);
        String encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path.toFile()));
    }

    @PutMapping("/rename/{fileId}")
    public Map<String, Object> rename(@PathVariable("fileId") int fileId,
                                      @RequestParam("new_filename") String newFilename,
                                      HttpServletRequest request) throws Exception {
        auth.require(request, "competitor_data", "write");
        Map<String, Object> row = db.requireRow("SELECT * FROM competitor_files WHERE competitor_file_id = :id", Maps.of("id", fileId), "文件不存在");
        Path oldPath = Paths.get(String.valueOf(row.get("file_path"))).toAbsolutePath().normalize();
        if (!Files.exists(oldPath)) {
            throw new ApiException(404, "文件已被删除");
        }
        String oldName = oldPath.getFileName().toString();
        String prefix = oldName.contains("_") && oldName.substring(0, oldName.indexOf('_')).length() == 8
                ? oldName.substring(0, oldName.indexOf('_'))
                : UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Path newPath = oldPath.getParent().resolve(prefix + "_" + newFilename).normalize();
        Files.move(oldPath, newPath);
        db.update("UPDATE competitor_files SET file_path = :path WHERE competitor_file_id = :id", Maps.of("path", newPath.toString(), "id", fileId));
        Map<String, Object> result = getByIdInternal(fileId);
        result.put("filename", newFilename);
        result.put("file_size", Files.size(newPath));
        return result;
    }

    @DeleteMapping("/delete/{fileId}")
    public Map<String, Object> delete(@PathVariable("fileId") int fileId, HttpServletRequest request) throws Exception {
        auth.require(request, "competitor_data", "delete");
        Map<String, Object> row = db.requireRow("SELECT * FROM competitor_files WHERE competitor_file_id = :id", Maps.of("id", fileId), "文件不存在");
        Path path = Paths.get(String.valueOf(row.get("file_path"))).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            Files.delete(path);
        }
        db.update("DELETE FROM competitor_files WHERE competitor_file_id = :id", Maps.of("id", fileId));
        return Maps.of("message", "文件删除成功");
    }

    @GetMapping("/check-integrity")
    public Map<String, Object> checkIntegrity(HttpServletRequest request) {
        auth.require(request, "competitor_data", "read");
        List<Map<String, Object>> rows = db.query("SELECT competitor_file_id, file_path FROM competitor_files", null);
        int fixed = 0;
        for (Map<String, Object> row : rows) {
            Path path = Paths.get(String.valueOf(row.get("file_path"))).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                db.update("DELETE FROM competitor_files WHERE competitor_file_id = :id", Maps.of("id", row.get("competitor_file_id")));
                fixed++;
            }
        }
        return Maps.of("message", "File integrity check completed", "issues_fixed", fixed);
    }

    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(@RequestParam(required = false) Integer batch_id,
                                                    @RequestParam(required = false) Integer person_id,
                                                    HttpServletRequest request) throws Exception {
        auth.require(request, "competitor_data", "read");
        List<Map<String, Object>> rows = list(0, 100000, batch_id, person_id, request);
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("competitor_files");
        String[] columns = new String[]{"文件ID", "文件名", "关联批次", "关联人员", "文件大小", "文件路径"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> item = rows.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(String.valueOf(item.get("competitor_file_id")));
            row.createCell(1).setCellValue(String.valueOf(item.get("filename")));
            row.createCell(2).setCellValue(String.valueOf(item.get("batch_number")));
            row.createCell(3).setCellValue(String.valueOf(item.get("person_name")));
            row.createCell(4).setCellValue(String.valueOf(item.get("file_size")));
            row.createCell(5).setCellValue(String.valueOf(item.get("file_path")));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        activityLog.log("data_export", "导出了竞品数据");
        String filename = URLEncoder.encode("竞品数据导出_" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx", "UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(out.toByteArray()));
    }

    private Map<String, Object> getByIdInternal(Object id) {
        Map<String, Object> row = db.requireRow(
                "SELECT cf.*, p.person_name, b.batch_number FROM competitor_files cf LEFT JOIN persons p ON cf.person_id = p.person_id LEFT JOIN batches b ON cf.batch_id = b.batch_id WHERE cf.competitor_file_id = :id",
                Maps.of("id", id),
                "文件不存在"
        );
        enrichOne(row);
        return row;
    }

    private void enrich(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            enrichOne(row);
        }
    }

    private void enrichOne(Map<String, Object> row) {
        try {
            Path path = Paths.get(String.valueOf(row.get("file_path"))).toAbsolutePath().normalize();
            row.put("filename", displayName(path));
            row.put("file_size", Files.exists(path) ? Files.size(path) : null);
        } catch (Exception e) {
            row.put("filename", null);
            row.put("file_size", null);
        }
    }

    private String displayName(Path path) {
        String filename = path.getFileName().toString();
        int index = filename.indexOf('_');
        if (index == 8) {
            return filename.substring(index + 1);
        }
        return filename;
    }
}
