package com.discordstrava.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/healthz")
    Map<String, String> healthz() {
        return Map.of("status", "ok");
    }
}
