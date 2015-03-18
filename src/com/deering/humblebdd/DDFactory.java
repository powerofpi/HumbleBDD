package com.deering.humblebdd;


import graphviz.GraphViz;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import org.jgrapht.Graph;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.EdgeNameProvider;
import org.jgrapht.ext.VertexNameProvider;
import org.jgrapht.graph.DirectedPseudograph;

import com.deering.humblebdd.util.FixedSizeHashMap;

/**
 * Common base class for decision diagram factories.
 * @author tdeering
 *
 */
public abstract class DDFactory {
	/**
	 * Defines the formats 
	 * @author tdeering
	 *
	 */
	public static enum ExportFormat{
		DOT,FIG,GIF,PDF,PS,SVG,PNG,PLAIN;
	}
	
	/**
	 * Large prime numbers for hashing.
	 */
	private static final int[] LARGE_PRIMES = new int[]{2147481053, 2147481151, 2147482093};
	
	/**
	 * Nodes of the shared graph.
	 */
	private WeakHashMap<DDNode, WeakReference<DDNode>> ddNodes; 
	
	/**
	 * Cache of BDD operation results
	 */
	private FixedSizeHashMap<DDOpKey, Object> opCache;
	
	/**
	 * Constant "false" node.
	 */
	protected DDNode LO;
	
	/**
	 * Constant "true" node.
	 */
	protected DDNode HI;
	
	/**
	 * Maps from variables to ordering indices.
	 */
	protected int[] v2i;
	
	/**
	 * Maps from ordering indices to variables.
	 */
	protected int[] i2v;
	
	/**
	 * Constructs a new DDFactory with the given zero-indexed variables the given order. For example:
	 * 
	 * varOrdering = new int{1,0,2,3};
	 * 
	 * Specifies a factory with 4 variables, ordered 1, 0, 2, 3.
	 * 
	 * The operator cache is used to cache the results of the last operatorCacheSize operations. Larger
	 * caches may result in more memory use, but substantial speedups.
	 * 
	 * @param numVars
	 * @param varOrdering
	 */
	public DDFactory(int[] varOrdering, int operatorCacheSize){
		// Error checking
		sanitizeOrdering(varOrdering);
		this.i2v = new int[varOrdering.length];
		this.v2i = new int[varOrdering.length];
		for(int i=0; i < varOrdering.length; i++){
			i2v[i] = varOrdering[i];
			v2i[i2v[i]] = i;
		}
		
		this.LO = new DDNode(-1, null, null);
		this.HI = new DDNode(-2, null, null);
		this.ddNodes = new WeakHashMap<DDNode, WeakReference<DDNode>>();
		this.opCache = new FixedSizeHashMap<DDOpKey, Object>(operatorCacheSize);
	}
	
	/**
	 * Returns the current variable ordering.
	 * @return
	 */
	public int[] getOrdering(){
		return Arrays.copyOf(v2i, v2i.length);
	}
	
	/**
	 * Reorders the DD with respect to the new ordering.
	 * 
	 * @param newOrdering
	 */
	public void reorder(int[] newOrdering){
		// Error checking
		if(i2v.length != newOrdering.length)
			throw new HumbleException("New ordering should be of the same length as the previous ordering");
		sanitizeOrdering(newOrdering);
		
		// Same ordering we already have? No work to be done.
		if(Arrays.equals(i2v, newOrdering)) return;
		
		// Create new mappings between order indices and variable labels
		int[] newI2V = new int[newOrdering.length];
		int[] newV2I = new int[newOrdering.length];
		for(int i=0; i < newOrdering.length; i++){
			newI2V[i] = newOrdering[i];
			newV2I[newI2V[i]] = i;
		}
		
		// TODO create a factory with the new ordering
		// TODO create correspondence 
		
		// TODO the real work
	}
	
	private void sanitizeOrdering(int[] ordering){
		int[] counts = new int[ordering.length];
		for(int i : ordering){
			try{
				if(++counts[i] > 1){
					throw new HumbleException("Same variable " + i + " appeared multiple times in variable ordering!");
				}
			}catch(ArrayIndexOutOfBoundsException e){
				throw new HumbleException("Variables in ordering must be 0 to ordering.length - 1. Got " + i, e);
			}
		}
	}
	
	/**
	 * Returns the current size of the backing graph.
	 * @return
	 */
	public int size(){
		System.gc();
		return ddNodes.keySet().size();
	}
	
