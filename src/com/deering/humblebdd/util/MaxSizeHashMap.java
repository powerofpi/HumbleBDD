package com.deering.humblebdd.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Used to implement a fixed-size operator cache.
 * @author tdeering
 *
 * @param <K>
 * @param <V>
 */
public final class MaxSizeHashMap<K, V> extends LinkedHashMap<K, V> {
	private static final long serialVersionUID = -3153647606474642376L;
	private final int maxSize;

    public MaxSizeHashMap(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}