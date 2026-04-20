/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configuration properties for ScopeFlow.
 *
 * <pre>
 * scopeflow.enabled=true
 * scopeflow.mdc.enabled=true
 * scopeflow.mdc.keys=request.id,tenant.id,user.id
 * scopeflow.mdc.prefix=
 * scopeflow.http.server.generate-request-scope=true
 * scopeflow.http.server.generate-request-id=true
 * scopeflow.http.server.request-id-header=X-Request-ID
 * </pre>
 *
 * @since 0.2.0
 */
@ConfigurationProperties(prefix = "scopeflow")
public class ScopeFlowProperties {

    /**
     * Whether ScopeFlow auto-configuration is enabled.
     */
    private boolean enabled = true;

    /**
     * MDC integration properties.
     */
    private Mdc mdc = new Mdc();

    /**
     * HTTP integration properties.
     */
    private Http http = new Http();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mdc getMdc() {
        return mdc;
    }

    public void setMdc(Mdc mdc) {
        this.mdc = mdc;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    /**
     * MDC integration properties.
     */
    public static class Mdc {

        /**
         * Whether MDC propagation is enabled.
         */
        private boolean enabled = true;

        /**
         * Context keys to propagate to MDC. If empty, all keys are propagated.
         */
        private Set<String> keys = new LinkedHashSet<>();

        /**
         * Optional prefix for MDC keys (e.g., "sf.").
         */
        private String prefix = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getKeys() {
            return keys;
        }

        public void setKeys(Set<String> keys) {
            this.keys = keys;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }

    /**
     * HTTP integration properties.
     */
    public static class Http {

        /**
         * Server-side HTTP properties.
         */
        private Server server = new Server();

        public Server getServer() {
            return server;
        }

        public void setServer(Server server) {
            this.server = server;
        }

        /**
         * Server-side HTTP scope properties.
         */
        public static class Server {

            /**
             * Whether to automatically create a request scope for each HTTP request.
             */
            private boolean generateRequestScope = true;

            /**
             * Whether to generate a request ID if not present in the incoming request header.
             */
            private boolean generateRequestId = true;

            /**
             * HTTP header name for the request ID.
             */
            private String requestIdHeader = "X-Request-ID";

            public boolean isGenerateRequestScope() {
                return generateRequestScope;
            }

            public void setGenerateRequestScope(boolean generateRequestScope) {
                this.generateRequestScope = generateRequestScope;
            }

            public boolean isGenerateRequestId() {
                return generateRequestId;
            }

            public void setGenerateRequestId(boolean generateRequestId) {
                this.generateRequestId = generateRequestId;
            }

            public String getRequestIdHeader() {
                return requestIdHeader;
            }

            public void setRequestIdHeader(String requestIdHeader) {
                this.requestIdHeader = requestIdHeader;
            }
        }
    }
}
