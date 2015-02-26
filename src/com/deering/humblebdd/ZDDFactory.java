package com.deering.humblebdd;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.WeakHashMap;

import com.deering.humblebdd.util.MaxSizeHashMap;

/**
 * A factory that wraps a universe graph of shared, reduced, ordered, zero-suppressed ZDDs (ZDDs).
 * Supports up to Integer.MAX_VALUE variables.
 * 
 * USAGE EXAMPLE: TODO
 * 
 * 
 * 
 * @author tdeering
 *
 */
public final class ZDDFactory {
	/**
	 * Op codes for BDD operations.
	 * @author tdeering
	 *
	 */
	public static interface Operation{
		static final byte SUBSET_HI = 1;
		static final byte SUBSET_LO = 2;
		static final byte UNION = 3;
		static final byte DIFFERENCE = 4;
		static final byte INTERSECTION = 5;
		static final byte CHANGE = 6;
		static final byte COUNT = 7;
	}
	
	/**
	 * Large prime numbers for hashing.
	 */
	private static final int[] LARGE_PRIMES = new int[]{2147481053, 2147481151, 2147482093};
	
	/**
	 * Constant "false" node.
	 */
	private ZDDNode LO;
	
	/**
	 * Constant "true" node.
	 */
	private ZDDNode HI;

	/**
	 * ZDD wrapping LO.
	 */
	private ZDD LO_ZDD;
	
	/**
	 * Maps from variables to ordering indices.
	 */
	private int[] varToIndex;
	
	/**
	 * Maps from ordering indices to variables.
	 */
	private int[] indexToVar;

	/**
	 * Nodes of the shared graph.
	 */
	private WeakHashMap<ZDDNode, ZDDNode> zddNodes; 
	
	/**
	 * Cache of ZDD operation results
	 */
	private Map<ZDDOp, Object> opCache;
	
	/**
	 * Constructs a new ZDDFactory with the given zero-indexed variables the given order. For example:
	 * 
	 * varOrdering = new int{1,0,2,3};
	 * 
	 * Specifies a factory with 4 variables, ordered 1, 0, 2, 3.
	 * 
	 * The operator cache is used to memoize the results of the last operatorCacheSize operations. Larger
	 * caches may result in more memory use, but substantial speedups.
	 * 
	 * @param numVars
	 * @param varOrdering
	 */
	public ZDDFactory(int[] varOrdering, int operatorCacheSize){
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
		this.indexToVar = new int[varOrdering.length];
		this.varToIndex = new int[varOrdering.length];
		for(int i=0; i < varOrdering.length; i++){
			indexToVar[i] = varOrdering[i];
			varToIndex[indexToVar[i]] = i;
		}
		
		this.LO = new ZDDNode(-1, null, null);
		this.LO_ZDD = new ZDD(LO);
		this.HI = new ZDDNode(-1, null, null);
		this.zddNodes = new WeakHashMap<ZDDNode, ZDDNode>();
		this.opCache = new MaxSizeHashMap<ZDDOp, Object>(operatorCacheSize);
	}
	
	/**
	 * Returns the current variable ordering.
	 * 
	 * @return
	 */
	public int[] getOrdering(){
		return Arrays.copyOf(varToIndex, varToIndex.length);
	}
	
	/**
	 * Return the empty ZDD
	 * 
	 * @return
	 */
	public ZDD empty(){
		return LO_ZDD;
	}

	/**
	 * Return the ZDD containing only element var
	 * 
	 * @param var
	 * @return
	 */
	public ZDD element(int var){
		if(var < 0 || var >= varToIndex.length) throw new HumbleException("No such variable: " + var);
		return new ZDD(getNode(var, LO, HI));
	}

	/**
	 * Return the ZDD containing only element 0
	 * 
	 * @return
	 */
	public ZDD base(){
		return element(0);
	}
	
	/**
	 * Return the shared node which represents the given variable and has exactly
	 * the given lo and hi children.
	 * 
	 * NOTE: This function is what gives our ZDDs the "Reduced" property. That is,
	 * there is exactly one ZDDNode with a particular pair of (lo, hi) children,
	 * and no ZDDNode has (lo, hi) such that lo == high. Therefore, the shared graph
	 * is maximally-reduced with respect to the given variable ordering.
	 * 
	 * @param var
	 * @param lo
	 * @param hi
	 * @return
	 */
	private ZDDNode getNode(int var, ZDDNode lo, ZDDNode hi){
		if(var < 0 || var >= varToIndex.length) throw new HumbleException("No such variable: " + var);
		// Node elimination
		if(LO == hi) return lo;
		ZDDNode key = new ZDDNode(var, lo, hi);
		// Node sharing
		ZDDNode sharedNode = zddNodes.get(key);
		if(sharedNode == null){
			sharedNode = key;
			zddNodes.put(sharedNode, sharedNode);
		}
		return sharedNode;
	}

