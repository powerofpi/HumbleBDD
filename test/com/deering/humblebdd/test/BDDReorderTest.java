package com.deering.humblebdd.test;

import java.io.IOException;
import java.util.Arrays;

import com.deering.humblebdd.DDFactory.ExportFormat;
import com.deering.humblebdd.bdd.BDDFactory;
import com.deering.humblebdd.bdd.BDDFactory.BDD;

public class BDDReorderTest {

	public static void main(String[] args) throws IOException{
		int[] order = new int[]{3,2,1,0};
		BDDFactory f = new BDDFactory(order, 100);
		BDD b = f.assignment(new boolean[]{false, true, true, false});
		
		System.out.println(b);
		System.out.println("Count: " + b.satCount());
		System.out.println("Effective Size: " + f.effectiveSize());
		System.out.println("Universe Size: " + f.universeSize());
		for(boolean[] sol : b) System.out.println(Arrays.toString(sol));
		b.exportDiagram("/home/tdeering/order1.pdf", ExportFormat.PDF);
		
		f.reorder(new int[]{2,3,1,0});
		System.out.println(b);
		System.out.println("Count: " + b.satCount());
		System.out.println("Effective Size: " + f.effectiveSize());
		System.out.println("Universe Size: " + f.universeSize());
		for(boolean[] sol : b) System.out.println(Arrays.toString(sol));
		b.exportDiagram("/home/tdeering/order2.pdf", ExportFormat.PDF);
	}
}
