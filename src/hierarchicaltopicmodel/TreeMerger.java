package hierarchicaltopicmodel;

import graph.Kruskal;
import graph.UndirectedGraph;
import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Stack;

public class TreeMerger {

	public static final double log2 = Math.log(2);
	public HierarchicalLDA h;
	public HashMap<Integer, HashMap<NCRPNode, Double>> referenceTreeMap;
	
	public TreeMerger(){
		h = new HierarchicalLDA();
	}
	public NCRPNode findReferenceTree(NCRPNode node1, NCRPNode node2){
		//Place SVM Logic here
		return node1;
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
		double dist1= klDivergence(probabilityDistributionMap1,probabilityDistributionMap2);
		double dist2= klDivergence(probabilityDistributionMap2,probabilityDistributionMap1);
		return (dist1+dist2)/2;
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

	public void unifyNodes(NCRPNode node1, NCRPNode node2) {

		System.out.println("In unifyNodes");
		//Adding the documents and customers
		for(int i=0;i<node2.documents.size();i++) {
			if(!(node1.documents.contains(node2.documents.get(i)))) {
				node1.documents.add(node2.documents.get(i));
				node1.customers++;
			}
		}

		//Adding the words and updating total tokens
		Set<String> words = node2.wordCount.keySet();
		Iterator<String> it = words.iterator();
		while(it.hasNext()) {
			String w = (String) it.next();
			if(node1.wordCount.containsKey(w)) {
				int count = (node1.wordCount.get(w) + node2.wordCount.get(w))/2;
				node1.wordCount.put(w, count);				
			}
			else {
				node1.wordCount.put(w, node2.wordCount.get(w));
				node1.totalTokens++;
			}
		}

	}

	public NCRPNode mergeNodes(NCRPNode node1, NCRPNode node2) throws IOException{
		NCRPNode merged = h.new NCRPNode();
		merged.documents.addAll(node1.documents);
		merged.customers=node1.customers;
		for(int i=0;i<node2.documents.size();i++) {
			if(!(node1.documents.contains(node2.documents.get(i)))) {
				merged.documents.add(node2.documents.get(i));
				merged.customers++;
			}
		}
		merged.wordCount.putAll(node1.wordCount);
		//Adding the words and updating total tokens
		Set<String> words = node2.wordCount.keySet();
		Iterator<String> it = words.iterator();
		while(it.hasNext()) {
			String w = (String) it.next();
			if(node1.wordCount.containsKey(w)) {
				int count = (node1.wordCount.get(w) + node2.wordCount.get(w))/2;
				merged.wordCount.put(w, count);				
			}
			else {
				merged.wordCount.put(w, node2.wordCount.get(w));
				merged.totalTokens++;
			}
		}

		return merged;
	}

	public NCRPNode findMergePoint(NCRPNode referenceTree,
			NCRPNode nonReferenceTree) throws IOException {

		HashMap<Integer, Double> minDist = new HashMap<Integer, Double>();
		HashMap<Integer, NCRPNode> candidate = 
				new HashMap<Integer, HierarchicalLDA.NCRPNode>();

		minDist.put(-1, Double.MAX_VALUE);
		minDist.put(0, findSimilarity(referenceTree, nonReferenceTree));
		candidate.put(0, referenceTree);

		int level = 0;

		while (minDist.get(level) < minDist.get(level - 1)
				&& candidate.get(level).getChildren() != null) {
			minDist.put(level + 1, Double.MAX_VALUE);
			for (NCRPNode i : candidate.get(level).children) {
				Double similarity = findSimilarity(i, nonReferenceTree);
				if (similarity < minDist.get(level + 1)) {
					minDist.put(level + 1, similarity);
					candidate.put(level + 1, i);
				}
			}
			level++;
		}

		if (minDist.get(level) < minDist.get(level - 1)) {
			return candidate.get(level);
		} else {
			return candidate.get(level - 1);
		}

	}

	public void findSubTree(NCRPNode root, List<NCRPNode> subTree){
		subTree.add(root);
		for(int i=0; i<root.children.size(); i++){
			findSubTree(root.children.get(i), subTree);
		}
	}

	public NCRPNode mergeTrees(NCRPNode referenceRoot, NCRPNode nonReferenceRoot) throws IOException{

		NCRPNode n = h.new NCRPNode();

		//Set Threshold
		double threshold=0.0;

		//Similarity below threshold
		if(findSimilarity(referenceRoot, nonReferenceRoot)>threshold){ 
			NCRPNode merged = mergeNodes(referenceRoot, nonReferenceRoot);
			merged.children.add(referenceRoot);
			merged.children.add(nonReferenceRoot);
			referenceRoot.parent=merged;
			nonReferenceRoot.parent=merged;
			return merged;
		}

		//Find merge point
		NCRPNode mergePoint=findMergePoint(referenceRoot, nonReferenceRoot);

		NCRPNode tempPtr=mergePoint;

		//to parallel - copy reference tree to a concurrent hash map? 
		while(tempPtr!=null){
			unifyNodes(tempPtr, nonReferenceRoot);
			tempPtr=tempPtr.parent;
		}

		//List of Nodes to be merged
		List<NCRPNode> subTreeRef=new ArrayList<NCRPNode>();
		List<NCRPNode> subTreeNonRef=new ArrayList<NCRPNode>();
		findSubTree(mergePoint, subTreeRef);
		findSubTree(nonReferenceRoot, subTreeNonRef);

		UndirectedGraph<NCRPNode> bipartiteGraph =
				new UndirectedGraph<HierarchicalLDA.NCRPNode>();

		for (NCRPNode refTreeNode : subTreeRef) {
			bipartiteGraph.addNode(refTreeNode);
			for (NCRPNode nonRefTreeNode : subTreeNonRef) {
				bipartiteGraph.addNode(nonRefTreeNode);
				bipartiteGraph.addEdge(refTreeNode, nonRefTreeNode,
						findSimilarity(refTreeNode, nonRefTreeNode));
			}
		}

		UndirectedGraph<NCRPNode> mst = Kruskal.mst(bipartiteGraph);

		List<MergeCandidate> nodePairList = new LinkedList<MergeCandidate>();

		double[] weightOfEdges = new double[subTreeRef.size()
				+ subTreeNonRef.size() - 1];
		int i = 0;
		
		for (NCRPNode node : subTreeRef) {
			Set<NCRPNode> neighbourSet = mst.getNeighbours(node);
			for (NCRPNode neighbour : neighbourSet) {
				weightOfEdges[i++] = mst.edgeCost(node, neighbour);
				nodePairList.add(new MergeCandidate(node, neighbour, mst
						.edgeCost(node, neighbour)));
			}
		}

		Statistics stats = new Statistics(weightOfEdges);
		double mean = stats.getMean();
		double stdDev = stats.getStdDev();

		PriorityQueue<MergeCandidate> heap = new PriorityQueue<MergeCandidate>(
				new Comparator<MergeCandidate>() {
					public int compare(MergeCandidate o1, MergeCandidate o2) {
						if (o1.getWeight() < o2.getWeight()) {
							return -1;
						} else if (o1.getWeight() < o2.getWeight()) {
							return 1;
						} else {
							return 0;
						}
					}
				});
		for (MergeCandidate candidate : nodePairList) {
			if (candidate.weight < (mean + stdDev))
				heap.add(candidate);
		}

		HashSet<NCRPNode> processedNonRefNode = 
				new HashSet<HierarchicalLDA.NCRPNode>();

		// make it parallel
		while (!heap.isEmpty()) {
			MergeCandidate fuseCandidate = heap.poll();
			if (!processedNonRefNode.contains(fuseCandidate.getNonRefNode())) {
				unifyNodes(fuseCandidate.getRefTreeNode(),
						fuseCandidate.getNonRefNode());
				processedNonRefNode.add(fuseCandidate.nonRefNode);
			}
		}

		return referenceRoot;
	}
}


class Statistics {

	double[] data;
	double size;

	public Statistics(double[] data) {
		this.data = data;
		size = data.length;
	}

	double getMean() {
		double sum = 0.0;
		for (double a : data)
			sum += a;
		return sum / size;
	}

	double getVariance() {
		double mean = getMean();
		double temp = 0;
		for (double a : data)
			temp += (mean - a) * (mean - a);
		return temp / size;
	}

	double getStdDev() {
		return Math.sqrt(getVariance());
	}

}


class MergeCandidate {
	NCRPNode refTreeNode;
	NCRPNode nonRefNode;
	double weight;

	public MergeCandidate(NCRPNode refTreeNode, NCRPNode nonRefNode,
			double weight) {
		super();
		this.refTreeNode = refTreeNode;
		this.nonRefNode = nonRefNode;
		this.weight = weight;
	}

	public NCRPNode getRefTreeNode() {
		return refTreeNode;
	}

	public void setRefTreeNode(NCRPNode refTreeNode) {
		this.refTreeNode = refTreeNode;
	}

	public NCRPNode getNonRefNode() {
		return nonRefNode;
	}

	public void setNonRefNode(NCRPNode nonRefNode) {
		this.nonRefNode = nonRefNode;
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}

}

