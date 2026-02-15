package com.homelab.controller;

import com.homelab.service.UnifiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DevicesController {

    private final UnifiService unifiService;

    public DevicesController(UnifiService unifiService) {
        this.unifiService = unifiService;
    }

    @GetMapping("/devices")
    public Map<String, Object> getDevices() {
        Map<String, Object> real = unifiService.getDevices();
        if (real != null) {
            return real;
        }
        return Map.of(
                "error", true,
                "message", "UniFi unreachable. Enable UniFi in config and check base URL and credentials."
        );
    }
}
