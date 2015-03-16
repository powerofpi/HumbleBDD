package com.deering.humblebdd;


import java.util.Arrays;
import java.util.WeakHashMap;

import com.deering.humblebdd.util.FixedSizeHashMap;

/**
 * Common base class for decision diagram factories.
 * @author tdeering
 *
 */
public abstract class DDFactory {
	/**
	 * Large prime numbers for hashing.
	 */
	private static final int[] LARGE_PRIMES = new int[]{2147481053, 2147481151, 2147482093};
	
	/**
	 * Nodes of the shared graph.
	 */
	private WeakHashMap<DDNode, DDNode> ddNodes; 
	
	/**
	 * Cache of BDD operation results
	 */
	private FixedSizeHashMap<DDOpKey, Object> opCache;
	
	/**
	 * Constant "false" node.
	 */
	protected DDNode LO;
	
	/**
	 * Constant "true" node.
	 */
	protected DDNode HI;
	
	/**
	 * Maps from variables to ordering indices.
	 */
	protected int[] v2i;
	
	/**
	 * Maps from ordering indices to variables.
	 */
	protected int[] i2v;
	
	/**
	 * Constructs a new DDFactory with the given zero-indexed variables the given order. For example:
	 * 
	 * varOrdering = new int{1,0,2,3};
	 * 
	 * Specifies a factory with 4 variables, ordered 1, 0, 2, 3.
	 * 
	 * The operator cache is used to cache the results of the last operatorCacheSize operations. Larger
	 * caches may result in more memory use, but substantial speedups.
	 * 
	 * @param numVars
	 * @param varOrdering
	 */
	public DDFactory(int[] varOrdering, int operatorCacheSize){
		int[] counts = new int[varOrdering.length];
		for(int i : varOrdering){
			try{
				if(++counts[i] > 1){
					throw new HumbleException("Same variable " + i + " appeared multiple times in variable ordering!");
				}
			}catch(ArrayIndexOutOfBoundsException e){
				throw new HumbleException("Variables in ordering must be 0 to ordering.length - 1. Got " + i, e);
			}
		}
		this.i2v = new int[varOrdering.length];
		this.v2i = new int[varOrdering.length];
		for(int i=0; i < varOrdering.length; i++){
			i2v[i] = varOrdering[i];
			v2i[i2v[i]] = i;
		}
		
		this.LO = new DDNode(-1, null, null);
		this.HI = new DDNode(-1, null, null);
		this.ddNodes = new WeakHashMap<DDNode, DDNode>();
		this.opCache = new FixedSizeHashMap<DDOpKey, Object>(operatorCacheSize);
	}
	
	/**
	 * Returns the current variable ordering.
	 * @return
	 */
	public int[] getOrdering(){
		return Arrays.copyOf(v2i, v2i.length);
	}
	
	/**
	 * Return the shared node which represents the given variable and has exactly
	 * the given lo and hi children.
	 * 
	 * NOTE: This function is what gives our BDDs the "Reduced" property. That is,
	 * there is exactly one BDDNode with a particular pair of (lo, hi) children,
	 * and no BDDNode has (lo, hi) such that lo == high. Therefore, the shared graph
	 * is maximally-reduced with respect to the given variable ordering.
	 * 
	 * @param var
	 * @param lo
	 * @param hi
	 * @return
	 */
	protected DDNode getNode(int var, DDNode lo, DDNode hi){
		if(var < 0 || var >= v2i.length) throw new HumbleException("No such variable: " + var);
		
		// Node elimination
		DDNode eliminated = nodeElimination(var, lo, hi);
		if(eliminated != null) return eliminated;

		// Node sharing
		DDNode key = new DDNode(var, lo, hi);
		DDNode sharedNode = ddNodes.get(key);
		if(sharedNode == null){
			sharedNode = key;
			ddNodes.put(sharedNode, sharedNode);
		}
		return sharedNode;
	}
	
