package hdbscan;

import java.util.Arrays;
import java.util.TreeMap;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

/**
 * A node of a {@link KdTree}, which represents one or more points in the same location.
 * 
 * @author dskea
 */
public class KdNode implements Comparable<KdNode> {

    private Coordinate p = null;
    private KdNode left;
    private KdNode right;
    private KdNode parent;
    private Integer axis;
    private Integer count;
    private Double coreDistance;
    private Double bboxDistance; 
	private Integer label;
	private TreeMap<Double, KdNode> neighbors;
	private Integer k;
	private static int nodeCount = 0;


	private Envelope bbox;
	private boolean hasKNeighbors;

    /**
     * Creates a new KdNode.
     * 
     * @param _x coordinate of point
     * @param _y coordinate of point
     * @param data a data objects to associate with this node
     */
    public KdNode(double _x, double _y,int axis, int k) {
        p = new Coordinate(_x, _y);
        left = null;
        right = null;
        count = 1;
        this.label = nodeCount;
        nodeCount += 1;
        this.k = k;
        this.axis = axis;
		this.coreDistance = Double.MAX_VALUE;
		this.neighbors = new TreeMap();
		
		bbox = new Envelope(p,p);
    }

    /**
     * Creates a new KdNode.
     * 
     * @param p point location of new node
     * @param data a data objects to associate with this node
     */
    public KdNode(Coordinate p, int axis, int k) {
        this.p = new Coordinate(p);
        left = null;
        right = null;
        count = 1;
        
        this.label = nodeCount;
        nodeCount += 1;
        this.k = k;
        this.axis = axis;
		this.coreDistance = Double.MAX_VALUE;
		this.neighbors = new TreeMap();
		
		bbox = new Envelope(p,p);
    }
    
    public void calculateBBox(Double dist){
    	if(neighbors.size() > 0){
	    	final int R = 6371;
	    	final double MIN_LAT = Math.toRadians(-90d);  // -PI/2
	    	final double MAX_LAT = Math.toRadians(90d);   //  PI/2
	    	final double MIN_LON = Math.toRadians(-180d); // -PI
	    	final double MAX_LON = Math.toRadians(180d);  //  PI
	    	double radLat = Math.toRadians(p.y);
	    	double radLon = Math.toRadians(p.x);
	    	
	    	double radDist = dist / R;
	    	
	    	double minLat = radLat - radDist;
	    	double maxLat = radLat + radDist;
	    	
	    	double minLon, maxLon;
	    	if (minLat > MIN_LAT && maxLat < MAX_LAT) {
				double deltaLon = Math.asin(Math.sin(radDist) /
					Math.cos(radLat));
				minLon = radLon - deltaLon;
				if (minLon < MIN_LON) minLon += 2d * Math.PI;
				maxLon = radLon + deltaLon;
				if (maxLon > MAX_LON) maxLon -= 2d * Math.PI;
			} else {
				// a pole is within the distance
				minLat = Math.max(minLat, MIN_LAT);
				maxLat = Math.min(maxLat, MAX_LAT);
				minLon = MIN_LON;
				maxLon = MAX_LON;
			}
	    	bbox.expandToInclude(Math.toDegrees(minLon), Math.toDegrees(minLat));
	    	bbox.expandToInclude(Math.toDegrees(maxLon), Math.toDegrees(maxLat));
    	}
    	
    }
    
    public void calculateBBox(){
    	Double dist = calculateMedianDistance();
    	calculateBBox(dist);
    }
    
    public Double calculateMinDistance(){
    	if(neighbors.size() < 1){
    		return null;
    	}
    	if(bboxDistance != null){
    		return neighbors.higherKey(bboxDistance);
    	}else{
    		return neighbors.firstKey();
    	}
    }
    
    public Double calculateMedianDistance(){
    	if(neighbors.size() < 1){
    		return null;
    	}
    	Double dist;
    	Double[] distArray = new Double[neighbors.size()];
    	neighbors.descendingKeySet().toArray(distArray);
    	
    	if(neighbors.size() % 2 == 0){
    		int half = neighbors.size()/2;
    		dist = (distArray[half] + distArray[half-1]) / 2;
    	}else{
    		dist = distArray[neighbors.size()/2];
    	}
    	return dist;
    }
    
    public Double addNeighbor(KdNode other){
    	if(neighbors.containsValue(other)){
    		return null;
    	}

		double currDistance = computeDistance(this.p,other.p);
		double retDistance = currDistance;
		
		if(neighbors.size() < k){
			neighbors.put(currDistance,other);
			if(neighbors.size() == k){
				hasKNeighbors = true;
			}
			coreDistance = neighbors.lastEntry().getKey();
			return retDistance;
		}
		else if(currDistance < coreDistance){
//			System.out.println("Before remove:" + neighbors.size());
			neighbors.pollLastEntry();
//			System.out.println("After remove: " + neighbors.size());
			neighbors.put(currDistance,other);
			coreDistance = neighbors.lastEntry().getKey();
			return retDistance;
		} else return null;
		
	}
    
