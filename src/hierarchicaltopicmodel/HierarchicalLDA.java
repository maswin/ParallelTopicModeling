package hierarchicaltopicmodel;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.io.*;

import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import gnu.trove.*;

public class HierarchicalLDA implements Serializable{

	InstanceList instances;
	InstanceList testing;

	NCRPNode rootNode, node;

	int numLevels;
	int numDocuments;
	int numTypes;

	double alpha; // smoothing on topic distributions
	double gamma; // "imaginary" customers at the next, as yet unused table
	double eta;   // smoothing on word distributions
	double etaSum;

	int[][] levels; // indexed < doc, token >
	NCRPNode[] documentLeaves; // currently selected path (ie leaf node) through the NCRP tree

	int totalNodes = 0;

	String stateFile = null; //"hlda.state";
	String wordCountFile = null;

	Randoms random;

	boolean showProgress = true;

	int displayTopicsInterval  = 5;
	int numWordsToDisplay = 10; // = 10;


	public HierarchicalLDA () {
		//alpha = 10.0;
		//gamma = 1.0;
		//eta = 0.1;
	}

	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

	public void setGamma(double gamma) {
		this.gamma = gamma;
	}

	public void setEta(double eta) {
		this.eta = eta;
	}

	public void setStateFile(String stateFile) {
		this.stateFile = stateFile;
	}

	public void setWordCountFile(String wordCountFile) {
		this.wordCountFile = wordCountFile;
	}

	public void setTopicDisplay(int interval, int words) {
		displayTopicsInterval = interval;
		numWordsToDisplay = words;
	}

	public NCRPNode getRootNode(){
		return rootNode;
	}

	/**  
	 *  This parameter determines whether the sampler outputs 
	 *   shows progress by outputting a character after every iteration.
	 */
	public void setProgressDisplay(boolean showProgress) {
		this.showProgress = showProgress;
	}

	public void initialize(InstanceList instances, InstanceList testing,
			int numLevels, Randoms random, String[] files) {
		this.instances = instances;
		this.testing = testing;
		this.numLevels = numLevels;
		this.random = random;

		if (! (instances.get(0).getData() instanceof FeatureSequence)) {
			throw new IllegalArgumentException("Input must be a FeatureSequence, using the --feature-sequence option when impoting data, for example");
		}

		numDocuments = instances.size();
		numTypes = instances.getDataAlphabet().size();

		etaSum = eta * numTypes;

		// Initialize a single path

		NCRPNode[] path = new NCRPNode[numLevels];

		rootNode = new NCRPNode(numTypes);

		levels = new int[numDocuments][];
		documentLeaves = new NCRPNode[numDocuments];

		// Initialize and fill the topic pointer arrays for 
		//  every document. Set everything to the single path that 
		//  we added earlier.
		for (int doc=0; doc < numDocuments; doc++) {
			FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();
			int seqLen = fs.getLength();

			path[0] = rootNode;
			rootNode.customers++;
			rootNode.documents.add(files[doc]);
			for (int level = 1; level < numLevels; level++) {
				path[level] = path[level-1].select();
				path[level].customers++;
				path[level].documents.add(files[doc]);
			}
			node = path[numLevels - 1];

			levels[doc] = new int[seqLen];
			documentLeaves[doc] = node;

			for (int token=0; token < seqLen; token++) {
				int type = fs.getIndexAtPosition(token);
				levels[doc][token] = random.nextInt(numLevels);
				node = path[ levels[doc][token] ];
				node.totalTokens++;
				node.typeCounts[type]++;
			}
		}
	}

	public void estimate(int numIterations, String filename, String[] files) {

		PrintWriter pw = null;
		try {
			pw = new PrintWriter(filename);
		}
		catch(FileNotFoundException f) {
			f.printStackTrace();
		}
		for (int iteration = 1; iteration <= numIterations; iteration++) {
			for (int doc=0; doc < numDocuments; doc++) {
				samplePath(doc, iteration,files);
			}
			for (int doc=0; doc < numDocuments; doc++) {
				sampleTopics(doc);
			}

			if (showProgress) {
				pw.print(".");
				if (iteration % displayTopicsInterval == 0) {
					pw.println(" " + iteration);
				}
			}

			if (iteration % displayTopicsInterval == 0) {
				printNodes(filename,pw);
			}
		}
		pw.close();
	}

