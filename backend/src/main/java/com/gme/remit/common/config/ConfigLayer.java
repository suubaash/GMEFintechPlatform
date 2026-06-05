package com.gme.remit.common.config;

/** Glossary config precedence: Route &gt; Corridor &gt; Tenant &gt; Platform default. */
public enum ConfigLayer {
    ROUTE, CORRIDOR, TENANT, PLATFORM
}
