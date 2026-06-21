package com.experimentms.service;

import com.experimentms.util.Maps;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class ActivityLogService {
    private final Db db;

    public ActivityLogService(Db db) {
        this.db = db;
    }

    public void log(String type, String description) {
        log(type, description, null);
    }

    public void log(String type, String description, Object userId) {
        db.insert("activities", Maps.of(
                "activity_type", type,
                "description", description,
                "user_id", userId,
                "createTime", Timestamp.valueOf(LocalDateTime.now())
        ), "activity_type", "description", "createTime", "user_id");
    }
}
