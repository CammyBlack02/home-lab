package com.homelab.controller;

import com.homelab.service.GoveeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GoveeController {

    private final GoveeService goveeService;

    public GoveeController(GoveeService goveeService) {
        this.goveeService = goveeService;
    }

    @GetMapping("/govee-devices")
    public Map<String, Object> getGoveeDevices() {
        Map<String, Object> result = goveeService.getDevices();
        if (result != null) {
            return result;
        }
        return Map.of(
                "error", true,
                "message", "Govee disabled. Set homelab.govee.enabled=true (and optionally API key for cloud, or use LAN discovery)."
        );
    }
}
