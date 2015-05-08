package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

public class TreeMerger {

	public static final double log2 = Math.log(2);
	public HashMap<Integer,HashMap<NCRPNode, Double>> referenceTreeMap;
	
	public NCRPNode merge(ArrayList<NCRPNode> subTreeRoots) throws IOException {
		
		//System.out.println("In Merge");
		int referenceTreeId = 0;
		NCRPNode root = subTreeRoots.get(referenceTreeId);
		NCRPNode referenceRoot = null;
		NCRPNode nonReferenceRoot = null;
		
		for(int j = 1; j < subTreeRoots.size(); j++){

			System.out.println("Sub tree: "+j);
			NCRPNode node1 = root;
			NCRPNode node2 = subTreeRoots.get(j);
			referenceRoot = findReferenceTree(node1,node2);
			if(referenceRoot.equals(node1)) nonReferenceRoot = node2;
			else nonReferenceRoot = node1;
			
			System.out.println("REFERENCE ROOT : "+referenceRoot.nodeID);
			System.out.println("NON REFERENCE ROOT : "+nonReferenceRoot.nodeID);
			
			//ArrayList<NCRPNode> refRoot = new ArrayList<NCRPNode>();
			//refRoot.add(referenceRoot);
			constructReferenceTreeMap(referenceRoot);
			
			HashMap<NCRPNode,Double> similarityMap = new HashMap<NCRPNode, Double>();
			similarityMap.put(referenceRoot, findSimilarity(referenceRoot,nonReferenceRoot));
			referenceTreeMap.put(0, (HashMap<NCRPNode, Double>) similarityMap.clone());
			
			System.out.println("LEVEL of ref root : "+referenceRoot.level);
			root = compareNodes(referenceRoot,nonReferenceRoot,referenceTreeMap.get(referenceRoot.level+1).keySet());
			//root = referenceRoot;	
			
		}
		System.out.println("Root : "+root.nodeID);
		return root;
	}
	
	public NCRPNode findReferenceTree(NCRPNode node1, NCRPNode node2) throws IOException {
		
		double min1 = Double.MAX_VALUE;
        double min2 = Double.MAX_VALUE;
        
		for(int k=0;k<node2.children.size();k++) {
			double sim = findSimilarity(node1,node2.children.get(k));
			if(sim < min1) min1 = sim; 
		}
		
		for(int k=0;k<node1.children.size();k++) {
			double sim = findSimilarity(node2,node1.children.get(k));
			if(sim < min2) min2 = sim; 
		}
		System.out.println("ROOT : "+node1.nodeID);
		System.out.println("SUB TREE ROOT : "+node2.nodeID);
		if(min1<min2) 
			return node1;
		else 
			return node2;

	}
	
	public double findSimilarity(NCRPNode node, NCRPNode subTreeRoot) throws IOException {

		ArrayList<String> vocabulary = new ArrayList<String>();

		Set<String> words1 = node.getKeySet();
		Iterator<String> it1 = words1.iterator();
		int nodeTotal=0,subTreeTotal=0;
		while(it1.hasNext()){ 
			String s = (String) it1.next();
			nodeTotal += (int)node.getWordCount(s);
			vocabulary.add(s);
		}

		Set<String> words3 = subTreeRoot.getKeySet();
		Set words2 = Collections.synchronizedSet(new HashSet(words3));
		Iterator<String> it2 = words2.iterator();
		while(it2.hasNext()) {
			String s = (String) it2.next();
			subTreeTotal += (int)subTreeRoot.getWordCount(s);
			if(!vocabulary.contains(s))
				vocabulary.add(s);
		}

		HashMap<String,Double> probabilityDistributionMap1 = new HashMap<String,Double>();
		HashMap<String,Double> probabilityDistributionMap2 = new HashMap<String,Double>();



		for(int i=0;i<vocabulary.size();i++) {

			String s = vocabulary.get(i);

			probabilityDistributionMap1.put(s, 0.0);
			probabilityDistributionMap2.put(s, 0.0);

			if(node.checkKey(s)) 
				probabilityDistributionMap1.put(s, node.getWordCount(s)/(1.0*nodeTotal));

			if(subTreeRoot.checkKey(s)) 
				probabilityDistributionMap2.put(s, subTreeRoot.getWordCount(s)/(1.0*subTreeTotal));

		}
		return klDivergence(probabilityDistributionMap1,probabilityDistributionMap2);
	}
	
