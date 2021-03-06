package com.deering.humblebdd.test;

import java.io.IOException;
import java.util.Arrays;

import com.deering.humblebdd.DDFactory.ExportFormat;
import com.deering.humblebdd.zdd.ZDDFactory;
import com.deering.humblebdd.zdd.ZDDFactory.ZDD;

public class ZDDTest {

	public static void main(String[] args) throws IOException, InterruptedException{
		int[] order = new int[]{0,2,1,3};
		ZDDFactory f = new ZDDFactory(order, 100);
		
		ZDD z = f.family(new int[][]{{0},{0,1},{0,1,2},{0,1,2,3}});
		System.out.println(z);
		System.out.println("Count: " + z.count());
		System.out.println("Effective Size: " + f.effectiveSize());
		System.out.println("Universe Size: " + f.universeSize());
		for(boolean[] sol : z) System.out.println(Arrays.toString(sol));
		z.exportDiagram("/home/tdeering/test.pdf", ExportFormat.PDF);
	}
}
