package com.deering.humblebdd.zdd;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.deering.humblebdd.HumbleException;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;

/**
 * A pool of ZDD sets implemented backed by the same ZDDFactory.
 * @author tdeering
 *
 */
public class ZDDSetPool<T> {
	private ZDDFactory f;
	private Map<T, Integer> e2v;
	private Object[] v2e;
	
	/**
	 * Constructs a new ZDDSetPool. The given iterator should iterate over the domain in increasing 
	 * predicted likelihood that each element will be present in sets. In other words, least common
	 * set element first, most common set member last. This helps us choose a good mapping from
	 * elements to ZDD variables.
	 * 
	 * @param domainSize
	 * @param averageSetSize
	 * @param domainIterator
	 * @param operatorCacheSize
	 */
	public ZDDSetPool(int domainSize, Iterator<T> domainIterator, int operatorCacheSize){
		if(domainSize <= 0) throw new HumbleException("Domain size must be a positive integer");
		e2v = new HashMap<T, Integer>(domainSize);
		v2e = new Object[domainSize];
		int[] varOrder = new int[domainSize];

		for(int i = 0; i < domainSize; ++i){
			T t = domainIterator.next();
			e2v.put(t, i);
			v2e[i] = t;
			varOrder[i] = i;
		}
		f = new ZDDFactory(varOrder, operatorCacheSize);
	}
	
	public class ZDDSet implements Set<T>{
		ZDD zdd;
		
		public ZDDSet(){
			zdd = f.empty();
		}
		
		public ZDDSet(Collection<T> collection){
			zdd = collectionToZDD(collection);
		}
		
		@Override
		public int size() {
			return zdd.count();
		}

		@Override
		public boolean isEmpty() {
			return zdd.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return !zdd.intersection(f.element(e2v.get(o))).isEmpty();
		}

		@Override
		public Iterator<T> iterator() {
			return new Iterator<T>(){
				ZDD z = zdd;
				Iterator<Integer> zIter = z.iterator();
				T last = null;
				
				@Override
				public boolean hasNext() {
					if(z != zdd) throw new ConcurrentModificationException();
					return zIter.hasNext();
				}

				@SuppressWarnings("unchecked")
				@Override
				public T next() {
					if(z != zdd) throw new ConcurrentModificationException();
					last = (T) v2e[zIter.next()];
					return last;
				}

				@Override
				public void remove() {
					if(last == null) throw new NoSuchElementException();
					ZDDSet.this.remove(last);
					last = null;
					z = zdd;
				}
			};
		}

		@Override
		public Object[] toArray() {
			Object[] ret = new Object[zdd.count()];
			int idx = 0;
			for(int var : zdd){
				ret[idx++] = v2e[var];
			}
			return ret;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <R> R[] toArray(R[] a) {
			int size = zdd.count();
			R[] ret = a.length >= size ? a: (R[]) Array.newInstance(a.getClass().getComponentType(), size);
			int idx = 0;
			for(int var : zdd){
				ret[idx++] = (R) v2e[var];
			}
			return ret;
		}

		@Override
		public boolean add(T e) {
			ZDD original = zdd;
			zdd = zdd.union(f.element(e2v.get(e)));
			return original != zdd;
		}

		@Override
		public boolean remove(Object o) {
			ZDD original = zdd;
			zdd = zdd.difference(f.element(e2v.get(o)));
			return original != zdd;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if(c == null) throw new HumbleException("c must not be null!", new NullPointerException());
			if(zdd.isEmpty() && !c.isEmpty()) return false;
			
			for(Object o : c){
				if(zdd.intersection(f.element(e2v.get(o))).isEmpty())
					return false;
			}
			
			return true;
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			ZDD z1 = zdd;
			ZDD z2 = zdd;
			boolean added = false;
			for(Object o : c){
				ZDD z3 = f.element(e2v.get(o));
				z1 = z1.union(z3);
				added |= z2 != z1;
				z2 = z1;
			}
			zdd = z1;
			return added;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			ZDD z = collectionToZDD(c);
			if(zdd.equals(z)) return false;
			zdd = z;
			return true;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			ZDD original = zdd;
			zdd = zdd.difference(collectionToZDD(c));
			return original != zdd;
		}
		
		@SuppressWarnings("unchecked")
		private ZDD collectionToZDD(Collection<?> c){
			Iterator<?> iter = c.iterator();
			if(!iter.hasNext()) return f.empty();
			ZDD z = f.element(e2v.get(iter.next()));
			while(iter.hasNext()){
				z = z.union(f.element(e2v.get((T) iter.next())));
			}
			return z;
		}

		@Override
		public void clear() {
			zdd = f.empty();
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder();
			sb.append('{');
			Iterator<T> iter = iterator();
			while(iter.hasNext()){
				sb.append(iter.next());
				if(iter.hasNext()) sb.append(',');
			}
			sb.append('}');
			return sb.toString();
		}
	}
}