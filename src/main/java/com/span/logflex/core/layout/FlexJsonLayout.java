package com.span.logflex.core.layout;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.span.logflex.core.marker.JsonMarker;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.jackson.XmlConstants;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.core.util.StringBuilderWriter;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Plugin(
        name = "FlexJsonLayout",
        category = Node.CATEGORY,
        elementType = Layout.ELEMENT_TYPE,
        printObject = true
)
public final class FlexJsonLayout extends AbstractStringLayout {
    private static final String DEFAULT_FOOTER = "]";
    private static final String DEFAULT_HEADER = "[";
    private static final String DEFAULT_EOL = "\r\n";
    private static final String COMPACT_EOL = Strings.EMPTY;
    private final ResolvableKeyValuePair[] additionalFields;
    private final String eol;
    private final ObjectWriter objectWriter;
    private final boolean complete;
    private final boolean includeNullDelimiter;
    private final boolean unwrapContextMap;

    private FlexJsonLayout(Configuration config,
                           Charset aCharset,
                           String headerPattern, String footerPattern,
                           boolean encodeThreadContextAsList,
                           boolean includeStacktrace,
                           boolean stackTraceAsString,
                           boolean objectMessageAsJsonObject,
                           boolean locationInfo,
                           boolean properties,
                           boolean compact,
                           boolean includeTimeMillis,
                           boolean complete,
                           boolean includeNullDelimiter,
                           boolean eventEol,
                           boolean unwrapContextMap,
                           String endOfLine,
                           KeyValuePair[] additionalFields) {

        super(config, aCharset,
                PatternLayout.newSerializerBuilder().setConfiguration(config)
                        .setPattern(headerPattern).setDefaultPattern(DEFAULT_HEADER).build(),
                PatternLayout.newSerializerBuilder().setConfiguration(config)
                        .setPattern(footerPattern).setDefaultPattern(DEFAULT_FOOTER).build());
        this.objectWriter = new JacksonFactory.JSON(encodeThreadContextAsList, includeStacktrace, stackTraceAsString, objectMessageAsJsonObject).newWriter(locationInfo, properties, compact, includeTimeMillis);
        this.complete = complete;
        this.includeNullDelimiter = includeNullDelimiter;
        this.unwrapContextMap = unwrapContextMap;
        this.eol = endOfLine != null ? endOfLine : (compact && !eventEol ? COMPACT_EOL : DEFAULT_EOL);
        this.additionalFields = prepareAdditionalFields(config, additionalFields);
    }

    static boolean valueNeedsLookup(final String value) {
        return value != null && value.contains("${");
    }

    private static ResolvableKeyValuePair[] prepareAdditionalFields(Configuration config, KeyValuePair[] additionalFields) {
        if (additionalFields != null && additionalFields.length != 0) {
            ResolvableKeyValuePair[] resolvableFields = new ResolvableKeyValuePair[additionalFields.length];

            for (int i = 0; i < additionalFields.length; ++i) {
                ResolvableKeyValuePair resolvable = resolvableFields[i] = new ResolvableKeyValuePair(additionalFields[i]);
                if (config == null && resolvable.valueNeedsLookup) {
                    throw new IllegalArgumentException("configuration needs to be set when there are additional fields with variables");
                }
            }

            return resolvableFields;
        } else {
            return new ResolvableKeyValuePair[0];
        }
    }

    private static LogEvent convertMutableToFlexJsonEvent(final LogEvent event) {
        return event instanceof FlexJsonEvent ? event : FlexJsonEvent.createMemento(event);
    }

    @PluginBuilderFactory
    public static <B extends FlexJsonLayout.Builder<B>> B newBuilder() {
        return new FlexJsonLayout.Builder<B>().asBuilder();
    }

    public byte[] getHeader() {
        if (!this.complete) {
            return null;
        } else {
            StringBuilder buf = new StringBuilder();
            String str = this.serializeToString(this.getHeaderSerializer());
            if (str != null) {
                buf.append(str);
            }

            buf.append(this.eol);
            return this.getBytes(buf.toString());
        }
    }

    public byte[] getFooter() {
        if (!this.complete) {
            return null;
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append(this.eol);
            String str = this.serializeToString(this.getFooterSerializer());
            if (str != null) {
                buf.append(str);
            }

            buf.append(this.eol);
            return this.getBytes(buf.toString());
        }
    }

    @Override
    public String toSerializable(LogEvent event) {
        final StringBuilderWriter writer = new StringBuilderWriter();
        try {
            toSerializable(event, writer);
            return writer.toString();
        } catch (final IOException e) {
            LOGGER.error(e);
            return Strings.EMPTY;
        }
    }

