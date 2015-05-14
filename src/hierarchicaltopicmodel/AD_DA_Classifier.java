package hierarchicaltopicmodel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class AD_DA_Classifier {

	/*
	 * Training Example (Performed Only once):
	 * 
	 * AD_DA_Classifier classifier = new AD_DA_Classifier();
	 * classifier.train(subTreeRootList);
	 * classifier.saveModel(); //saves the model in "model" file for future use
	 * 
	 * Prediction :
	 * 
	 * AD_DA_Classifier classifier = new AD_DA_Classifier();
	 * classifier.loadModel(); //loads model from "model" file
	 * AD_DA_Classifier.Relation relation = classifier.predict(root1,root2);
	 * if(relation == AD_DA_Classifier.Relation.AD)
	 * 		//tree1 is reference and tree2 is non_reference tree
	 * else
	 * 		//tree2 is reference and tree1 is non_reference tree
	 * 
	 */
	private svm_model model = null;
	private final String FEATURE_FILE_NAME = "wordlist.txt";
	private final String MODEL_FILE_NAME = "model";

	public void train(List<NCRPNode> topicTreeList) {

		List<TrainingSample> trainSampleList = new LinkedList<>();
		for (NCRPNode topicTree : topicTreeList) {
			trainSampleList.addAll(generateTrainingSample(topicTree));
		}

		HashMap<String, Integer> featureIndexMap = getFeatureIndexMap();

		svm_node[][] x = new svm_node[trainSampleList.size()][];
		double[] y = new double[trainSampleList.size()];
		int i = 0;

		for (TrainingSample trainingSample : trainSampleList) {
			x[i] = getFeatureVector(trainingSample.getTree1(),
					trainingSample.getTree2(), featureIndexMap);
			y[i] = trainingSample.getRelation().value;
			i++;
		}

		svm_problem prob = new svm_problem();
		prob.l = trainSampleList.size();
		prob.x = x;
		prob.y = y;

		svm_parameter param = new svm_parameter();

		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.RBF;
		param.gamma = 0.5;
		param.nu = 0.5;
		param.cache_size = 20000;
		param.C = 1;
		param.eps = 0.001;
		param.p = 0.1;

		this.model = svm.svm_train(prob, param);
	}

	private List<TrainingSample> generateTrainingSample(NCRPNode topicTree) {
		Queue<NCRPNode> workList = new LinkedList<>();
		workList.add(topicTree);
		List<TrainingSample> trainingSampleList = new LinkedList<>();
		while (!workList.isEmpty()) {
			NCRPNode node = workList.remove();
			List<NCRPNode> descendantList = new LinkedList<>();
			findDesendants(node, descendantList);

			for (NCRPNode descendant : descendantList) {
				trainingSampleList.add(new TrainingSample(node, descendant,
						Relation.AD));
				trainingSampleList.add(new TrainingSample(descendant, node,
						Relation.DA));
			}

			for (NCRPNode child : node.getChildren()) {
				workList.add(child);
			}
		}
		return trainingSampleList;
	}

	private void findDesendants(NCRPNode root, List<NCRPNode> descendantList) {
		for (NCRPNode child : root.getChildren()) {
			descendantList.add(child);
			findDesendants(child, descendantList);
		}
	}

	public Relation predict(NCRPNode topicTree1, NCRPNode topicTree2) {

		HashMap<String, Integer> featureIndexMap = getFeatureIndexMap();

		svm_node[] x = getFeatureVector(topicTree1, topicTree2, featureIndexMap);

		double y = svm.svm_predict(model, x);

		if ((int) y == Relation.AD.value) {
			return Relation.AD;
		} else {
			return Relation.DA;
		}

	}

	public void saveModel() throws IOException {
		svm.svm_save_model(MODEL_FILE_NAME, model);
	}
	
	public void loadModel() throws IOException{
		this.model = svm.svm_load_model(MODEL_FILE_NAME);
	}

	private svm_node[] getFeatureVector(NCRPNode topicTree1,
			NCRPNode topicTree2, HashMap<String, Integer> featureIndexMap) {

		HashSet<String> words = new HashSet<>();
		words.addAll(topicTree1.wordCount.keySet());
		words.addAll(topicTree2.wordCount.keySet());

		List<svm_node> featureList = new ArrayList<svm_node>();

		for (String s : words) {
			if (featureIndexMap.containsKey(s)) {
				int wordCount1 = 0, wordCount2 = 0;
				if (topicTree1.wordCount.containsKey(s)) {
					wordCount1 = topicTree1.wordCount.get(s);
				}
				if (topicTree2.wordCount.containsKey(s)) {
					wordCount2 = topicTree2.wordCount.get(s);
				}
				svm_node node = new svm_node();
				node.index = featureIndexMap.get(s);
				node.value = (double) wordCount1 - wordCount2;
				featureList.add(node);
			}
		}

		return featureList.toArray(new svm_node[featureList.size()]);
	}

	private HashMap<String, Integer> getFeatureIndexMap() {

		Scanner sc = null;
		try {
			sc = new Scanner(new File(FEATURE_FILE_NAME));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		HashMap<String, Integer> featureIndex = new HashMap<>();
		for (int i = 0; sc.hasNext(); i++) {
			featureIndex.put(sc.next(), i);
		}

		sc.close();
		return featureIndex;
	}

	class TrainingSample {
		private NCRPNode tree1;
		private NCRPNode tree2;
		private Relation relation;

		public TrainingSample(NCRPNode tree1, NCRPNode tree2, Relation relation) {
			super();
			this.tree1 = tree1;
			this.tree2 = tree2;
			this.relation = relation;
		}

		public NCRPNode getTree1() {
			return tree1;
		}

		public void setTree1(NCRPNode tree1) {
			this.tree1 = tree1;
		}

		public NCRPNode getTree2() {
			return tree2;
		}

		public void setTree2(NCRPNode tree2) {
			this.tree2 = tree2;
		}

		public Relation getRelation() {
			return relation;
		}

		public void setRelation(Relation relation) {
			this.relation = relation;
		}

	}

	enum Relation {
		AD(1), DA(0);
		private int value;

		private Relation(int value) {
			this.value = value;
		}
	}

}