	public double klDivergence(HashMap<String,Double> p1, HashMap<String,Double> p2) {

	      double klDiv = 0.0;
	      Collection<Double> c1 = p1.values();
	      Collection<Double> c2 = p2.values();
	      Iterator<Double> it1 = c1.iterator();
	      Iterator<Double> it2 = c2.iterator();
	      
	      while(it1.hasNext()) {
	    	  double prob1 = ((Double) it1.next()).doubleValue();
	    	  double prob2 = ((Double) it2.next()).doubleValue();
	    	  if((prob1!=0.0) && (prob2!= 0.0))
	    		  klDiv += prob1 * Math.log( prob1 / prob2 );
	      }
	      
	     klDiv /= log2;
	     return klDiv;
	      
	}

	public NCRPNode compareNodes(NCRPNode root1, NCRPNode root2, Set<NCRPNode> nodeSet) throws IOException {
		
		int level = root1.level+1;
		NCRPNode simNode = null;
		
		while(level<=referenceTreeMap.size()) {
			
			if(!nodeSet.isEmpty()) {
				
				simNode = findMinDissimilarityNode(root2,level,nodeSet);
				System.out.println("SIM NODE : "+simNode.nodeID);
				if(isCloselyRelated(simNode)) {
					System.out.println("-----Merge Point Reached-----");
					joinNodes(simNode.parent,root2);
					break;
				}
				
				else {
					System.out.println("-----Pruning-----");
					nodeSet = pruneNodes(simNode);
					
					//hereeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee 1
					Iterator iter = nodeSet.iterator();
					while(iter.hasNext())
						System.out.println("NODESET : "+((NCRPNode)iter.next()).nodeID);
					
					level++;
				}
				
			}
			
			if( (level>referenceTreeMap.size()) || (nodeSet.isEmpty())) {
				
				System.out.println("LAST LEVEL -- Merge Point not achieved :( ");
				simNode.children = new ArrayList<NCRPNode>();
				simNode.children.add(root2);
				root2.parent = simNode;
				updateReferenceTreeMap(root2,simNode.level+1);
				updateDocumentAllocation(root2);
				
				//hereeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee 2
				for(int i=0;i<referenceTreeMap.size();i++) {
					
					Set<NCRPNode> nodes = referenceTreeMap.get(i).keySet();
					Iterator<NCRPNode> iterator = nodes.iterator();
					System.out.println("LEVEL : "+i+"\n");
					while(iterator.hasNext()) {
						//System.out.println("IN ITER");
						NCRPNode n = iterator.next();
						if(i==0)
							System.out.println("node : "+n.nodeID);
						else
							System.out.println("node : "+n.nodeID+"   parent : "+n.parent.nodeID);
					}
				}
				
				
				break;
				
			}
			
			System.out.println();
		
		}

		return root1;
		
	}
	
