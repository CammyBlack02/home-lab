package com.homelab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@Service
public class TailscaleService {

    private static final Logger log = LoggerFactory.getLogger(TailscaleService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fetch Tailscale devices using `tailscale status --json`.
     * Returns null if Tailscale is not available or command fails.
     */
    public Map<String, Object> getDevices() {
        try {
            Process process = new ProcessBuilder("tailscale", "status", "--json")
                    .redirectErrorStream(true)
                    .start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Tailscale status command failed with exit code: {}", exitCode);
                return null;
            }
            
            JsonNode root = objectMapper.readTree(output.toString());
            List<Map<String, String>> devices = new ArrayList<>();
            
            // Parse Self (current device)
            if (root.has("Self")) {
                JsonNode self = root.get("Self");
                String dnsName = self.path("DNSName").asText("");
                if (dnsName.isEmpty()) {
                    // Fallback to hostname if DNSName is empty
                    dnsName = self.path("HostName").asText("This device");
                }
                boolean online = self.path("Online").asBoolean(true);
                String ip = "";
                if (self.has("TailscaleIPs") && self.get("TailscaleIPs").isArray() && self.get("TailscaleIPs").size() > 0) {
                    ip = self.get("TailscaleIPs").get(0).asText("");
                }
                devices.add(createDeviceMap(dnsName, online, ip, "self"));
            }
            
            // Parse Peers
            if (root.has("Peer")) {
                root.get("Peer").fields().forEachRemaining(entry -> {
                    JsonNode peer = entry.getValue();
                    String dnsName = peer.path("DNSName").asText("");
                    if (dnsName.isEmpty()) {
                        dnsName = peer.path("HostName").asText("Unknown");
                    }
                    boolean online = peer.path("Online").asBoolean(false);
                    String ip = "";
                    if (peer.has("TailscaleIPs") && peer.get("TailscaleIPs").isArray() && peer.get("TailscaleIPs").size() > 0) {
                        ip = peer.get("TailscaleIPs").get(0).asText("");
                    }
                    devices.add(createDeviceMap(dnsName, online, ip, "peer"));
                });
            }
            
            return Map.of(
                "devices", devices,
                "total", devices.size(),
                "timestamp", System.currentTimeMillis()
            );
            
        } catch (Exception e) {
            log.warn("Failed to fetch Tailscale devices: {}", e.getMessage());
            return null;
        }
    }
    
    private Map<String, String> createDeviceMap(String dnsName, boolean online, String ip, String type) {
        Map<String, String> device = new HashMap<>();
        device.put("name", dnsName.isEmpty() ? "Unknown" : dnsName);
        device.put("ip", ip);
        device.put("status", online ? "online" : "offline");
        device.put("type", type);
        return device;
    }
}
