package com.homelab.controller;

import com.homelab.service.TailscaleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TailscaleController {

    private final TailscaleService tailscaleService;

    public TailscaleController(TailscaleService tailscaleService) {
        this.tailscaleService = tailscaleService;
    }

    @GetMapping("/tailscale-devices")
    public Map<String, Object> getTailscaleDevices() {
        Map<String, Object> real = tailscaleService.getDevices();
        if (real != null) {
            return real;
        }
        return Map.of(
                "error", true,
                "message", "Tailscale unavailable. Ensure Tailscale is installed and running."
        );
    }
}
