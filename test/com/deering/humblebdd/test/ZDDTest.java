package com.deering.humblebdd.test;

import java.util.Arrays;

import com.deering.humblebdd.zdd.ZDDFactory;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;

public class ZDDTest {

	public static void main(String[] args){
		int[] order = new int[]{0,2,1,3};
		ZDDFactory f = new ZDDFactory(order, 100);
		
		ZDD z = f.family(new int[][]{{0},{0,1},{0,1,2},{0,1,2,3}});
		System.out.println(z);
		System.out.println("Count: " + z.count());
		System.out.println("Size: " + f.size());
		for(boolean[] sol : z) System.out.println(Arrays.toString(sol));
	}
}
