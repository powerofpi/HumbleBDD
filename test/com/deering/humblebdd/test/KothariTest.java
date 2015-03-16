package com.deering.humblebdd.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.deering.humblebdd.zdd.ZDDFactory;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;

public class KothariTest {
	
	public static void main(String[] args){
		
		int numVariables = Integer.parseInt(args[0]);
		int numAttempts = Integer.parseInt(args[1]);
		
		Random rand = new Random(System.currentTimeMillis());
		
		// Try the specified number of times to find a counterexample
		for(int i = 0; i < numAttempts; ++i){
			// Randomly select the number of sets in the family
			int familySize = rand.nextInt((1 << numVariables));
			
			// Fill up the family with distinct randomly-generated sets
			Set<Set<Integer>> family = new HashSet<Set<Integer>>(familySize);
			while(family.size() < familySize){
				Set<Integer> set = new HashSet<Integer>(numVariables);
				// Add each element with probability 1/2
				for(int j = 0; j < numVariables; ++j) if(rand.nextBoolean()) set.add(j);
				family.add(set);
			}
			
			int[][] familyArr = new int[familySize][];
			
			// Tally the element frequency
			FrequencyCount[] frequencyCounts = new FrequencyCount[numVariables];
			for(int j = 0; j < numVariables; ++j) frequencyCounts[j] = new FrequencyCount(j);
			int familyIdx = 0;
			for(Set<Integer> set : family){
				familyArr[familyIdx] = new int[set.size()];
				int setIdx = 0;
				for(Integer j : set){
					familyArr[familyIdx][setIdx++] = j;
					++frequencyCounts[j].count;
				}
				familyIdx++;
			}
			
			// Get decreasing, and random orders
			Arrays.sort(frequencyCounts);
			int[] decreasingOrder = toOrder(frequencyCounts);
			int[] randomOrder = Arrays.copyOf(decreasingOrder, decreasingOrder.length);
			shuffle(randomOrder, rand);
			
			// Construct a ZDD with each order
			ZDDFactory zDec = new ZDDFactory(decreasingOrder, 10000);
			ZDDFactory zRand = new ZDDFactory(randomOrder, 10000);
			ZDD zddDec = zDec.family(familyArr);
			ZDD zddRand = zRand.family(familyArr);
			
			// Report counterexample
			if(zDec.size() > zRand.size()){
				int[] frequencies = new int[numVariables];
				for(FrequencyCount count : frequencyCounts){
					frequencies[count.element] = count.count;
				}
			
				System.out.println("Found counterexample to decreasing frequency heuristic!");
				System.out.println("Set family: " + twoDimArrayString(familyArr));
				System.out.println("Element frequencies: " + Arrays.toString(frequencies));
				
				System.out.println("\nDecreasing Order: " + Arrays.toString(decreasingOrder));
				System.out.println("ZDD size: " + zDec.size());
				System.out.println("ZDD: " + zddDec);
				
				System.out.println("\nRandom Order: " + Arrays.toString(randomOrder));
				System.out.println("ZDD size: " + zRand.size());
				System.out.println("ZDD: " + zddRand);
				return;
			}
		}
		
		System.out.println("Unable to find a counterexample");
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
	
	private static void shuffle(int[] arr, Random rand){
		int tmp;
		for(int i = 0; i < arr.length; ++i){
			int newIdx = rand.nextInt(arr.length);
			tmp = arr[newIdx];
			arr[newIdx] = arr[i];
			arr[i] = tmp;
		}
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
}