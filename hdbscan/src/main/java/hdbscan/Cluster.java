package hdbscan;

import java.util.ArrayList;


/**
 * An HDBSCAN* cluster, which will have a birth level, death level, stability, and constraint 
 * satisfaction once fully constructed.
 * @author zjullion
 */
public class Cluster {

	// ------------------------------ PRIVATE VARIABLES ------------------------------
	
	private int label;
	private double birthLevel;
	private int numPoints;
	
	private double stability;
	private double propagatedStability;

	private Cluster parent;
	private boolean hasChildren;
	public ArrayList<Cluster> propagatedDescendants;
	

	// ------------------------------ CONSTANTS ------------------------------

	// ------------------------------ CONSTRUCTORS ------------------------------
	
	/**
	 * Creates a new Cluster.
	 * @param label The cluster label, which should be globally unique
	 * @param parent The cluster which split to create this cluster
	 * @param birthLevel The MST edge level at which this cluster first appeared
	 * @param numPoints The initial number of points in this cluster
	 */
	public Cluster(int label, Cluster parent, double birthLevel, int numPoints) {
		this.label = label;
		this.birthLevel = birthLevel;
		this.numPoints = numPoints;
		
		this.stability = 0;
		this.propagatedStability = 0;
		
		this.parent = parent;
		if (this.parent != null)
			this.parent.hasChildren = true;
		this.hasChildren = false;
		this.propagatedDescendants = new ArrayList<Cluster>(1);
	}
	

	// ------------------------------ PUBLIC METHODS ------------------------------
	
	/**
	 * Removes the specified number of points from this cluster at the given edge level, which will
	 * update the stability of this cluster and potentially cause cluster death.  If cluster death
	 * occurs, the number of constraints satisfied by the virtual child cluster will also be calculated.
	 * @param numPoints The number of points to remove from the cluster
	 * @param level The MST edge level at which to remove these points
	 */
	public void detachPoints(int numPoints, double level) {
		this.numPoints-=numPoints;
		this.stability+=(numPoints * (1/level - 1/this.birthLevel));
		
		if (this.numPoints < 0)
			throw new IllegalStateException("Cluster cannot have less than 0 points.");
	}
	

	/**
	 * This cluster will propagate itself to its parent if its number of satisfied constraints is
	 * higher than the number of propagated constraints.  Otherwise, this cluster propagates its
	 * propagated descendants.  In the case of ties, stability is examined.
	 * Additionally, this cluster propagates the lowest death level of any of its descendants to its
	 * parent.
	 */
	public void propagate() {
		if (this.parent != null) {
			
			//If this cluster has no children, it must propagate itself:
			if (!this.hasChildren) {
				this.parent.propagatedStability+= this.stability;
				this.parent.propagatedDescendants.add(this);
			}
			else{
				//Chose the parent over descendants if there is a tie in stability:
				if (this.stability >= this.propagatedStability) {
					this.parent.propagatedStability+= this.stability;
					this.parent.propagatedDescendants.add(this);
				}
				else {
					this.parent.propagatedStability+= this.propagatedStability;
					this.parent.propagatedDescendants.addAll(this.propagatedDescendants);
				}	
			}	
		}
	}
	
	// ------------------------------ PRIVATE METHODS ------------------------------

	// ------------------------------ GETTERS & SETTERS ------------------------------
	
	public int getLabel() {
		return this.label;
	}
	
	public Cluster getParent() {
		return this.parent;
	}
	
	public double getBirthLevel() {
		return this.birthLevel;
	}

	public double getStability() {
		return this.stability;
	}

	public ArrayList<Cluster> getPropagatedDescendants() {
		return this.propagatedDescendants;
	}
	
	public boolean hasChildren() {
		return this.hasChildren;
	}
}