package com.deering.humblebdd.test;

import java.util.Iterator;

import com.deering.humblebdd.zdd.ZDDSetPool;

public class ZDDSetPoolTest {
	public static void main(String[] args){
		Iterator<Integer> iter = new Iterator<Integer>(){
			int i = 0;
			@Override
			public boolean hasNext() {
				return i < 8;
			}

			@Override
			public Integer next() {
				return i++;
			}

			@Override
			public void remove() {}
		};
		ZDDSetPool<Integer> zsp = new ZDDSetPool<Integer>(8, iter, 10000);
		
		ZDDSetPool<Integer>.ZDDSet z1 = zsp.new ZDDSet();
		
		z1.add(5);
		z1.add(7);
		z1.add(5);
		z1.remove(7);
		
		ZDDSetPool<Integer>.ZDDSet z2 = zsp.new ZDDSet();
		z2.add(0);
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
