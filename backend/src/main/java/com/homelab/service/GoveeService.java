package com.homelab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homelab.config.HomelabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fetches Govee devices: cloud API (if API key set) and/or LAN discovery (UDP multicast per Govee WLAN guide).
 */
@Service
public class GoveeService {

    private static final Logger log = LoggerFactory.getLogger(GoveeService.class);
    private static final String LIGHTS_URL = "https://developer-api.govee.com/v1/devices";
    private static final String APPLIANCES_URL = "https://developer-api.govee.com/v1/appliance/devices";
    private static final String LAN_MULTICAST = "239.255.255.250";
    private static final int LAN_MULTICAST_PORT = 4001;
    private static final int LAN_LISTEN_PORT = 4002;
    private static final String LAN_SCAN_JSON = "{\"msg\":{\"cmd\":\"scan\",\"data\":{\"account_topic\":\"reserve\"}}}";
    private static final int LAN_RECEIVE_TIMEOUT_MS = 5000;

    private final HomelabProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoveeService(HomelabProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void logConfig() {
        HomelabProperties.Govee g = properties.getGovee();
        if (!g.isEnabled()) {
            log.info("Govee: disabled (set homelab.govee.enabled=true to enable cloud and/or LAN discovery)");
            return;
        }
        boolean cloud = g.getApiKey() != null && !g.getApiKey().isBlank();
        log.info("Govee: enabled (cloud={}, LAN discovery={})", cloud, g.isLanDiscoveryEnabled());
    }

    /**
     * Fetch all Govee devices: cloud (if API key set) + LAN discovery (if enabled). Returns null only if Govee is disabled.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDevices() {
        HomelabProperties.Govee g = properties.getGovee();
        if (!g.isEnabled()) {
            return null;
        }

        List<Map<String, Object>> allDevices = new ArrayList<>();
        Set<String> seenDeviceIds = new HashSet<>();

        // Cloud API
        if (g.getApiKey() != null && !g.getApiKey().isBlank()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Govee-API-Key", g.getApiKey());
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                List<Map<String, Object>> lights = fetchDevicesCloud(LIGHTS_URL, entity, "light");
                if (lights != null) for (Map<String, Object> d : lights) { addIfNew(d, allDevices, seenDeviceIds); }
                List<Map<String, Object>> appliances = fetchDevicesCloud(APPLIANCES_URL, entity, "appliance");
                if (appliances != null) for (Map<String, Object> d : appliances) { addIfNew(d, allDevices, seenDeviceIds); }
            } catch (Exception e) {
                log.warn("Govee cloud API failed: {}", e.getMessage());
            }
        }

        // LAN discovery (same network, LAN enabled in Govee app)
        if (g.isLanDiscoveryEnabled()) {
            try {
                List<Map<String, Object>> lanDevices = discoverLanDevices();
                for (Map<String, Object> d : lanDevices) addIfNew(d, allDevices, seenDeviceIds);
            } catch (Exception e) {
                log.debug("Govee LAN discovery failed: {}", e.getMessage());
            }
        }

        log.info("Govee: {} devices total", allDevices.size());
        return Map.of(
                "devices", allDevices,
                "total", allDevices.size(),
                "timestamp", System.currentTimeMillis()
        );
    }

    private void addIfNew(Map<String, Object> device, List<Map<String, Object>> list, Set<String> seen) {
        String id = (String) device.get("device");
        if (id != null && !id.isBlank() && seen.add(id)) list.add(device);
        else if (id == null || id.isBlank()) list.add(device);
    }

    /**
     * Discover Govee devices on LAN via UDP multicast (port 4001). Devices must have LAN enabled in Govee Home app.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> discoverLanDevices() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket(LAN_LISTEN_PORT)) {
            socket.setSoTimeout(LAN_RECEIVE_TIMEOUT_MS);
            byte[] sendBuf = LAN_SCAN_JSON.getBytes(StandardCharsets.UTF_8);
            DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, InetAddress.getByName(LAN_MULTICAST), LAN_MULTICAST_PORT);
            socket.send(sendPacket);

            byte[] recvBuf = new byte[1024];
            long deadline = System.currentTimeMillis() + LAN_RECEIVE_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(recvPacket);
                    String json = new String(recvPacket.getData(), 0, recvPacket.getLength(), StandardCharsets.UTF_8);
                    Map<String, Object> root = objectMapper.readValue(json, Map.class);
                    Object msg = root.get("msg");
                    if (!(msg instanceof Map)) continue;
                    Map<String, Object> msgMap = (Map<String, Object>) msg;
                    if (!"scan".equals(msgMap.get("cmd"))) continue;
                    Object data = msgMap.get("data");
                    if (!(data instanceof Map)) continue;
                    Map<String, Object> dataMap = (Map<String, Object>) data;
                    String ip = (String) dataMap.get("ip");
                    String device = (String) dataMap.get("device");
                    String sku = (String) dataMap.get("sku");
                    String name = sku != null ? sku : (ip != null ? ip : "Govee (LAN)");
                    out.add(Map.<String, Object>of(
                            "device", device != null ? device : "",
                            "model", sku != null ? sku : "",
                            "name", name,
                            "type", "lan",
                            "ip", ip != null ? ip : "",
                            "controllable", true,
                            "supportCmds", List.of()
                    ));
                } catch (java.net.SocketTimeoutException e) {
                    break;
                }
            }
        }
        return out;
    }

    private static boolean isCode200(Object code) {
        if (code == null) return false;
        if (code instanceof Number) return ((Number) code).intValue() == 200;
        if (code instanceof String) return "200".equals(code);
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchDevicesCloud(String url, HttpEntity<Void> entity, String type) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                log.warn("Govee {}: empty response body", type);
                return Collections.emptyList();
            }
            if (!isCode200(body.get("code"))) {
                log.warn("Govee {}: code={}, message={}", type, body.get("code"), body.get("message"));
                return Collections.emptyList();
            }
            Object data = body.get("data");
            if (!(data instanceof Map)) {
                log.warn("Govee {}: no data object", type);
                return Collections.emptyList();
            }
            Object devices = ((Map<?, ?>) data).get("devices");
            if (!(devices instanceof List)) {
                log.warn("Govee {}: no devices array (data keys: {})", type, data instanceof Map ? ((Map<?, ?>) data).keySet() : "?");
                return Collections.emptyList();
            }

            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : (List<?>) devices) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> dev = (Map<String, Object>) o;
                String device = (String) dev.get("device");
                String model = (String) dev.get("model");
                String name = (String) dev.get("deviceName");
                Boolean controllable = (Boolean) dev.get("controllable");
                Object supportCmds = dev.get("supportCmds");
                out.add(Map.of(
                        "device", device != null ? device : "",
                        "model", model != null ? model : "",
                        "name", name != null ? name : (model != null ? model : "—"),
                        "type", type,
                        "controllable", Boolean.TRUE.equals(controllable),
                        "supportCmds", supportCmds instanceof List ? supportCmds : List.of()
                ));
            }
            return out;
        } catch (Exception e) {
            log.debug("Govee {} fetch failed: {}", type, e.getMessage());
            return Collections.emptyList();
        }
    }
}