	/**
	 * If the given node should be eliminated, returns its replacement. If it should
	 * not be eliminated, returns null;
	 * 
	 * @param var
	 * @param lo
	 * @param hi
	 * @return
	 */
	protected abstract DDNode nodeElimination(int var, DDNode lo, DDNode hi);
	
	/**
	 * Immutable decision diagram class. Operations return new DD instances.
	 * 
	 * @author tdeering
	 *
	 */
	public abstract class DD implements Iterable<boolean[]>{
		/**
		 * Node from the shared DD graph that this DD refers to.
		 */
		protected DDNode ref;
		
		/**
		 * Creates a DD represented by the given node. 
		 * 
		 * USERS: Do not directly construct. Use factory or operations of another DD.
		 * @param ref
		 * @param factory
		 */
		protected DD(DDNode ref){
			this.ref = ref;
		}
		
		/**
		 * Apply the given operation on this DD and, for binary operations,
		 * on the other DD. Look up the answer in cache if it's there, and
		 * compute upon cache miss.
		 * 
		 * @param op
		 * @param first
		 * @param second
		 * @return
		 */
		protected Object apply(int op, DDNode first, DDNode second){
			DDOpKey key = new DDOpKey(op, first, second);
			Object cached = opCache.get(key);
			if(cached == null){
				cached = compute(op, first, second);
				opCache.put(key, cached);
			}
			return cached;
		}
		
		/**
		 * Returns whether this DD represents the false terminal
		 * 
		 * Time complexity: O(1)
		 * @return
		 */
		public boolean isLo(){
			return ref == LO;
		}

		/**
		 * Returns whether this DD represents the true terminal
		 * 
		 * Time complexity: O(1)
		 * @return
		 */
		public boolean isHi(){
			return ref == HI;
		}
		
		/**
		 * Return the DDFactory used to construct this DD.
		 * @return
		 */
		public DDFactory getFactory(){
			return DDFactory.this;
		}
		
		/**
		 * Compute the result of the given operation on the given DDNodes.
		 * 
		 * @param op
		 * @param first
		 * @param second
		 * @return
		 */
		protected abstract Object compute(int op, DDNode first, DDNode second);
	}
	
	/**
	 * Node in the shared DD universe graph.
	 * @author tdeering
	 *
	 */
	protected final class DDNode{
		public int var;
		public DDNode lo, hi;
		Integer hash;
		
		private DDNode(int var, DDNode lo, DDNode hi){
			this.var = var;
			this.lo = lo;
			this.hi = hi;
		}
		
		@Override
		public int hashCode() {
			if(hash == null){
				hash = LARGE_PRIMES[0] * var + 
					   (lo == null ? 0:LARGE_PRIMES[1] * lo.hashCode()) + 
					   (hi == null ? 0:LARGE_PRIMES[2] * hi.hashCode());
			}
			
			return hash;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DDNode other = (DDNode) obj;
			if(!getOuterType().equals(other.getOuterType()))
				return false;
			return var == other.var && hi == other.hi && lo == other.lo;
		}
		
		@Override
		public String toString(){
			if(this == LO) return "LO";
			if(this == HI) return "HI";
			return var + "(" + lo + "," + hi + ")";
		}
		
		public DDFactory getOuterType(){
			return DDFactory.this;
		}
	}
	
	/**
	 * Used as key into the operation cache
	 * @author tdeering
	 *
	 */
	private final class DDOpKey{
		int op;
		DDNode a, b;
		public DDOpKey(int op, DDNode a, DDNode b){
			this.op = op;
			this.a = a;
			this.b = b;
		}
		@Override
		public int hashCode() {
			return LARGE_PRIMES[0] * op + a.hashCode() + (b == null ? 0:b.hashCode());
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DDOpKey other = (DDOpKey) obj;
			if(!getOuterType().equals(other.getOuterType()))
				return false;
			if(op != other.op)
				return false;
			return (a == other.a && b == other.b) ||
				   (a == other.b && b == other.a);
		}
		
		public DDFactory getOuterType(){
			return DDFactory.this;
		}
	}
}