	/**
	 * Immutable ZDD class. Operations return new ZDD object instances.
	 * 
	 * Time complexity of operations:
	 * 
	 * not(this): O(|this|)
	 * and(this, other): O(|this|*|other|)
	 * or(this, other: O(|this|*|other|)
	 * xor(this, other): O(|this|*|other|)
	 * isLo(this): O(1)
	 * isHi(this): O(1)
	 * satCount(this): O(|this|)
	 * iteration: O(|this|)
	 * 
	 * @author tdeering
	 *
	 */
	public final class ZDD implements Iterable<boolean[]>{
		/**
		 * Head node of this ZDD in the shared ZDD graph.
		 */
		private ZDDNode ref;
		
		/**
		 * Creates a ZDD represented by the given node of the given factory. 
		 * 
		 * USERS: Do not directly construct. Use ZDDFactory or operations of another ZDD.
		 * @param ref
		 * @param factory
		 */
		private ZDD(ZDDNode ref){
			this.ref = ref;
		}
		
		/**
		 * Applies the given operation between this ZDD and the other.
		 * 
		 * @param op
		 * @param other
		 * @return
		 */
		private ZDD apply(byte op, ZDD other){
			// Null check
			if(other == null) throw new HumbleException("Other cannot be null!", new NullPointerException());
			// Different factories?
			if(ZDDFactory.this != other.getFactory()) throw new HumbleException("ZDDs must come from the same factory!");
			ZDDNode applied = (ZDDNode) apply(op, ref, other.ref, -1);
			if(applied == ref) return this;
			else if(applied == other.ref) return other;
			return new ZDD(applied);
		}
		
		/**
		 * Applies the given operation on this ZDD.
		 * 
		 * @param op
		 * @param other
		 * @return
		 */
		private ZDD apply(byte op, int var){
			// Nonsensical-variable check
			if(var < 0 || var >= varToIndex.length) throw new HumbleException("No such variable: " + var);
			ZDDNode applied = (ZDDNode) apply(op, ref, null, var);
			if(applied == ref) return this;
			return new ZDD(applied);
		}
		
		/**
		 * Returns the subset of variables >= the given var
		 * 
		 * @param zdd
		 * @param var
		 * @return
		 */
		public ZDD subsetHi(int var){
			return apply(Operation.SUBSET_HI, var);
		}
		
		/**
		 * Returns the subset of variables <= the given var
		 * 
		 * @param zdd
		 * @param var
		 * @return
		 */
		public ZDD subsetLo(int var){
			return apply(Operation.SUBSET_LO, var);
		}
		
		/**
		 * Returns this ZDD with the given variable inverted
		 * 
		 * @param var
		 * @return
		 */
		public ZDD change(int var){
			return apply(Operation.CHANGE, var);
		}
		
		/**
		 * Returns the union of this ZDD and the other ZDD
		 * 
		 * @param other
		 * @return
		 */
		public ZDD union(ZDD other){
			return apply(Operation.UNION, other);
		}
		
		/**
		 * Returns the intersection of this ZDD and the other ZDD
		 * 
		 * @param other
		 * @return
		 */
		public ZDD intersection(ZDD other){
			return apply(Operation.INTERSECTION, other);
		}
		
		/**
		 * Returns the difference of this ZDD and the other ZDD
		 * 
		 * @param other
		 * @return
		 */
		public ZDD difference(ZDD other){
			return apply(Operation.DIFFERENCE, other);
		}
		
