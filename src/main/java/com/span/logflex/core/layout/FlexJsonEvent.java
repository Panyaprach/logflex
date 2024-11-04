package com.span.logflex.core.layout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class FlexJsonEvent implements LogEvent {

        @JsonIgnore
        private final LogEvent event;

        FlexJsonEvent(LogEvent event) {
            this.event = event;
        }

        public static FlexJsonEvent createMemento(LogEvent event) {
            // wrap with log4j event to avoid serializing exception
            event = Log4jLogEvent.createMemento(event);
            return new FlexJsonEvent(event);
        }

        @Override
        public LogEvent toImmutable() {
            return event.toImmutable();
        }

        /**
         * @deprecated
         */
        @Override
        public Map<String, String> getContextMap() {
            return event.getContextMap();
        }

        @Override
        public ReadOnlyStringMap getContextData() {
            return event.getContextData();
        }

        @Override
        public ThreadContext.ContextStack getContextStack() {
            return event.getContextStack();
        }

        @Override
        @JsonIgnore
        public String getLoggerFqcn() {
            return event.getLoggerFqcn();
        }

        @Override
        public Level getLevel() {
            return event.getLevel();
        }

        @Override
        @JsonProperty("logger")
        public String getLoggerName() {
            return event.getLoggerName();
        }

        @Override
        public Marker getMarker() {
            return event.getMarker();
        }

        @Override
        public Message getMessage() {
            return event.getMessage();
        }

        @Override
        @JsonProperty("timestamp")
        public long getTimeMillis() {
            return event.getTimeMillis();
        }

        @Override
        @JsonSerialize(using = InstantIsoSerializer.class)
        public Instant getInstant() {
            return event.getInstant();
        }

        @Override
        @JsonUnwrapped
        public StackTraceElement getSource() {
            return event.getSource();
        }

        @Override
        public String getThreadName() {
            return event.getThreadName();
        }

        @Override
        public long getThreadId() {
            return event.getThreadId();
        }

        @Override
        public int getThreadPriority() {
            return event.getThreadPriority();
        }

        @Override
        public Throwable getThrown() {
            return event.getThrown();
        }

        @Override
        public ThrowableProxy getThrownProxy() {
            return event.getThrownProxy();
        }

        @Override
        @JsonIgnore
        public boolean isEndOfBatch() {
            return event.isEndOfBatch();
        }

        @Override
        public void setEndOfBatch(boolean b) {
            event.setEndOfBatch(b);
        }

        @Override
        public boolean isIncludeLocation() {
            return event.isIncludeLocation();
        }

        @Override
        public void setIncludeLocation(boolean b) {
            event.setIncludeLocation(b);
        }

        @Override
        public long getNanoTime() {
            return event.getNanoTime();
        }

        static class InstantIsoSerializer extends JsonSerializer<Instant> {

            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                java.time.Instant instant =
                        java.time.Instant.ofEpochSecond(value.getEpochSecond(), value.getNanoOfSecond());

                ZonedDateTime datetime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
                gen.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(datetime));
            }
        }
}
