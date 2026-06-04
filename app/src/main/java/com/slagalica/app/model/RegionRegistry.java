package com.slagalica.app.model;

import com.slagalica.app.R;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegionRegistry {

    public static final Map<String, Region> REGISTRY = new LinkedHashMap<>();
    
    private RegionRegistry() {}

    public static Region get(String regionKey) {
        return REGISTRY.get(regionKey);
    }

    public static Map<String, Region> all() {
        return REGISTRY;
    }

    public static int icon(String regionKey) {
        Region r = REGISTRY.get(regionKey);
        return r != null ? r.getIcon() : R.drawable.ic_place;
    }

    public static void populate(List<Region> regions) {
        REGISTRY.clear();
        for (Region r : regions) {
            REGISTRY.put(r.getRegionKey(), r);
        }
    }
}