package hdbscan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.graph.UndirectedWeightedSubgraph;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;

public class HDBSCAN {
	
	public static NearestKdTree calculateNearestKdTree(Coordinate[] points,int k,double tolerance){
		NearestKdTree tree = new NearestKdTree(points,k,tolerance);
		tree.findKNN();
		return tree;
		
	}
	
	
	
	public static SimpleWeightedGraph<ClusterNode, DefaultWeightedEdge> calculateMST(NearestKdTree kdTree){
		SimpleWeightedGraph<ClusterNode,DefaultWeightedEdge> swg = new SimpleWeightedGraph(DefaultWeightedEdge.class);
		HashSet<MutualReachabilityEdge> mrEdges = new HashSet();
		TreeSet<KdNode> nodes = new TreeSet(kdTree.getAllNodes());
		KdNode currNode = nodes.pollLast();
		ClusterNode v1 = new ClusterNode(currNode);
		swg.addVertex(v1);
		ClusterNode prevNode = v1;
		
		while(nodes.size() > 0){
			currNode.calculatePotentialEdgeFromNeighbors(nodes);

			if(currNode.getCurrEdge() == null){
				currNode.setBboxDistance(currNode.getCoreDistance());

				while(currNode.getCurrEdge() == null){
					currNode.calculateBBox();

					if(currNode.getIntervals().last().equals(currNode.getBboxDistance())){
						NearestKdTree.queryNode(kdTree.getRoot(), currNode, kdTree.getTreeBBox(), nodes);
					}
					NearestKdTree.queryNode(kdTree.getRoot(), currNode, currNode.getBbox(), nodes);
				}
			}
			ClusterNode v2 = new ClusterNode(currNode.getCurrEdge());
			swg.addVertex(v2);
			DefaultWeightedEdge e = swg.addEdge(prevNode,v2);
			swg.setEdgeWeight(e, currNode.getCurrEdgeWeight());
			currNode = currNode.getCurrEdge();
			prevNode = v2;
			nodes.remove(currNode);
		}

		return swg;
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
	
	public static void createClusterWKT(SimpleWeightedGraph<ClusterNode, DefaultWeightedEdge> clusterGraph){
		GeometryFactory gf = new GeometryFactory(new PrecisionModel(),4326);
		
		try{
			File file = new File("testClusterWkt.csv");
			if (!file.exists()) {
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write("v1,v2,weight,wkt");
			for(DefaultWeightedEdge e : clusterGraph.edgeSet()){
				ClusterNode node1 = clusterGraph.getEdgeSource(e);
				ClusterNode node2 = clusterGraph.getEdgeTarget(e);
				Coordinate point1 =node1.getCoord();
				Coordinate point2 = node2.getCoord();
				Coordinate[] coords = {point1,point2};
				bw.write("\n\"" + node1.getCluster().getLabel() + "\"" + "," + "\"" + node2.getCluster().getLabel() + "\"" + "," +
						"\"" + clusterGraph.getEdgeWeight(e) + "\"" + "," +"\"" + gf.createLineString(coords) + "\"");
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
			Coordinate[] data = readInDataSet("data/testData.csv", ",");
			long startTime = System.currentTimeMillis();
			NearestKdTree tree = calculateNearestKdTree(data, 32, 0.001);
			System.out.println("Time to calculate NN: " + (System.currentTimeMillis() - startTime));
			startTime = System.currentTimeMillis();
			SimpleWeightedGraph<ClusterNode, DefaultWeightedEdge> kmst = calculateMST(tree);
			System.out.println("Time add edges to create Minimum Spanning Tree: " + (System.currentTimeMillis() - startTime));
			startTime = System.currentTimeMillis();
			Double maxWeight = 0.0;
			for(DefaultWeightedEdge e : kmst.edgeSet()){
				Double currWeight = kmst.getEdgeWeight(e);
				if(currWeight > maxWeight){
					maxWeight = currWeight;
				}
			}
			Cluster rootCluster = new Cluster(null,maxWeight,32,new UndirectedWeightedSubgraph<>(kmst, null, null));
			ClusterHeirarchy ch = new ClusterHeirarchy(rootCluster);
			System.out.println("Build root cluster and init heirarchy: " + (System.currentTimeMillis() - startTime));
			startTime = System.currentTimeMillis();
			ch.makeHeirarchy();
			System.out.println("Make Heirarchy:" + (System.currentTimeMillis() - startTime));
			createClusterWKT(kmst);
			System.out.println("Write MST to WKT: " + (System.currentTimeMillis() - startTime));
			startTime = System.currentTimeMillis();
			

		}catch(IOException e){
			System.out.println(e);
		}
	}
}
