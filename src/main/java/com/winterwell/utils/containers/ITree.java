package com.winterwell.utils.containers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.winterwell.utils.Utils;

/**
 * A generic tree data structure. See the default implementation: {@link Tree}.
 * 
 * @author Daniel
 */
public interface ITree<X> {
	

	/**
	 * @return all tree nodes from this node downwards (inc this node).
	 *         flattened so that a parent comes before its children.
	 */
	public default List<ITree<X>> flatten() {
		List flat = new ArrayList<>();
		flatten2(this, flat);
		return flat;
	}

	private static void flatten2(ITree dis, List<ITree> flat) {
		flat.add(dis);
		for (Object kid : dis.getChildren()) {
			flatten2((ITree) kid, flat);
		}
	}
	
	/**
	 * Walk the tree, creating a new tree
	 * 
	 * @param fn (old-value) -> new-value 
	 * 	Apply this to each node (leaf and branch). 
	 * 	If it returns null, remove the node.
	 */
	public default <Y> Tree<Y> apply(Function<X, Y> fn) {
		Y nv = fn.apply(getValue());
		if (nv==null) return null;
		Tree<Y> nn = new Tree(nv);
		List<? extends ITree<X>> kids = getChildren();
		if (kids == null) return nn;
		for (ITree<X> kid : kids) {
			Tree<Y> nkid = kid.apply(fn);
			if (nkid!=null) {
				nkid.setParent(nn);
			}
		}
		return nn;
	}
	
	/**
	 * Add a child node.
	 * 
	 * @param childNode
	 *            This must not have a parent. Use setParent(null) if necessary
	 *            to first detach the child.
	 * @Deprecated Use {@link #setParent(ITree)} instead. This method should
	 *             only be called by setParent()!
	 */
	@Deprecated 	// Use setParent instead
	abstract void addChild(ITree<X> childNode);

	/**
	 * 
	 * @return the immeadiate children
	 */
	public abstract List<? extends ITree<X>> getChildren();

	/**
	 * @return the depth of this tree, ie. the longest chain from here to a leaf
	 *         node. 1 if this is a leaf node. Will get stuck if some numpty has
	 *         made a loop.
	 */
	public default int getMaxDepthToLeaf() {
		int max = 0;
		for (ITree k : getChildren()) {
			max = Math.max(max, k.getMaxDepthToLeaf());
		}
		return max + 1;
	}

	/**
	 * Convenience for getting the child node if there is one and only one. It
	 * is an error to call this is there are multiple child nodes.
	 */
	public default ITree<X> getOnlyChild() {
		List<? extends ITree<X>> kids = getChildren();
		if (kids.size() != 1) throw new IllegalStateException("Multiple kids "+this);
		return kids.get(0);	
	}

	public abstract ITree<X> getParent();

	public abstract X getValue();

	public default boolean isLeaf() {
		return Utils.isEmpty(getChildren());
	}

	/**
	 * Remove a child node.
	 * 
	 * @param childNode
	 *            This must be a child node.
	 * @Deprecated Use {@link #setParent(ITree)} with null instead. This method
	 *             should only be called by setParent().
	 */
	@Deprecated
	abstract void removeChild(ITree<X> childNode);

	/**
	 * Set the parent node for this node. Remove the previous parent if
	 * necessary. Manage the child links.
	 * 
	 * @param parent
	 */
	public abstract void setParent(ITree<X> parent);

	public abstract void setValue(X value);

}