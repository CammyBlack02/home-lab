package com.homelab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "homelab")
public class HomelabProperties {

    private String serverAgentUrl;
    private String desktopAgentUrl;
    private Unifi unifi = new Unifi();

    public String getServerAgentUrl() {
        return serverAgentUrl;
    }

    public void setServerAgentUrl(String serverAgentUrl) {
        this.serverAgentUrl = serverAgentUrl;
    }

    public String getDesktopAgentUrl() {
        return desktopAgentUrl;
    }

    public void setDesktopAgentUrl(String desktopAgentUrl) {
        this.desktopAgentUrl = desktopAgentUrl;
    }

    public Unifi getUnifi() {
        return unifi;
    }

    public void setUnifi(Unifi unifi) {
        this.unifi = unifi;
    }

    public static class Unifi {
        private boolean enabled;
        private String baseUrl;
        private String username;
        private String password;
        private boolean useUnifiOs;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isUseUnifiOs() {
            return useUnifiOs;
        }

        public void setUseUnifiOs(boolean useUnifiOs) {
            this.useUnifiOs = useUnifiOs;
        }
    }
}
