package com.homelab.controller;

import com.homelab.service.SpeedTestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SpeedTestController {

    private final SpeedTestService speedTestService;

    public SpeedTestController(SpeedTestService speedTestService) {
        this.speedTestService = speedTestService;
    }

    @GetMapping("/speed-test")
    public Map<String, Object> getSpeedTest() {
        Map<String, Object> result = speedTestService.getSpeedTest();
        if (result != null) {
            return result;
        }
        return Map.of(
                "error", true,
                "message", "Speed test unavailable. Install Ookla Speedtest CLI (speedtest -f json) on the server."
        );
    }
}
