package com.homelab.service;

import com.homelab.config.HomelabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Fetches device list from Govee Developer API (lights/plugs and appliances).
 * Requires API key from Govee Home App (Profile → Settings → Apply for API Key).
 */
@Service
public class GoveeService {

    private static final Logger log = LoggerFactory.getLogger(GoveeService.class);
    private static final String LIGHTS_URL = "https://developer-api.govee.com/v1/devices";
    private static final String APPLIANCES_URL = "https://developer-api.govee.com/v1/appliance/devices";

    private final HomelabProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public GoveeService(HomelabProperties properties) {
        this.properties = properties;
    }

    /**
     * Fetch all Govee devices (lights/plugs + appliances). Returns null if disabled, no API key, or request fails.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDevices() {
        HomelabProperties.Govee g = properties.getGovee();
        if (!g.isEnabled() || g.getApiKey() == null || g.getApiKey().isBlank()) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Govee-API-Key", g.getApiKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        List<Map<String, Object>> allDevices = new ArrayList<>();

        try {
            List<Map<String, Object>> lights = fetchDevices(LIGHTS_URL, entity, "light");
            if (lights != null) allDevices.addAll(lights);

            List<Map<String, Object>> appliances = fetchDevices(APPLIANCES_URL, entity, "appliance");
            if (appliances != null) allDevices.addAll(appliances);
        } catch (Exception e) {
            log.warn("Govee API failed: {}", e.getMessage());
            return null;
        }

        return Map.of(
                "devices", allDevices,
                "total", allDevices.size(),
                "timestamp", System.currentTimeMillis()
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchDevices(String url, HttpEntity<Void> entity, String type) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !Integer.valueOf(200).equals(body.get("code"))) {
                return Collections.emptyList();
            }
            Object data = body.get("data");
            if (!(data instanceof Map)) return Collections.emptyList();
            Object devices = ((Map<?, ?>) data).get("devices");
            if (!(devices instanceof List)) return Collections.emptyList();

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