    public Double addNeighbor(KdNode other, double distance){
    	if(neighbors.containsValue(other)){
    		return null;
    	}

		double currDistance = distance;
		double retDistance = currDistance;

		if(neighbors.size() < k){
			neighbors.put(currDistance,other);
			if(neighbors.size() == k){
				hasKNeighbors = true;
			}
			coreDistance = neighbors.lastEntry().getKey();
			return retDistance;
		}
		else if(currDistance < coreDistance){
			neighbors.pollLastEntry();
			neighbors.put(currDistance,other);
			coreDistance = neighbors.lastEntry().getKey();
			return retDistance;
		} else return null;
    }
    
    public double computeDistance(Coordinate point1, Coordinate point2){
		final int R = 6371; // Radius of the earth
		
        Double lat1 = point1.y;
        Double lon1 = point1.x;
        Double lat2 = point2.y;
        Double lon2 = point2.x;
        Double latDistance = toRad(lat2-lat1);
        Double lonDistance = toRad(lon2-lon1);
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + 
                   Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * 
                   Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        Double distance = R * c;
        
		return distance;
	}
	
	private static Double toRad(Double value) {
        return value * Math.PI / 180;
    }
	
	public int getAxis(){
		return axis;
	}
	
	 /**
     * The value of this node's coordinate along the split dimension.
     * @return this node's split dimension coordinate
     * @since 1.12
     */
    public double getSplitValue() { 
    	double retval ; 
    	switch (axis) { 
    	case 0 :
    		retval = p.x ; 
    		break ;
    	case 1 : 
    		retval = p.y ; 
    		break ; 
    	default :
    		retval = Double.NaN ; 
    	}
    	return retval ;
    }
    
    /**
     * Returns the value of the coordinate of the specified point along the
     * split axis. 
     * @param other the specified point
     * @return the coordinate value of {@link other} along the split axis.
     * @since 1.12
     */
    public double getSplitValue(Coordinate other) { 
    	double retval ; 
    	switch (axis) {
    	case 0 :
    		retval = other.x ; 
    		break ;
    	case 1 : 
    		retval = other.y ; 
    		break ; 
    	default :
    		retval = Double.NaN ; 
    	}
    	return retval ;
    }
    /**
     * Returns the X coordinate of the node
     * 
     * @retrun X coordiante of the node
     */
    public double getX() {
        return p.x;
    }

    /**
     * Returns the Y coordinate of the node
     * 
     * @return Y coordiante of the node
     */
    public double getY() {
        return p.y;
    }

    /**
     * Returns the location of this node
     * 
     * @return p location of this node
     */
    public Coordinate getCoordinate() {
        return p;
    }

    public double getCoreDistance() {
		return coreDistance;
	}

	public int getLabel() {
		return label;
	}


	public TreeMap<Double, KdNode> getNeighbors() {
		return neighbors;
	}
	
	public int getK() {
		return k;
	}

    /**
     * Returns the left node of the tree
     * 
     * @return left node
     */
    public KdNode getLeft() {
        return left;
    }

    /**
     * Returns the right node of the tree
     * 
     * @return right node
     */
    public KdNode getRight() {
        return right;
    }

    // Increments counts of points at this location
    void increment() {
        count = count + 1;
    }

    /**
     * Returns the number of inserted points that are coincident at this location.
     * 
     * @return number of inserted points that this node represents
     */
    public int getCount() {
        return count;
    }

    public Envelope getBbox() {
		return bbox;
	}


	public boolean hasKNeighbors() {
		return hasKNeighbors;
	}
	
	public boolean isBottom(){
		return (left == null && right == null);
	}

	/**
     * Tests whether more than one point with this value have been inserted (up to the tolerance)
     * 
     * @return true if more than one point have been inserted with this value
     */
    public boolean isRepeated() {
        return count > 1;
    }

    // Sets left node value
    public void setLeft(KdNode _left) {
        left = _left;
    }

    // Sets right node value
    public void setRight(KdNode _right) {
        right = _right;
    }
    
	public KdNode getParent() {
		return parent;
	}

	public void setParent(KdNode parent) {
		this.parent = parent;
	}

	@Override
	public String toString() {
		return "KdNode [p=" + p + ", coreDistance=" + coreDistance + ", label=" + label + ", neighborDistances="
				+ Arrays.toString(neighbors.descendingKeySet().toArray()) + ", bbox=" + bbox + ", hasKNeighbors=" + hasKNeighbors + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((p == null) ? 0 : p.hashCode());
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
		KdNode other = (KdNode) obj;
		if (p == null) {
			if (other.p != null)
				return false;
		} else if (!p.equals(other.p))
			return false;
		return true;
	}

	@Override
	public int compareTo(KdNode other) {
		return this.label < other.label ? -1 : (this.label > other.label ? 1 : 0);
	}
}
