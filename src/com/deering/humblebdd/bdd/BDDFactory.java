package com.deering.humblebdd.bdd;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Stack;

import com.deering.humblebdd.DDFactory;
import com.deering.humblebdd.HumbleException;
import com.deering.humblebdd.zdd.ZDDFactory;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;

/**
 * A factory for shared, reduced, ordered, BDDs (SROBDDs).
 * Supports up to Integer.MAX_VALUE variables.
 * 
 * USAGE EXAMPLE:
 * 
 * // New factory with 4 variables in the given order and an operator cache of size 10
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
public final class BDDFactory extends DDFactory{
	/**
	 * Unary logical NOT
	 */
	public static final int NOT = 1;
	
	/**
	 * Binary logical AND
	 */
	public static final int AND = 2;
	
	/**
	 * Binary logical OR
	 */
	public static final int OR = 3;
	
	/**
	 * Binary logical XOR
	 */
	public static final int XOR = 4;
	
	/**
	 * Unary solution count
	 */
	public static final int COUNT = 5;

	/**
	 * BDD wrapping LO.
	 */
	private BDD LO_BDD;
	
	/**
	 * BDD wrapping HI.
	 */
	private BDD HI_BDD;
	
	/**
	 * Constructs a new BDDFactory with the given zero-indexed variables the given order. For example:
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
	public BDDFactory(int[] varOrdering, int operatorCacheSize){
		super(varOrdering, operatorCacheSize);
		this.LO_BDD = new BDD(LO);
		this.HI_BDD = new BDD(HI);
	}

	@Override
	protected DDNode nodeElimination(int var, DDNode lo, DDNode hi) {
		// BDD node elimination rule
		if(lo == hi) return lo;
		return null;
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
		if(assignment.length != v2i.length) throw new HumbleException("Assignment length should match the number of variables!");
		BDD toReturn = assignment[0] ? hiVar(0):loVar(0);
		for(int i = 1; i < v2i.length; i++){
			toReturn = toReturn.and(assignment[i] ? hiVar(i) : loVar(i));
		}
		return toReturn;
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
	public final class BDD extends DD{
		/**
		 * Creates a BDD represented by the given node of the given factory. 
		 * 
		 * USERS: Do not directly construct. Use BDDFactory or operations of another BDD.
		 * @param ref
		 * @param factory
		 */
		private BDD(DDNode ref){
			super(ref);
		}
		
		/**
		 * Return the logical NOT of this BDD
		 * 
		 * Time complexity: O(|this|)
		 * 
		 * @return
		 */
		public BDD not(){
			return new BDD((DDNode) apply(NOT, ref, null));
		}
		
		/**
		 * Return the logical AND of this BDD with the other BDD
		 * 
		 * Time complexity: O(|this| * |other|)
		 * 
		 * @return
		 */
		public BDD and(BDD other){
			// Same BDD?
			if(ref == other.ref) return other;
			return new BDD((DDNode) apply(AND, ref, other.ref));
		}
		
		/**
		 * Return the logical OR of this BDD with the other BDD
		 * 
		 * Time complexity: O(|this| * |other|)
		 * 
		 * @return
		 */
		public BDD or(BDD other){
			// Same BDD?
			if(ref == other.ref) return other;
			return new BDD((DDNode) apply(OR, ref, other.ref));
		}
		
		/**
		 * Return the logical XOR of this BDD with the other BDD
		 * 
		 * Time complexity: O(|this| * |other|)
		 * 
		 * @return
		 */
		public BDD xor(BDD other){
			// Same BDD?
			if(ref == other.ref) return LO_BDD;
			return new BDD((DDNode) apply(XOR, ref, other.ref));
		}
		
		@SuppressWarnings("unused")
		@Override
		protected Object compute(int op, DDNode first, Object other) {
			DDNode second = (DDNode) other;
			// First is a leaf node
			if(first.lo == null){
				// Second is also a leaf node
				if(second == null || second.lo == null){
					switch(op){
					case NOT:
						return (first == HI) ? LO : HI;
					case AND:
						return (first == HI && second == HI) ? HI : LO;
					case OR:
						return (first == HI || second == HI) ? HI : LO;
					case XOR:
						return (first == HI ^ second == HI) ? HI : LO;
					case COUNT:
						return first == HI ? 1 : 0;
					default:
						throw new HumbleException("Unknown operator: " + op);
					}
				}
				// Second is a non-leaf
				else if(second != null){
					return getNode(second.var, (DDNode) apply(op, first, second.lo), (DDNode) apply(op, first, second.hi));
				}			
			}
			// First not a leaf, but second is a leaf
			else if(second != null && second.lo == null){	
				return getNode(first.var, (DDNode) apply(op, first.lo, second), (DDNode) apply(op, first.hi, second));
			}
			// Neither first nor second is a leaf node
			else{
				if(second == null){
					if(op == COUNT){
						int sub1 = (Integer) apply(op, first.lo, null);
						int sub2 = (Integer) apply(op, first.hi, null);
						int shift1 = (first.lo.lo == null ? v2i.length : v2i[first.lo.var]) - v2i[first.var] - 1;
						int shift2 = (first.hi.lo == null ? v2i.length : v2i[first.hi.var]) - v2i[first.var] - 1;
						return (sub1 << shift1) + (sub2 << shift2);
					}else{
						return getNode(first.var, (DDNode) apply(op, first.lo, null), (DDNode) apply(op, first.hi, null));
					}
				}else if(first.var == second.var){
					return getNode(first.var, (DDNode) apply(op, first.lo, second.lo), (DDNode) apply(op, first.hi, second.hi));
				}else if(v2i[first.var] < v2i[second.var]){
					return getNode(first.var, (DDNode) apply(op, first.lo, second), (DDNode) apply(op, first.hi, second));
				}else{
					return getNode(second.var, (DDNode) apply(op, first, second.lo), (DDNode) apply(op, first, second.hi));
				}
			}
			return null;
		}
		
		/**
		 * Returns the number of satisfying solutions to this BDD.
		 * 
		 * Time complexity: O(log(|this BDD|))
		 * @return
		 */
		public int satCount(){
			return (int) apply(COUNT, ref, null) << v2i[ref.var];
		}
		
		/**
		 * Convert this BDD into a ZDD in the given factory.
		 * 
		 * TODO This can be done more efficiently than enumerating the satisfying solutions
		 * 
		 * @param factory
		 * @return
		 */
		public ZDD toZDD(ZDDFactory factory){
			ZDD res = factory.empty();

			for(boolean[] solution : this){
				int trueCount = 0;
				for(boolean b : solution) if(b) trueCount++;
				
				int idx = 0;
				int[] set = new int[trueCount];
				for(int i = 0; i < solution.length; ++i) if(solution[i]) set[idx++] = i;
				
				res = res.union(factory.family(new int[][]{set}));
			}
			
			return res;
		}
		
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
					solution = new boolean[v2i.length];
					nextSolution = new boolean[v2i.length];
					if(ref == HI || i2v[0] != ref.var){
						dfsLocation = new IterNode(i2v[0], ref, ref);
					}else{
						dfsLocation = new IterNode(i2v[0], ref.lo, ref.hi);
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

				public IterNode(int var, DDNode lo, DDNode hi){
					this.var = var;
					// Non-leaf
					if(var >= 0){
						// Index of the variable that follows this one in the ordering
						int nextIdx = v2i[var] + 1;
						
						// If there is a successor in the ordering
						if(nextIdx < v2i.length){
							// Find it
							int nextVar = i2v[nextIdx];
							
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
}