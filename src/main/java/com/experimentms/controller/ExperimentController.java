package com.experimentms.controller;

import com.experimentms.exception.ApiException;
import com.experimentms.security.AuthService;
import com.experimentms.service.ActivityLogService;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
    private final Db db;
    private final AuthService auth;
    private final ActivityLogService activityLog;

    public ExperimentController(Db db, AuthService auth, ActivityLogService activityLog) {
        this.db = db;
        this.auth = auth;
        this.activityLog = activityLog;
    }

    @GetMapping("/")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "0") int skip,
                                          @RequestParam(defaultValue = "100") int limit,
                                          @RequestParam(required = false) Integer batch_id,
                                          @RequestParam(required = false) Integer person_id,
                                          HttpServletRequest request) {
        auth.require(request, "experiment_management", "read");
        StringBuilder sql = new StringBuilder("SELECT DISTINCT e.*, b.batch_number FROM experiments e LEFT JOIN batches b ON e.batch_id = b.batch_id");
        Map<String, Object> params = Maps.of("skip", skip, "limit", limit);
        if (person_id != null) {
            sql.append(" JOIN experiment_members em_filter ON e.experiment_id = em_filter.experiment_id");
        }
        sql.append(" WHERE 1=1");
        if (batch_id != null) {
            sql.append(" AND e.batch_id = :batch_id");
            params.put("batch_id", batch_id);
        }
        if (person_id != null) {
            sql.append(" AND em_filter.person_id = :person_id");
            params.put("person_id", person_id);
        }
        sql.append(" ORDER BY e.experiment_id DESC LIMIT :limit OFFSET :skip");

        List<Map<String, Object>> rows = db.query(sql.toString(), params);
        for (Map<String, Object> row : rows) {
            row.put("members", members(row.get("experiment_id")));
        }
        return rows;
    }

    @PostMapping("/")
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "experiment_management", "write");
        int batchId = Payload.requiredInt(body, "batch_id");
        db.requireRow("SELECT batch_id, batch_number FROM batches WHERE batch_id = :id", Maps.of("id", batchId), "指定的批次不存在");
        List<Object> memberIds = (List<Object>) body.get("member_ids");
        if (memberIds == null || memberIds.isEmpty()) {
            throw new ApiException(400, "至少需要一个实验成员");
        }
        for (Object personId : memberIds) {
            db.requireRow("SELECT person_id FROM persons WHERE person_id = :id", Maps.of("id", personId), "人员ID " + personId + " 不存在");
        }

        Number id = db.insert("experiments", Maps.of(
                "batch_id", batchId,
                "experiment_content", body.get("experiment_content"),
                "created_time", Timestamp.valueOf(LocalDateTime.now())
        ), "batch_id", "experiment_content", "created_time");
        for (Object personId : memberIds) {
            db.insert("experiment_members", Maps.of("experiment_id", id, "person_id", personId), "experiment_id", "person_id");
        }
        activityLog.log("experiment_create", "创建了实验 " + id);
        return experimentResponse(id);
    }

    @GetMapping("/{experimentId}")
    public Map<String, Object> get(@PathVariable("experimentId") int experimentId, HttpServletRequest request) {
        auth.require(request, "experiment_management", "read");
        return experimentResponse(experimentId);
    }

    @PutMapping("/{experimentId}")
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> update(@PathVariable("experimentId") int experimentId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.require(request, "experiment_management", "write");
        db.requireRow("SELECT experiment_id FROM experiments WHERE experiment_id = :id", Maps.of("id", experimentId), "实验不存在");
        Map<String, Object> data = Payload.pick(body, "batch_id", "experiment_content");
        if (data.containsKey("batch_id")) {
            db.requireRow("SELECT batch_id FROM batches WHERE batch_id = :id", Maps.of("id", data.get("batch_id")), "指定的批次不存在");
        }
        db.updateById("experiments", "experiment_id", experimentId, data, "batch_id", "experiment_content");

        if (body.containsKey("member_ids")) {
            List<Object> memberIds = (List<Object>) body.get("member_ids");
            db.update("DELETE FROM experiment_members WHERE experiment_id = :id", Maps.of("id", experimentId));
            if (memberIds != null) {
                for (Object personId : memberIds) {
                    db.requireRow("SELECT person_id FROM persons WHERE person_id = :id", Maps.of("id", personId), "人员ID " + personId + " 不存在");
                    db.insert("experiment_members", Maps.of("experiment_id", experimentId, "person_id", personId), "experiment_id", "person_id");
                }
            }
        }
        activityLog.log("experiment_update", "更新了实验 " + experimentId);
        return experimentResponse(experimentId);
    }

    @DeleteMapping("/{experimentId}")
    @Transactional
    public Map<String, Object> delete(@PathVariable("experimentId") int experimentId, HttpServletRequest request) {
        auth.require(request, "experiment_management", "write");
        db.requireRow("SELECT experiment_id FROM experiments WHERE experiment_id = :id", Maps.of("id", experimentId), "实验不存在");
        activityLog.log("experiment_delete", "删除了实验 " + experimentId);
        db.update("DELETE FROM experiment_members WHERE experiment_id = :id", Maps.of("id", experimentId));
        db.update("DELETE FROM experiments WHERE experiment_id = :id", Maps.of("id", experimentId));
        return Maps.of("message", "实验记录删除成功");
    }

    @PostMapping("/{experimentId}/members")
    public Map<String, Object> addMember(@PathVariable("experimentId") int experimentId,
                                         @RequestParam("person_id") int personId,
                                         HttpServletRequest request) {
        auth.require(request, "experiment_management", "write");
        db.requireRow("SELECT experiment_id FROM experiments WHERE experiment_id = :id", Maps.of("id", experimentId), "实验不存在");
        Map<String, Object> person = db.requireRow("SELECT person_id, person_name FROM persons WHERE person_id = :id", Maps.of("id", personId), "人员不存在");
        if (db.count("SELECT COUNT(*) FROM experiment_members WHERE experiment_id = :experiment_id AND person_id = :person_id", Maps.of("experiment_id", experimentId, "person_id", personId)) > 0) {
            throw new ApiException(400, "该人员已经是实验成员");
        }
        db.insert("experiment_members", Maps.of("experiment_id", experimentId, "person_id", personId), "experiment_id", "person_id");
        activityLog.log("experiment_member_add", "为实验 " + experimentId + " 添加了成员 " + person.get("person_name"));
        return Maps.of("message", "成功添加成员 " + person.get("person_name"));
    }

    @DeleteMapping("/{experimentId}/members/{personId}")
    public Map<String, Object> removeMember(@PathVariable("experimentId") int experimentId,
                                            @PathVariable("personId") int personId,
                                            HttpServletRequest request) {
        auth.require(request, "experiment_management", "write");
        db.requireRow("SELECT experiment_id FROM experiments WHERE experiment_id = :id", Maps.of("id", experimentId), "实验不存在");
        Map<String, Object> member = db.requireRow(
                "SELECT em.id, p.person_name FROM experiment_members em LEFT JOIN persons p ON em.person_id = p.person_id WHERE em.experiment_id = :experiment_id AND em.person_id = :person_id",
                Maps.of("experiment_id", experimentId, "person_id", personId),
                "该人员不是实验成员"
        );
        if (db.count("SELECT COUNT(*) FROM experiment_members WHERE experiment_id = :id", Maps.of("id", experimentId)) <= 1) {
            throw new ApiException(400, "不能移除最后一个实验成员");
        }
        db.update("DELETE FROM experiment_members WHERE id = :id", Maps.of("id", member.get("id")));
        activityLog.log("experiment_member_remove", "从实验 " + experimentId + " 移除了成员 " + member.get("person_name"));
        return Maps.of("message", "成功移除成员 " + member.get("person_name"));
    }

    private Map<String, Object> experimentResponse(Object experimentId) {
        Map<String, Object> row = db.requireRow(
                "SELECT e.*, b.batch_number FROM experiments e LEFT JOIN batches b ON e.batch_id = b.batch_id WHERE e.experiment_id = :id",
                Maps.of("id", experimentId),
                "实验不存在"
        );
        row.put("members", members(experimentId));
        return row;
    }

    private List<Map<String, Object>> members(Object experimentId) {
        List<Map<String, Object>> rows = db.query(
                "SELECT em.id, em.experiment_id, em.person_id, p.person_name FROM experiment_members em LEFT JOIN persons p ON em.person_id = p.person_id WHERE em.experiment_id = :id ORDER BY em.id",
                Maps.of("id", experimentId)
        );
        return rows == null ? new ArrayList<Map<String, Object>>() : rows;
    }
}
