package com.deering.humblebdd.test;

import com.deering.humblebdd.bdd.BDDFactory;
import com.deering.humblebdd.bdd.BDDFactory.BDD;
import com.deering.humblebdd.zdd.ZDDFactory;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;

public class BDDZDDTest {

	public static void main(String[] args){
		int[] order = new int[]{0,2,1,3};
		ZDDFactory zF = new ZDDFactory(order, 100);
		BDDFactory bF = new BDDFactory(order, 100);
		
		
		ZDD z = zF.family(new int[][]{{0},{0,1,2,3}});
		BDD b = z.toBDD(bF);
		System.out.println(z);
		System.out.println(b);
	}
}
