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
 * A factory that wraps a universe graph of shared, reduced, ordered, BDDs (SROBDDs).
 * Supports up to Integer.MAX_VALUE variables.
 * 
 * USAGE EXAMPLE:
 * 
 * // New factory with 4 variables and an operator cache of size 10
 * BDDFactory f = new BDDFactory(new int[]{2,1,3,0}, 10);
 * 
 * // Build some boolean expression out of the variables 
 * BDD complexBDD = f.hiVar(0).and(f.lowVar(1)).xor(f.hiVar(2));
 * 
 * // Do something with the solutions
 * for(boolean[] solution : complexBDD){
 * 	 System.out.println(Arrays.toString(solution));
 * }
 * 
 * @author tdeering
 *
 */
public final class BDDFactory {
	/**
	 * Op codes for BDD operations.
	 * @author tdeering
	 *
	 */
	public static interface Operation{
		static final byte NOT = 1;
		static final byte AND = 2;
		static final byte OR = 3;
		static final byte XOR = 4;
	}
	
	/**
	 * Large prime numbers for hashing.
	 */
	private static final int[] LARGE_PRIMES = new int[]{2147481053, 2147481151, 2147482093};
	
	/**
	 * Constant "false" node.
	 */
	private BDDNode LO;
	
	/**
	 * Constant "true" node.
	 */
	private BDDNode HI;

	/**
	 * BDD wrapping LO.
	 */
	private BDD LO_BDD;
	
	/**
	 * BDD wrapping HI.
	 */
	private BDD HI_BDD;
	
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
	private WeakHashMap<BDDNode, BDDNode> bddNodes; 
	
	/**
	 * Cache of BDD operation results
	 */
	private Map<BDDOp, BDDNode> opCache;
	
	/**
	 * Used by BDD.satCount to memoize partial recursive solutions so as to avoid rework.
	 */
	private Map<BDDNode, Integer> satCountCache;
	
	/**
	 * Constructs a new BDDFactory with the given zero-indexed variables the given order. For example:
	 * 
	 * varOrdering = new int{1,0,2,3};
	 * 
	 * Specifies a factory with 4 variables, ordered 1, 0, 2, 3.
	 * 
	 * @param numVars
	 * @param varOrdering
	 */
	public BDDFactory(int[] varOrdering, int operatorCacheSize){
		int[] counts = new int[varOrdering.length];
		for(int i : varOrdering){
			try{
				if(++counts[i] > 1){
					throw new BDDException("Same variable " + i + " appeared multiple times in variable ordering!");
				}
			}catch(ArrayIndexOutOfBoundsException e){
				throw new BDDException("Variables in ordering must be 0 to ordering.length - 1. Got " + i, e);
			}
		}
		this.indexToVar = new int[varOrdering.length];
		this.varToIndex = new int[varOrdering.length];
		for(int i=0; i < varOrdering.length; i++){
			indexToVar[i] = varOrdering[i];
			varToIndex[indexToVar[i]] = i;
		}
		
		this.LO = new BDDNode(-1, null, null);
		this.LO_BDD = new BDD(LO);
		this.HI = new BDDNode(-1, null, null);
		this.HI_BDD = new BDD(HI);
		this.bddNodes = new WeakHashMap<BDDNode, BDDNode>();
		satCountCache = new HashMap<BDDNode, Integer>();
		this.opCache = new MaxSizeHashMap<BDDOp, BDDNode>(operatorCacheSize);
	}
	
	/**
	 * Returns the current variable ordering.
	 * @return
	 */
	public int[] getOrdering(){
		return Arrays.copyOf(varToIndex, varToIndex.length);
	}
	
	/**
	 * Return the BDD corresponding to the boolean constant "false"
	 * @return
	 */
	public BDD lo(){
		return LO_BDD;
	}
	
	/**
	 * Return the BDD corresponding to the boolean constant "true"
	 * @return
	 */
	public BDD hi(){
		return HI_BDD;
	}
	
	/**
	 * Return the BDD corresponding to the single-variable formula "var"
	 * @param var
	 * @return
	 */
	public BDD hiVar(int var){
		return new BDD(getNode(var, LO, HI));
	}
	
	/**
	 * Return the BDD corresponding to the single-variable formula "NOT(var)"
	 * @param var
	 * @return
	 */
	public BDD loVar(int var){
		return new BDD(getNode(var, HI, LO));
	}
	
