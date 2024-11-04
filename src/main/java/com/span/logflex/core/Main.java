package com.span.logflex.core;

import com.span.logflex.core.marker.JsonMarker;
import org.apache.logging.log4j.*;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        Marker danger = MarkerManager.getMarker("Danger");
        JsonMarker json = JsonMarker.create().with("custom", "my-element");
        json.addParents(danger, danger);
        ThreadContext.put("project", "LogFlex");
        log.info(json, "hello, info");
        log.debug("hello, debug");
        log.trace("hello, trace");
        log.warn("hello, warn");
        RuntimeException ex = new RuntimeException("Error");
        log.error(danger, "hello, error", ex);
    }
}