	/**
	 * Return the shared node which represents the given variable and has exactly
	 * the given lo and hi children.
	 * 
	 * NOTE: This function is what gives our BDDs the "Reduced" property. That is,
	 * there is exactly one BDDNode with a particular pair of (lo, hi) children,
	 * and no BDDNode has (lo, hi) such that lo == high. Therefore, the shared graph
	 * is maximally-reduced with respect to the given variable ordering.
	 * 
	 * @param var
	 * @param lo
	 * @param hi
	 * @return
	 */
	protected DDNode getNode(int var, DDNode lo, DDNode hi){
		if(var < 0 || var >= v2i.length) throw new HumbleException("No such variable: " + var);
		
		// Node elimination
		DDNode eliminated = nodeElimination(var, lo, hi);
		if(eliminated != null) return eliminated;

		// Node sharing
		DDNode key = new DDNode(var, lo, hi);
		WeakReference<DDNode> sharedNode = ddNodes.get(key);
		if(sharedNode == null){
			sharedNode = new WeakReference<DDNode>(key);
			ddNodes.put(key, sharedNode);
		}
		return sharedNode.get();
	}
	
	/**
	 * If the given node should be eliminated, returns its replacement. If it should
	 * not be eliminated, returns null;
	 * 
	 * @param var
	 * @param lo
	 * @param hi
	 * @return
	 */
	protected abstract DDNode nodeElimination(int var, DDNode lo, DDNode hi);
	
	/**
	 * Immutable decision diagram class. Operations return new DD instances.
	 * 
	 * @author tdeering
	 *
	 */
	public abstract class DD implements Iterable<boolean[]>{
		/**
		 * Node from the shared DD graph that this DD refers to.
		 */
		protected DDNode ref;
		
		/**
		 * Creates a DD represented by the given node. 
		 * 
		 * USERS: Do not directly construct. Use factory or operations of another DD.
		 * @param ref
		 * @param factory
		 */
		protected DD(DDNode ref){
			this.ref = ref;
		}
		
		/**
		 * Apply the given operation on this DD and, for binary operations,
		 * on the other DD. Look up the answer in cache if it's there, and
		 * compute upon cache miss.
		 * 
		 * @param op
		 * @param first
		 * @param second
		 * @return
		 */
		protected Object apply(int op, DDNode first, Object second){
			DDOpKey key = new DDOpKey(op, first, second);
			Object cached = opCache.get(key);
			if(cached == null){
				cached = compute(op, first, second);
				opCache.put(key, cached);
			}
			return cached;
		}
		
		/**
		 * Returns whether this DD represents the false terminal
		 * 
		 * Time complexity: O(1)
		 * @return
		 */
		public boolean isLo(){
			return ref == LO;
		}

		/**
		 * Returns whether this DD represents the true terminal
		 * 
		 * Time complexity: O(1)
		 * @return
		 */
		public boolean isHi(){
			return ref == HI;
		}
		
		/**
		 * Return the DDFactory used to construct this DD.
		 * @return
		 */
		public DDFactory getFactory(){
			return DDFactory.this;
		}
		
		/**
		 * Compute the result of the given operation on the given DDNodes.
		 * 
		 * @param op
		 * @param first
		 * @param second
		 * @return
		 */
		protected abstract Object compute(int op, DDNode first, Object second);
		
		/**
		 * Iterates over the satisfying solutions to this decision diagram. The returned solutions have the following meaning:
		 * 
		 * // One satisfying solution for the DD 
		 * boolean[] sat = iter.next();
		 * 
		 * // Variable X takes this value in this solution
		 * boolean varThree = sat[X];
		 * 
		 * NOTE: The returned array is re-used by the iterator for efficiency reasons.
		 */
		@Override
		public abstract Iterator<boolean[]> iterator();
		
		/**
		 * Exports this DD to a file of the requested format using Graphviz (must be installed).
		 * 
		 * @throws IOException 
		 */
		public void exportDiagram(String path, ExportFormat format) throws IOException{
			String type = null;
			switch(format){
			case DOT:
				type = "dot";
				break;
			case FIG:
				type = "fig";
				break;
			case GIF:
				type = "gif";
				break;
			case PDF:
				type = "pdf";
				break;
			case PLAIN:
				type = "plain";
				break;
			case PNG:
				type = "png";
				break;
			case PS:
				type = "ps";
				break;
			case SVG:
				type = "svg";
				break;
			default:
				throw new HumbleException("Unknown export format: " + format);
			}
			
			StringWriter writer = new StringWriter();
			exportDOT(writer);
			GraphViz gv = new GraphViz();
			gv.writeGraphToFile(gv.getGraph(writer.toString(), type), path);
		}
		
