package com.homelab.controller;

import com.homelab.service.GoveeService;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Control a Govee device (Control You Device API).
     * Body: { "device": "mac", "model": "H6089", "cmd": { "name": "turn", "value": "on" } }
     * turn: "on" | "off"; brightness: 0-100; color: { r, g, b }; colorTem: 2000-9000
     */
    @PostMapping("/govee-devices/control")
    public Map<String, Object> control(@RequestBody Map<String, Object> body) {
        String device = (String) body.get("device");
        String model = (String) body.get("model");
        Object cmd = body.get("cmd");
        if (device == null || model == null || !(cmd instanceof Map)) {
            return Map.of("error", true, "message", "Missing device, model, or cmd");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cmdMap = (Map<String, Object>) cmd;
        String name = (String) cmdMap.get("name");
        Object value = cmdMap.get("value");
        if (name == null) {
            return Map.of("error", true, "message", "Missing cmd.name");
        }
        Map<String, Object> result = goveeService.control(device, model, name, value);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Map.of("success", true);
        }
        return Map.of("error", true, "message", result.get("message") != null ? result.get("message") : "Control failed");
    }
}
