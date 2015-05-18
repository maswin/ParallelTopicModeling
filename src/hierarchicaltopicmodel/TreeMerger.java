package hierarchicaltopicmodel;

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

	public double[][] formMST(List<NCRPNode> subTreeRef, List<NCRPNode> subTreeNonRef) throws IOException{
		System.out.println("Forming MST");
		int numOfVertices=subTreeRef.size()+subTreeNonRef.size();
		double adjMatrix[][]=new double[numOfVertices+1][numOfVertices+1];
		for(int i=1; i<=numOfVertices; i++){
			for(int j=1; j<=numOfVertices; j++){
				if (i == j)
                {
                    adjMatrix[i][j] = 0;
                    continue;
                }		
				if(i<=subTreeRef.size()&&j>subTreeRef.size()){
					int index=j-subTreeRef.size();
					adjMatrix[i][j]=findSimilarity(subTreeRef.get(i),subTreeNonRef.get(index));
					adjMatrix[j][i]=adjMatrix[i][j];
				}
                if (adjMatrix[i][j] == 0)
                {
                    adjMatrix[i][j] = Double.MAX_VALUE;
                }
			}
		}
		KruskalAlgorithm kruskalAlgorithm=new KruskalAlgorithm(numOfVertices);
		kruskalAlgorithm.kruskalAlgorithm(adjMatrix);
		double[][] spanningTree=kruskalAlgorithm.getSpanningTree();
		return spanningTree;
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

		int numOfVertices=subTreeRef.size()+subTreeNonRef.size();
		double[][] spanningTree = formMST(subTreeRef, subTreeNonRef);
		List<Edge> edgesOfMST=new ArrayList<Edge>();
		double[] weightOfEdges=new double[numOfVertices-1];
		int count=0;
		for (int source = 1; source <= numOfVertices; source++)
		{
			for (int destination = 1; destination <= numOfVertices; destination++)

			{
				if(spanningTree[source][destination]!=0){
					Edge e=new Edge();
					e.sourcevertex=source;
					e.destinationvertex=destination;
					e.weight=spanningTree[source][destination];
					edgesOfMST.add(e); 
					weightOfEdges[count]=e.weight;
					count++;
				}

			}
		}
		Statistics stats=new Statistics(weightOfEdges);
		double mean=stats.getMean();
		double stdDev=stats.getStdDev();
		List<Edge> edgesToFuse=new ArrayList<Edge>();
		Iterator<Edge> iterator=edgesOfMST.iterator();
		while(iterator.hasNext()){
			Edge e=iterator.next();
			if(e.weight<(mean+stdDev)){
				e.weight=mean+stdDev-e.weight;
				edgesToFuse.add(e);
			}
		}
		Collections.sort(edgesToFuse, new EdgeDecComparator());
		//make it parallel
		iterator=edgesToFuse.iterator();
		while(iterator.hasNext()){
			Edge e=iterator.next();
			unifyNodes(subTreeRef.get(e.sourcevertex-1),subTreeNonRef.get(e.destinationvertex-subTreeRef.size()-1));
		}

		return referenceRoot;
	}
}

class KruskalAlgorithm

{

	private List<Edge> edges;

	private int numberOfVertices;

	public static final double MAX_VALUE = Double.MAX_VALUE;

	private int visited[];

	private double spanning_tree[][];

	public KruskalAlgorithm(int numberOfVertices)

	{

		this.numberOfVertices = numberOfVertices;

		edges = new LinkedList<Edge>();

		visited = new int[this.numberOfVertices + 1];

		spanning_tree = new double[numberOfVertices + 1][numberOfVertices + 1];

	}

	public double[][] getSpanningTree() {
		return spanning_tree;
	}

	public void kruskalAlgorithm(double adjacencyMatrix[][])

