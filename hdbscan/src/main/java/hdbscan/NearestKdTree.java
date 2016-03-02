package hdbscan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeSet;
import java.util.Iterator;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.ArrayListVisitor;
import com.vividsolutions.jts.index.ItemVisitor;

/**
 * <p>A {@link KdTree} with methods to query for the nearest neighbor to a
 * specified search point. There are three current strategies for determining
 * the nearest neighbor:</p>
 * 
 * <ol>
 * <li> The point in the tree nearest to the search point. (The search point
 *      need not exist in the tree.)</li>
 * <li> The point in the tree nearest to the search point which is not 
 *      identical to the search point. (Used to locate nearest neighbors 
 *      to points which are in the tree.)</li>
 * <li> The point in the tree nearest to the search point which is not
 *      contained within a specified <code>Collection</code> of points. (Used to 
 *      locate nearest neighbors which are not part of the same cluster.)</li>
 * </ol>
 * 
 * <p>Range searches are inherited from {@link KdTree}, as are all the mutable
 * behaviors (such as adding points).</p>
 * 
 * @author Bryce Nordgren
 * @since 1.12
 * @see KdTree
 * @see NearestSearch
 * @see NearestNonIdenticalSearch
 * @see NearestNotInSearch
 *
 */
public class NearestKdTree{
	private KdNode root;
	private double tolerance;
	private KdNode last = null;
	private long numberOfNodes;
	private Envelope treeBBox;
	/**
	 * <p>Creates an empty <code>NearestKdTree</code>.</p>
	 * 
	 * <p><b>NOTE:</b> if you already have all or most of the points you
	 * intend to store in this tree, it is more efficient to use the 
	 * factory method: {@link #loadNearestKdTree(Coordinate[])}.</p>
	 */
	public NearestKdTree(Coordinate[] points,int k) { 
		super();
		this.tolerance = Double.NaN;
		loadTree(points, k);
	}
	
	/**
	 * Creates an empty KdTree with the specified snap tolerance. See 
	 * {@link KdTree#KdTree(double)} for more details.
	 * 
	 * @param tol the snap tolerance
	 */
	public NearestKdTree(Coordinate[] points, int k, double tol) { 
		super();
		this.tolerance = tol;
		loadTree(points,k);
	}
	
	 /**
	   * Tests whether the index contains any items.
	   * 
	   * @return true if the index does not contain any items
	   */
	  public boolean isEmpty()
	  {
	    if (root == null) return true;
	    return false;
	  }
	  

		private void queryNode(KdNode currentNode, KdNode bottomNode,
				Envelope queryEnv, boolean odd, List<KdNode> result) {
			if (currentNode == bottomNode)
				return;

			double min;
			double max;
			double discriminant;
			if (odd) {
				min = queryEnv.getMinX();
				max = queryEnv.getMaxX();
				discriminant = currentNode.getX();
			} else {
				min = queryEnv.getMinY();
				max = queryEnv.getMaxY();
				discriminant = currentNode.getY();
			}
			boolean searchLeft = min < discriminant;
			boolean searchRight = discriminant <= max;

			if (searchLeft) {
				queryNode(currentNode.getLeft(), bottomNode, queryEnv, !odd, result);
			}
			if (queryEnv.contains(currentNode.getCoordinate())) {
				result.add(currentNode);
			}
			if (searchRight) {
				queryNode(currentNode.getRight(), bottomNode, queryEnv, !odd, result);
			}

		}

		/**
		 * Performs a range search of the points in the index.
		 * 
		 * @param queryEnv
		 *          the range rectangle to query
		 * @return a list of the KdNodes found
		 */
		public ArrayList<KdNode> query(Envelope queryEnv) {
			ArrayList<KdNode> result = new ArrayList<KdNode>();
			queryNode(root, last, queryEnv, true, result);
			return result;
		}

		/**
		 * Performs a range search of the points in the index.
		 * 
		 * @param queryEnv
		 *          the range rectangle to query
		 * @param result
		 *          a list to accumulate the result nodes into
		 */
		public void query(Envelope queryEnv, List<KdNode> result) {
			queryNode(root, last, queryEnv, true, result);
		}

