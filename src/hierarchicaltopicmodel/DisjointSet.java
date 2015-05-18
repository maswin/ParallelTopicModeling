package hierarchicaltopicmodel;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class DisjointSet<E>{
	private HashMap<E, Integer> rankMap;
	private HashMap<E, E> parentMap;
	private HashMap<E, Integer> setCount;//stores num of elements in set with root E


	public DisjointSet() {
		this.rankMap = new HashMap<E, Integer>();
		this.parentMap = new HashMap<E, E>();
		this.setCount = new HashMap<E, Integer>();
	}

	public void makeSet(E e) {
		rankMap.put(e, 0);
		parentMap.put(e, e);
		setCount.put(e, 1);
	}

	//union method with union by rank heuristic
	public void union(E x, E y) {
		link(findSet(x), findSet(y));
	}

	//findSet method with path compression heuristic
	public E findSet(E e) {
		if (!e.equals(parentMap.get(e))) {
			parentMap.put(e, findSet(parentMap.get(e)));
		}
		return parentMap.get(e);
	}

	private void link(E x, E y) {
		if (x == y) {			
			return;
		}
		E root, nonRoot;
		if (rankMap.get(x) > rankMap.get(y)) {
			parentMap.put(y, x);
			root = x;
			nonRoot = y;
		} else {
			root = y;
			nonRoot = x;
			parentMap.put(x, y);
			if (rankMap.get(x) == rankMap.get(y)) {
				rankMap.put(y, rankMap.get(y) + 1);
			}
		}
		setCount.put(root, setCount.get(root) + setCount.get(nonRoot));
		setCount.remove(nonRoot);
	}

	public boolean contains(E e) {
		return parentMap.containsKey(e);
	}

	public List<List<E>> getAllSets(){
		List<List<E>> allSets = new ArrayList<List<E>>();
		HashMap<E, List<E>> setOfElements = new HashMap<E, List<E>>();
		HashMap<E, Boolean> processed = new HashMap<E, Boolean>();
		Iterator<E> it;
		List<E> similarElementsList, tempSimilarElementsList;
		E e;

		//Form initial sets with the parent elements.
		it = setCount.keySet().iterator();
		while(it.hasNext()){
			similarElementsList = new ArrayList<E>();
			e = (E)it.next();
			similarElementsList.add(e);
			processed.put(e, true);
			setOfElements.put(e, similarElementsList);
		}

		//Examine all the other elements.
		it = parentMap.keySet().iterator();
		while(it.hasNext()){
			e = (E)it.next();

			//If processed contains the node then its placed in its set already. 
			if(processed.containsKey(e)){
				continue;
			}

			//Form a temporary similar element list as you iterate to find the parent.
			tempSimilarElementsList = new ArrayList<E>();
			while(!e.equals(parentMap.get(e))){
				if(!processed.containsKey(e)){
					processed.put(e, true);
					tempSimilarElementsList.add(e);
				}
				e = parentMap.get(e);
			}

			//e iterates to its parent
			similarElementsList = setOfElements.get(e);
			similarElementsList.addAll(tempSimilarElementsList);
		}

		//Form final sets.
		it = setCount.keySet().iterator();
		while(it.hasNext()){
			e = (E)it.next();
			similarElementsList = setOfElements.get(e);
			allSets.add(similarElementsList);
		}
		
		return allSets;
	}
}

