package com.deering.humblebdd.util;

/**
 * A simple fixed-size hash map where collisions are handled simply by 
 * flushing the prior value for a key.
 * 
 * @author tdeering
 *
 * @param <K>
 * @param <V>
 */
public class FixedSizeHashMap<K, V> {
	private Object[] keyCache;
	private Object[] valCache;
	
	public FixedSizeHashMap(int size){
		keyCache = new Object[size];
		valCache = new Object[size];
	}
	
	@SuppressWarnings("unchecked")
	public V get(K key){
		if(keyCache.length == 0) return null;
		int hash = key.hashCode();
		hash = (hash >= 0 ? hash : -hash) % keyCache.length;
		if(key.equals(keyCache[hash])) return (V) valCache[hash];
		return null;
	}
	
	public void put(K key, V val){
		if(keyCache.length == 0) return;
		int hash = key.hashCode();
		hash = (hash >= 0 ? hash : -hash) % keyCache.length;
		keyCache[hash] = key;
		valCache[hash] = val;
	}
}
