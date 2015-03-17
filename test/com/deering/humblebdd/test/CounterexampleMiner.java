package com.deering.humblebdd.test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

import com.deering.humblebdd.zdd.ZDDFactory;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;
import com.google.common.math.BigIntegerMath;

public class CounterexampleMiner {
	public static void main(String[] args) throws InterruptedException{
		CounterexampleMiner kt = new CounterexampleMiner(Integer.parseInt(args[0]));
		kt.runTest();
	}
	
	/**
	 * Number of families in each bucket of families.
	 */
	private BigInteger[] chooseBuckets;
	
	/**
	 * The power set of the universe.
	 */
	private int[][] powerSet;
	
	/**
	 * The number of elements in the universe
	 */
	private int universeSize;
	
	/**
	 * Construct a new test on the universe of the given size.
	 * @param universeSize
	 */
	public CounterexampleMiner(int universeSize){
		this.universeSize = universeSize;
	}
	
	/**
	 * Run the test, searching for a counterexample to our rule.
	 * 
	 * @throws InterruptedException
	 */
	private void runTest() throws InterruptedException{
		System.out.println("Computing sizes of choose buckets");
		int numSets = 1 << universeSize;
		powerSet = new int[numSets][];
		chooseBuckets = new BigInteger[numSets + 1];
		for(int i = 0; i <= numSets; ++i){
			if(i < numSets){
				powerSet[i] = new int[Integer.bitCount(i)];
				int idx = 0;
				for(int j = 0; j < 32; ++j){
					if(((i >> j) & 0x1) == 1)
						powerSet[i][idx++] = j;
				}
			}
			
			chooseBuckets[i] = BigIntegerMath.binomial(numSets, i);
		}
		
		System.out.println("Assigning famlies to threads");
		BigInteger numFamilies = BigInteger.ONE.shiftLeft(numSets);
		int numCPUs = Runtime.getRuntime().availableProcessors();
		Miner[] jobs = new Miner[numCPUs];
		ArrayList<Thread> running = new ArrayList<Thread>(numCPUs);
		BigInteger workPerThread = numFamilies.divide(BigInteger.valueOf(numCPUs));
		BigInteger remainder = numFamilies.mod(BigInteger.valueOf(numCPUs));
		for(int i = 0; i < numCPUs; ++i){
			BigInteger rank = BigInteger.valueOf(i);
			BigInteger first = workPerThread.multiply(rank);
			first = first.add(rank.compareTo(remainder) < 0 ? rank:remainder);
			BigInteger last = first.add(workPerThread).subtract(BigInteger.ONE);
			if(rank.compareTo(remainder) <= 0) last = last.add(BigInteger.ONE);
			
			jobs[i] = new Miner(first, last);
			Thread t = new Thread(jobs[i]);
			running.add(t);
			t.start();
		}
		
		// Wait for all explorer threads to complete
		System.out.println("Waiting for threads to finish exploring families");
		while(!running.isEmpty()){
			if(running.get(0).isAlive()){
				Thread.sleep(1000);
			}else{
				running.remove(0);
			}
		}

		// Find the counterexample, if there is one
		Miner example = null;
		for(Miner oe : jobs){
			if(oe.heuristicZDD != null){
				example = oe;
				break;
			}
		}
		
		if(example == null){
			System.out.println("Unable to find a counterexample");
		}else{
			System.out.println("Found counterexample!");
			System.out.println("Universe size: " + universeSize);
			System.out.println("Family: " + twoDimArrayString(example.counterFamily));
			System.out.println("Heuristic Size: " + example.heuristicZDD.getFactory().size());
			System.out.println("Heuristic Order: " + Arrays.toString(example.heuristicZDD.getFactory().getOrdering()));
			System.out.println("Heuristic ZDD: " + example.heuristicZDD);
			System.out.println("Optimal Size: " + example.optimalZDD.getFactory().size());
			System.out.println("Optimal Order: " + Arrays.toString(example.optimalZDD.getFactory().getOrdering()));
			System.out.println("Optimal ZDD: " + example.optimalZDD);
		}
	}
	
	/**
	 * Given a family identifier (between 0 and 2^2^N), construct the specific family.
	 * 
	 * @param id
	 * @return
	 */
	private int[][] constructFamily(BigInteger id){
		if(BigInteger.ZERO.equals(id)) return new int[0][];
		
		// Determine the cardinality of the family
		BigInteger cumulative = BigInteger.ONE;
		int m = 1;
		while(id.compareTo(cumulative) >= 0 && m < chooseBuckets.length - 1){
			cumulative = cumulative.add(chooseBuckets[++m]); 
		}
		
		// Determine the family within the bucket
		BigInteger familyIdx = id.add(chooseBuckets[m]).subtract(cumulative);
		
		// Find the family which is the Cth combination of M elements
		int[][] family = new int[m][];
		buildFamily(familyIdx, family, powerSet.length, m, 0);
		return family;
	}
	