	public void samplePath(int doc, int iteration, String files[]) {
		NCRPNode[] path = new NCRPNode[numLevels];
		NCRPNode node;
		int level, token, type, topicCount;
		double weight;

		node = documentLeaves[doc];
		for (level = numLevels - 1; level >= 0; level--) {
			path[level] = node;
			node = node.parent;
		}

		documentLeaves[doc].dropPath(doc,files);

		TObjectDoubleHashMap<NCRPNode> nodeWeights = 
				new TObjectDoubleHashMap<NCRPNode>();

		// Calculate p(c_m | c_{-m})
		calculateNCRP(nodeWeights, rootNode, 0.0);

		// Add weights for p(w_m | c, w_{-m}, z)

		// The path may have no further customers and therefore
		//  be unavailable, but it should still exist since we haven't
		//  reset documentLeaves[doc] yet...

		TIntIntHashMap[] typeCounts = new TIntIntHashMap[numLevels];

		int[] docLevels;

		for (level = 0; level < numLevels; level++) {
			typeCounts[level] = new TIntIntHashMap();
		}

		docLevels = levels[doc];
		FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();

		// Save the counts of every word at each level, and remove
		//  counts from the current path

		for (token = 0; token < docLevels.length; token++) {
			level = docLevels[token];
			type = fs.getIndexAtPosition(token);

			if (! typeCounts[level].containsKey(type)) {
				typeCounts[level].put(type, 1);
			}
			else {
				typeCounts[level].increment(type);
			}

			path[level].typeCounts[type]--;
			assert(path[level].typeCounts[type] >= 0);

			path[level].totalTokens--;	    
			assert(path[level].totalTokens >= 0);
		}

		// Calculate the weight for a new path at a given level.
		double[] newTopicWeights = new double[numLevels];
		for (level = 1; level < numLevels; level++) {  // Skip the root...
			int[] types = typeCounts[level].keys();
			int totalTokens = 0;

			for (int t: types) {
				for (int i=0; i<typeCounts[level].get(t); i++) {
					newTopicWeights[level] += 
							Math.log((eta + i) / (etaSum + totalTokens));
					totalTokens++;
				}
			}

			//if (iteration > 1) { System.out.println(newTopicWeights[level]); }
		}

		calculateWordLikelihood(nodeWeights, rootNode, 0.0, typeCounts, newTopicWeights, 0, iteration);

		NCRPNode[] nodes = nodeWeights.keys(new NCRPNode[] {});
		double[] weights = new double[nodes.length];
		double sum = 0.0;
		double max = Double.NEGATIVE_INFINITY;

		// To avoid underflow, we're using log weights and normalizing the node weights so that 
		//  the largest weight is always 1.
		for (int i=0; i<nodes.length; i++) {
			if (nodeWeights.get(nodes[i]) > max) {
				max = nodeWeights.get(nodes[i]);
			}
		}

		for (int i=0; i<nodes.length; i++) {
			weights[i] = Math.exp(nodeWeights.get(nodes[i]) - max);

			/*
			  if (iteration > 1) {
			  if (nodes[i] == documentLeaves[doc]) {
			  System.out.print("* ");
			  }
			  System.out.println(((NCRPNode) nodes[i]).level + "\t" + weights[i] + 
			  "\t" + nodeWeights.get(nodes[i]));
			  }
			 */

			sum += weights[i];
		}

		//if (iteration > 1) {System.out.println();}

		node = nodes[ random.nextDiscrete(weights, sum) ];

		// If we have picked an internal node, we need to 
		//  add a new path.
		if (! node.isLeaf()) {
			node = node.getNewLeaf();
		}

		node.addPath(doc,files);
		documentLeaves[doc] = node;

		for (level = numLevels - 1; level >= 0; level--) {
			int[] types = typeCounts[level].keys();

			for (int t: types) {
				node.typeCounts[t] += typeCounts[level].get(t);
				node.totalTokens += typeCounts[level].get(t);
			}

			node = node.parent;
		}
	}

