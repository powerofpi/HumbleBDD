package com.deering.humblebdd.test;

import com.deering.humblebdd.zdd.ZDDSetPool;
import com.deering.humblebdd.zdd.ZDDSetPool.ZDDSet;

public class ZDDSetPoolTest {
	public static void main(String[] args){
		ZDDSetPool<Integer> zsp = new ZDDSetPool<Integer>(100, 10000);
		
		ZDDSetPool<Integer>.ZDDSet z1 = zsp.new ZDDSet();
		
		z1.add(5);
		z1.add(7);
		z1.add(5);
		z1.remove(7);
		
		ZDDSetPool<Integer>.ZDDSet z2 = zsp.new ZDDSet();
		z2.add(1);
		z2.add(2);
		z2.add(3);
		z2.add(4);
		z2.add(5);
		z2.add(6);
		z2.add(7);
		
		System.out.println(z1);
		System.out.println(z2);
	}
}
