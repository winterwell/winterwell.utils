package com.winterwell.utils.containers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.web.IHasJson;

/**
 * A simple double-linked tree data structure (the json output is single-linked, parent->children). 
 * Iteration lets you step through ALL the nodes.
 * 
 * @author daniel
 * @testedby  TreeTest}
 */
public class Tree<X> 
implements Iterable<Tree<X>>, ITree<X>, IHasJson 
{
	
	private final List<ITree<X>> children = new ArrayList<ITree<X>>();

	private ITree<X> parent;

	private X x;

	/**
	 * Create a value-less tree root.
	 */
	public Tree() {
	}

	public Tree(ITree<X> parent, X value) {
		setParent(parent);
		setValue(value);
	}

	/**
	 * Create a tree root.
	 * 
	 * @param value
	 */
	public Tree(X value) {
		setValue(value);
	}

	/**
	 * @deprecated Use {@link #setParent(ITree)} instead. This method should only be called by setParent()!
	 */
	@Override
	public void addChild(ITree<X> childNode) {
		assert !children.contains(childNode);
		children.add(childNode);
	}

	public List<X> flattenToValues() {
		List<ITree<X>> f = flatten();
		List<X> vs = new ArrayList<X>(f.size());
		for (ITree<X> n : f) {
			if (n.getValue() == null) {
				continue;
			}
			vs.add(n.getValue());
		}
		return vs;
	}

	@Override
	public List<? extends ITree<X>> getChildren() {
		return Collections.unmodifiableList(children);
	}

	/**
	 * Convenience for getting the node-values of the child nodes.
	 * 
	 * @return
	 * @see #getChildren()
	 */
	public List<X> getChildValues() {
		ArrayList<X> vs = new ArrayList<X>(children.size());
		for (ITree<X> kid : getChildren()) {
			vs.add(kid.getValue());
		}
		return vs;
	}

	public List<Tree<X>> getLeaves() {
		// inefficient - a tree walker would be better
		List<ITree<X>> all = flatten();
		List leaves = new ArrayList();
		for (ITree<X> n : all) {
			if (n.isLeaf()) {
				leaves.add(n);
			}
		}
		return leaves;
	}

	/**
	 * Convenience for drilling down through a tree.
	 * 
	 * @param childIndices
	 *            E.g. 0,1,2 indicates the 2nd child (zero-indexed) of the 1st
	 *            child of the 0th child of this node.
	 * @return
	 */
	public ITree<X> getNode(int... childIndices) {
		ITree<X> node = this;
		for (int i : childIndices) {
			node = node.getChildren().get(i);
		}
		return node;
	}

	/**
	 * Search through this node & sub-nodes for one with the right value
	 * 
	 * @param v
	 *            Can be null
	 * @return node with value equals to v, or null
	 */
	public ITree<X> getNodeByValue(X v) {
		for (ITree<X> n : flatten()) {
			if (Utils.equals(v, n.getValue()))
				return n;
		}
		return null;
	}

	@Override
	public ITree<X> getParent() {
		return parent;
	}

	@Override
	public X getValue() {
		return x;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.winterwell.utils.containers.ITree#isLeaf()
	 */
	@Override
	public boolean isLeaf() {
		return children.size() == 0;
	}

	@Override
	public Iterator<Tree<X>> iterator() {
		List flat = flatten();
		return flat.iterator();
	}

	@Override
	@Deprecated
	public void removeChild(ITree<X> childNode) {
		boolean ok = children.remove(childNode);
		assert ok : this;
	}

	@Override
	@SuppressWarnings("deprecation")
	public synchronized void setParent(ITree<X> parent) {
		if (this.parent != null) {
			this.parent.removeChild(this);
		}
		this.parent = parent;
		if (parent != null) {
			parent.addChild(this);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.winterwell.utils.containers.ITree#setValue(X)
	 */
	@Override
	public void setValue(X value) {
		x = value;
	}

	@Override
	public String toString() {
		return toString2(0, 5);
	}

	/**
	 * @param maxDepth
	 *            max-depth
	 * @return
	 */
	public String toString2(int depth, final int maxDepth) {
		assert depth <= maxDepth;
		assert maxDepth > 0;
		String s = toString3();
		if (isLeaf())
			return s;
		depth++;
		if (maxDepth == depth)
			return s + " ...";
		for (ITree<X> t : children) {
			s += "\n" + StrUtils.repeat('-', depth)
					+ ((Tree<X>) t).toString2(depth, maxDepth);
		}
		return s;
	}

	protected String toString3() {
		return toString4_nodeName() + (x == null ? "" : ":" + x.toString());
	}

	protected String toString4_nodeName() {
		return getClass().getSimpleName();
	}

	

	public static final class DepthFirst<X> implements Iterable<ITree<X>> {

		private final ITree<X> tree;

		public DepthFirst(ITree<? extends X> tree) {
			this.tree = (ITree<X>) tree;		
		}

		@Override
		public Iterator<ITree<X>> iterator() {
			return new DepthFirstIterator<X>(tree);
		}
	}



	/**
	 * A Map which is compatible with Tree.js
	 */
	@Override
	public Map toJson2() throws UnsupportedOperationException {
		return Tree.toJsonTree(this);
	}

	/**
	 * A Map which is compatible with Tree.js
	 */
	private static Map toJsonTree(ITree<?> tree) {
		Object value = tree.getValue(); // TODO json-ify maps etc
		if (value instanceof IHasJson) {
			value = ((IHasJson) value).toJson2();
		}
		if (tree.isLeaf()) {
			return new ArrayMap("value", value);
		}
		// recurse
		List jsonkids = Containers.apply(tree.getChildren(), kid -> toJsonTree(kid));
		return new ArrayMap(
			"value", value,
			"children", jsonkids
		);
	}
	
	
}


final class DepthFirstIterator<X> extends AbstractIterator<ITree<X>> {

	private final ITree<X> tree;

	public DepthFirstIterator(ITree<X> tree) {
		this.tree = tree;
		it = (Iterator) tree.getChildren().iterator();
	}
	
	private boolean rootSent;

	private Iterator<ITree<X>> it;
	private DepthFirstIterator<X> dfit;
	
	
	@Override
	protected ITree<X> next2() throws Exception {
		if ( ! rootSent) {
			rootSent = true;
			return tree;
		}
		if (dfit!=null && dfit.hasNext()) {
			return dfit.next();
		}
		if (it.hasNext()) {
			ITree<X> kid = it.next();
			dfit = new DepthFirstIterator(kid);
			return dfit.next();
		}
		return null;
	}
	
}