    private void toSerializable(final LogEvent event, final Writer writer)
            throws IOException {
        if (complete && eventCount > 0) {
            writer.append(", ");
        }

        objectWriter.writeValue(writer, wrapLogEvent(convertMutableToFlexJsonEvent(event)));
        writer.write(eol);
        if (includeNullDelimiter) {
            writer.write('\0');
        }
        markEvent();
    }

    private Map<String, String> resolveAdditionalFields(final LogEvent logEvent) {
        final ReadOnlyStringMap contextData = logEvent.getContextData();
        final Map<String, String> additionalFieldsMap = new LinkedHashMap<>(additionalFields.length);
        final StrSubstitutor strSubstitutor = configuration.getStrSubstitutor();

        for (final ResolvableKeyValuePair pair : additionalFields) {
            if (pair.valueNeedsLookup) {
                additionalFieldsMap.put(pair.key, strSubstitutor.replace(logEvent, pair.value));
            } else {
                additionalFieldsMap.put(pair.key, pair.value);
            }
        }
        additionalFieldsMap.putAll(contextData.toMap());

        if (logEvent.getMarker() instanceof JsonMarker jm) {
            additionalFieldsMap.putAll(jm.data());
        }

        return additionalFieldsMap;
    }

    private Object wrapLogEvent(final LogEvent event) {
        if (additionalFields.length > 0 || unwrapContextMap) {
            // Construct map for serialization - note that we are intentionally using original LogEvent
            final Map<String, String> additionalFieldsMap = resolveAdditionalFields(event);
            // This class combines LogEvent with AdditionalFields during serialization
            return new LogEventWithAdditionalFields(event, additionalFieldsMap);
        } else if (event instanceof Message) {
            // If the LogEvent implements the Message interface Jackson will not treat is as a LogEvent.
            return new ReadOnlyLogEventWrapper(event);
        } else {
            // No additional fields, return original object
            return event;
        }
    }

    public static class Builder<B extends Builder<B>> extends AbstractStringLayout.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<FlexJsonLayout> {
        @PluginBuilderAttribute
        private boolean eventEol;

        @PluginBuilderAttribute
        private String endOfLine;

        @PluginBuilderAttribute
        private boolean compact;

        @PluginBuilderAttribute
        private boolean complete;

        @PluginBuilderAttribute
        private boolean locationInfo;

        @PluginBuilderAttribute
        private boolean properties;

        @PluginBuilderAttribute
        private boolean includeStacktrace = true;

        @PluginBuilderAttribute
        private boolean stacktraceAsString = false;

        @PluginBuilderAttribute
        private boolean includeNullDelimiter = false;

        @PluginBuilderAttribute
        private boolean includeTimeMillis = true;

        @PluginBuilderAttribute
        private boolean unwrapContextMap = true;

        @PluginBuilderAttribute
        private boolean propertiesAsList;

        @PluginBuilderAttribute
        private boolean objectMessageAsJsonObject;

        @PluginElement("AdditionalField")
        private KeyValuePair[] additionalFields;

        public Builder() {
            super();
            setCharset(StandardCharsets.UTF_8);
        }

        protected String toStringOrNull(final byte[] header) {
            return header == null ? null : new String(header, Charset.defaultCharset());
        }

        @Override
        public FlexJsonLayout build() {
            final boolean encodeThreadContextAsList = isProperties() && isPropertiesAsList();
            final String headerPattern = toStringOrNull(getHeader());
            final String footerPattern = toStringOrNull(getFooter());
            return new FlexJsonLayout(getConfiguration(), getCharset(),
                    headerPattern, footerPattern, encodeThreadContextAsList, isIncludeStacktrace(),
                    isStacktraceAsString(), isObjectMessageAsJsonObject(), isLocationInfo(), isProperties(),
                    isCompact(), isIncludeTimeMillis(), isComplete(), isIncludeNullDelimiter(),
                    isEventEol(), isUnwrapContextMap(), getEndOfLine(), getAdditionalFields());
        }

        public boolean isEventEol() {
            return eventEol;
        }

        public B setEventEol(final boolean eventEol) {
            this.eventEol = eventEol;
            return asBuilder();
        }

        public String getEndOfLine() {
            return endOfLine;
        }

        public B setEndOfLine(final String endOfLine) {
            this.endOfLine = endOfLine;
            return asBuilder();
        }

        public boolean isCompact() {
            return compact;
        }

        public B setCompact(final boolean compact) {
            this.compact = compact;
            return asBuilder();
        }

        public boolean isComplete() {
            return complete;
        }

        public B setComplete(final boolean complete) {
            this.complete = complete;
            return asBuilder();
        }

        public boolean isLocationInfo() {
            return locationInfo;
        }

        public B setLocationInfo(final boolean locationInfo) {
            this.locationInfo = locationInfo;
            return asBuilder();
        }

        public boolean isProperties() {
            return properties;
        }

        public B setProperties(final boolean properties) {
            this.properties = properties;
            return asBuilder();
        }

