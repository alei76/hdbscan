package hdbscan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;

import edu.princeton.cs.algorithms.EdgeWeightedGraph;
import edu.princeton.cs.algorithms.BoruvkaMST;
import edu.princeton.cs.algorithms.Edge;

import org.jgrapht.*;
import org.jgrapht.alg.KruskalMinimumSpanningTree;
import org.jgrapht.alg.PrimMinimumSpanningTree;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;


public class HDBSCAN {
	
	public static NearestKdTree calculateNearestKdTree(Coordinate[] points,int k,double tolerance){
		NearestKdTree tree = new NearestKdTree(points,k,tolerance);
		tree.findKNN();
		return tree;
		
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static EdgeWeightedGraph calculateWeightedGraph(ArrayList<KdNode> nodes){
		HashSet<MutualReachabilityEdge> mrEdges = new HashSet();
		int numNodes = nodes.size();
		long startTime = System.currentTimeMillis();
		startTime = System.currentTimeMillis();
		for(KdNode node : nodes){
//			if(!node.hasKNeighbors()){
//			System.out.println(node.getLabel());
//			System.out.println(node.getNeighbors().size());
//			System.out.println(node.getCoreDistance());
//			System.out.println(node.getNeighbors().values());
//			}

			
			for(KdNode other : node.getNeighbors().values()){
				if(node != null && other != null){
					MutualReachabilityEdge mrEdge = new MutualReachabilityEdge(node, other);
					mrEdges.add(mrEdge);
				}
			}
		}
		System.out.println("Time compute edges: " + (System.currentTimeMillis() - startTime));
		startTime = System.currentTimeMillis();
		nodes = null;
		EdgeWeightedGraph ewg = new EdgeWeightedGraph(numNodes);
		for(MutualReachabilityEdge e : mrEdges){
			ewg.addEdge(new Edge(e.getLabel1(),e.getLabel2(),e.getMrDistance()));
		}
		System.out.println("Time add edges to ewg: " + (System.currentTimeMillis() - startTime));
//		System.out.println(ewg.toString());
		return ewg;
		
	}
	
	public static SimpleWeightedGraph<KdNode, DefaultWeightedEdge> calculateSimpleWeightedGraph(ArrayList<KdNode> nodes){
		SimpleWeightedGraph<KdNode,DefaultWeightedEdge> swg = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
		HashSet<MutualReachabilityEdge> mrEdges = new HashSet();
		long startTime = System.currentTimeMillis();
		int sumNeighbors = 0;
		int count = 0;
		for(KdNode node : nodes){
			if(!node.hasKNeighbors()){
			sumNeighbors += node.getNeighbors().size();
			count += 1;
//			System.out.println(node.getLabel());
//			System.out.println(node.getNeighbors().size());
//			System.out.println(node.getCoreDistance());
//			System.out.println(node.getNeighbors().values());
			}

			swg.addVertex(node);
			
			for(KdNode other : node.getNeighbors().values()){
				if(node != null && other != null){
					MutualReachabilityEdge mrEdge = new MutualReachabilityEdge(node, other);
					mrEdges.add(mrEdge);
				}
			}
		}
//		System.out.println("Average neighbors LT K: " + sumNeighbors / count);
		for(MutualReachabilityEdge e : mrEdges){
			DefaultWeightedEdge we = swg.addEdge(e.getNode1(),e.getNode2());
			if(we != null){
				swg.setEdgeWeight(we, e.getMrDistance());
			}
		}
		System.out.println("Time add edges to swg: " + (System.currentTimeMillis() - startTime));
		return swg;
	}
	
	
	
	public static BoruvkaMST createMST(EdgeWeightedGraph ewg){
		return new BoruvkaMST(ewg);
	}
	
	public static SimpleWeightedGraph<KdNode, DefaultWeightedEdge> calculateMST(SimpleWeightedGraph<KdNode, 
			DefaultWeightedEdge> graph){
		PrimMinimumSpanningTree<KdNode, DefaultWeightedEdge> mstFinder = new PrimMinimumSpanningTree<KdNode, DefaultWeightedEdge>(
	            graph);

	    Set<DefaultWeightedEdge> mstEdges = mstFinder.getMinimumSpanningTreeEdgeSet();
	    SimpleWeightedGraph<KdNode, DefaultWeightedEdge> mst = new SimpleWeightedGraph<KdNode, DefaultWeightedEdge>(
	            DefaultWeightedEdge.class);

	    for (KdNode mv : graph.vertexSet()) {
	        mst.addVertex(mv);
	    }

	    for (DefaultWeightedEdge e : mstEdges) {
	        mst.addEdge(graph.getEdgeSource(e), graph.getEdgeTarget(e));
	        mst.setEdgeWeight(e, graph.getEdgeWeight(e));
	    }

	    return mst;
	}
	
	public static void createMstWKT(BoruvkaMST mst,ArrayList<KdNode> nodes){
		GeometryFactory gf = new GeometryFactory(new PrecisionModel(),4326);
		try{
			File file = new File("testWkt.csv");
			if (!file.exists()) {
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write("v1,v2,weight,wkt");
			for(Edge e : mst.edges()){
				int v1 = e.either();
				int v2 = e.other(v1);
				Coordinate point1 = nodes.get(v1).getCoordinate();
				Coordinate point2 = nodes.get(v2).getCoordinate();
				Coordinate[] coords = {point1,point2};
				bw.write("\n\"" + v1 + "\"" + "," + "\"" + v2 + "\"" + "," +
						"\"" + e.weight() + "\"" + "," +"\"" + gf.createLineString(coords) + "\"");
			}
			bw.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public static void createKmstWKT(SimpleWeightedGraph<KdNode, DefaultWeightedEdge> kmst){
		GeometryFactory gf = new GeometryFactory(new PrecisionModel(),4326);
		
		try{
			File file = new File("testWkt.csv");
			if (!file.exists()) {
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write("v1,v2,weight,wkt");
			for(DefaultWeightedEdge e : kmst.edgeSet()){
				KdNode node1 = kmst.getEdgeSource(e);
				KdNode node2 = kmst.getEdgeTarget(e);
				Coordinate point1 =node1.getCoordinate();
				Coordinate point2 = node2.getCoordinate();
				Coordinate[] coords = {point1,point2};
				bw.write("\n\"" + node1.getLabel() + "\"" + "," + "\"" + node2.getLabel() + "\"" + "," +
						"\"" + kmst.getEdgeWeight(e) + "\"" + "," +"\"" + gf.createLineString(coords) + "\"");
			}
			bw.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Reads in the input data set from the file given, assuming the delimiter separates attributes
	 * for each data point, and each point is given on a separate line.  Error messages are printed
	 * if any part of the input is improperly formatted.
	 * @param fileName The path to the input file
	 * @param delimiter A regular expression that separates the attributes of each point
	 * @return A double[][] where index [i][j] indicates the jth attribute of data point i
	 * @throws IOException If any errors occur opening or reading from the file
	 */
	public static Coordinate[] readInDataSet(String fileName, String delimiter) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(fileName));
		ArrayList<double[]> dataSet = new ArrayList<double[]>();
		int numAttributes = -1;
		int lineIndex = 0;
		String line = reader.readLine();

		while (line != null) {
			lineIndex++;
			String[] lineContents = line.split(delimiter);

			if (numAttributes == -1)
				numAttributes = lineContents.length;
			else if (lineContents.length != numAttributes)
				System.err.println("Line " + lineIndex + " of data set has incorrect number of attributes.");

			double[] attributes = new double[numAttributes];
			for (int i = 0; i < numAttributes; i++) {
				try {
					//If an exception occurs, the attribute will remain 0:
					attributes[i] = Double.parseDouble(lineContents[i]);
				}
				catch (NumberFormatException nfe) {
					System.err.println("Illegal value on line " + lineIndex + " of data set: " + lineContents[i]);
				}
			}

			dataSet.add(attributes);
			line = reader.readLine();
		}

		reader.close();
		Coordinate[] finalDataSet = new Coordinate[dataSet.size()];

		for (int i = 0; i < dataSet.size(); i++) {
			double[] point = dataSet.get(i);
			finalDataSet[i] = new Coordinate(point[0],point[1]);
		}
		return finalDataSet;
	}
	 public static void main(String[] args) {
		try{
			Coordinate[] data = readInDataSet("testData.csv", ",");
			long startTime = System.currentTimeMillis();
			NearestKdTree tree = calculateNearestKdTree(data, 32, 0.001);
			System.out.println("Time to calculate NN: " + (System.currentTimeMillis() - startTime));
			startTime = System.currentTimeMillis();
			ArrayList<KdNode> nodes = tree.getAllNodes();
			Collections.sort(nodes);
			EdgeWeightedGraph ewg = calculateWeightedGraph(nodes);
			System.out.println("Time to create Edge Weighted Graph: " + (System.currentTimeMillis() - startTime));
			startTime = System.currentTimeMillis();
			BoruvkaMST mst = new BoruvkaMST(ewg);
			System.out.println("Time to create Minimum Spanning Tree: " + (System.currentTimeMillis() - startTime));
			SimpleWeightedGraph<KdNode, DefaultWeightedEdge> swg = calculateSimpleWeightedGraph(nodes);
			startTime = System.currentTimeMillis();
			SimpleWeightedGraph<KdNode, DefaultWeightedEdge> kmst = calculateMST(swg) ;
			System.out.println("Time to create Minimum Spanning Tree: " + (System.currentTimeMillis() - startTime));
			startTime = System.currentTimeMillis();
//			createMstWKT(mst,nodes);
			createKmstWKT(kmst);
			System.out.println("Write MST to WKT: " + (System.currentTimeMillis() - startTime));

		}catch(IOException e){
			System.out.println(e);
		}
	}
}
