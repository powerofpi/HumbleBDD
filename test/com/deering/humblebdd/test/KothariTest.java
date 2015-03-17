package com.deering.humblebdd.test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javafx.concurrent.Worker.State;

import com.deering.humblebdd.zdd.ZDDFactory;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;

public class KothariTest {
	/**
	 * Number of families in each bucket of families.
	 */
	private static BigInteger[] chooseBuckets;
	
	/**
	 * The power set of the universe.
	 */
	private static int[][] powerSet;
	
	public static void main(String[] args) throws InterruptedException{
		int universeSize = Integer.parseInt(args[0]);

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
			
			chooseBuckets[i] = choose(numSets, i);
		}
		
		System.out.println("Assigning famlies to threads");
		BigInteger numFamilies = BigInteger.valueOf(2).shiftLeft(numSets);
		int numCPUs = Runtime.getRuntime().availableProcessors();
		OrderExplorer[] jobs = new OrderExplorer[numCPUs];
		ArrayList<Thread> running = new ArrayList<Thread>(numCPUs);
		BigInteger workPerThread = numFamilies.divide(BigInteger.valueOf(numCPUs));
		BigInteger remainder = numFamilies.mod(BigInteger.valueOf(numCPUs));
		for(int i = 0; i < numCPUs; ++i){
			BigInteger rank = BigInteger.valueOf(i);
			BigInteger first = workPerThread.multiply(rank);
			first = first.add(rank.compareTo(remainder) < 0 ? rank:remainder);
			BigInteger last = first.add(workPerThread).subtract(BigInteger.ONE);
			
			jobs[i] = new OrderExplorer(first, last, universeSize);
			Thread t = new Thread(jobs[i]);
			running.add(t);
			t.start();
		}
		
		// Wait for all explorer threads to complete
		System.out.println("Waiting for threads to finish exploring families");
		while(!running.isEmpty()){
			Thread t = running.get(0);
			if(State.RUNNING.equals(t.getState())){
				Thread.sleep(1000);
			}else{
				running.remove(0);
			}
		}
		
		OrderExplorer example = null;
		for(OrderExplorer oe : jobs){
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
	
	private static int[][] constructFamily(BigInteger id){
		if(BigInteger.ZERO.equals(id)) return new int[0][];
		
		// Determine the cardinality of the family
		BigInteger cumulative = BigInteger.ONE;
		int m = 1;
		while(id.compareTo(cumulative) >= 0){
			cumulative = cumulative.add(chooseBuckets[++m]); 
		}
		
		// Determine the family within the bucket
		BigInteger familyIdx = id.add(chooseBuckets[m]).subtract(cumulative);
		
		// Find the family which is the Cth combination of M elements
		int[][] family = new int[m][];
		buildFamily(familyIdx, family, powerSet.length, m, 0);
		return family;
	}
	
	private static void buildFamily(BigInteger choiceIdx, int[][] family, int n, int k, int powerSetIdx){
		if(n == 0 || k == 0) return;
		
		// Split the number of remaining choices into two subgroups
		// Those which choose the next power set element
		BigInteger include = choose(n-1, k-1);
		// Those which do not
		BigInteger dontInclude = choose(n-1, k);
		
		if(choiceIdx.compareTo(include) < 0){
			family[family.length - k - 1] = powerSet[powerSetIdx];
			buildFamily(choiceIdx.subtract(dontInclude), family, n - 1, k - 1, powerSetIdx + 1);
		}else{
			buildFamily(choiceIdx.subtract(include), family, n - 1, k, powerSetIdx + 1);
		}
	}
	
	private static class OrderExplorer implements Runnable{
		BigInteger firstFamily, lastFamily;
		int universeSize;
		
		ZDD heuristicZDD = null;
		ZDD optimalZDD = null;
		int[][] counterFamily = null;
		
		public OrderExplorer(BigInteger firstFamily, BigInteger lastFamily, int universeSize){
			this.firstFamily = firstFamily;
			this.lastFamily = lastFamily;
			this.universeSize = universeSize;
		}

		@Override
		public void run() {
			// Iterate over all assigned families
			for(BigInteger familyID = firstFamily; heuristicZDD == null && familyID.compareTo(lastFamily) <= 0; familyID = familyID.add(BigInteger.ONE)){
				// Construct the family
				int[][] family = constructFamily(familyID);
				FrequencyCount[] frequencyCounts = new FrequencyCount[universeSize];
				for(int i = 0; i < universeSize; ++i) frequencyCounts[i] = new FrequencyCount(i);
				
				// Determine N, the number of variables in the family
				for(int[] set : family){
					for(int i : set){
						++frequencyCounts[i].count;
					}
				}
				
				// Get the element order in decreasing frequency
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
	
/*	private static void shuffle(int[] arr, Random rand){
		int tmp;
		for(int i = 0; i < arr.length; ++i){
			int newIdx = rand.nextInt(arr.length);
			tmp = arr[newIdx];
			arr[newIdx] = arr[i];
			arr[i] = tmp;
		}
	}*/

	private static BigInteger choose(int n, int k){
		return factorial(BigInteger.valueOf(n)).divide(
				factorial(BigInteger.valueOf(n-k)).multiply(factorial(BigInteger.valueOf(k))));
	}
	
	public static BigInteger factorial(BigInteger n) {
	    BigInteger result = BigInteger.ONE;

	    while (!n.equals(BigInteger.ZERO)) {
	        result = result.multiply(n);
	        n = n.subtract(BigInteger.ONE);
	    }

	    return result;
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