	/*public void pruneNodes(NCRPNode node) {
		
		int level = node.level;
		int numLevels = referenceTreeMap.size();
	
		Set<NCRPNode> nodes = referenceTreeMap.get(level).keySet();
		Iterator<NCRPNode> it = nodes.iterator();
		ArrayList<NCRPNode> p = new ArrayList<NCRPNode>();
		while(it.hasNext()) {
			NCRPNode n = (NCRPNode) it.next();
			if(!n.equals(node)) 
				p.add(n);
		}
		
		while(level <= numLevels) { // p.isEmpty()
			
			System.out.println("Pruning level "+level);
			//if(referenceTreeMap.get(level)==null) break;
			//nodes = referenceTreeMap.get(level).keySet();
			//it = nodes.iterator();
			
			
			ArrayList<NCRPNode> c = new ArrayList<NCRPNode>();
			for(int i=0;i<p.size();i++) 
				for(int j=0;j<p.get(i).children.size();j++) 
					c.add(p.get(i).children.get(j));
			
			for(int i=0;i<p.size();i++) {
				System.out.println("PRUNING NODE : "+p.get(i).nodeID);
				referenceTreeMap.get(level).remove(p.get(i));
			}
			
			p.clear();
			if(!c.isEmpty()) {
				p = (ArrayList<NCRPNode>) c.clone();
				c.clear();
			}
			else
				break;
			
			level++;
		
		}
		
	}*/
	
	public Set<NCRPNode> pruneNodes(NCRPNode node) {
		
		Set<NCRPNode> c = new HashSet<NCRPNode>();
		for(int i=0;i<node.children.size();i++)
			c.add(node.children.get(i));
		return c; 
		
	}
	
	double threshold = 1;
	public void joinNodes(NCRPNode node1, NCRPNode node2) throws IOException {
		
		if(referenceTreeMap.get(node1.level).get(node1)<threshold) { // merge
			
			System.out.println("UNIFYING TWO NODES : "+node1.nodeID+" , "+node2.nodeID);
			unifyNodes(node1,node2);
			
			for(int i=0;i<node2.children.size();i++) {
				
				System.out.println("Starting node : "+node1.nodeID);
				System.out.println("Analysing node : "+node2.children.get(i).nodeID);
				
				HashMap<NCRPNode,Double> similarityMap = referenceTreeMap.get(node1.level);
				similarityMap.put(node1, findSimilarity(node1,node2.children.get(i)));
				referenceTreeMap.put(node1.level, (HashMap<NCRPNode, Double>) similarityMap.clone());
				
				Set<NCRPNode> c = new HashSet<NCRPNode>();
				for(int m=0;m<node1.children.size();m++)
					c.add(node1.children.get(m));
				
				compareNodes(node1, node2.children.get(i), c);
				
			}
			
		}
		
		else { // child
			
			System.out.println("Adding as child : "+node1.nodeID+" , "+node2.nodeID);
			node1.children.add(node2);
			node2.parent = node1;
			updateReferenceTreeMap(node2,node1.level+1);
			updateDocumentAllocation(node2);
			
			
			//hereeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee 3
			for(int i=0;i<referenceTreeMap.size();i++) {
				
				Set<NCRPNode> nodes = referenceTreeMap.get(i).keySet();
				Iterator<NCRPNode> iterator = nodes.iterator();
				System.out.println("LEVEL : "+i+"\n");
				while(iterator.hasNext()) {
					//System.out.println("IN ITER");
					NCRPNode n = iterator.next();
					if(i==0)
						System.out.println("node : "+n.nodeID);
					else
						System.out.println("node : "+n.nodeID+"   parent : "+n.parent.nodeID);
				}
			}
			
		}
		
	}
	
	public void updateReferenceTreeMap(NCRPNode node2, int level) {
		
		ArrayList<NCRPNode> p = new ArrayList<NCRPNode>();
		ArrayList<NCRPNode> c = new ArrayList<NCRPNode>();
		
		node2.level = level;
		p.add(node2);
				
		while(!p.isEmpty()) {
			
			for(int i=0;i<p.size();i++) {
				
				HashMap<NCRPNode,Double> simMap = null;
				if(referenceTreeMap.get(level)==null) 
					simMap = new HashMap<NCRPNode, Double>();
				else 
					simMap = referenceTreeMap.get(level);
				
				simMap.put(p.get(i), 0.0);
				referenceTreeMap.put(level, (HashMap<NCRPNode, Double>) simMap.clone());
				
				for(int j=0;j<p.get(i).children.size();j++) {
					
					NCRPNode n = p.get(i).children.get(j);
					n.level = level+1;
					c.add(n);
				
				}
					
			}
			
			p.clear();
			if(!c.isEmpty()) {
				p = (ArrayList<NCRPNode>) c.clone();
				c.clear();
			}
			
			level++;
		}
		
	}
	
