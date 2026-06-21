package com.experimentms.controller;

import com.experimentms.security.AuthService;
import com.experimentms.service.ActivityLogService;
import com.experimentms.service.Db;
import com.experimentms.util.Maps;
import com.experimentms.util.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
public class ActivityStatsController {
    private final Db db;
    private final AuthService auth;
    private final ActivityLogService activityLog;

    public ActivityStatsController(Db db, AuthService auth, ActivityLogService activityLog) {
        this.db = db;
        this.auth = auth;
        this.activityLog = activityLog;
    }

    @GetMapping("/api/activities/")
    public List<Map<String, Object>> activities(@RequestParam(defaultValue = "10") int limit,
                                                @RequestParam(defaultValue = "0") int skip,
                                                HttpServletRequest request) {
        auth.currentUser(request);
        int cappedLimit = Math.min(limit, 50);
        return db.query(
                "SELECT a.activity_id, a.activity_type, a.description, a.createTime, a.user_id, u.username FROM activities a LEFT JOIN users u ON a.user_id = u.user_id ORDER BY a.createTime DESC LIMIT :limit OFFSET :skip",
                Maps.of("limit", cappedLimit, "skip", skip)
        );
    }

    @PostMapping("/api/activities/")
    public Map<String, Object> createActivity(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.currentUser(request);
        activityLog.log(Payload.stringValue(body, "activity_type"), Payload.stringValue(body, "description"), body.get("user_id"));
        return db.requireRow(
                "SELECT a.activity_id, a.activity_type, a.description, a.createTime, a.user_id, u.username FROM activities a LEFT JOIN users u ON a.user_id = u.user_id ORDER BY a.activity_id DESC LIMIT 1",
                null,
                "活动不存在"
        );
    }

    @GetMapping("/api/stats/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest request) {
        auth.require(request, "batch_management", "read");
        return Maps.of(
                "batches_count", db.count("SELECT COUNT(*) FROM batches", null),
                "persons_count", db.count("SELECT COUNT(*) FROM persons", null),
                "experiments_count", db.count("SELECT COUNT(*) FROM experiments", null),
                "finger_blood_data_count", db.count("SELECT COUNT(*) FROM finger_blood_files", null)
        );
    }
}