	{

		boolean finished = false;

		for (int source = 1; source <= numberOfVertices; source++)

		{

			for (int destination = 1; destination <= numberOfVertices; destination++)

			{

				if (adjacencyMatrix[source][destination] != Double.MAX_VALUE
						&& source != destination)

				{

					Edge edge = new Edge();

					edge.sourcevertex = source;

					edge.destinationvertex = destination;

					edge.weight = adjacencyMatrix[source][destination];

					adjacencyMatrix[destination][source] = MAX_VALUE;

					edges.add(edge);

				}

			}

		}

		Collections.sort(edges, new EdgeComparator());

		CheckCycle checkCycle = new CheckCycle();

		for (Edge edge : edges)

		{

			spanning_tree[edge.sourcevertex][edge.destinationvertex] = edge.weight;

			spanning_tree[edge.destinationvertex][edge.sourcevertex] = edge.weight;

			if (checkCycle.checkCycle(spanning_tree, edge.sourcevertex))

			{

				spanning_tree[edge.sourcevertex][edge.destinationvertex] = 0;

				spanning_tree[edge.destinationvertex][edge.sourcevertex] = 0;

				edge.weight = -1;

				continue;

			}

			visited[edge.sourcevertex] = 1;

			visited[edge.destinationvertex] = 1;

			for (int i = 0; i < visited.length; i++)

			{

				if (visited[i] == 0)

				{

					finished = false;

					break;

				} else

				{

					finished = true;

				}

			}

			if (finished)

				break;

		}

		System.out.println("The spanning tree is ");

		for (int i = 1; i <= numberOfVertices; i++)

			System.out.print("\t" + i);

		System.out.println();

		for (int source = 1; source <= numberOfVertices; source++)

		{

			System.out.print(source + "\t");

			for (int destination = 1; destination <= numberOfVertices; destination++)

			{

				System.out.print(spanning_tree[source][destination] + "\t");

			}

			System.out.println();

		}

	}

}

class Edge

{

	int sourcevertex;

	int destinationvertex;

	double weight;

}

class EdgeComparator implements Comparator<Edge>

{

	public int compare(Edge edge1, Edge edge2)

	{

		if (edge1.weight < edge2.weight)

			return -1;

		if (edge1.weight > edge2.weight)

			return 1;

		return 0;

	}

}

class EdgeDecComparator implements Comparator<Edge>

{

	public int compare(Edge edge1, Edge edge2) {

		if (edge1.weight > edge2.weight)

			return -1;

		if (edge1.weight < edge2.weight)

			return 1;

		return 0;

	}

}

class CheckCycle

{

	private Stack<Integer> stack;

	private double adjacencyMatrix[][];

	public CheckCycle()

	{

		stack = new Stack<Integer>();

	}

	public boolean checkCycle(double adjacency_matrix[][], int source)

	{

		boolean cyclepresent = false;

		int number_of_nodes = adjacency_matrix[source].length - 1;

		adjacencyMatrix = new double[number_of_nodes + 1][number_of_nodes + 1];

		for (int sourcevertex = 1; sourcevertex <= number_of_nodes; sourcevertex++)

		{

			for (int destinationvertex = 1; destinationvertex <= number_of_nodes; destinationvertex++)

			{

				adjacencyMatrix[sourcevertex][destinationvertex] = adjacency_matrix[sourcevertex][destinationvertex];

			}

		}

		int visited[] = new int[number_of_nodes + 1];

		int element = source;

		int i = source;

		visited[source] = 1;

		stack.push(source);

		while (!stack.isEmpty())

		{

			element = stack.peek();

			i = element;

			while (i <= number_of_nodes)

			{

				if (adjacencyMatrix[element][i] >= 1 && visited[i] == 1)

				{

					if (stack.contains(i))

					{

						cyclepresent = true;

						return cyclepresent;

					}

				}

				if (adjacencyMatrix[element][i] >= 1 && visited[i] == 0)

				{

					stack.push(i);

					visited[i] = 1;

					adjacencyMatrix[element][i] = 0;// mark as labelled;

					adjacencyMatrix[i][element] = 0;

					element = i;

					i = 1;

					continue;

				}

				i++;

			}

			stack.pop();

		}

		return cyclepresent;

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
