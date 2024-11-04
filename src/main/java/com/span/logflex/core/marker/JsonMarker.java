package com.span.logflex.core.marker;

import org.apache.logging.log4j.Marker;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class JsonMarker implements Marker {
    private final HashMap<String, String> data = new HashMap<>();
    private List<Marker> parents;

    protected JsonMarker() {
    }

    public static JsonMarker create() {
        return new JsonMarker();
    }

    public JsonMarker with(String key, String value) {
        data.put(key, value);

        return this;
    }

    public JsonMarker with(String key, Object value) {
        return with(key, Objects.toString(value));
    }

    @Override
    public Marker addParents(Marker... markers) {
        for (Marker marker : markers) {
            Objects.requireNonNull(marker, "Marker element cannot be null");
        }

        if (parents == null)
            parents = new CopyOnWriteArrayList<>();

        parents.addAll(Arrays.asList(markers));

        return this;
    }

    @Override
    public String getName() {
        return "Json";
    }

    @Override
    public Marker[] getParents() {

        return hasParents() ?
                parents.toArray(new Marker[0]) : null;
    }

    @Override
    public boolean hasParents() {
        return parents != null;
    }

    @Override
    public boolean isInstanceOf(Marker m) {
        return false;
    }

    @Override
    public boolean isInstanceOf(String name) {
        return false;
    }

    @Override
    public boolean remove(Marker marker) {
        return parents.remove(marker);
    }

    @Override
    public Marker setParents(Marker... markers) {
        parents = Arrays.asList(markers);

        return this;
    }

    public Map<String, String> data() {
        HashMap<String, String> data = new HashMap<>(this.data);

        if (hasParents()) {
            for (Marker parent : parents) {
                if (parent instanceof JsonMarker that) {
                    data.putAll(that.data);
                }
            }
        }

        return data;
    }

    @Override
    public String toString() {

        return getName() + data;
    }
}
