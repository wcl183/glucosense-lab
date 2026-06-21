package com.experimentms.controller;

import com.experimentms.exception.ApiException;
import com.experimentms.security.AuthService;
import com.experimentms.service.Db;
import com.experimentms.util.Maps;
import com.experimentms.util.Payload;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/persons")
public class PersonController {
    private final Db db;
    private final AuthService auth;

    public PersonController(Db db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @GetMapping("/batches")
    public List<Map<String, Object>> batches(HttpServletRequest request) {
        auth.require(request, "person_management", "read");
        return db.query("SELECT b.*, (SELECT COUNT(*) FROM persons p WHERE p.batch_id = b.batch_id) person_count FROM batches b ORDER BY b.batch_id", null);
    }

    @GetMapping("/")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int skip,
                                          @RequestParam(defaultValue = "100") int limit,
                                          @RequestParam(required = false) String search,
                                          HttpServletRequest request) {
        auth.require(request, "person_management", "read");
        StringBuilder sql = new StringBuilder("SELECT p.*, b.batch_number FROM persons p LEFT JOIN batches b ON p.batch_id = b.batch_id WHERE 1=1");
        Map<String, Object> params = Maps.of("skip", skip, "limit", limit);
        if (search != null && !search.isEmpty()) {
            sql.append(" AND p.person_name LIKE :search");
            params.put("search", "%" + search + "%");
        }
        sql.append(" ORDER BY p.person_id LIMIT :limit OFFSET :skip");
        return db.query(sql.toString(), params);
    }

    @PostMapping("/")
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "person_management", "write");
        Map<String, Object> data = Payload.pick(body, "person_name", "gender", "age", "batch_id");
        Number id = db.insert("persons", data, "person_name", "gender", "age", "batch_id");
        if (data.get("batch_id") != null) {
            syncPersonIntoBatchExperiments(id, data.get("batch_id"));
        }
        return getByIdInternal(id);
    }

    @GetMapping("/{personId}")
    public Map<String, Object> get(@PathVariable("personId") int personId, HttpServletRequest request) {
        auth.require(request, "person_management", "read");
        return getByIdInternal(personId);
    }

    @PutMapping("/{personId}")
    @Transactional
    public Map<String, Object> update(@PathVariable("personId") int personId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "person_management", "write");
        Map<String, Object> old = db.requireRow("SELECT person_id, batch_id FROM persons WHERE person_id = :id", Maps.of("id", personId), "人员不存在");
        Map<String, Object> data = Payload.pick(body, "person_name", "gender", "age", "batch_id");
        db.updateById("persons", "person_id", personId, data, "person_name", "gender", "age", "batch_id");
        if (data.containsKey("batch_id")) {
            Object oldBatch = old.get("batch_id");
            Object newBatch = data.get("batch_id");
            if (!String.valueOf(oldBatch).equals(String.valueOf(newBatch))) {
                if (oldBatch != null) {
                    db.update("DELETE em FROM experiment_members em JOIN experiments e ON em.experiment_id = e.experiment_id WHERE e.batch_id = :batch_id AND em.person_id = :person_id", Maps.of("batch_id", oldBatch, "person_id", personId));
                }
                if (newBatch != null) {
                    syncPersonIntoBatchExperiments(personId, newBatch);
                }
            }
        }
        return getByIdInternal(personId);
    }

    @DeleteMapping("/{personId}")
    public Map<String, Object> delete(@PathVariable("personId") int personId, HttpServletRequest request) {
        auth.require(request, "person_management", "delete");
        db.requireRow("SELECT person_id FROM persons WHERE person_id = :id", Maps.of("id", personId), "人员不存在");
        int related = db.count("SELECT (SELECT COUNT(*) FROM experiment_members WHERE person_id=:id) + (SELECT COUNT(*) FROM wear_records WHERE person_id=:id) + (SELECT COUNT(*) FROM competitor_files WHERE person_id=:id) + (SELECT COUNT(*) FROM finger_blood_files WHERE person_id=:id) + (SELECT COUNT(*) FROM sensors WHERE person_id=:id)", Maps.of("id", personId));
        if (related > 0) {
            throw new ApiException(400, "无法删除该人员，存在关联数据");
        }
        db.update("DELETE FROM persons WHERE person_id = :id", Maps.of("id", personId));
        return Maps.of("message", "人员删除成功");
    }

    private Map<String, Object> getByIdInternal(Object id) {
        return db.requireRow(
                "SELECT p.*, b.batch_number FROM persons p LEFT JOIN batches b ON p.batch_id = b.batch_id WHERE p.person_id = :id",
                Maps.of("id", id),
                "人员不存在"
        );
    }

    private void syncPersonIntoBatchExperiments(Object personId, Object batchId) {
        List<Map<String, Object>> experiments = db.query("SELECT experiment_id FROM experiments WHERE batch_id = :batch_id", Maps.of("batch_id", batchId));
        for (Map<String, Object> experiment : experiments) {
            if (db.count("SELECT COUNT(*) FROM experiment_members WHERE experiment_id = :experiment_id AND person_id = :person_id", Maps.of("experiment_id", experiment.get("experiment_id"), "person_id", personId)) == 0) {
                db.insert("experiment_members", Maps.of("experiment_id", experiment.get("experiment_id"), "person_id", personId), "experiment_id", "person_id");
            }
        }
    }
}