	public void unifyNodes(NCRPNode node1, NCRPNode node2) {
		
		System.out.println("In unifyNodes");
		//Adding the documents and customers
		for(int i=0;i<node2.documents.size();i++) {
			if(!(node1.documents.contains(node2.documents.get(i)))) {
				node1.documents.add(node2.documents.get(i));
				node1.customers++;
			}
		}
		updateDocumentAllocation(node1);
		
		//Adding the words and updating total tokens
		Set<String> words = node2.wordCount.keySet();
		Iterator<String> it = words.iterator();
		while(it.hasNext()) {
			String w = (String) it.next();
			if(node1.wordCount.containsKey(w)) {
				int count = node1.wordCount.get(w) + node2.wordCount.get(w);
				node1.wordCount.put(w, count);				
			}
			else {
				node1.wordCount.put(w, node2.wordCount.get(w));
				node1.totalTokens++;
			}
		}
		
	}
	
	public void updateDocumentAllocation(NCRPNode node) {
		
		NCRPNode parent = node.parent;
		while(parent!=null) {
			for(int i=0;i<node.documents.size();i++) {
				if(!(parent.documents.contains(node.documents.get(i)))) {
					parent.documents.add(node.documents.get(i));
					parent.customers++;
				}
			}
			parent = parent.parent;
		}
		
	}
	
	
	public boolean isCloselyRelated(NCRPNode node) {
		try{
			if((referenceTreeMap.get(node.parent.level).get(node.parent))!=null)
				if( (referenceTreeMap.get(node.level).get(node)) < (referenceTreeMap.get(node.parent.level).get(node.parent)) ) 
					return true;
			return false;
		}catch(Exception e){
			System.out.println("CloseRelated Error");
			//System.out.println("Debug 1: "+(referenceTreeMap.get(node.level).get(node)));
			//System.out.println("Debug 2: "+(referenceTreeMap.get(node.parent.level).get(node.parent)));
			return false;
		}
		
	}
	
	public NCRPNode findMinDissimilarityNode(NCRPNode subTreeRoot, int level, Set<NCRPNode> nodeSet) throws IOException {
		
		Iterator<NCRPNode> iterator;
		ArrayList<Double> similarityList = new ArrayList<Double>();	
			
		iterator = nodeSet.iterator();	
		
		while(iterator.hasNext()) {
			NCRPNode node = (NCRPNode) iterator.next();
			double sim = findSimilarity(node, subTreeRoot);
			similarityList.add(sim);
		}
		updateReferenceTreeSimilarities(nodeSet, similarityList,level);
		NCRPNode n = getMin(similarityList, level, nodeSet);
		return n;
					
	}
	
	public void updateReferenceTreeSimilarities(Set<NCRPNode> nodeSet, ArrayList<Double> similarities, int level){
		
		HashMap<NCRPNode, Double> simMap = referenceTreeMap.get(level);
		//Set<NCRPNode> nodeSet = map.keySet();
		Iterator<NCRPNode> it = nodeSet.iterator();

		for(int i=0;i<similarities.size();i++) {
			
			NCRPNode n = (NCRPNode) it.next();
			simMap.put(n,similarities.get(i));
			
		}
		
		referenceTreeMap.put(level, (HashMap<NCRPNode, Double>)simMap.clone());
		
	}

	public NCRPNode getMin(ArrayList<Double> similarities, int level, Set<NCRPNode> nodeSet){
		
		double min = Double.MAX_VALUE;
		int index = -1;
		for(int i = 0; i < similarities.size(); i++){
			if(min > similarities.get(i).doubleValue() ) {
				min = similarities.get(i).doubleValue();
				index = i;
			}
		}
		//HashMap<NCRPNode, Double> levelMap = referenceTreeMap.get(level);
		//Set<NCRPNode> nodes = levelMap.keySet();
		Iterator<NCRPNode> it = nodeSet.iterator();
		int k = 0;
		NCRPNode node = (NCRPNode) it.next();
		while(k++ != index)
			node = (NCRPNode) it.next();
		return node;
		
	}

