package com.deering.humblebdd.zdd;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

import com.deering.humblebdd.DDFactory;
import com.deering.humblebdd.HumbleException;
import com.deering.humblebdd.bdd.BDDFactory;
import com.deering.humblebdd.bdd.BDDFactory.BDD;

/**
 * A factory for shared, reduced, ordered, zero-suppressed BDDs (ZDDs).
 * Supports up to Integer.MAX_VALUE variables.
 * 
 * USAGE EXAMPLE:
 * 
 * TODO 
 * 
 * @author tdeering
 *
 */
public final class ZDDFactory extends DDFactory{
	/**
	 * Unary subset of the family that contains this variable, with this variable removed.
	 */
	public static final int SUBSET1 = 1;
	
	/**
	 * Unary subset of the family that does not contain this variable
	 */
	public static final int SUBSET0 = 2;
	
	/**
	 * Unary inversion of the variable
	 */
	public static final int CHANGE = 3;

	/**
	 * Unary count of the number of elements
	 */
	public static final int ELEMENT_COUNT = 4;
	
	/**
	 * Unary count of the number of sets
	 */
	public static final int SET_COUNT = 5;
	
	/**
	 * Binary union of two set families
	 */
	public static final int UNION = 6;
	
	/**
	 * Binary difference of two set families
	 */
	public static final int DIFFERENCE = 7;
	
	/**
	 * Binary intersection of two set families
	 */
	public static final int INTERSECTION = 8;

	/**
	 * ZDD wrapping LO.
	 */
	private ZDD LO_ZDD;
	
