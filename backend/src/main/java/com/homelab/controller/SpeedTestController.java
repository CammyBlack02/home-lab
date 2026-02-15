package com.homelab.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SpeedTestController {

    @GetMapping("/speed-test")
    public Map<String, Object> getSpeedTest() {
        return Map.of(
                "download_mbps", 94.2,
                "upload_mbps", 22.1,
                "ping_ms", 12,
                "timestamp", System.currentTimeMillis()
        );
    }
}
