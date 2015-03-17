package com.deering.humblebdd.test.kothari;

import java.io.IOException;
import java.util.Arrays;

import com.deering.humblebdd.zdd.ZDDFactory;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;

public class BestWorstOrdering {

	public static void main(String[] args) throws IOException {
		int[] universe = {0,1,2,3};
		int[][] family = new int[][]{{0},{0,1},{0,1,2},{0,1,2,3}};
		
		int bestSize = Integer.MAX_VALUE, worstSize = -1;
		ZDD best = null;
		ZDD worst = null;
		
		int[] ordering = new int[universe.length];
		Permutations p = new Permutations(universe, ordering);
		while(p.next()){
			ZDDFactory f = new ZDDFactory(ordering, 10000);
			ZDD z = f.family(family);
			
			int size = f.size();
			
			if(best == null || size < bestSize){
				best = z;
				bestSize = size;
			}
			if(worst == null || size > worstSize){
				worst = z;
				worstSize = size;
			}
		}
		
		System.out.println("Universe: " + Arrays.toString(universe));
		System.out.println("Family: " + twoDimArrayString(family));
		System.out.println("Best Order: " + Arrays.toString(best.getFactory().getOrdering()));
		System.out.println("Best Size: " + bestSize);
		System.out.println("Worst Order: " + Arrays.toString(worst.getFactory().getOrdering()));
		System.out.println("Worst Size: " + worstSize);
		String bestPath = "/Users/kothari/best.dot";
		String worstPath = "/Users/kothari/worst.dot";
		best.exportDOT(bestPath);
		worst.exportDOT(worstPath);
		System.out.println("Best ZDD exported to " + bestPath);
		System.out.println("Worst ZDD exported to " + worstPath);
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
