package com.experimentms.controller;

import com.experimentms.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {
    @GetMapping("/")
    public Map<String, Object> root() {
        return Maps.of(
                "message", "实验数据管理系统 API",
                "version", "1.0.0",
                "docs", "/swagger-ui.html"
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Maps.of("status", "healthy");
    }
}