	/**
	 * Return the BDD corresponding to the formula where each variable takes the
	 * value given in the assignment. 
	 * 
	 * @param assignment
	 * @return
	 */
	public BDD assignment(boolean[] assignment){
		if(assignment.length != varToIndex.length) throw new BDDException("Assignment length should match the number of variables!");
		BDD toReturn = assignment[0] ? hiVar(0):loVar(0);
		for(int i = 1; i < varToIndex.length; i++){
			toReturn = toReturn.and(assignment[i] ? hiVar(i) : loVar(i));
		}
		return toReturn;
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
	private BDDNode getNode(int var, BDDNode lo, BDDNode hi){
		if(var < 0 || var >= varToIndex.length) throw new BDDException("No such variable: " + var);
		// Optimization for reduce: if lo and hi are the same node, just return that node. 
		if(lo == hi) return lo;
		BDDNode key = new BDDNode(var, lo, hi);
		BDDNode sharedNode = bddNodes.get(key);
		if(sharedNode == null){
			sharedNode = key;
			bddNodes.put(sharedNode, sharedNode);
		}
		return sharedNode;
	}

	/**
	 * Immutable BDD class. Operations return new BDD object instances.
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
	public final class BDD implements Iterable<boolean[]>{
		/**
		 * Head node of this BDD in the shared BDD graph.
		 */
		private BDDNode ref;
		
		/**
		 * Creates a BDD represented by the given node of the given factory. 
		 * 
		 * USERS: Do not directly construct. Use BDDFactory or operations of another BDD.
		 * @param ref
		 * @param factory
		 */
		private BDD(BDDNode ref){
			this.ref = ref;
		}
		
		/**
		 * Return the logical NOT of this BDD
		 * 
		 * Time complexity: O(|this|)
		 * 
		 * @return
		 */
		public BDD not(){
			return apply(Operation.NOT, null);
		}
		
		/**
		 * Return the logical AND of this BDD with the other BDD
		 * 
		 * Time complexity: O(|this| * |other|)
		 * 
		 * @return
		 */
		public BDD and(BDD other){
			// Null check
			if(other == null) throw new BDDException("other must not be null!", new NullPointerException());
			// Different factories?
			if(BDDFactory.this != other.getFactory()) throw new BDDException("BDDs must come from the same factory!");
			// Same BDD?
			if(ref == other.ref) return other;
			
			return apply(Operation.AND, other);
		}
		
		/**
		 * Return the logical OR of this BDD with the other BDD
		 * 
		 * Time complexity: O(|this| * |other|)
		 * 
		 * @return
		 */
		public BDD or(BDD other){
			// Null check
			if(other == null) throw new BDDException("other must not be null!", new NullPointerException());
			// Different factories?
			if(BDDFactory.this != other.getFactory()) throw new BDDException("BDDs must come from the same factory!");
			// Same BDD?
			if(ref == other.ref) return other;

			return apply(Operation.OR, other);
		}
		
		/**
		 * Return the logical XOR of this BDD with the other BDD
		 * 
		 * Time complexity: O(|this| * |other|)
		 * 
		 * @return
		 */
		public BDD xor(BDD other){
			// Null check
			if(other == null) throw new BDDException("other must not be null!", new NullPointerException());
			// Different factories?
			if(BDDFactory.this != other.getFactory()) throw new BDDException("BDDs must come from the same factory!");
			// Same BDD?
			if(ref == other.ref) return LO_BDD;
			
			return apply(Operation.XOR, other);
		}
		
		/**
		 * Applies the given operation to this and the given BDD.
		 * 
		 * Time complexity: O(|this| * |other|)
		 * 
		 * @param op
		 * @param other
		 * @return
		 */
		public BDD apply(byte op, BDD other){
			BDDNode applied = apply(op, ref, other == null ? null:other.ref);
			if(applied == ref) return this;
			else if(other != null && applied == other.ref) return other;
			return new BDD(applied);
		}
		
		/**
		 * Applies the given operation to the given BDD nodes.
		 * 
		 * @param op
		 * @param first
		 * @param second
		 * @return
		 */
		private BDDNode apply(byte op, BDDNode first, BDDNode second){
			BDDOp key = new BDDOp(op, first, second);
			BDDNode cached = (BDDNode) opCache.get(key);
			if(cached == null){
				// First is a leaf node
				if(first.lo == null){
					// Second is also a leaf node
					if(second == null || second.lo == null){
						switch(op){
						case Operation.NOT:
							cached = (first == HI) ? LO : HI;
							break;
						case Operation.AND:
							cached = (first == HI && second == HI) ? HI : LO;
							break;
						case Operation.OR:
							cached = (first == HI || second == HI) ? HI : LO;
							break;
						case Operation.XOR:
							cached = (first == HI ^ second == HI) ? HI : LO;
							break;
						default:
							throw new BDDException("Unknown operator: " + op);
						}
					}
					// Second is a non-leaf
					else if(second != null){
						cached = getNode(second.var, apply(op, first, second.lo), apply(op, first, second.hi));
					}			
				}
				// First not a leaf, but second is a leaf
				else if(second != null && second.lo == null){	
					cached = getNode(first.var, apply(op, first.lo, second), apply(op, first.hi, second));
				}
				// Neither first nor second is a leaf node
				else{
					if(second == null){
						cached = getNode(first.var, apply(op, first.lo, null), apply(op, first.hi, null));
					}else if(first.var == second.var){
						cached = getNode(first.var, apply(op, first.lo, second.lo), apply(op, first.hi, second.hi));
					}else if(varToIndex[first.var] < varToIndex[second.var]){
						cached = getNode(first.var, apply(op, first.lo, second), apply(op, first.hi, second));
					}else{
						cached = getNode(second.var, apply(op, first, second.lo), apply(op, first, second.hi));
					}
				}
				opCache.put(key, cached);
			}
			
			return cached;
		}
		
		/**
		 * Returns whether this BDD represents the logical constant "false"
		 * 
		 * Time complexity: O(1)
		 * @return
		 */
		public boolean isLo(){
			return ref == LO;
		}

		/**
		 * Returns whether this BDD represents the logical constant "true"
		 * 
		 * Time complexity: O(1)
		 * @return
		 */
		public boolean isHi(){
			return ref == HI;
		}
		
		/**
		 * Returns the number of satisfying solutions to this BDD.
		 * 
		 * Time complexity: O(log(|this BDD|))
		 * @return
		 */
		public int satCount(){
			// Invariant: cache is empty
			int res = satCount(-1, ref);
			satCountCache.clear();
			return res;
		}
		
		/**
		 * Recursively returns the number of satisfying (paths to HI) from the current node.
		 * 
		 * @param current
		 * @return
		 */
		private int satCount(int lastVar, BDDNode current){
			// Non-leaf. First case here for speed.
			if(current.lo != null){
				// Power of two multiplier for "don't care" variables
				int skipped = lastVar == -1 ? varToIndex[current.var] : varToIndex[current.var] - varToIndex[lastVar] - 1;
				Integer memoized = (Integer) satCountCache.get(current);
				if(memoized == null){
					memoized = satCount(current.var, current.lo) + satCount(current.var, current.hi);
					satCountCache.put(current, memoized);
				}
				return memoized << skipped;
			}else if(current == HI){
				// Power of two multiplier for "don't care" variables
				int skipped = lastVar == -1 ? indexToVar.length : indexToVar.length - varToIndex[lastVar] - 1;
				return 1 << skipped;
			}else{
				return 0;
			}
		}
		
		/**
		 * Return the BDDFactory used to construct this BDD.
		 * @return
		 */
		public BDDFactory getFactory(){
			return BDDFactory.this;
		}
		
		/**
		 * Iterates over the solutions to this BDD. The returned solutions have the following meaning:
		 * 
		 * // One satisfying solution for the BDD 
		 * boolean[] sat = iter.next();
		 * 
		 * // Variable 3 has this value
		 * boolean varThree = sat[3];
		 * 
		 * NOTE: The returned array is re-used by the iterator for efficiency reasons.
		 */
		@Override
		public Iterator<boolean[]> iterator() {
			return new BDDSatIterator();
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
			BDD other = (BDD) obj;
			
			// Canonical representation of ROBDD allows us to do this
			return ref == other.ref;
		}
		
		/**
		 * Iterates over the satisfying assignments for this BDD.
		 * @author tdeering
		 *
		 */
		private class BDDSatIterator implements Iterator<boolean[]>{
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
			
			public BDDSatIterator(){
				// The constant LO bdd has no satisfying solutions, nothing to do
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

				public IterNode(int var, BDDNode lo, BDDNode hi){
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
	 * Node in the shared BDD universe graph.
	 * @author tdeering
	 *
	 */
	private final class BDDNode{
		int var;
		Integer hash;
		BDDNode lo, hi;
		
		private BDDNode(int var, BDDNode lo, BDDNode hi){
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
			BDDNode other = (BDDNode) obj;
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
		
		public BDDFactory getOuterType(){
			return BDDFactory.this;
		}
	}
	
	/**
	 * Used as key into the operation cache
	 * @author tdeering
	 *
	 */
	private final class BDDOp{
		byte op;
		BDDNode a, b;
		public BDDOp(byte op, BDDNode a, BDDNode b){
			this.op = op;
			this.a = a;
			this.b = b;
		}
		@Override
		public int hashCode() {
			return op + a.hashCode() + (b == null ? 0:b.hashCode());
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BDDOp other = (BDDOp) obj;
			if(!getOuterType().equals(other.getOuterType()))
				return false;
			if(op != other.op)
				return false;
			return (a == other.a && b == other.b) ||
				   (a == other.b && b == other.a);
		}
		
		public BDDFactory getOuterType(){
			return BDDFactory.this;
		}
	}
}