	/**
	 * Performs a range search on the envelope and visits each of the 
	 * results.
	 * 
	 * @param searchEnv the envelope to search
	 * @param visitor the {@link ItemVisitor} which should visit each result.
	 */
	public void query(Envelope searchEnv, ItemVisitor visitor) {
		List<KdNode> results = query(searchEnv) ; 
		Iterator<KdNode> visitator = results.iterator() ; 
		while (visitator.hasNext()) { 
			visitor.visitItem(visitator.next()) ; 
		}		
	}
	
	
	/**
	 * Returns the path through the tree (all the way to the leaf node) caused 
	 * by traversing the tree looking for coordinate p. The coordinate is not expected
	 * to exist in the tree. 
	 * 
	 * @param p coordinate to search for.
	 * @return path from root to leaf, caused by searching for p.
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<KdNode> path(Coordinate p) {
		ArrayListVisitor v = new ArrayListVisitor() ; 
		
		traverse(getRoot(), p, v) ; 
		return v.getItems() ; 
	}
	
	/**
	 * Searches the tree for the provided point. Terminates when the point
	 * is found.
	 * @param p The point to find
	 * @return true if the point is in the tree.
	 */
//	public boolean contains(Coordinate p) { 
//		return traverseFind(getRoot(), p) ; 
//	}
	
	/**
	 * Returns the path from the root node to the specified coordinate.
	 * @param p coordinate to search for
	 * @return list of KdNodes in order of traversal.
	 */
	public ArrayList<KdNode> coordinatePath(Coordinate p) { 
		ArrayList<KdNode> leafPath = path(p) ; 
		ListIterator<KdNode> popper = leafPath.listIterator(leafPath.size()) ; 
		
		boolean found = false; 
		while (popper.hasPrevious() && !found) { 
			KdNode node = (KdNode)(popper.previous()) ;
			found = node.getCoordinate().equals2D(p) ; 
			if (!found) { 
				popper.remove() ; 
			}
		}
		
		return leafPath ; 		
	}
	
	/**
	 * Traverses the tree structure in search of the coordinate p, starting 
	 * from the provided node. Note that this method will not terminate 
	 * on "p" if it occurs in the interior of the tree. It always navigates all 
	 * the way to a leaf node.
	 * @param start The node in the tree from which to begin the traversal
	 * @param p coordinate to search for
	 * @param v if provided, will be made to visit all the nodes from start to
	 *          a leaf.
	 */
	public static void traverse(KdNode start, Coordinate searchPoint, ItemVisitor v) { 
		KdNode currentNode = start;
		KdNode last = null;		
		boolean isOddLevel = true;
		boolean isLessThan = true;

		// traverse the tree first cutting the plane left-right the top-bottom
		while (currentNode != last) {
			if(v!=null){
				v.visitItem(currentNode);
			}
			if (isOddLevel) {
				isLessThan = searchPoint.x < currentNode.getX();
			} else {
				isLessThan = searchPoint.y < currentNode.getY();
			}
			if (isLessThan) {
				currentNode = currentNode.getLeft();
			} else {
				currentNode = currentNode.getRight();
			}
			isOddLevel = !isOddLevel;
		}
	}
	
