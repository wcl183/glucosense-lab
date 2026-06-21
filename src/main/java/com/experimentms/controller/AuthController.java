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
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String[] MODULES = new String[]{
            "batch_management",
            "person_management",
            "experiment_management",
            "competitor_data",
            "finger_blood_data",
            "sensor_data",
            "sensor_details",
            "wear_records",
            "experiment_data_analysis",
            "sensor_data_visualization"
    };

    private final Db db;
    private final AuthService auth;

    public AuthController(Db db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = Payload.stringValue(body, "username");
        String password = Payload.stringValue(body, "password");
        Map<String, Object> user = auth.authenticate(username, password);
        return tokenResponse(user);
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String username = Payload.stringValue(body, "username");
        String password = Payload.stringValue(body, "password");
        if (username == null || username.trim().isEmpty()) {
            throw new ApiException(400, "用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new ApiException(400, "密码长度至少6位");
        }
        if (db.count("SELECT COUNT(*) FROM users WHERE username = :username", Maps.of("username", username)) > 0) {
            throw new ApiException(400, "用户名已存在");
        }
        Number id = db.insert("users", Maps.of(
                "username", username,
                "password_hash", auth.hash(password),
                "role", "User",
                "createTime", Timestamp.valueOf(LocalDateTime.now()),
                "updateTime", Timestamp.valueOf(LocalDateTime.now())
        ), "username", "password_hash", "role", "createTime", "updateTime");

        for (String module : MODULES) {
            db.insert("user_permissions", Maps.of(
                    "user_id", id,
                    "module", module,
                    "can_read", true,
                    "can_write", false,
                    "can_delete", false
            ), "user_id", "module", "can_read", "can_write", "can_delete");
        }

        Map<String, Object> user = db.requireRow(
                "SELECT user_id, username, role, createTime, updateTime FROM users WHERE user_id = :id",
                Maps.of("id", id),
                "用户不存在"
        );
        return tokenResponse(user);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Map<String, Object> user = auth.currentUser(request);
        user.put("permissions", db.query(
                "SELECT permission_id, user_id, module, can_read, can_write, can_delete FROM user_permissions WHERE user_id = :user_id",
                Maps.of("user_id", user.get("user_id"))
        ));
        return user;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        return Maps.of("message", "登出成功");
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(HttpServletRequest request) {
        auth.requireAdmin(request);
        return db.query("SELECT user_id, username, role, createTime, updateTime FROM users ORDER BY user_id", null);
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.requireAdmin(request);
        String username = Payload.stringValue(body, "username");
        if (db.count("SELECT COUNT(*) FROM users WHERE username = :username", Maps.of("username", username)) > 0) {
            throw new ApiException(400, "用户名已存在");
        }
        Number id = db.insert("users", Maps.of(
                "username", username,
                "password_hash", auth.hash(Payload.stringValue(body, "password")),
                "role", Payload.stringValue(body, "role") == null ? "User" : Payload.stringValue(body, "role"),
                "createTime", Timestamp.valueOf(LocalDateTime.now()),
                "updateTime", Timestamp.valueOf(LocalDateTime.now())
        ), "username", "password_hash", "role", "createTime", "updateTime");
        return db.requireRow("SELECT user_id, username, role, createTime, updateTime FROM users WHERE user_id = :id", Maps.of("id", id), "用户不存在");
    }

    @PutMapping("/users/{userId}")
    public Map<String, Object> updateUser(@PathVariable("userId") int userId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.requireAdmin(request);
        db.requireRow("SELECT user_id FROM users WHERE user_id = :id", Maps.of("id", userId), "用户不存在");
        if (body.containsKey("username")) {
            String username = Payload.stringValue(body, "username");
            if (db.count("SELECT COUNT(*) FROM users WHERE username = :username AND user_id <> :id", Maps.of("username", username, "id", userId)) > 0) {
                throw new ApiException(400, "用户名已存在");
            }
        }
        Map<String, Object> data = Payload.pick(body, "username", "role");
        if (body.containsKey("password")) {
            data.put("password_hash", auth.hash(Payload.stringValue(body, "password")));
        }
        data.put("updateTime", Timestamp.valueOf(LocalDateTime.now()));
        db.updateById("users", "user_id", userId, data, "username", "password_hash", "role", "updateTime");
        return db.requireRow("SELECT user_id, username, role, createTime, updateTime FROM users WHERE user_id = :id", Maps.of("id", userId), "用户不存在");
    }

    @DeleteMapping("/users/{userId}")
    public Map<String, Object> deleteUser(@PathVariable("userId") int userId, HttpServletRequest request) {
        auth.requireAdmin(request);
        Map<String, Object> current = auth.currentUser(request);
        if (String.valueOf(current.get("user_id")).equals(String.valueOf(userId))) {
            throw new ApiException(400, "不能删除自己的账户");
        }
        db.update("DELETE FROM user_permissions WHERE user_id = :id", Maps.of("id", userId));
        int deleted = db.update("DELETE FROM users WHERE user_id = :id", Maps.of("id", userId));
        if (deleted == 0) {
            throw new ApiException(404, "用户不存在");
        }
        return Maps.of("message", "用户删除成功");
    }

    @GetMapping("/users/{userId}/permissions")
    public List<Map<String, Object>> permissions(@PathVariable("userId") int userId, HttpServletRequest request) {
        auth.requireAdmin(request);
        return db.query("SELECT permission_id, user_id, module, can_read, can_write, can_delete FROM user_permissions WHERE user_id = :id", Maps.of("id", userId));
    }

    @PostMapping("/assign-permissions")
    @SuppressWarnings("unchecked")
    public Map<String, Object> assignPermissions(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.requireAdmin(request);
        int userId = Payload.requiredInt(body, "user_id");
        db.requireRow("SELECT user_id FROM users WHERE user_id = :id", Maps.of("id", userId), "用户不存在");
        db.update("DELETE FROM user_permissions WHERE user_id = :id", Maps.of("id", userId));
        Object permissions = body.get("permissions");
        if (permissions instanceof List) {
            for (Object item : (List<Object>) permissions) {
                Map<String, Object> perm = (Map<String, Object>) item;
                db.insert("user_permissions", Maps.of(
                        "user_id", userId,
                        "module", perm.get("module"),
                        "can_read", perm.get("can_read"),
                        "can_write", perm.get("can_write"),
                        "can_delete", perm.get("can_delete")
                ), "user_id", "module", "can_read", "can_write", "can_delete");
            }
        }
        db.update("UPDATE users SET updateTime = :time WHERE user_id = :id", Maps.of("time", Timestamp.valueOf(LocalDateTime.now()), "id", userId));
        return Maps.of("message", "权限分配成功");
    }

    private Map<String, Object> tokenResponse(Map<String, Object> user) {
        return Maps.of(
                "access_token", auth.tokenFor(String.valueOf(user.get("username"))),
                "token_type", "bearer",
                "user_info", Maps.of(
                        "user_id", user.get("user_id"),
                        "username", user.get("username"),
                        "role", user.get("role")
                )
        );
    }
}
