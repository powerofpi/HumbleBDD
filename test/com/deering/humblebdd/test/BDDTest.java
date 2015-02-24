package com.deering.humblebdd.test;

import java.util.Arrays;

import com.deering.humblebdd.BDDFactory;
import com.deering.humblebdd.BDDFactory.BDD;

public class BDDTest {

	public static void main(String[] args){
		BDDFactory factory = new BDDFactory(new int[]{0,1,2}, 100);
		
		BDD b0 = factory.hiVar(0);
		System.out.println(b0);
		System.out.println(b0.satCount());
		for(boolean[] sol : b0) System.out.println(Arrays.toString(sol));

		BDD b1 = factory.loVar(1);
		System.out.println(b1);
		System.out.println(b1.satCount());
		for(boolean[] sol : b1) System.out.println(Arrays.toString(sol));

		BDD b2 = factory.loVar(2);
		System.out.println(b2);
		System.out.println(b2.satCount());
		for(boolean[] sol : b2) System.out.println(Arrays.toString(sol));
		
		BDD b3 = b0.and(b1);
		System.out.println(b3);
		System.out.println(b3.satCount());
		for(boolean[] sol : b3) System.out.println(Arrays.toString(sol));
		
		BDD b4 = b0.or(b1);
		System.out.println(b4);
		System.out.println(b4.satCount());
		for(boolean[] sol : b4) System.out.println(Arrays.toString(sol));
		
		BDD b5 = b0.or(b1).xor(b2);
		System.out.println(b5);
		System.out.println(b5.satCount());
		for(boolean[] sol : b5) System.out.println(Arrays.toString(sol));
	}
}
