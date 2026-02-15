package com.homelab.controller;

import com.homelab.service.UnifiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
        List<Map<String, String>> devices = List.of(
                Map.of("name", "Bazzite Desktop", "ip", "192.168.1.10", "mac", "aa:bb:cc:dd:ee:01", "status", "online"),
                Map.of("name", "MacBook Pro", "ip", "192.168.1.20", "mac", "aa:bb:cc:dd:ee:02", "status", "offline"),
                Map.of("name", "HP Stream 7", "ip", "192.168.1.30", "mac", "aa:bb:cc:dd:ee:03", "status", "online"),
                Map.of("name", "iPhone", "ip", "192.168.1.40", "mac", "aa:bb:cc:dd:ee:04", "status", "offline")
        );
        return Map.of(
                "devices", devices,
                "total", devices.size(),
                "timestamp", System.currentTimeMillis()
        );
    }
}
