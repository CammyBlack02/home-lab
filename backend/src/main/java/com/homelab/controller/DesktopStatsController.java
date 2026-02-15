package com.homelab.controller;

import com.homelab.service.AgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DesktopStatsController {

    private final AgentService agentService;

    public DesktopStatsController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/desktop-stats")
    public Map<String, Object> getDesktopStats() {
        Map<String, Object> real = agentService.getDesktopStats();
        if (real != null && !real.containsKey("error")) {
            return real;
        }
        return Map.of(
                "error", true,
                "message", "Desktop agent unreachable. Check agent URL and that the agent is running."
        );
    }
}