	public int constructReferenceTreeMap(NCRPNode root) {
		
		int level = 0;
		ArrayList<NCRPNode> p = new ArrayList<NCRPNode>();
		
		root.level = level;
		p.add(root);
		
		referenceTreeMap = new HashMap<Integer,HashMap<NCRPNode, Double>>();
		
		while(!p.isEmpty()) {
			
			HashMap<NCRPNode,Double> simMap = new HashMap<NCRPNode,Double>();
			for(int i=0;i<p.size();i++) 
				simMap.put(p.get(i), 0.0);
			referenceTreeMap.put(level, (HashMap<NCRPNode, Double>) simMap.clone());
			
			ArrayList<NCRPNode> c = new ArrayList<NCRPNode>();
			for(int i=0;i<p.size();i++) {
				for(int j=0;j<p.get(i).children.size();j++) {
					NCRPNode n = p.get(i).children.get(j);
					n.level = level+1;
					c.add(n);
				}
			}
				
			p.clear();
			if(!c.isEmpty()) {
				p = (ArrayList<NCRPNode>) c.clone();
				c.clear();
			}

			level++;
		}
		
		for(int i=1;i<referenceTreeMap.size();i++) {
			
			Set<NCRPNode> nodes = referenceTreeMap.get(i).keySet();
			Iterator<NCRPNode> iterator = nodes.iterator();
			System.out.println("LEVEL : "+i+"\n");
			while(iterator.hasNext()) {
				//System.out.println("IN ITER");
				NCRPNode n = iterator.next();
				System.out.println("node : "+n.nodeID+"   parent : "+n.parent.nodeID);
			}
		}

		return referenceTreeMap.size();
		
		
	}
	/*public int constructReferenceTreeMap(ArrayList<NCRPNode> subTreeNode){
		
		if(referenceTreeMap==null) referenceTreeMap = new HashMap<Integer,HashMap<NCRPNode, Double>>();
		int level = 0; 
	//	int ID = 0;
		
		while(!subTreeNode.isEmpty()){
			
			ArrayList<NCRPNode> childNodes = new ArrayList<NCRPNode>();

			for(int i=0; i<subTreeNode.size(); i++){
				
				subTreeNode.get(i).level = level;
		//		subTreeNode.get(i).nodeID = ID;
			//	ID++;
				if(referenceTreeMap.containsKey(level))
					(referenceTreeMap.get(level)).put(subTreeNode.get(i), 0.0);
				
				else {
					
					HashMap<NCRPNode,Double> similarityMap = new HashMap<NCRPNode, Double>();
					similarityMap.put(subTreeNode.get(i), 0.0);
					referenceTreeMap.put(level, (HashMap<NCRPNode, Double>) similarityMap.clone());
					
				}
				
				for(int j=0; j<subTreeNode.get(i).children.size(); j++)
					childNodes.add(subTreeNode.get(i).children.get(j));
				
			}
			
			subTreeNode.clear();
			if(!childNodes.isEmpty())
				subTreeNode = (ArrayList<NCRPNode>) childNodes.clone();
			
			level++;
		}
	
		for(int i=1;i<referenceTreeMap.size();i++) {
			
			Set<NCRPNode> nodes = referenceTreeMap.get(i).keySet();
			Iterator<NCRPNode> iterator = nodes.iterator();
			System.out.println("LEVEL : "+i+"\n");
			while(iterator.hasNext()) {
				//System.out.println("IN ITER");
				NCRPNode n = iterator.next();
				System.out.println("node : "+n.nodeID+"   parent : "+n.parent.nodeID);
			}
		}
		
		return referenceTreeMap.size();
		
	}*/
	
	

}