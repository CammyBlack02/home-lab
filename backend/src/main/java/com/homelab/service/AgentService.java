package com.homelab.service;

import com.homelab.config.HomelabProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AgentService {

    private final HomelabProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public AgentService(HomelabProperties properties) {
        this.properties = properties;
    }

    /**
     * Fetch server stats from the server agent. Returns null if URL not set or request fails.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getServerStats() {
        String url = properties.getServerAgentUrl();
        if (url == null || url.isBlank()) return null;
        try {
            return restTemplate.getForObject(url + "/stats", Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetch desktop stats from the desktop agent. Returns null if URL not set or request fails.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDesktopStats() {
        String url = properties.getDesktopAgentUrl();
        if (url == null || url.isBlank()) return null;
        try {
            return restTemplate.getForObject(url + "/stats", Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
