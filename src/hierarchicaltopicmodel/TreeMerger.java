package hierarchicaltopicmodel;

import graph.Kruskal;
import graph.UndirectedGraph;
import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;
import dissimilaritymetrics.MetricAPI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class TreeMerger {

	public static final double log2 = Math.log(2);
	public HierarchicalLDA h;

	public TreeMerger() {
		h = new HierarchicalLDA();
	}

	public NCRPNode findReferenceTree(NCRPNode root1, NCRPNode root2) throws IOException {
		AD_DA_Classifier classifier = new AD_DA_Classifier();
		classifier.loadModel();
		AD_DA_Classifier.Relation relation = classifier.predict(root1, root2);
		if (relation == AD_DA_Classifier.Relation.AD)
			return root1;
		else
			return root2;
	}

	public double findSimilarity(NCRPNode node1, NCRPNode node2)
			throws IOException {

		MetricAPI m = new MetricAPI();
		Map<String, Integer> wordMap1 = node1.wordCount;
		int wordMap1Count = node1.totalTokens;
		Map<String, Integer> wordMap2 = node2.wordCount;
		int wordMap2Count = node2.totalTokens;

		return m.findJSDivergence(wordMap1, wordMap1Count, wordMap2,
				wordMap2Count);
	}

	public NCRPNode mergeNodes(NCRPNode node1, NCRPNode node2)
			throws IOException {
		NCRPNode merged = h.new NCRPNode();
		merged.documents.addAll(node1.documents);
		merged.customers = node1.customers;
		for (int i = 0; i < node2.documents.size(); i++) {
			if (!(node1.documents.contains(node2.documents.get(i)))) {
				merged.documents.add(node2.documents.get(i));
				merged.customers++;
			}
		}
		merged.wordCount.putAll(node1.wordCount);
		// Adding the words and updating total tokens
		Set<String> words = node2.wordCount.keySet();
		Iterator<String> it = words.iterator();
		while (it.hasNext()) {
			String w = (String) it.next();
			if (node1.wordCount.containsKey(w)) {
				int count = (node1.wordCount.get(w) + node2.wordCount.get(w)) / 2;
				merged.wordCount.put(w, count);
			} else {
				merged.wordCount.put(w, node2.wordCount.get(w));
				merged.totalTokens++;
			}
		}

		return merged;
	}

	public NCRPNode findMergePoint(NCRPNode referenceTree,
			NCRPNode nonReferenceTree) throws IOException {

		HashMap<Integer, Double> minDist = new HashMap<Integer, Double>();
		HashMap<Integer, NCRPNode> candidate = new HashMap<Integer, HierarchicalLDA.NCRPNode>();

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

	public void findSubTree(NCRPNode root, List<NCRPNode> subTree) {
		for (NCRPNode child : root.children) {
			subTree.add(child);
			findSubTree(child, subTree);
		}
	}

	public void fetchMasterWords(NCRPNode node,
			Map<String, Integer> masterWordMap) {
		masterWordMap.putAll(node.getWordCount());
		for (NCRPNode n : node.getChildren()) {
			fetchMasterWords(n, masterWordMap);
		}
	}

	public List<String> formMasterWordList(List<NCRPNode> nodeList) {
		Map<String, Integer> masterWordMap = new HashMap<String, Integer>();
		List<String> masterWordList = new ArrayList<String>();
		for (NCRPNode node : nodeList) {
			fetchMasterWords(node, masterWordMap);
		}
		for (String word : masterWordMap.keySet()) {
			masterWordList.add(word);
		}
		return masterWordList;
	}

	private CountDownLatch percolateNodeSummary(NCRPNode referenceTreeNode,
			NCRPNode nonReferenceRoot, int dept) {

		CountDownLatch latch;

		if (referenceTreeNode.parent != null) {
			latch = percolateNodeSummary(referenceTreeNode.parent,
					nonReferenceRoot, dept + 1);
		} else {
			latch = new CountDownLatch(dept);
		}

		List<NCRPNode> nonRefRootList = new ArrayList<>();
		nonRefRootList.add(nonReferenceRoot);

		new NodeUnifier(referenceTreeNode, nonRefRootList, latch).run();

		return latch;
	}

	public NCRPNode mergeTrees(NCRPNode root1, NCRPNode root2)
			throws IOException, InterruptedException {

		double threshold = 0.80;

		// If trees are dissimilar
		if (findSimilarity(root1, root2) > threshold) {
			// create a new node representing average word distribution of
			// topicTree1 and topicTree2
			NCRPNode newNode = mergeNodes(root1, root2);

			// set topicTree1 and topicTree1 as child node of newNode
			newNode.children.add(root1);
			newNode.children.add(root2);
			root1.parent = newNode;
			root2.parent = newNode;

			return newNode;
		}

		NCRPNode referenceRoot, nonReferenceRoot;

		// Determine Reference Tree
		if (root1 == findReferenceTree(root1, root2)) {
			referenceRoot = root1;
			nonReferenceRoot = root2;
		} else {
			referenceRoot = root2;
			nonReferenceRoot = root1;
		}

		// Determine Merge Point in Reference Tree
		NCRPNode mergePoint = findMergePoint(referenceRoot, nonReferenceRoot);

		// Fuse Non Reference Root with Merge Point and its ancestors in
		// parallel
		CountDownLatch percolationStatus = percolateNodeSummary(mergePoint,
				nonReferenceRoot, 1);

		// Extract reference to the node in sub tree rooted at merge point
		List<NCRPNode> subTreeRef = new ArrayList<NCRPNode>();
		findSubTree(mergePoint, subTreeRef);

		// Extract reference to the node in sub tree rooted at nonReference Root
		List<NCRPNode> subTreeNonRef = new ArrayList<NCRPNode>();
		findSubTree(nonReferenceRoot, subTreeNonRef);

		/*
		 * Form complete bipartite graph such that there exist edge between
		 * nodes in sub tree rooted at merge point and nodes in sub tree rooted
		 * at nonRegerence Root with edge weight as distance between them
		 */
		UndirectedGraph<NCRPNode> bipartiteGraph = new UndirectedGraph<>();
		for (NCRPNode refTreeNode : subTreeRef) {
			bipartiteGraph.addNode(refTreeNode);
			for (NCRPNode nonRefTreeNode : subTreeNonRef) {
				bipartiteGraph.addNode(nonRefTreeNode);
				bipartiteGraph.addEdge(refTreeNode, nonRefTreeNode,
						findSimilarity(refTreeNode, nonRefTreeNode));
			}
		}

		// Form minimum spanning tree using Kruskal's algorithm
		UndirectedGraph<NCRPNode> mst = Kruskal.mst(bipartiteGraph);

		/*
		 * A node in sub tree rooted at merge point and a node in sub tree
		 * rooted at nonReference Root are merge candidate if there exist an
		 * edge between them in minimum spanning tree
		 */
		List<MergeCandidate> mergeCandidateList = new ArrayList<MergeCandidate>();

		// Find all merge candidates and store edge weights of MST
		double[] weightOfEdges = new double[subTreeRef.size()
				+ subTreeNonRef.size() - 1];
		int i = 0;
		for (NCRPNode node : subTreeRef) {
			Set<NCRPNode> neighbourSet = mst.getNeighbours(node);
			for (NCRPNode neighbour : neighbourSet) {
				mergeCandidateList.add(new MergeCandidate(node, neighbour, mst
						.edgeCost(node, neighbour)));
				weightOfEdges[i++] = mst.edgeCost(node, neighbour);
			}
		}

		// Find mean and standard deviation of the edge weights of MST
		Statistics stats = new Statistics(weightOfEdges);
		double mean = stats.getMean();
		double stdDev = stats.getStdDev();

		// Sort merge candidates in increasing order of edge weight
		Collections.sort(mergeCandidateList, new Comparator<MergeCandidate>() {
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

		HashSet<NCRPNode> unProcessedNonRefNode = new HashSet<>();
		unProcessedNonRefNode.addAll(subTreeNonRef);

		/*
		 * Determine the reference tree node to which non reference tree node
		 * has to be merged
		 */
		HashMap<NCRPNode, List<NCRPNode>> mergeMap = new HashMap<>();
		for (MergeCandidate candidate : mergeCandidateList) {
			if (candidate.weight < (mean + stdDev)) {
				// Check whether non reference node has not already been mapped
				// to reference tree node for merging
				if (unProcessedNonRefNode.contains(candidate.getNonRefNode())) {
					if (!mergeMap.containsKey(candidate.getRefTreeNode())) {
						mergeMap.put(candidate.getRefTreeNode(),
								new ArrayList<HierarchicalLDA.NCRPNode>());
					}
					mergeMap.get(candidate.getRefTreeNode()).add(
							candidate.getNonRefNode());
					unProcessedNonRefNode.remove(candidate.getNonRefNode());
				}
			}
		}

		/*
		 * Start threads to merge reference tree node and non reference tree
		 * node
		 */
		CountDownLatch mergeStatus = new CountDownLatch(mergeMap.size());
		for (NCRPNode referenceTreeNode : mergeMap.keySet()) {
			new NodeUnifier(referenceTreeNode, mergeMap.get(referenceTreeNode),
					mergeStatus).run();
		}

		// Add un merged non reference tree node as child node of merge point
		for (NCRPNode nonReferenceTreeNode : unProcessedNonRefNode) {
			mergePoint.children.add(nonReferenceTreeNode);
			nonReferenceTreeNode.parent = mergePoint;
			nonReferenceTreeNode.children = new ArrayList<>();
		}

		// wait for all threads performing percolation
		percolationStatus.await();
		// wait for all threads performing merge
		mergeStatus.await();

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

class NodeUnifier implements Runnable {

	private NCRPNode referenceTreeNode;
	private List<NCRPNode> nonRefNodeList;
	private CountDownLatch latch;

	public NodeUnifier(NCRPNode referenceTreeNode,
			List<NCRPNode> nonRefNodeList, CountDownLatch latch) {
		super();
		this.referenceTreeNode = referenceTreeNode;
		this.nonRefNodeList = nonRefNodeList;
		this.latch = latch;
	}

	@Override
	public void run() {
		unifyNodes();
		this.latch.countDown();
	}

	public void unifyNodes() {

		HashSet<String> wordSet = new HashSet<String>();
		wordSet.addAll(referenceTreeNode.wordCount.keySet());

		// Adding the documents and customers
		for (NCRPNode nonRefNode : nonRefNodeList) {
			for (String doc : nonRefNode.documents) {
				if (!referenceTreeNode.documents.contains(doc)) {
					referenceTreeNode.documents.add(doc);
					referenceTreeNode.customers++;
				}
			}
			wordSet.addAll(nonRefNode.wordCount.keySet());
		}

		// Adding the words and updating total tokens
		for (String word : wordSet) {
			int count = 0;
			if (referenceTreeNode.wordCount.containsKey(word)) {
				count += referenceTreeNode.wordCount.get(word);
			}
			for (NCRPNode nonRefNode : nonRefNodeList) {
				if (nonRefNode.wordCount.containsKey(word)) {
					count += nonRefNode.wordCount.get(word);
				}
			}
			// take average
			referenceTreeNode.wordCount.put(word,
					count / (nonRefNodeList.size() + 1));
		}
		referenceTreeNode.totalTokens = wordSet.size();
	}
}