	public static void traverse(KdNode start, KdNode searchNode, ItemVisitor v){
		KdNode currNode = searchNode;
		v.visitItem(start);
		while(!currNode.equals(start)){
			currNode = currNode.getParent();
			v.visitItem(currNode);
		}
		if(searchNode.getLeft() != null){
			currNode = searchNode.getLeft();
			v.visitItem(currNode);
			while(!currNode.isBottom()){
				if(currNode.getAxis() == 0){
					if(searchNode.getX() < currNode.getX() && currNode.getLeft() != null){
						currNode = currNode.getLeft();
						v.visitItem(currNode);
					}else if(currNode.getRight() != null){
						currNode = currNode.getRight();
						v.visitItem(currNode);
					}else{
						break;
					}
				}else{
					if(searchNode.getY() < currNode.getY() && currNode.getLeft() != null){
						currNode = currNode.getLeft();
						v.visitItem(currNode);
					}else if (currNode.getRight() != null){
						currNode = currNode.getRight();
						v.visitItem(currNode);
					}else{
						break;
					}
				}
			}
		}
		if(searchNode.getRight() != null){
			currNode = searchNode.getRight();
			v.visitItem(currNode);
			while(!currNode.isBottom()){
				if(currNode.getAxis() == 0){
					if(searchNode.getX() < currNode.getX() && currNode.getLeft() != null){
						currNode = currNode.getLeft();
						v.visitItem(currNode);
					}else if(currNode.getRight() != null){
						currNode = currNode.getRight();
						v.visitItem(currNode);
					}else{
						break;
					}
				}else{
					if(searchNode.getY() < currNode.getY() && currNode.getLeft() != null){
						currNode = currNode.getLeft();
						v.visitItem(currNode);
					}else if (currNode.getRight() != null){
						currNode = currNode.getRight();
						v.visitItem(currNode);
					}else{
						break;
					}
				}
			}
		}
	}
	
	/**
	 * Searches for the nearest neighbor to the indicated point, excluding from
	 * consideration all points listed in the exclude collection. (Recursive)
	 * @param start location in the tree to start searching.
	 * @param searchPt point to search for
	 * @param exclude collection of points which cannot be the nearest neighbor.
	 * @return search point, nearest neighbor, and separation distance.
	 */
	@SuppressWarnings("unchecked")
	public void findKNN() {
		ArrayList<KdNode> path;
		for(KdNode node : getAllNodes()){
			
			boolean envQuery = node.hasKNeighbors();
			if(node.hasKNeighbors()){
				node.calculateBBox();
				path = query(node.getBbox());
			}else{
				ArrayListVisitor v = new ArrayListVisitor();
				traverse(root,node,v);
				path = v.getItems();
			}
			ListIterator<KdNode> unwind = path.listIterator(path.size()) ; 
			while (unwind.hasPrevious()) {
				KdNode current = (unwind.previous());
				if(!node.equals(current)){
					Double addCurrentDistance = node.addNeighbor(current);
					if(addCurrentDistance != null){
						current.addNeighbor(node,addCurrentDistance);
					}else{
						current.addNeighbor(node);
					}
				}
			}
			
			if(!envQuery){
				node.calculateBBox();
				path = query(node.getBbox());
				unwind = path.listIterator(path.size()) ; 
				while (unwind.hasPrevious()) {
					KdNode current = (unwind.previous());
					if(!node.equals(current)){
						Double addCurrentDistance = node.addNeighbor(current);
						if(addCurrentDistance != null){
							current.addNeighbor(node,addCurrentDistance);
						}else{
							current.addNeighbor(node);
						}
					}
				}

			}
		}
	}
	
	
	public KdNode getRoot() {
		return root;
	}

	public void setRoot(KdNode root) {
		this.root = root;
	}

	public double getTolerance() {
		return tolerance;
	}

	public void setTolerance(double tolerance) {
		this.tolerance = tolerance;
	}

	public KdNode getLast() {
		return last;
	}

	public void setLast(KdNode last) {
		this.last = last;
	}

	public long getNumberOfNodes() {
		return numberOfNodes;
	}

	public void setNumberOfNodes(long numberOfNodes) {
		this.numberOfNodes = numberOfNodes;
	}
	
	public ArrayList<KdNode> getAllNodes(){
		ArrayList<KdNode> visitor = new ArrayList<KdNode>();
		query(treeBBox,visitor);
		return visitor;
	}

