package com.deering.humblebdd.test;

import com.deering.humblebdd.ZDDFactory;
import com.deering.humblebdd.ZDDFactory.ZDD;

public class ZDDTest {

	public static void main(String[] args){
		ZDDFactory f = new ZDDFactory(new int[]{0,2,1,3}, 100);
		
		ZDD z0 = f.element(0);
		System.out.println(z0);
		System.out.println(z0.count());
		for(Integer i : z0) System.out.print(i);
		System.out.println();
		
		ZDD z1 = f.element(1);
		System.out.println(z1);
		System.out.println(z1.count());
		for(Integer i : z1) System.out.print(i);
		System.out.println();
		
		ZDD z2 = f.element(2);
		System.out.println(z2);
		System.out.println(z2.count());
		for(Integer i : z2) System.out.print(i);
		System.out.println();
		
		ZDD z3 = f.element(3);
		System.out.println(z3);
		System.out.println(z3.count());
		for(Integer i : z3) System.out.print(i);
		System.out.println();

		ZDD z4 = z0.union(z3).union(z1).union(z2);
		System.out.println(z4);
		System.out.println(z4.count());
		for(Integer i : z4) System.out.print(i);
		System.out.println();
	}
}
