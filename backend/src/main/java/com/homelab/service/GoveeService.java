package com.homelab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homelab.config.HomelabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.UUID;

/**
 * Fetches Govee devices: cloud API (if API key set) and/or LAN discovery (UDP multicast per Govee WLAN guide).
 */
@Service
public class GoveeService {

    private static final Logger log = LoggerFactory.getLogger(GoveeService.class);
    /** Current Govee Open API – single call for all devices and capabilities. */
    private static final String OPENAPI_DEVICES_URL = "https://openapi.api.govee.com/router/api/v1/user/devices";
    /** Control You Device – https://developer.govee.com/reference/control-you-devices */
    private static final String OPENAPI_CONTROL_URL = "https://openapi.api.govee.com/router/api/v1/device/control";
    /** Legacy endpoints (fallback if openapi fails). */
    private static final String LEGACY_LIGHTS_URL = "https://developer-api.govee.com/v1/devices";
    private static final String LEGACY_APPLIANCES_URL = "https://developer-api.govee.com/v1/appliance/devices";
    private static final String LEGACY_CONTROL_URL = "https://developer-api.govee.com/v1/devices/control";
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

        // Cloud API (try current Open API first, then legacy)
        if (g.getApiKey() != null && !g.getApiKey().isBlank()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Govee-API-Key", g.getApiKey());
                headers.set("Content-Type", "application/json");
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                List<Map<String, Object>> cloud = fetchDevicesCloud(OPENAPI_DEVICES_URL, entity, "cloud");
                if (cloud == null || cloud.isEmpty()) {
                    List<Map<String, Object>> lights = fetchDevicesCloud(LEGACY_LIGHTS_URL, entity, "light");
                    if (lights != null) for (Map<String, Object> d : lights) addIfNew(d, allDevices, seenDeviceIds);
                    List<Map<String, Object>> appliances = fetchDevicesCloud(LEGACY_APPLIANCES_URL, entity, "appliance");
                    if (appliances != null) for (Map<String, Object> d : appliances) addIfNew(d, allDevices, seenDeviceIds);
                } else {
                    for (Map<String, Object> d : cloud) addIfNew(d, allDevices, seenDeviceIds);
                }
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
     * Send control command to a Govee device (Control You Device API).
     * Open API format: POST with requestId + payload (sku, device, capability: type/instance/value).
     * See https://developer.govee.com/reference/control-you-devices – on_off uses devices.capabilities.on_off, powerSwitch, value 0|1.
     * Falls back to legacy (PUT device/model/cmd) if Open API fails.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> control(String device, String model, String cmdName, Object cmdValue) {
        Map<String, Object> out = new HashMap<>();
        out.put("success", false);
        HomelabProperties.Govee g = properties.getGovee();
        if (!g.isEnabled() || g.getApiKey() == null || g.getApiKey().isBlank()) {
            out.put("message", "Govee disabled or no API key");
            return out;
        }
        if (device == null || device.isBlank() || model == null || model.isBlank() || cmdName == null || cmdName.isBlank()) {
            out.put("message", "Missing device, model, or command");
            return out;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Govee-API-Key", g.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        String lastMessage = null;

        // Open API: POST with requestId + payload (sku, device, capability)
        if ("turn".equals(cmdName)) {
            int value = "on".equals(String.valueOf(cmdValue).toLowerCase()) ? 1 : 0;
            Map<String, Object> capability = new HashMap<>();
            capability.put("type", "devices.capabilities.on_off");
            capability.put("instance", "powerSwitch");
            capability.put("value", value);
            Map<String, Object> payload = new HashMap<>();
            payload.put("sku", model);
            payload.put("device", device);
            payload.put("capability", capability);
            Map<String, Object> openBody = new HashMap<>();
            openBody.put("requestId", UUID.randomUUID().toString());
            openBody.put("payload", payload);
            HttpEntity<Map<String, Object>> openEntity = new HttpEntity<>(openBody, headers);
            try {
                ResponseEntity<Map> response = restTemplate.exchange(OPENAPI_CONTROL_URL, HttpMethod.POST, openEntity, Map.class);
                Map<String, Object> res = response.getBody();
                if (res != null && isCode200(res.get("code"))) {
                    out.put("success", true);
                    return out;
                }
                if (res != null) {
                    lastMessage = String.valueOf(res.get("message"));
                    log.warn("Govee control (openapi): code={}, message={}", res.get("code"), lastMessage);
                }
            } catch (Exception e) {
                lastMessage = e.getMessage();
                log.warn("Govee control (openapi) failed: {}", lastMessage);
            }
        }

        // Legacy: PUT with device, model, cmd (name/value)
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("name", cmdName);
        cmd.put("value", cmdValue != null ? cmdValue : "on");
        Map<String, Object> legacyBody = new HashMap<>();
        legacyBody.put("device", device);
        legacyBody.put("model", model);
        legacyBody.put("cmd", cmd);
        HttpEntity<Map<String, Object>> legacyEntity = new HttpEntity<>(legacyBody, headers);
        try {
            ResponseEntity<Map> legacy = restTemplate.exchange(LEGACY_CONTROL_URL, HttpMethod.PUT, legacyEntity, Map.class);
            Map<String, Object> leg = legacy.getBody();
            if (leg != null && isCode200(leg.get("code"))) {
                out.put("success", true);
                return out;
            }
            if (leg != null) {
                lastMessage = String.valueOf(leg.get("message"));
                log.warn("Govee control (legacy) failed: code={}, message={}", leg.get("code"), lastMessage);
            }
        } catch (Exception e) {
            if (lastMessage == null) lastMessage = e.getMessage();
            log.warn("Govee control (legacy) failed: {}", e.getMessage());
        }
        out.put("message", lastMessage != null && !lastMessage.isEmpty() ? lastMessage : "Control failed (check API key and device support)");
        return out;
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
            if (!out.isEmpty()) {
                log.info("Govee LAN: found {} device(s)", out.size());
            }
        } catch (Exception e) {
            log.warn("Govee LAN discovery failed: {} (is port {} in use? Same network as devices?)", e.getMessage(), LAN_LISTEN_PORT);
            return Collections.emptyList();
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
            Object devices = null;
            if (data instanceof Map) {
                devices = ((Map<?, ?>) data).get("devices");
                if (devices == null) devices = ((Map<?, ?>) data).get("deviceList");
            } else if (data instanceof List) {
                devices = data;
            }
            if (devices == null && body.containsKey("devices")) {
                devices = body.get("devices");
            }
            if (!(devices instanceof List)) {
                log.warn("Govee {}: no devices array (body keys: {}, data type: {})", type, body.keySet(), data == null ? "null" : data.getClass().getSimpleName());
                return Collections.emptyList();
            }

            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : (List<?>) devices) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> dev = (Map<String, Object>) o;
                String device = (String) dev.get("device");
                String model = (String) dev.get("model");
                String name = (String) dev.get("deviceName");
                Object supportCmds = dev.get("supportCmds");
                Object capabilities = dev.get("capabilities");
                boolean controllable = isControllable(dev.get("controllable"), supportCmds, capabilities, type, device, model);
                out.add(Map.of(
                        "device", device != null ? device : "",
                        "model", model != null ? model : "",
                        "name", name != null ? name : (model != null ? model : "—"),
                        "type", type,
                        "controllable", controllable,
                        "supportCmds", supportCmds instanceof List ? supportCmds : List.of()
                ));
            }
            return out;
        } catch (Exception e) {
            log.debug("Govee {} fetch failed: {}", type, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** True if API says controllable, has supportCmds/capabilities, or (cloud only) has device+model so we can call control API. */
    private static boolean isControllable(Object controllable, Object supportCmds, Object capabilities, String type, String device, String model) {
        if (Boolean.TRUE.equals(controllable)) return true;
        if (controllable instanceof String && "true".equalsIgnoreCase((String) controllable)) return true;
        if (supportCmds instanceof List && !((List<?>) supportCmds).isEmpty()) return true;
        if (capabilities instanceof List && !((List<?>) capabilities).isEmpty()) return true;
        if (capabilities instanceof Map && !((Map<?, ?>) capabilities).isEmpty()) return true;
        // Open API devices with device+model can be controlled via Control You Device API
        if ("cloud".equals(type) && device != null && !device.isBlank() && model != null && !model.isBlank()) return true;
        return false;
    }
}
