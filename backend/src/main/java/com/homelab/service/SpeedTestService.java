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
 * Runs Ookla Speedtest CLI and parses JSON. Caches result to avoid running on every poll.
 * Requires: speedtest (Ookla) installed, e.g. from https://www.speedtest.net/apps/cli
 */
@Service
public class SpeedTestService {

    private static final Logger log = LoggerFactory.getLogger(SpeedTestService.class);
    private static final long CACHE_MS = 10 * 60 * 1000; // 10 minutes
    private static final int PROCESS_TIMEOUT_SEC = 120;
    private static final double BYTES_PER_SEC_TO_MBPS = 1.0 / 125_000; // 1 Mbps = 125000 bytes/s

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
        ProcessBuilder pb = new ProcessBuilder("speedtest", "-f", "json");
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
                log.warn("Speedtest exited with {}: {}", p.exitValue(), output.length() > 200 ? output.substring(0, 200) + "..." : output);
                return null;
            }
            return parseOoklaJson(output);
        } catch (Exception e) {
            log.warn("Speedtest failed: {}", e.getMessage());
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

            return Map.of(
                    "download_mbps", Math.round(downloadMbps * 10) / 10.0,
                    "upload_mbps", Math.round(uploadMbps * 10) / 10.0,
                    "ping_ms", (int) Math.round(pingMs),
                    "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.warn("Speedtest JSON parse failed: {}", e.getMessage());
            return null;
        }
    }
}