	public void calculateNCRP(TObjectDoubleHashMap<NCRPNode> nodeWeights, 
			NCRPNode node, double weight) {
		for (NCRPNode child: node.children) {
			calculateNCRP(nodeWeights, child,
					weight + Math.log(child.customers / (node.customers + gamma)));
		}

		nodeWeights.put(node, weight + Math.log(gamma / (node.customers + gamma)));
	}

	public void calculateWordLikelihood(TObjectDoubleHashMap<NCRPNode> nodeWeights,
			NCRPNode node, double weight, 
			TIntIntHashMap[] typeCounts, double[] newTopicWeights,
			int level, int iteration) {

		// First calculate the likelihood of the words at this level, given
		//  this topic.
		double nodeWeight = 0.0;
		int[] types = typeCounts[level].keys();
		int totalTokens = 0;

		//if (iteration > 1) { System.out.println(level + " " + nodeWeight); }

		for (int type: types) {
			for (int i=0; i<typeCounts[level].get(type); i++) {
				nodeWeight +=
						Math.log((eta + node.typeCounts[type] + i) /
								(etaSum + node.totalTokens + totalTokens));
				totalTokens++;

				/*
				  if (iteration > 1) {
				  System.out.println("(" +eta + " + " + node.typeCounts[type] + " + " + i + ") /" + 
				  "(" + etaSum + " + " + node.totalTokens + " + " + totalTokens + ")" + 
				  " : " + nodeWeight);
				  }
				 */

			}
		}

		//if (iteration > 1) { System.out.println(level + " " + nodeWeight); }

		// Propagate that weight to the child nodes

		for (NCRPNode child: node.children) {
			calculateWordLikelihood(nodeWeights, child, weight + nodeWeight,
					typeCounts, newTopicWeights, level + 1, iteration);
		}

		// Finally, if this is an internal node, add the weight of
		//  a new path

		level++;
		while (level < numLevels) {
			nodeWeight += newTopicWeights[level];
			level++;
		}

		nodeWeights.adjustValue(node, nodeWeight);

	}

	/** Propagate a topic weight to a node and all its children.
		weight is assumed to be a log.
	 */
	public void propagateTopicWeight(TObjectDoubleHashMap<NCRPNode> nodeWeights,
			NCRPNode node, double weight) {
		if (! nodeWeights.containsKey(node)) {
			// calculating the NCRP prior proceeds from the
			//  root down (ie following child links),
			//  but adding the word-topic weights comes from
			//  the bottom up, following parent links and then 
			//  child links. It's possible that the leaf node may have
			//  been removed just prior to this round, so the current
			//  node may not have an NCRP weight. If so, it's not 
			//  going to be sampled anyway, so ditch it.
			return;
		}

		for (NCRPNode child: node.children) {
			propagateTopicWeight(nodeWeights, child, weight);
		}

		nodeWeights.adjustValue(node, weight);
	}

	public void sampleTopics(int doc) {
		FeatureSequence fs = (FeatureSequence) instances.get(doc).getData();
		int seqLen = fs.getLength();
		int[] docLevels = levels[doc];
		NCRPNode[] path = new NCRPNode[numLevels];
		NCRPNode node;
		int[] levelCounts = new int[numLevels];
		int type, token, level;
		double sum;

		// Get the leaf
		node = documentLeaves[doc];
		for (level = numLevels - 1; level >= 0; level--) {
			path[level] = node;
			node = node.parent;
		}

		double[] levelWeights = new double[numLevels];

		// Initialize level counts
		for (token = 0; token < seqLen; token++) {
			levelCounts[ docLevels[token] ]++;
		}

		for (token = 0; token < seqLen; token++) {
			type = fs.getIndexAtPosition(token);

			levelCounts[ docLevels[token] ]--;
			node = path[ docLevels[token] ];
			node.typeCounts[type]--;
			node.totalTokens--;


			sum = 0.0;
			for (level=0; level < numLevels; level++) {
				levelWeights[level] = 
						(alpha + levelCounts[level]) * 
						(eta + path[level].typeCounts[type]) /
						(etaSum + path[level].totalTokens);
				sum += levelWeights[level];
			}
			level = random.nextDiscrete(levelWeights, sum);

			docLevels[token] = level;
			levelCounts[ docLevels[token] ]++;
			node = path[ level ];
			node.typeCounts[type]++;
			node.totalTokens++;
		}
	}