		/**
		 * Apply the requested operation inductively, first consulting the operator cache
		 * @param op
		 * @param first
		 * @param second
		 * @param var
		 * @return
		 */
		private Object apply(byte op, ZDDNode first, ZDDNode second, int var){
			ZDDOp key = new ZDDOp(op, first, second, var);
			Object cached = opCache.get(key);
			if(cached == null){
				switch(op){
				case Operation.SUBSET_HI:
					if(first.var < var) cached = LO;
					else if(first.var == var) cached = ref.hi;
					else cached = getNode(first.var, (ZDDNode) apply(op, first.lo, null, var), (ZDDNode) apply(op, first.hi, null, var));
					break;
				case Operation.SUBSET_LO:
					if(first.var < var) cached = first;
					else if(first.var == var) cached = first.lo;
					else cached = getNode(first.var, (ZDDNode) apply(op, first.lo, null, var), (ZDDNode) apply(op, first.hi, null, var)); 
					break;
				case Operation.CHANGE:
					if(ref.var < var) cached = getNode(first.var, LO, first);
					else if(ref.var == var) return getNode(first.var, first.hi, first.lo);
					else cached = getNode(op, (ZDDNode) apply(op, first.lo, null, var), (ZDDNode) apply(op, first.hi, null, var));
					break;
				case Operation.UNION:
					if(first == LO) cached = second;
					else if(second == LO) cached = first;
					else if(first == second) cached = first;
					else if(first.var > second.var) cached = getNode(first.var, (ZDDNode) apply(op, first.lo, second, var), first.hi);
					else if(first.var < second.var) cached = getNode(second.var, (ZDDNode) apply(op, first, second.lo, var), second.hi);
					else cached = getNode(first.var, (ZDDNode) apply(op, first.lo, second.lo, var), (ZDDNode) apply(op, first.hi, second.hi, var));
					break;
				case Operation.INTERSECTION:
					if(first == LO) cached = LO;
					else if(second == LO) cached = LO;
					else if(first == second) cached = first;
					else if(first.var > second.var) cached = apply(op, first.lo, second, var);
					else if(first.var < second.var) cached = apply(op, first, second.lo, var);
					else cached = getNode(first.var, (ZDDNode) apply(op, first.lo, second.lo, var), (ZDDNode) apply(op, first.hi, second.hi, var));
					break;
				case Operation.DIFFERENCE:
					if(first == LO) cached = LO;
					else if(second == LO) cached = first;
					else if(first == second) cached = LO;
					else if(first.var > second.var) cached = getNode(first.var, (ZDDNode) apply(op, first.lo, second, var), first.hi);
					else if(first.var < second.var) cached = apply(op, first, second.lo, var);
					else cached = getNode(first.var, (ZDDNode) apply(op, first.lo, second.lo, var), (ZDDNode) apply(op, first.hi, second.hi, var));
					break;
				case Operation.COUNT:
					if(first == LO) cached = 0;
					else if(first.var == 0) cached = 1;
					else cached = ((Integer) apply(op, first.lo, second, var)) + ((Integer) apply(op, first.hi, second, var));
				default:
					throw new HumbleException("Unsupported op code: " + op);
				}
				opCache.put(key, cached);
			}
			return cached;
		}

		/**
		 * Returns the number of variables in this ZDD
		 * 
		 * @return
		 */
		public int count(){
			return (Integer) apply(Operation.COUNT, ref, null, -1);
		}
		
		/**
		 * Return the ZDDFactory used to construct this ZDD.
		 * @return
		 */
		public ZDDFactory getFactory(){
			return ZDDFactory.this;
		}
		
		/**
		 * Iterates over the solutions to this ZDD. The returned solutions have the following meaning:
		 * 
		 * // One satisfying solution for the ZDD 
		 * boolean[] sat = iter.next();
		 * 
		 * // Variable 3 has this value
		 * boolean varThree = sat[3];
		 * 
		 * NOTE: The returned array is re-used by the iterator for efficiency reasons.
		 */
		@Override
		public Iterator<boolean[]> iterator() {
			return new ZDDSatIterator();
		}
		
		@Override
		public String toString(){
			return ref.toString();
		}
		
		@Override
		public int hashCode() {
			return ref.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ZDD other = (ZDD) obj;
			
			// Canonical representation of ROZDD allows us to do this
			return ref == other.ref;
		}
		
		/**
		 * Iterates over the satisfying assignments for this ZDD.
		 * @author tdeering
		 *
		 */
		private class ZDDSatIterator implements Iterator<boolean[]>{
			private static final byte UNVISITED = 1;
			private static final byte VISITED_LO = 2;
			private static final byte VISITED_HI = 3;
			boolean[] solution, nextSolution;
			
			/**
			 * Used to carry out a DFS over satisfying solutions
			 */
			Stack<IterNode> stack;
			
			/**
			 *  Current location in the DFS
			 */
			IterNode dfsLocation;
			
			public ZDDSatIterator(){
				// The constant LO zdd has no satisfying solutions, nothing to do
				if(ref != LO){
					stack = new Stack<IterNode>();
					solution = new boolean[varToIndex.length];
					nextSolution = new boolean[varToIndex.length];
					if(ref == HI || indexToVar[0] != ref.var){
						dfsLocation = new IterNode(indexToVar[0], ref, ref);
					}else{
						dfsLocation = new IterNode(indexToVar[0], ref.lo, ref.hi);
					}
					
					stack.push(dfsLocation);
					findNextSolution();
				}
			}
			
			@Override
			public boolean hasNext() {
				return dfsLocation != null;
			}

			@Override
			public boolean[] next() {
				if(dfsLocation == null) throw new NoSuchElementException("No more satisfying solutions!");
				
				for(int i = 0; i < solution.length; ++i) solution[i] = nextSolution[i];
				findNextSolution();
				
				return solution;
			}
			