	/**
	 * Recursively build a family of sets based on a combination index.
	 * 
	 * @param comboIdx Index of the combination within the remaining combinations
	 * @param family Family of sets that we're building
	 * @param n Remaining sets to choose from
	 * @param k Remaining number of sets to choose
	 * @param powerSetIdx Index in the power set from which to choose or not choose our next set
	 */
	private void buildFamily(BigInteger comboIdx, int[][] family, int n, int k, int powerSetIdx){
		if(n == 0 || k == 0) return;
		
		// We must choose all remaining sets from the power set
		if(k < n){
			// Split the number of remaining choices into two subgroups
			// Those which choose the next power set element
			BigInteger include = BigIntegerMath.binomial(n-1, k-1);
			// Those which do not
			BigInteger dontInclude = BigIntegerMath.binomial(n-1, k);
			
			if(comboIdx.compareTo(include) < 0){
				family[family.length - k] = powerSet[powerSetIdx];
				buildFamily(comboIdx.subtract(dontInclude), family, n - 1, k - 1, powerSetIdx + 1);
			}else{
				buildFamily(comboIdx.subtract(include), family, n - 1, k, powerSetIdx + 1);
			}
		}
		// We may potentially not choose this set from the power set
		else{
			for(int i = 0; i < k; ++i){
				family[family.length - i - 1] = powerSet[powerSetIdx + i];
			}
		}
	}
	
	/**
	 * Worker thread to explore a subset of families, searching for a counterexample.
	 * 
	 * @author tdeering
	 *
	 */
	private class Miner implements Runnable{
		BigInteger firstFamily, lastFamily;
		
		ZDD heuristicZDD = null;
		ZDD optimalZDD = null;
		int[][] counterFamily = null;
		
		public Miner(BigInteger firstFamily, BigInteger lastFamily){
			this.firstFamily = firstFamily;
			this.lastFamily = lastFamily;
		}

		@Override
		public void run() {
			// Iterate over all assigned families
			for(BigInteger familyID = firstFamily; heuristicZDD == null && familyID.compareTo(lastFamily) <= 0; familyID = familyID.add(BigInteger.ONE)){				
				// Construct the family for the given identifier
				int[][] family = constructFamily(familyID);
				
				// Get the element order in decreasing frequency
				FrequencyCount[] frequencyCounts = new FrequencyCount[universeSize];
				for(int i = 0; i < universeSize; ++i) frequencyCounts[i] = new FrequencyCount(i);
				for(int[] set : family) for(int i : set) ++frequencyCounts[i].count;
				Arrays.sort(frequencyCounts);
				int[] decreasingFrequency = toOrder(frequencyCounts);
				
				// Represent our heuristic ZDD
				ZDDFactory heuristicFactory = new ZDDFactory(decreasingFrequency, 1000);
				ZDD heuristic = heuristicFactory.family(family);
				
				// Find the optimal ordering over all permuted orderings
				ZDD optimal = null;
				int[] permuted = Arrays.copyOf(decreasingFrequency, decreasingFrequency.length);
				Permutations p = new Permutations(decreasingFrequency, permuted);
				while(p.next()){
					ZDDFactory testFactory = new ZDDFactory(permuted, 1000);
					ZDD test = testFactory.family(family);
					
					if(optimal == null || testFactory.size() < optimal.getFactory().size()){
						optimal = test;
					}
				}
				
				// If we find a counterexample, we're done.
				if(heuristic.getFactory().size() > optimal.getFactory().size() + universeSize - 2){
					heuristicZDD = heuristic;
					optimalZDD = optimal;
					counterFamily = family;
				}
			}
		}
	}
	
	private static int[] toOrder(FrequencyCount[] counts){
		int[] res = new int[counts.length];
		for(int i = 0; i < counts.length; ++i){
			res[i] = counts[i].element;
		}
		return res;
	}
	
	private static String twoDimArrayString(int[][] order){
		StringBuilder sb = new StringBuilder();
		
		sb.append('[');
		for(int i = 0; i < order.length; ++i){
			sb.append(Arrays.toString(order[i]));
			if(i < order.length - 1) sb.append(',');
		}
		sb.append(']');
	
		return sb.toString();
	}

	private static class FrequencyCount implements Comparable<FrequencyCount> {
		int element;
		Integer count = 0;
		
		public FrequencyCount(int element){
			this.element = element;
		}

		@Override
		public int compareTo(FrequencyCount o) {
			return count.compareTo(o.count) * -1;
		}
	}
	
	private static class Permutations {
	    private int[] in;
	    private int[] out;
	    private int n, m;
	    private int[] index;
	    private boolean hasNext = true;

	    private Permutations(int[] in, int[] out) {
	        this.n = in.length;
	        this.m = in.length;
	        this.in = in;
	        this.out = out;
	        index = new int[n];
	        for (int i = 0; i < n; i++) {
	            index[i] = i;
	        }
	        reverseAfter(m - 1);
	    }

	    private void moveIndex() {
	        int i = rightmostDip();
	        if (i < 0) {
	            hasNext = false;
	            return;
	        }

	        int leastToRightIndex = i + 1;
	        for (int j = i + 2; j < n; j++) {
	            if (index[j] < index[leastToRightIndex] && index[j] > index[i]) {
	                leastToRightIndex = j;
	            }
	        }

	        int t = index[i];
	        index[i] = index[leastToRightIndex];
	        index[leastToRightIndex] = t;

	        if (m - 1 > i) {
	            reverseAfter(i);
	            reverseAfter(m - 1);
	        }
	    }

	    private int rightmostDip() {
	        for (int i = n - 2; i >= 0; i--) {
	            if (index[i] < index[i + 1]) {
	                return i;
	            }
	        }
	        return -1;
	    }
	    
	    private void reverseAfter(int i) {
	        int start = i + 1;
	        int end = n - 1;
	        while (start < end) {
	            int t = index[start];
	            index[start] = index[end];
	            index[end] = t;
	            start++;
	            end--;
	        }
	    }

	    public boolean next() {
	        if (!hasNext) {
	            return false;
	        }
	        for (int i = 0; i < m; i++) {
	            out[i] = in[index[i]];
	        }
	        moveIndex();
	        return true;
	    }
	}
}