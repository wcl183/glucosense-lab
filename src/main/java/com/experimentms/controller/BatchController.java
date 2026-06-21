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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
public class BatchController {
    private final Db db;
    private final AuthService auth;

    public BatchController(Db db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @GetMapping("/")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int skip,
                                          @RequestParam(defaultValue = "100") int limit,
                                          @RequestParam(required = false) String search,
                                          HttpServletRequest request) {
        auth.require(request, "batch_management", "read");
        StringBuilder sql = new StringBuilder("SELECT b.*, (SELECT COUNT(*) FROM persons p WHERE p.batch_id = b.batch_id) person_count FROM batches b WHERE 1=1");
        Map<String, Object> params = Maps.of("skip", skip, "limit", limit);
        if (search != null && !search.isEmpty()) {
            sql.append(" AND b.batch_number LIKE :search");
            params.put("search", "%" + search + "%");
        }
        sql.append(" ORDER BY b.batch_id LIMIT :limit OFFSET :skip");
        return db.query(sql.toString(), params);
    }

    @PostMapping("/")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "batch_management", "write");
        Map<String, Object> data = Payload.withDateTimes(Payload.pick(body, "batch_number", "start_time", "end_time", "person_count"), "start_time", "end_time");
        if (db.count("SELECT COUNT(*) FROM batches WHERE batch_number = :batch_number", Maps.of("batch_number", data.get("batch_number"))) > 0) {
            throw new ApiException(400, "批次号已存在");
        }
        Number id = db.insert("batches", data, "batch_number", "start_time", "end_time", "person_count");
        return getByIdInternal(id);
    }

    @GetMapping("/{batchId}")
    public Map<String, Object> get(@PathVariable("batchId") int batchId, HttpServletRequest request) {
        auth.require(request, "batch_management", "read");
        return getByIdInternal(batchId);
    }

    @PutMapping("/{batchId}")
    public Map<String, Object> update(@PathVariable("batchId") int batchId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "batch_management", "write");
        db.requireRow("SELECT batch_id FROM batches WHERE batch_id = :id", Maps.of("id", batchId), "批次不存在");
        if (body.containsKey("batch_number")
                && db.count("SELECT COUNT(*) FROM batches WHERE batch_number = :batch_number AND batch_id <> :id",
                Maps.of("batch_number", body.get("batch_number"), "id", batchId)) > 0) {
            throw new ApiException(400, "批次号已存在");
        }
        Map<String, Object> data = Payload.withDateTimes(Payload.pick(body, "batch_number", "start_time", "end_time", "person_count"), "start_time", "end_time");
        db.updateById("batches", "batch_id", batchId, data, "batch_number", "start_time", "end_time", "person_count");
        return getByIdInternal(batchId);
    }

    @DeleteMapping("/{batchId}")
    public Map<String, Object> delete(@PathVariable("batchId") int batchId, HttpServletRequest request) {
        auth.require(request, "batch_management", "delete");
        db.requireRow("SELECT batch_id FROM batches WHERE batch_id = :id", Maps.of("id", batchId), "批次不存在");
        int related = db.count("SELECT (SELECT COUNT(*) FROM experiments WHERE batch_id=:id) + (SELECT COUNT(*) FROM competitor_files WHERE batch_id=:id) + (SELECT COUNT(*) FROM finger_blood_files WHERE batch_id=:id) + (SELECT COUNT(*) FROM sensors WHERE batch_id=:id)", Maps.of("id", batchId));
        if (related > 0) {
            throw new ApiException(400, "该批次下还有关联数据，无法删除");
        }
        db.update("DELETE FROM batches WHERE batch_id = :id", Maps.of("id", batchId));
        return Maps.of("message", "批次删除成功");
    }

    private Map<String, Object> getByIdInternal(Object id) {
        return db.requireRow(
                "SELECT b.*, (SELECT COUNT(*) FROM persons p WHERE p.batch_id = b.batch_id) person_count FROM batches b WHERE b.batch_id = :id",
                Maps.of("id", id),
                "批次不存在"
        );
    }
}
