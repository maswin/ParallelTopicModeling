package hierarchicaltopicmodel;

import graph.Kruskal;
import graph.UndirectedGraph;
import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;
import dissimilaritymetrics.MetricAPI;

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
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;

public class TreeMerger {

	public static final double log2 = Math.log(2);
	public HierarchicalLDA h;

	public TreeMerger(){
		h = new HierarchicalLDA();
	}

	public NCRPNode findReferenceTree(NCRPNode node1, NCRPNode node2){
		//Place SVM Logic here
		return node1;
	}

	public double findSimilarity(NCRPNode node1, NCRPNode node2) throws IOException {

		MetricAPI m = new MetricAPI();
		Map<String,Integer> wordMap1 = node1.wordCount;
		int wordMap1Count = node1.totalTokens;
		Map<String,Integer> wordMap2 = node2.wordCount;
		int wordMap2Count = node2.totalTokens;

		return m.findJSDivergence(wordMap1, wordMap1Count, wordMap2, wordMap2Count);
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

	public void fetchMasterWords(NCRPNode node, Map<String, Integer> masterWordMap){
		masterWordMap.putAll(node.getWordCount());
		for(NCRPNode n : node.getChildren()){
			fetchMasterWords(n, masterWordMap);
		}
	}
	public List<String> formMasterWordList(List<NCRPNode> nodeList){
		Map<String, Integer> masterWordMap = new HashMap<String, Integer>();
		List<String> masterWordList = new ArrayList<String>();
		for(NCRPNode node : nodeList){
			fetchMasterWords(node, masterWordMap);
		}
		for(String word : masterWordMap.keySet()){
			masterWordList.add(word);
		}
		return masterWordList;
	}
	
	private CountDownLatch percolateNodeSummary(NCRPNode referenceTreeNode,
			NCRPNode nonReferenceRoot,int dept) {
		
		CountDownLatch latch;
		
		if(referenceTreeNode.parent != null){
			latch = percolateNodeSummary(referenceTreeNode.parent, nonReferenceRoot, dept+1);
		}else{
			latch = new CountDownLatch(dept);
		}
		
		new NodeUnifier(referenceTreeNode, nonReferenceRoot, latch).run();
		
		return latch;
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

		CountDownLatch percolationStatus = 
				percolateNodeSummary(mergePoint,nonReferenceRoot,1);

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

		percolationStatus.await();
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

class NodeUnifier implements Runnable{

	private NCRPNode node1;
	private NCRPNode node2;
	private CountDownLatch latch;
	
	public NodeUnifier(NCRPNode node1, NCRPNode node2, CountDownLatch latch) {
		super();
		this.node1 = node1;
		this.node2 = node2;
		this.latch = latch;
	}

	@Override
	public void run() {
		unifyNodes(node1, node2);
		this.latch.countDown();
	}
	private void unifyNodes(NCRPNode node1, NCRPNode node2) {

		System.out.println("In unifyNodes");
		// Adding the documents and customers
		for (int i = 0; i < node2.documents.size(); i++) {
			if (!(node1.documents.contains(node2.documents.get(i)))) {
				node1.documents.add(node2.documents.get(i));
				node1.customers++;
			}
		}

		// Adding the words and updating total tokens
		Set<String> words = node2.wordCount.keySet();
		Iterator<String> it = words.iterator();
		while (it.hasNext()) {
			String w = (String) it.next();
			if (node1.wordCount.containsKey(w)) {
				int count = (node1.wordCount.get(w) + node2.wordCount.get(w)) / 2;
				node1.wordCount.put(w, count);
			} else {
				node1.wordCount.put(w, node2.wordCount.get(w));
				node1.totalTokens++;
			}
		}

	}	
}