		/**
		 * Writes a DOT graph for this DD to the given writer.
		 * @param writer
		 */
		private void exportDOT(Writer writer){
			final Map<DDNode, Integer> idMap = new HashMap<DDNode, Integer>();
			
			DOTExporter<DDNode, DDEdge> exporter = new DOTExporter<DDNode, DDEdge>(
               new VertexNameProvider<DDNode>(){
				@Override
				public String getVertexName(DDNode arg0) {
					Integer id = idMap.get(arg0);
					if(id == null){
						id = idMap.size();
						idMap.put(arg0, id);
					}
					return id.toString();
				}
			}, new VertexNameProvider<DDNode>(){
				@Override
				public String getVertexName(DDNode arg0) {
					if(arg0 == LO) return "F";
					if(arg0 == HI) return "T";
					return Integer.toString(arg0.var);
				}
			}, new EdgeNameProvider<DDEdge>(){
				@Override
				public String getEdgeName(DDEdge arg0) {
					return arg0.hi ? "T":"F";
				}
			});
			Graph<DDNode, DDEdge> graph = new DirectedPseudograph<DDNode, DDEdge>(DDEdge.class);
			createGraph(ref, graph);
			exporter.export(writer, graph);
		}
		
		private void createGraph(DDNode current, Graph<DDNode, DDEdge> graph){
			graph.addVertex(current);
			if(current.var >= 0){
				createGraph(current.lo, graph);
				graph.addEdge(current, current.lo, new DDEdge(current, false));
				createGraph(current.hi, graph);
				graph.addEdge(current, current.hi, new DDEdge(current, true));
			}
		}
	}
	
	private class DDEdge{
		DDNode origin;
		boolean hi;
		public DDEdge(DDNode origin, boolean hi){
			this.origin = origin;
			this.hi = hi;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + (hi ? 1231 : 1237);
			result = prime * result
					+ ((origin == null) ? 0 : origin.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DDEdge other = (DDEdge) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (hi != other.hi)
				return false;
			if (origin == null) {
				if (other.origin != null)
					return false;
			} else if (!origin.equals(other.origin))
				return false;
			return true;
		}
		private DDFactory getOuterType() {
			return DDFactory.this;
		}
	}
	
	/**
	 * Node in the shared DD universe graph.
	 * @author tdeering
	 *
	 */
	protected final class DDNode{
		public int var;
		public DDNode lo, hi;
		Integer hash;
		
		private DDNode(int var, DDNode lo, DDNode hi){
			this.var = var;
			this.lo = lo;
			this.hi = hi;
		}
		
		@Override
		public int hashCode() {
			if(hash == null){
				hash = LARGE_PRIMES[0] * var + 
					   (lo == null ? 0:LARGE_PRIMES[1] * lo.hashCode()) + 
					   (hi == null ? 0:LARGE_PRIMES[2] * hi.hashCode());
			}
			
			return hash;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DDNode other = (DDNode) obj;
			if(!getOuterType().equals(other.getOuterType()))
				return false;
			return var == other.var && hi == other.hi && lo == other.lo;
		}
		
		@Override
		public String toString(){
			if(this == LO) return "LO";
			if(this == HI) return "HI";
			return var + "(" + lo + "," + hi + ")";
		}
		
		public DDFactory getOuterType(){
			return DDFactory.this;
		}
	}
	
	/**
	 * Used as key into the operation cache
	 * @author tdeering
	 *
	 */
	private final class DDOpKey{
		int op;
		DDNode a;
		Object b;
		public DDOpKey(int op, DDNode a, Object b){
			this.op = op;
			this.a = a;
			this.b = b;
		}
		@Override
		public int hashCode() {
			return LARGE_PRIMES[0] * op + a.hashCode() + (b == null ? 0:b.hashCode());
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DDOpKey other = (DDOpKey) obj;
			if(!getOuterType().equals(other.getOuterType()))
				return false;
			if(op != other.op)
				return false;
			return (a == other.a && b == other.b) ||
				   (a == other.b && b == other.a);
		}
		
		public DDFactory getOuterType(){
			return DDFactory.this;
		}
	}
}