	/**
	 * ZDD wrapping HI
	 */
	private ZDD HI_ZDD;
	
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
		super(varOrdering, operatorCacheSize);
		this.LO_ZDD = new ZDD(LO);
		this.HI_ZDD = new ZDD(HI);
	}
	
	@Override
	protected DDNode nodeElimination(int var, DDNode lo, DDNode hi) {
		// ZDD elimination rule.
		if(hi == LO) return lo;
		return null;
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
	 * Return the ZDD set family containing only the empty set.
	 * 
	 * @return
	 */
	public ZDD base(){
		return HI_ZDD;
	}
	
	/**
	 * Return the ZDD representing the given family of sets
	 * 
	 * @param var
	 * @return
	 */
	public ZDD family(int[][] sets){
		Set<Set<Integer>> familyList = new HashSet<Set<Integer>>(sets.length);
		for(int[] set : sets){
			Set<Integer> setList = new HashSet<Integer>(set.length);
			for(int i : set){
				if(i < 0 || i >= v2i.length) throw new HumbleException("No such variable: " + i);
				setList.add(i);
			}
			familyList.add(setList);
		}
			
		return new ZDD(family(i2v[0], familyList));
	}
	
	/**
	 * Recursively construct the requested set family.
	 * 
	 * @param var
	 * @param familyList
	 * @return
	 */
	private DDNode family(int var, Set<Set<Integer>> familyList){
		if(familyList.isEmpty()) return LO;
		if(familyList.size() == 1 && familyList.iterator().next().isEmpty()) return HI;
		
		// Construct F0 and F1
		Set<Set<Integer>> f0 = new HashSet<Set<Integer>>(familyList.size());
		Set<Set<Integer>> f1 = new HashSet<Set<Integer>>(familyList.size());
		
		boolean anyContained = false;
		for(Set<Integer> set : familyList){
			if(set.contains(var)){
				anyContained = true;
				Set<Integer> s1 = new HashSet<Integer>(set);
				s1.remove(var);
				f1.add(s1);
			}else{
				f0.add(set);
			}
		}
		int nextVarIdx = v2i[var] + 1;
		if(nextVarIdx < v2i.length){
			int nextVar = i2v[nextVarIdx];
			if(anyContained){
				return getNode(var, family(nextVar, f0), family(nextVar,f1));
			}else{
				return family(nextVar, familyList);
			}
		}else{
			return getNode(var, f0.isEmpty() ? LO:HI, f1.isEmpty() ? LO:HI);
		}
	}
	
	/**
	 * Immutable ZDD class. Operations return new ZDD object instances.
	 * 
	 * Time complexity of operations:
	 * 
	 * union(this, other): O(|this| + |other|)
	 * difference(this, other): O(|this| + |other|)
	 * intersection(this, other): O(|this| + |other|)
	 * equals(this, other): O(1)
	 * isEmpty(this): O(1)
	 * count(this): O(|this|)
	 * subsetHi(this, var): O(|this|)
	 * subsetLo(this, var): O(|this|)
	 * toggle(this, var): O(|this|)
	 * 
	 * 
	 * @author tdeering
	 *
	 */
	public final class ZDD extends DD{
		/**
		 * Head node of this ZDD in the shared ZDD graph.
		 */
		private DDNode ref;
		
		/**
		 * Creates a ZDD represented by the given node of the given factory. 
		 * 
		 * USERS: Do not directly construct. Use ZDDFactory or operations of another ZDD.
		 * @param ref
		 * @param factory
		 */
		private ZDD(DDNode ref){
			super(ref);
			this.ref = ref;
		}
		
		/**
		 * Returns the subset of variables >= the given var
		 * 
		 * @param zdd
		 * @param var
		 * @return
		 */
		public ZDD subset1(int var){
			return new ZDD((DDNode) apply(SUBSET1, ref, var));
		}
		
		/**
		 * Returns the subset of variables <= the given var
		 * 
		 * @param zdd
		 * @param var
		 * @return
		 */
		public ZDD subset0(int var){
			return new ZDD((DDNode) apply(SUBSET0, ref, var));
		}
		
		/**
		 * Returns this ZDD with the given variable toggled
		 * 
		 * @param var
		 * @return
		 */
		public ZDD change(int var){
			return new ZDD((DDNode) apply(CHANGE, ref, var));
		}
		
		/**
		 * Returns the union of this ZDD and the other ZDD
		 * 
		 * @param other
		 * @return
		 */
		public ZDD union(ZDD other){
			return new ZDD((DDNode) apply(UNION, ref, other.ref));
		}
		
		/**
		 * Returns the intersection of this ZDD and the other ZDD
		 * 
		 * @param other
		 * @return
		 */
		public ZDD intersection(ZDD other){
			return new ZDD((DDNode) apply(INTERSECTION, ref, other.ref));
		}
		
		/**
		 * Returns the difference of this ZDD and the other ZDD
		 * 
		 * @param other
		 * @return
		 */
		public ZDD difference(ZDD other){
			return new ZDD((DDNode) apply(DIFFERENCE, ref, other.ref));
		}
		
		/**
		 * Returns the number of variables in this ZDD
		 * 
		 * @return
		 */
		public int count(){
			return (Integer) apply(ELEMENT_COUNT, ref, null);
		}
		
		@Override
		protected Object compute(int op, DDNode first, Object second) {
			switch(op){
			case SUBSET1:
				if(v2i[first.var] > v2i[(Integer) second]) return LO;
				if(first.var == (Integer) second) return ref.hi;
				return getNode(first.var, (DDNode) apply(op, first.lo, second), (DDNode) apply(op, first.hi, second));
			case SUBSET0:
				if(v2i[first.var] > v2i[(Integer) second]) return first;
				if(first.var == (Integer) second) return first.lo;
				return getNode(first.var, (DDNode) apply(op, first.lo, second), (DDNode) apply(op, first.hi, second)); 
			case CHANGE:
				if(v2i[ref.var] > v2i[(Integer) second]) return getNode(first.var, LO, first);
				if(ref.var == (Integer) second) return getNode(first.var, first.hi, first.lo);
				return getNode(op, (DDNode) apply(op, first.lo, second), (DDNode) apply(op, first.hi, second));
			case UNION:
				if(first == LO) return second;
				if(second == LO) return first;
				if(first == second) return first;
				if(v2i[first.var] < v2i[((DDNode)second).var]) return getNode(first.var, (DDNode) apply(op, first.lo, second), first.hi);
				if(v2i[first.var] > v2i[((DDNode)second).var]) return getNode(((DDNode)second).var, (DDNode) apply(op, first, ((DDNode)second).lo), ((DDNode)second).hi);
				return getNode(first.var, (DDNode) apply(op, first.lo, ((DDNode)second).lo), (DDNode) apply(op, first.hi, ((DDNode)second).hi));
			case INTERSECTION:
				if(first == LO) return LO;
				if(second == LO) return LO;
				if(first == second) return first;
				if(v2i[first.var] < v2i[((DDNode)second).var]) return apply(op, first.lo, second);
				if(v2i[first.var] > v2i[((DDNode)second).var]) return apply(op, first, ((DDNode)second).lo);
				return getNode(first.var, (DDNode) apply(op, first.lo, ((DDNode)second).lo), (DDNode) apply(op, first.hi, ((DDNode)second).hi));
			case DIFFERENCE:
				if(first == LO) return LO;
				if(second == LO) return first;
				if(first == second) return LO;
				if(v2i[first.var] < v2i[((DDNode)second).var]) return getNode(first.var, (DDNode) apply(op, first.lo, second), first.hi);
				if(v2i[first.var] > v2i[((DDNode)second).var]) return apply(op, first, ((DDNode)second).lo);
				return getNode(first.var, (DDNode) apply(op, first.lo, ((DDNode)second).lo), (DDNode) apply(op, first.hi, ((DDNode)second).hi));
			case ELEMENT_COUNT:
				if(first == LO) return 0;
				else if(first == HI) return 1;
				else return ((Integer) apply(op, first.lo, second)) + ((Integer) apply(op, first.hi, second));
			default:
				throw new HumbleException("Unsupported op code: " + op);
			}
		}

		/**
		 * Convert this ZDD into a BDD in the given factory.
		 * 
		 * @param factory
		 * @return
		 */
		public BDD toBDD(BDDFactory factory){
			// TODO make this more efficient
			BDD res = factory.lo();

			for(boolean[] family : this){
				res = res.or(factory.assignment(family));
			}
			
			return res;
		}
		
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
		 * Iterates over the satisfying assignments for this BDD.
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
				// The constant LO zdd has no family members
				if(ref != LO){
					stack = new Stack<IterNode>();
					solution = new boolean[v2i.length];
					nextSolution = new boolean[v2i.length];
					dfsLocation = new IterNode(ref.var, ref.lo, ref.hi);
					
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
					if(var >= 0){
						this.hi = new IterNode(hi.var, hi.lo, hi.hi);
						if(lo != LO) this.lo = new IterNode(lo.var, lo.lo, lo.hi);
					}
				}
			}
		}
	}
}