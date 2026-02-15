package com.homelab.service;

import com.homelab.config.HomelabProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UnifiService {

    private final HomelabProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public UnifiService(HomelabProperties properties) {
        this.properties = properties;
    }

    /**
     * Fetch clients from Unifi Controller. Returns null if Unifi is disabled or request fails.
     */
    public Map<String, Object> getDevices() {
        HomelabProperties.Unifi u = properties.getUnifi();
        if (!u.isEnabled() || u.getBaseUrl() == null || u.getBaseUrl().isBlank()
                || u.getUsername() == null || u.getPassword() == null) {
            return null;
        }
        String base = u.getBaseUrl().replaceAll("/$", "");
        String loginPath = u.isUseUnifiOs() ? "/proxy/network/api/auth/login" : "/api/login";
        String clientsPath = u.isUseUnifiOs() ? "/proxy/network/api/s/default/stat/sta" : "/api/s/default/stat/sta";

        try {
            HttpHeaders loginHeaders = new HttpHeaders();
            loginHeaders.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("username", u.getUsername(), "password", u.getPassword());
            ResponseEntity<Map> loginResp = restTemplate.exchange(
                    base + loginPath,
                    HttpMethod.POST,
                    new HttpEntity<>(body, loginHeaders),
                    Map.class
            );
            List<String> cookies = loginResp.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (cookies == null || cookies.isEmpty()) {
                return null;
            }
            String cookieHeader = cookies.stream().collect(Collectors.joining("; "));

            HttpHeaders getHeaders = new HttpHeaders();
            getHeaders.set(HttpHeaders.COOKIE, cookieHeader);
            ResponseEntity<Map> clientsResp = restTemplate.exchange(
                    base + clientsPath,
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders),
                    Map.class
            );
            Map<String, Object> data = clientsResp.getBody();
            if (data == null || !(data.get("data") instanceof List)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raw = (List<Map<String, Object>>) data.get("data");
            List<Map<String, String>> devices = raw.stream()
                    .map(this::toDevice)
                    .filter(Objects::nonNull)
                    .toList();
            return Map.<String, Object>of(
                    "devices", devices,
                    "total", devices.size(),
                    "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> toDevice(Map<String, Object> raw) {
        try {
            String mac = raw.get("mac") != null ? raw.get("mac").toString() : "";
            String ip = raw.get("ip") != null ? raw.get("ip").toString() : "";
            Object nameObj = raw.get("hostname");
            if (nameObj == null) nameObj = raw.get("name");
            String name = nameObj != null ? nameObj.toString() : (mac.isEmpty() ? "Unknown" : mac);
            Object lastSeen = raw.get("last_seen");
            long nowSec = System.currentTimeMillis() / 1000;
            boolean online = false;
            if (lastSeen instanceof Number) {
                long seen = ((Number) lastSeen).longValue();
                online = (nowSec - seen) < 300; // within 5 min
            }
            String status = online ? "online" : "offline";
            return Map.of("name", name, "ip", ip, "mac", mac, "status", status);
        } catch (Exception e) {
            return null;
        }
    }
}