	/**
	 *  Writes the current sampling state to the file specified in <code>stateFile</code>.
	 */
	public void printState() throws IOException, FileNotFoundException {
		PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(stateFile)));
		printState(pw);
		pw.close();
	}

	/**
	 *  Write a text file describing the current sampling state. 
	 */
	public void printState(PrintWriter out) throws IOException {
		int doc = 0;

		Alphabet alphabet = instances.getDataAlphabet();

		for (Instance instance: instances) {
			FeatureSequence fs = (FeatureSequence) instance.getData();
			int seqLen = fs.getLength();
			int[] docLevels = levels[doc];
			NCRPNode node;
			int type, token, level;

			StringBuffer path = new StringBuffer();

			// Start with the leaf, and build a string describing the path for this doc
			node = documentLeaves[doc];
			for (level = numLevels - 1; level >= 0; level--) {
				path.append(node.nodeID + " ");
				node = node.parent;
			}

			for (token = 0; token < seqLen; token++) {
				type = fs.getIndexAtPosition(token);
				level = docLevels[token];

				// The "" just tells java we're not trying to add a string and an int
				out.println(path + "" + type + " " + alphabet.lookupObject(type) + " " + level + " ");
			}

			doc++;
		}
	}	    


	public void printNodes(String filename, PrintWriter pw) {
		printNode(rootNode, 0, pw);
	}

	public void printNode(NCRPNode node, int indent, PrintWriter pw) {

		for (int i=0; i<indent; i++) {
			pw.print("  ");
		}

		//pw.print(node.totalTokens + "/" + node.customers + " ");
		pw.print(node.nodeID+"/"+node.customers+"  ");
		pw.print(node.getTopWords(numWordsToDisplay));
		pw.println();

		for (NCRPNode child: node.children) {
			printNode(child, indent + 1, pw);
		}

	}

	public void writeWordCounts() {
		try {
			PrintWriter pw = new PrintWriter(wordCountFile);
			writeWordCount(rootNode,pw);
			pw.close();
		}
		catch(FileNotFoundException f) {
			f.printStackTrace();
		}
	}

	public void writeWordCount(NCRPNode node, PrintWriter pw) {

		pw.println("\n----------------- LEVEL "+node.level+"-----------------\n");		
		pw.println("NODE ID : "+node.nodeID);
		pw.println("\nTOTAL TOKENS : "+node.totalTokens+"\n");
		String wordList[] = node.getWords();
		if(wordList.length == node.typeCounts.length) {
			for(int i=0;i<node.typeCounts.length;i++) {
				if(node.typeCounts[i]!=0) {
					node.wordCount.put(wordList[i],new Integer(node.typeCounts[i]));
					pw.println(wordList[i]+" : "+node.wordCount.get(wordList[i]));
				}
			}
		}
		for (NCRPNode child: node.children) {
			writeWordCount(child,pw);
		}	
	}

	/** For use with empirical likelihood evaluation: 
	 *   sample a path through the tree, then sample a multinomial over
	 *   topics in that path, then return a weighted sum of words.
	 */
	public double empiricalLikelihood(int numSamples, InstanceList testing)  {
		NCRPNode[] path = new NCRPNode[numLevels];
		NCRPNode node;
		double weight;
		path[0] = rootNode;

		FeatureSequence fs;
		int sample, level, type, token, doc, seqLen;

		Dirichlet dirichlet = new Dirichlet(numLevels, alpha);
		double[] levelWeights;
		double[] multinomial = new double[numTypes];

		double[][] likelihoods = new double[ testing.size() ][ numSamples ];

		for (sample = 0; sample < numSamples; sample++) {
			Arrays.fill(multinomial, 0.0);

			for (level = 1; level < numLevels; level++) {
				path[level] = path[level-1].selectExisting();
			}

			levelWeights = dirichlet.nextDistribution();

			for (type = 0; type < numTypes; type++) {
				for (level = 0; level < numLevels; level++) {
					node = path[level];
					multinomial[type] +=
							levelWeights[level] * 
							(eta + node.typeCounts[type]) /
							(etaSum + node.totalTokens);
				}

			}

			for (type = 0; type < numTypes; type++) {
				multinomial[type] = Math.log(multinomial[type]);
			}

			for (doc=0; doc<testing.size(); doc++) {
				fs = (FeatureSequence) testing.get(doc).getData();
				seqLen = fs.getLength();

				for (token = 0; token < seqLen; token++) {
					type = fs.getIndexAtPosition(token);
					likelihoods[doc][sample] += multinomial[type];
				}
			}
		}

		double averageLogLikelihood = 0.0;
		double logNumSamples = Math.log(numSamples);
		for (doc=0; doc<testing.size(); doc++) {
			double max = Double.NEGATIVE_INFINITY;
			for (sample = 0; sample < numSamples; sample++) {
				if (likelihoods[doc][sample] > max) {
					max = likelihoods[doc][sample];
				}
			}

			double sum = 0.0;
			for (sample = 0; sample < numSamples; sample++) {
				sum += Math.exp(likelihoods[doc][sample] - max);
			}

			averageLogLikelihood += Math.log(sum) + max - logNumSamples;
		}

		return averageLogLikelihood;
	}


	/** 
	 *  This method is primarily for testing purposes. The {@link cc.mallet.topics.tui.HierarchicalLDATUI}
	 *   class has a more flexible interface for command-line use.
	 */
	/* public static void main (String[] args) {
		try {
			InstanceList instances = InstanceList.load(new File(args[0]));
			InstanceList testing = InstanceList.load(new File(args[1]));

			HierarchicalLDA sampler = new HierarchicalLDA();
			sampler.initialize(instances, testing, 5, new Randoms());
			String filename = "Tree.txt";
			sampler.estimate(250,filename);
		} catch (Exception e) {
			e.printStackTrace();
		}
    } */

	public class NCRPNode implements Serializable{
		/**
		 * 
		 */
		private static final long serialVersionUID = 2853412884200276303L;
		int customers;
		public ArrayList<String> documents;
		public ArrayList<NCRPNode> children;
		public NCRPNode parent;
		int level;

		//Helper parameter to identify if the node can be merged
		int mergeId;

		int totalTokens;
		int[] typeCounts;

		public int nodeID;
		HashMap<String, Integer> wordCount;

		//Getter & Setter Methods
		public int getCustomers() {
			return customers;
		}
		public void setCustomers(int customers) {
			this.customers = customers;
		}
		public ArrayList<NCRPNode> getChildren() {
			return children;
		}
		public void setChildren(ArrayList<NCRPNode> children) {
			this.children = children;
		}
		public NCRPNode getParent() {
			return parent;
		}
		public void setParent(NCRPNode parent) {
			this.parent = parent;
		}
		public int getMergeId() {
			return mergeId;
		}
		public void setMergeId(int mergeId) {
			this.mergeId = mergeId;
		}
		public int getTotalTokens() {
			return totalTokens;
		}
		public void setTotalTokens(int totalTokens) {
			this.totalTokens = totalTokens;
		}
		public int[] getTypeCounts() {
			return typeCounts;
		}
		public void setTypeCounts(int[] typeCounts) {
			this.typeCounts = typeCounts;
		}
		public int getNodeID() {
			return nodeID;
		}
		public void setNodeID(int nodeID) {
			this.nodeID = nodeID;
		}
		public HashMap<String, Integer> getWordCount() {
			return wordCount;
		}
		public void setWordCount(HashMap<String, Integer> wordCount) {
			this.wordCount = wordCount;
		}
		public void setDocuments(ArrayList<String> documents) {
			this.documents = documents;
		}
		public void setLevel(int level) {
			this.level = level;
		}

		//Constructors
		public NCRPNode() {
			customers = 0;
			documents = new ArrayList<String>();
			this.parent = null;
			children = new ArrayList<NCRPNode>();
			this.level = 0;
			wordCount = new HashMap<String,Integer>();
			//System.out.println("new node at level " + level);

			totalTokens = 0;
			typeCounts = new int[0];

			nodeID = totalNodes;
			totalNodes++;
		}
		public NCRPNode(NCRPNode parent, int dimensions, int level) {
			customers = 0;
			documents = new ArrayList<String>();
			this.parent = parent;
			children = new ArrayList<NCRPNode>();
			this.level = level;
			wordCount = new HashMap<String,Integer>();
			//System.out.println("new node at level " + level);

			totalTokens = 0;
			typeCounts = new int[dimensions];

			nodeID = totalNodes;
			totalNodes++;
		}

		public NCRPNode(int dimensions) {
			this(null, dimensions, 0);
		}


		public NCRPNode copy() {
			NCRPNode copyNode = new NCRPNode(this.typeCounts.length);
			copyNode.customers = this.customers;
			copyNode.documents = (ArrayList<String>) this.documents.clone();
			copyNode.parent = this.parent;
			copyNode.children = (ArrayList<NCRPNode>) this.children.clone();
			copyNode.level = this.level;
			copyNode.wordCount.putAll(this.wordCount);
			copyNode.totalTokens = this.totalTokens;
			copyNode.typeCounts = this.typeCounts;
			copyNode.nodeID = this.nodeID;
			return copyNode;
		}

		public int getLevel() {
			return level;
		}

		public NCRPNode addChild() {
			NCRPNode node = new NCRPNode(this, typeCounts.length, level + 1);
			children.add(node);
			return node;
		}

		public boolean isLeaf() {
			return level == numLevels - 1;
		}

		public NCRPNode getNewLeaf() {
			NCRPNode node = this;
			for (int l=level; l<numLevels - 1; l++) {
				node = node.addChild();
			}
			return node;
		}

		public void dropPath(int doc,String[] files) {
			NCRPNode node = this;
			try{
				node.customers--;
				if(node.documents.indexOf(files[doc])!=-1)
					node.documents.remove(node.documents.indexOf(files[doc]));
				if (node.customers == 0) {
					node.parent.remove(node);
				}
				for (int l = 1; l < numLevels; l++) {
					node = node.parent;
					node.customers--;
					if(node.documents.indexOf(files[doc])!=-1)
						node.documents.remove(node.documents.indexOf(files[doc]));
					if (node.customers == 0) {
						node.parent.remove(node);
					}
				}
			}catch(Exception e){
				System.out.println("Exception : Find why ???");
			}
		}

		public void remove(NCRPNode node) {
			children.remove(node);
		}

		public void addPath(int doc, String[] files) {
			NCRPNode node = this;
			node.customers++;
			node.documents.add(files[doc]);
			for (int l = 1; l < numLevels; l++) {
				node = node.parent;
				node.customers++;
				node.documents.add(files[doc]);
			}
		}

		public NCRPNode selectExisting() {
			double[] weights = new double[children.size()];

			int i = 0;
			for (NCRPNode child: children) {
				weights[i] = child.customers / (gamma + customers);
				i++;
			}

			int choice = random.nextDiscrete(weights);
			return children.get(choice);
		}

		public NCRPNode select() {
			double[] weights = new double[children.size() + 1];

			weights[0] = gamma / (gamma + customers);

			int i = 1;
			for (NCRPNode child: children) {
				weights[i] = child.customers / (gamma + customers);
				i++;
			}

			int choice = random.nextDiscrete(weights);
			if (choice == 0) {
				return(addChild());
			}
			else {
				return children.get(choice - 1);
			}
		}

		public String getTopWords(int numWords) {
			IDSorter[] sortedTypes = new IDSorter[numTypes];

			for (int type=0; type < numTypes; type++) {
				sortedTypes[type] = new IDSorter(type, typeCounts[type]);
			}
			Arrays.sort(sortedTypes);

			Alphabet alphabet = instances.getDataAlphabet();
			StringBuffer out = new StringBuffer();
			for (int i=0; i<numWords; i++) {
				out.append(alphabet.lookupObject(sortedTypes[i].getID()) + " ");
			}
			return out.toString();
		}

		public String getDocuments() {
			StringBuffer out = new StringBuffer();
			for (int i=0; i<this.documents.size(); i++) {
				String s = this.documents.get(i);
				int index = s.lastIndexOf("\\");
				s = s.substring(index+1, s.length());
				out.append(s + "\n");
			}
			return out.toString();
		}

		public String[] getWords() {

			Alphabet alphabet = instances.getDataAlphabet();
			String[] wordList = new String[numTypes];
			for (int i=0; i<numTypes; i++) 
				wordList[i] = (alphabet.lookupObject(i)).toString();
			return wordList;

		}


	}
}
