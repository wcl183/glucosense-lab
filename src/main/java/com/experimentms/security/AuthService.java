package com.experimentms.security;

import com.experimentms.exception.ApiException;
import com.experimentms.service.Db;
import com.experimentms.util.Maps;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Service
public class AuthService {
    private final Db db;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(Db db, JwtService jwtService) {
        this.db = db;
        this.jwtService = jwtService;
    }

    public Map<String, Object> authenticate(String username, String password) {
        Map<String, Object> user = db.row(
                "SELECT user_id, username, password_hash, role, createTime, updateTime FROM users WHERE username = :username",
                Maps.of("username", username)
        );
        if (user == null || !passwordEncoder.matches(password, String.valueOf(user.get("password_hash")))) {
            throw new ApiException(401, "用户名或密码错误");
        }
        return user;
    }

    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    public String tokenFor(String username) {
        return jwtService.createToken(username);
    }

    public Map<String, Object> loadUserByToken(String token) {
        String username = jwtService.parseSubject(token);
        Map<String, Object> user = db.row(
                "SELECT user_id, username, role, createTime, updateTime FROM users WHERE username = :username",
                Maps.of("username", username)
        );
        if (user == null) {
            throw new ApiException(401, "Could not validate credentials");
        }
        return user;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> currentUser(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (!(user instanceof Map)) {
            throw new ApiException(401, "Could not validate credentials");
        }
        return (Map<String, Object>) user;
    }

    public Map<String, Object> require(HttpServletRequest request, String module, String permissionType) {
        Map<String, Object> user = currentUser(request);
        if ("Admin".equals(String.valueOf(user.get("role")))) {
            return user;
        }
        Map<String, Object> permission = db.row(
                "SELECT can_read, can_write, can_delete FROM user_permissions WHERE user_id = :user_id AND module = :module",
                Maps.of("user_id", user.get("user_id"), "module", module)
        );
        if (permission == null) {
            throw new ApiException(403, "没有访问 " + module + " 模块的权限");
        }
        Object allowed;
        if ("write".equals(permissionType)) {
            allowed = permission.get("can_write");
        } else if ("delete".equals(permissionType)) {
            allowed = permission.get("can_delete");
        } else {
            allowed = permission.get("can_read");
        }
        if (!truthy(allowed)) {
            throw new ApiException(403, "没有" + permissionType + " " + module + " 模块的权限");
        }
        return user;
    }

    public void requireAdmin(HttpServletRequest request) {
        Map<String, Object> user = currentUser(request);
        if (!"Admin".equals(String.valueOf(user.get("role")))) {
            throw new ApiException(403, "需要管理员权限");
        }
    }

    private boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }
}
