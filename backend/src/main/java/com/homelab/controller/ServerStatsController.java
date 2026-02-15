package com.homelab.controller;

import com.homelab.service.AgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ServerStatsController {

    private final AgentService agentService;

    public ServerStatsController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/server-stats")
    public Map<String, Object> getServerStats() {
        Map<String, Object> real = agentService.getServerStats();
        if (real != null && !real.containsKey("error")) {
            return real;
        }
        return Map.of(
                "error", true,
                "message", "Server agent unreachable. Check agent URL and that the agent is running."
        );
    }
}