	/**
	 * Recursively creates a balanced set of nodes given a list of 
	 * points. The list of points is sorted by the axis which is 
	 * being used for the split on this level. The median value
	 * is taken for this node. The remaining points are divided into
	 * left and right lists, which are processed by another call to
	 * this algorithm. The node returned by this 
	 * @param points List of points to make into a balanced tree
	 * @param level level of the tree (root is zero).
	 * @return the root of the produced tree.
	 * @since 1.12
	 */
	@SuppressWarnings("unchecked")
	protected static KdNode makeTree(Coordinate[]points, int level, int k) {
		KdNode middle = null;
		KdNode left = null;
		KdNode right = null;
		int axis = level %2 ; 
		CoordinateComparator sortAxis = 
				CoordinateComparator.getComparator(axis) ; 
		
		// Sort the list 
		Arrays.sort(points, sortAxis) ; 
		
		// If the list is bigger than three points, recurse.
		if (points.length > 3) { 
			int median_idx = points.length/2 ; 
			middle = new KdNode(points[median_idx],axis, k) ;
			
			Coordinate []leftPoints = new Coordinate[median_idx];  
			Coordinate []rightPoints = new Coordinate[points.length-(median_idx+1)] ; 
			
			// split the list into "left" and "right"
			for (int i=0; i<median_idx; i++) {
				leftPoints[i] = points[i] ; 
			}		
			for (int i=median_idx+1; i<points.length; i++) { 
				rightPoints[i-(median_idx+1)] = points[i] ; 
			}
			left = makeTree(leftPoints,level+1,k);
			right = makeTree(rightPoints,level+1,k);
			middle.setLeft(left) ; 
			left.setParent(middle);
			middle.setRight(right);
			right.setParent(middle);
		} else if (points.length == 3) {
			// if exactly three points, we know how this plays out
			middle = new KdNode(points[1],axis,k) ; 
			axis = (axis+1) %2 ;
			left = new KdNode(points[0],axis,k);
			right = new KdNode(points[2],axis,k);
			
			middle.setLeft(left);
			left.setParent(middle);
			middle.setRight(right);
			right.setParent(middle);
		} else if (points.length == 2) { 
			// if exactly two points, we can also just hardcode it
			middle = new KdNode(points[1],axis,k) ;
			axis = (axis+1)%2 ;
			left = new KdNode(points[0],axis,k);
			middle.setLeft(left);
			left.setParent(middle);
		} else if (points.length == 1) { 
			// we should only get here if the list starts out with 
			// length one.
			middle = new KdNode(points[0],axis,k);  
		}
		
		return middle ; 
	}
	
	/**
	 * Factory method to create a balanced kd-tree from an array of 
	 * {@link Coordinate}s. The algorithm used is recursive. The points 
	 * array is sorted based on the x coordinate when this call completes.
	 * @param points Points to index with a kd-tree.
	 * @return Balanced Kd tree containing all the points in the array. 
	 * @since 1.12
	 */
	private void loadTree(Coordinate []points,int k) { 
		// sift for duplicates
		TreeSet<Coordinate> uniquePoints = new TreeSet<Coordinate>() ;
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = Double.MIN_VALUE;
		double maxY = Double.MIN_VALUE;
		
		for (Coordinate point : points) {
			if(tolerance != Double.NaN){
				point.x = Math.round(point.x / tolerance) / (1/tolerance);
				point.y = Math.round(point.y / tolerance) / (1/tolerance);
			}
			if(point.x < minX) minX = point.x;
			if(point.y < minY) minY = point.y;
			if(point.x > maxX) maxX = point.x;
			if(point.y > maxY) maxY = point.y;
			
			uniquePoints.add(point) ; 
		}
		Coordinate []unique = new Coordinate[uniquePoints.size()];
		uniquePoints.toArray(unique); 
		
		this.root = makeTree(unique,0,k);		
		this.numberOfNodes = unique.length;
		this.treeBBox = new Envelope(new Coordinate(minX,minY), new Coordinate(maxX,maxY));
	}
	
	public static void main(String[] args) {
		// sift for duplicates
		Coordinate[] points = {new Coordinate(24.37623,48.911923), new Coordinate(24.37619,48.911899)};
		TreeSet<Coordinate> uniquePoints = new TreeSet<Coordinate>() ; 
		for (Coordinate point : points) {
			point.x = Math.round(point.x/.001) / (1/.001);
			point.y = Math.round(point.y/.001) / (1/.001);
			System.out.println(point);
			uniquePoints.add(point) ; 
		}
		System.out.println("Size: " + uniquePoints.size());
//		Coordinate []unique = (Coordinate[])(uniquePoints.toArray(coordType));
	}


}