        /**
         * If "true", includes the stacktrace of any Throwable in the generated data, defaults to "true".
         *
         * @return If "true", includes the stacktrace of any Throwable in the generated data, defaults to "true".
         */
        public boolean isIncludeStacktrace() {
            return includeStacktrace;
        }

        /**
         * If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
         *
         * @param includeStacktrace If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
         * @return this builder
         */
        public B setIncludeStacktrace(final boolean includeStacktrace) {
            this.includeStacktrace = includeStacktrace;
            return asBuilder();
        }

        public boolean isStacktraceAsString() {
            return stacktraceAsString;
        }

        /**
         * Whether to format the stacktrace as a string, and not a nested object (optional, defaults to false).
         *
         * @return this builder
         */
        public B setStacktraceAsString(final boolean stacktraceAsString) {
            this.stacktraceAsString = stacktraceAsString;
            return asBuilder();
        }

        public boolean isIncludeNullDelimiter() {
            return includeNullDelimiter;
        }

        /**
         * Whether to include NULL byte as delimiter after each event (optional, default to false).
         *
         * @return this builder
         */
        public B setIncludeNullDelimiter(final boolean includeNullDelimiter) {
            this.includeNullDelimiter = includeNullDelimiter;
            return asBuilder();
        }

        public boolean isIncludeTimeMillis() {
            return includeTimeMillis;
        }

        /**
         * Whether to include the timestamp (in addition to the Instant) (optional, default to false).
         *
         * @return this builder
         */
        public B setIncludeTimeMillis(final boolean includeTimeMillis) {
            this.includeTimeMillis = includeTimeMillis;
            return asBuilder();
        }

        public boolean isPropertiesAsList() {
            return propertiesAsList;
        }

        public B setPropertiesAsList(final boolean propertiesAsList) {
            this.propertiesAsList = propertiesAsList;
            return asBuilder();
        }

        public boolean isObjectMessageAsJsonObject() {
            return objectMessageAsJsonObject;
        }

        public B setObjectMessageAsJsonObject(final boolean objectMessageAsJsonObject) {
            this.objectMessageAsJsonObject = objectMessageAsJsonObject;
            return asBuilder();
        }

        public boolean isUnwrapContextMap() {
            return unwrapContextMap;
        }

        public void setUnwrapContextMap(boolean unwrapContextMap) {
            this.unwrapContextMap = unwrapContextMap;
        }

        public KeyValuePair[] getAdditionalFields() {
            return additionalFields;
        }

        /**
         * Additional fields to set on each log event.
         *
         * @return this builder
         */
        public B setAdditionalFields(final KeyValuePair[] additionalFields) {
            this.additionalFields = additionalFields;
            return asBuilder();
        }
    }

    @JsonRootName(XmlConstants.ELT_EVENT)
    public static class LogEventWithAdditionalFields {

        private final Object logEvent;
        private final Map<String, String> additionalFields;

        public LogEventWithAdditionalFields(final Object logEvent, final Map<String, String> additionalFields) {
            this.logEvent = logEvent;
            this.additionalFields = additionalFields;
        }

        @JsonUnwrapped
        public Object getLogEvent() {
            return logEvent;
        }

        @JsonAnyGetter
        @SuppressWarnings("unused")
        public Map<String, String> getAdditionalFields() {
            return additionalFields;
        }
    }

    private static class ResolvableKeyValuePair {

        final String key;
        final String value;
        final boolean valueNeedsLookup;

        ResolvableKeyValuePair(final KeyValuePair pair) {
            this.key = pair.getKey();
            this.value = pair.getValue();
            this.valueNeedsLookup = valueNeedsLookup(this.value);
        }
    }

    private static class ReadOnlyLogEventWrapper implements LogEvent {

        @JsonIgnore
        private final LogEvent event;

        public ReadOnlyLogEventWrapper(LogEvent event) {
            this.event = event;
        }

        @Override
        public LogEvent toImmutable() {
            return event.toImmutable();
        }

        @Override
        public Map<String, String> getContextMap() {
            return event.getContextData().toMap();
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
        public String getLoggerFqcn() {
            return event.getLoggerFqcn();
        }

        @Override
        public Level getLevel() {
            return event.getLevel();
        }

        @Override
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
        public long getTimeMillis() {
            return event.getTimeMillis();
        }

        @Override
        public Instant getInstant() {
            return event.getInstant();
        }

        @Override
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
        public boolean isEndOfBatch() {
            return event.isEndOfBatch();
        }

        @Override
        public void setEndOfBatch(boolean endOfBatch) {

        }

        @Override
        public boolean isIncludeLocation() {
            return event.isIncludeLocation();
        }

        @Override
        public void setIncludeLocation(boolean locationRequired) {

        }

        @Override
        public long getNanoTime() {
            return event.getNanoTime();
        }
    }

}
