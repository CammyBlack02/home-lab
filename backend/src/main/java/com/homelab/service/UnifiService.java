package com.homelab.service;

import com.homelab.config.HomelabProperties;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UnifiService {

    private static final Logger log = LoggerFactory.getLogger(UnifiService.class);
    private static final long SESSION_CACHE_MS = 8 * 60 * 1000; // 8 minutes – avoid login on every poll / rate limit

    private final HomelabProperties properties;
    private final RestTemplate restTemplate;

    private volatile String cachedCookie;
    private volatile String cachedCsrf;
    private volatile long cacheExpiresAt;

    public UnifiService(HomelabProperties properties) {
        this.properties = properties;
        this.restTemplate = createUnifiRestTemplate();
    }

    /** RestTemplate that accepts self-signed certs (for local Unifi controller). Use only for internal homelab. */
    private static RestTemplate createUnifiRestTemplate() {
        try {
            SSLContext ssl = SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(ssl, NoopHostnameVerifier.INSTANCE);
            HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(cm)
                    .build();
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            return new RestTemplate(factory);
        } catch (Exception e) {
            return new RestTemplate();
        }
    }

    /**
     * Fetch clients from Unifi Controller. Returns null if Unifi is disabled or request fails.
     */
    public Map<String, Object> getDevices() {
        HomelabProperties.Unifi u = properties.getUnifi();
        if (!u.isEnabled() || u.getBaseUrl() == null || u.getBaseUrl().isBlank()
                || u.getUsername() == null || u.getUsername().isBlank()
                || u.getPassword() == null || u.getPassword().isBlank()) {
            log.warn("UniFi skipped: enabled={}, baseUrl set={}. Create application-local.yml with unifi config.", u.isEnabled(), u.getBaseUrl() != null && !u.getBaseUrl().isBlank());
            return null;
        }
        String base = u.getBaseUrl().replaceAll("/$", "");
        String clientsPath = u.isUseUnifiOs() ? "/proxy/network/api/s/default/stat/sta" : "/api/s/default/stat/sta";

        try {
            String cookieHeader = null;
            String csrfToken = null;
            long now = System.currentTimeMillis();

            if (cachedCookie != null && now < cacheExpiresAt) {
                cookieHeader = cachedCookie;
                csrfToken = cachedCsrf;
            }
            if (cookieHeader == null || cookieHeader.isBlank()) {
                log.info("UniFi: attempting login to {} (use-unifi-os={})", base, u.isUseUnifiOs());
                HttpHeaders loginHeaders = new HttpHeaders();
                loginHeaders.setContentType(MediaType.APPLICATION_JSON);
                Map<String, String> body = Map.of("username", u.getUsername(), "password", u.getPassword());
                String[] loginPaths = u.isUseUnifiOs()
                        ? new String[]{"/api/auth/login", "/proxy/network/api/auth/login"}
                        : new String[]{"/api/login"};
                for (String loginPath : loginPaths) {
                    try {
                        ResponseEntity<String> loginRespStr = restTemplate.exchange(
                                base + loginPath,
                                HttpMethod.POST,
                                new HttpEntity<>(body, loginHeaders),
                                String.class
                        );
                        HttpHeaders respHeaders = loginRespStr.getHeaders();
                        List<String> setCookies = respHeaders.get(HttpHeaders.SET_COOKIE);
                        if (setCookies == null) setCookies = respHeaders.get("Set-Cookie");
                        if (setCookies == null) setCookies = respHeaders.get("set-cookie");
                        if (setCookies != null && !setCookies.isEmpty()) {
                            Map<String, String> cookiePairs = new LinkedHashMap<>();
                            for (String s : setCookies) {
                                String nv = s.contains(";") ? s.substring(0, s.indexOf(';')).trim() : s.trim();
                                int eq = nv.indexOf('=');
                                if (eq > 0) cookiePairs.put(nv.substring(0, eq).trim(), nv.substring(eq + 1).trim());
                            }
                            cookieHeader = cookiePairs.entrySet().stream()
                                    .map(e -> e.getKey() + "=" + e.getValue())
                                    .collect(Collectors.joining("; "));
                            csrfToken = respHeaders.getFirst("X-CSRF-Token");
                            if (csrfToken == null) csrfToken = respHeaders.getFirst("X-Updated-Csrf-Token");
                            cachedCookie = cookieHeader;
                            cachedCsrf = csrfToken;
                            cacheExpiresAt = now + SESSION_CACHE_MS;
                            log.info("UniFi login ok, session cached for {} min", SESSION_CACHE_MS / 60000);
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("UniFi login request failed for {}: {}", base + loginPath, e.getMessage());
                    }
                }
            }
            if (cookieHeader == null || cookieHeader.isBlank()) {
                log.warn("UniFi login returned no cookie from any path. Check credentials and use-unifi-os.");
                return null;
            }

            HttpHeaders getHeaders = new HttpHeaders();
            getHeaders.set(HttpHeaders.COOKIE, cookieHeader);
            if (csrfToken != null && !csrfToken.isBlank()) {
                getHeaders.set("X-CSRF-Token", csrfToken);
            }
            ResponseEntity<Map> clientsResp = restTemplate.exchange(
                    base + clientsPath,
                    HttpMethod.GET,
                    new HttpEntity<>(getHeaders),
                    Map.class
            );
            Map<String, Object> data = clientsResp.getBody();
            if (data == null || !(data.get("data") instanceof List)) {
                log.warn("UniFi clients response missing data list. Status={}, body={}", clientsResp.getStatusCode(), data);
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
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 401) {
                cachedCookie = null;
                cachedCsrf = null;
                cacheExpiresAt = 0;
                log.warn("UniFi session rejected ({}), cache cleared. Will re-login on next request.", e.getStatusCode());
            } else {
                log.warn("UniFi request failed: {}", e.getMessage());
            }
            return null;
        } catch (Exception e) {
            log.warn("UniFi request failed: {}", e.getMessage());
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
