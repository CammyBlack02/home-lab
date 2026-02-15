package com.homelab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs Speedtest CLI and parses JSON. Supports both Ookla CLI (-f json) and Python speedtest-cli (--json).
 * Caches result for 10 minutes.
 */
@Service
public class SpeedTestService {

    private static final Logger log = LoggerFactory.getLogger(SpeedTestService.class);
    private static final long CACHE_MS = 10 * 60 * 1000; // 10 minutes
    private static final int PROCESS_TIMEOUT_SEC = 120;
    private static final double BYTES_PER_SEC_TO_MBPS = 1.0 / 125_000; // 1 Mbps = 125000 bytes/s
    private static final double BITS_PER_SEC_TO_MBPS = 1.0 / 1_000_000; // Python CLI uses bits/s

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile Map<String, Object> cached;
    private volatile long cacheExpiresAt;

    /**
     * Run speed test (or return cached result). Returns null if CLI missing or fails.
     */
    public Map<String, Object> getSpeedTest() {
        long now = System.currentTimeMillis();
        if (cached != null && now < cacheExpiresAt) {
            return cached;
        }
        Map<String, Object> result = runSpeedTest();
        if (result != null) {
            cached = result;
            cacheExpiresAt = now + CACHE_MS;
        }
        return result;
    }

    private Map<String, Object> runSpeedTest() {
        // Try Python speedtest-cli first (--json), then Ookla (-f json)
        String[][] commands = {
                { "speedtest", "--json" },
                { "speedtest", "-f", "json" }
        };
        for (String[] cmd : commands) {
            Map<String, Object> result = runCommand(cmd);
            if (result != null) return result;
        }
        return null;
    }

    private Map<String, Object> runCommand(String[] command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            boolean finished = p.waitFor(PROCESS_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                log.warn("Speedtest timed out after {}s", PROCESS_TIMEOUT_SEC);
                return null;
            }
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.exitValue() != 0) {
                return null;
            }
            // Try Ookla format first (ping.latency, download.bandwidth), then Python (top-level download/upload/ping in bits/s)
            Map<String, Object> result = parseOoklaJson(output);
            if (result != null) return result;
            return parsePythonCliJson(output);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse Ookla CLI JSON: ping.latency (ms), download.bandwidth, upload.bandwidth (bytes/s).
     */
    private Map<String, Object> parseOoklaJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            double pingMs = 0;
            double downloadMbps = 0;
            double uploadMbps = 0;

            JsonNode ping = root.path("ping");
            if (!ping.isMissingNode()) {
                pingMs = ping.has("latency") ? ping.get("latency").asDouble() : 0;
            }
            JsonNode download = root.path("download");
            if (!download.isMissingNode() && download.has("bandwidth")) {
                long bytesPerSec = download.get("bandwidth").asLong();
                downloadMbps = bytesPerSec * BYTES_PER_SEC_TO_MBPS;
            }
            JsonNode upload = root.path("upload");
            if (!upload.isMissingNode() && upload.has("bandwidth")) {
                long bytesPerSec = upload.get("bandwidth").asLong();
                uploadMbps = bytesPerSec * BYTES_PER_SEC_TO_MBPS;
            }

            if (downloadMbps <= 0 && uploadMbps <= 0) return null;
            return Map.of(
                    "download_mbps", Math.round(downloadMbps * 10) / 10.0,
                    "upload_mbps", Math.round(uploadMbps * 10) / 10.0,
                    "ping_ms", (int) Math.round(pingMs),
                    "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse Python speedtest-cli (--json): top-level download, upload in bits/s; ping in ms.
     */
    private Map<String, Object> parsePythonCliJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.has("download") || !root.has("upload")) return null;
            double downloadBitsPerSec = root.get("download").asDouble();
            double uploadBitsPerSec = root.get("upload").asDouble();
            double downloadMbps = downloadBitsPerSec * BITS_PER_SEC_TO_MBPS;
            double uploadMbps = uploadBitsPerSec * BITS_PER_SEC_TO_MBPS;
            double pingMs = root.has("ping") ? root.get("ping").asDouble() : 0;
            return Map.of(
                    "download_mbps", Math.round(downloadMbps * 10) / 10.0,
                    "upload_mbps", Math.round(uploadMbps * 10) / 10.0,
                    "ping_ms", (int) Math.round(pingMs),
                    "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            return null;
        }
    }
}