			private void findNextSolution(){
				if(stack.isEmpty()){
					dfsLocation = null;
				}else{
					dfsLocation = stack.pop();
					
					while(dfsLocation.var >= 0){
						// Lo is non-null and unvisited in the current stack
						if(dfsLocation.lo != null && dfsLocation.lo.visitStatus == UNVISITED){
							nextSolution[dfsLocation.var] = false;
							dfsLocation.lo.visitStatus = VISITED_LO;
							stack.push(dfsLocation);
							dfsLocation = dfsLocation.lo;
						}
						// Hi is non-null and unvisited in the current stack
						else if(dfsLocation.hi != null && dfsLocation.hi.visitStatus != VISITED_HI){
							nextSolution[dfsLocation.var] = true;
							dfsLocation.hi.visitStatus = VISITED_HI;
							stack.push(dfsLocation);
							dfsLocation = dfsLocation.hi;
						}
						// No more children to visit. Go back to parent
						else{
							if(dfsLocation.lo != null) dfsLocation.lo.visitStatus = UNVISITED;
							if(dfsLocation.hi != null) dfsLocation.hi.visitStatus = UNVISITED;
							if(stack.isEmpty()){
								dfsLocation = null;
								break;
							}else{
								dfsLocation = stack.pop();
							}
						}
					}
				}
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("Operation remove() is not supported!");
			}
			
			/**
			 * Node for iterating over satisfying solutions in variable order. Used to account for the fact that we
			 * may have "don't care" variables.
			 * 
			 * Invariant: 
			 * @author tdeering
			 *
			 */
			private class IterNode{
				int var;
				byte visitStatus = UNVISITED;
				IterNode lo, hi;

				public IterNode(int var, ZDDNode lo, ZDDNode hi){
					this.var = var;
					// Non-leaf
					if(var >= 0){
						// Index of the variable that follows this one in the ordering
						int nextIdx = varToIndex[var] + 1;
						
						// If there is a successor in the ordering
						if(nextIdx < varToIndex.length){
							// Find it
							int nextVar = indexToVar[nextIdx];
							
							// Do not create paths to lo
							if(lo != LO){
								// If the lo successor is that variable, then accept it as-is
								if(lo.var == nextVar){
									this.lo = new IterNode(nextVar, lo.lo, lo.hi);
								}
								// Else create a dummy successor for this variable representing a "don't care" value
								else{
									this.lo = new IterNode(nextVar, lo, lo);
								}
							}
							
							// Do not create paths to lo
							if(hi != LO){
								// If the two successors are the same (this is a dummy successor), then don't duplicate stack nodes
								if(lo == hi){
									this.hi = this.lo;
								}else{
									// If the hi successor is that variable, then accept it as-is
									if(hi.var == nextVar){
										this.hi = new IterNode(nextVar, hi.lo, hi.hi);
									}
									// Else create a dummy successor for this variable representing a "don't care" value
									else{
										this.hi = new IterNode(nextVar, hi, hi);
									}
								}
							}
						}
						// Terminal nodes
						else{
							// Do not create paths to lo
							if(lo == HI){
								this.lo = new IterNode(-1, null, null);
							}
							// Do not create paths to lo
							if(hi == HI){
								// If both successors are hi, just point to the same one as lo
								if(this.lo != null){
									this.hi = this.lo;
								}else{
									this.hi = new IterNode(-1, null, null);
								}
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Node in the shared ZDD universe graph.
	 * @author tdeering
	 *
	 */
	private final class ZDDNode{
		int var;
		Integer hash;
		ZDDNode lo, hi;
		
		private ZDDNode(int var, ZDDNode lo, ZDDNode hi){
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
			ZDDNode other = (ZDDNode) obj;
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
		
		public ZDDFactory getOuterType(){
			return ZDDFactory.this;
		}
	}
	
	/**
	 * Used as key into the operation cache
	 * @author tdeering
	 *
	 */
	private final class ZDDOp{
		byte op;
		ZDDNode a, b;
		int var;
		public ZDDOp(byte op, ZDDNode a, ZDDNode b, int var){
			this.op = op;
			this.a = a;
			this.b = b;
			this.var = var;
		}
		@Override
		public int hashCode() {
			return (op * LARGE_PRIMES[0]) + a.hashCode() + (b == null ? 0:b.hashCode()) + (var * LARGE_PRIMES[1]);
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ZDDOp other = (ZDDOp) obj;
			if(!getOuterType().equals(other.getOuterType()))
				return false;
			if(op != other.op)
				return false;
			if(var != other.var)
				return false;
			if(a == other.a && b == other.b)
				return true;
			// Operand order matters only for difference operation
			return op != Operation.DIFFERENCE && a == other.b && b == other.a;
		}
		
		public ZDDFactory getOuterType(){
			return ZDDFactory.this;
		}
	}
}