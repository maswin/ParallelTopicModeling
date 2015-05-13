package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MergerServices2 {
	public static List<NCRPNode> subTreeRoots; // The Roots of Sub Trees to be merged
	public static TreeMerger treeMerger; // The TreeMerger who performs the merging
	public final static int NTHREAD = Runtime.getRuntime().availableProcessors(); // Number of threads to create
	public static List<List<NCRPNode>> equivalenceClasses;
	public static List<NCRPNode> output;
	//A Constructor for the MergerServices
	public MergerServices2(TreeMerger merger, List<NCRPNode> tempSubTreeRoots) {
		subTreeRoots = tempSubTreeRoots;
		treeMerger = merger;
	}

	//The Actual Method that does the Merging using MultiThreading
	public NCRPNode runMergerServices() throws IOException, InterruptedException {
		Object lock = new Object();
		treeMerger=new TreeMerger();
		boolean flag=false;
		double simMatrix[][]; 
		HashMap<NCRPNode, List<NCRPNode>> mostSimilarTrees;
		while(!flag){

			simMatrix=new double[subTreeRoots.size()][subTreeRoots.size()];
			mostSimilarTrees=new HashMap<NCRPNode, List<NCRPNode>>();
			for(int i=0; i<subTreeRoots.size(); i++){
				double min=Double.MIN_VALUE;
				List<NCRPNode> mostSimilar=new ArrayList<NCRPNode>();
				for(int j=0; j<subTreeRoots.size(); j++){
					if(j>i){
						simMatrix[i][j]=treeMerger.findSimilarity(subTreeRoots.get(i), subTreeRoots.get(j));
						simMatrix[j][i]=simMatrix[i][j];
					}
					if(j!=i){
						if(simMatrix[i][j]<min){
							min=simMatrix[i][j];
							mostSimilar.clear();
							mostSimilar.add(subTreeRoots.get(i));
						}
						else if(simMatrix[i][j]==min){
							mostSimilar.add(subTreeRoots.get(i));
						}
					}

				}
				mostSimilarTrees.put(subTreeRoots.get(i),mostSimilar);

			}
			equivalenceClasses=new ArrayList<List<NCRPNode>>();
			output=new ArrayList<NCRPNode>();

			for(int i=0; i<mostSimilarTrees.size(); i++){
				for(int j=0; j<mostSimilarTrees.get(subTreeRoots.get(i)).size(); j++){
					NCRPNode minTree=mostSimilarTrees.get(subTreeRoots.get(i)).get(j);
					if(!mostSimilarTrees.get(minTree).contains(subTreeRoots.get(i))){
						mostSimilarTrees.get(subTreeRoots.get(i)).remove(minTree);
					}
				}
			}

			for(int i=0; i<mostSimilarTrees.size(); i++){
				List<NCRPNode> equiClass=new ArrayList<NCRPNode>();
				if(!mostSimilarTrees.get(subTreeRoots.get(i)).isEmpty())
				{	equiClass.add(subTreeRoots.get(i));
				for(int j=0; j<mostSimilarTrees.get(subTreeRoots.get(i)).size(); j++){
					NCRPNode minTree=mostSimilarTrees.get(subTreeRoots.get(i)).get(j);
					if(!equiClass.contains(minTree)){
						equiClass.add(minTree);
						mostSimilarTrees.get(subTreeRoots.get(i)).addAll(mostSimilarTrees.get(minTree));
						mostSimilarTrees.get(minTree).clear();
					}
				}
				if(!equiClass.isEmpty())
					equivalenceClasses.add(equiClass);
				}
			}
			//Till there is Only one root in the subTreeRoots i.e. No more Trees to merge
			System.out.println("Merge Started : "+equivalenceClasses.size());
			while (equivalenceClasses.size() > 0) {

				Thread[] threads = new Thread[NTHREAD];
				for (int i = 0; i < NTHREAD; i++) {
					threads[i] = new Thread(new MyRunnable1(equivalenceClasses.remove(0),lock));
					threads[i].start();
					if(equivalenceClasses.isEmpty())
						break;

				}
				for(int i=0; i<NTHREAD; i++){
					threads[i].join();
				}
			}
			
			if(output.size()==1)
				flag=true;
			subTreeRoots=output;
		}
		return subTreeRoots.get(0);
	}	
	class MyRunnable1 implements Runnable {
		Object lock;
		List<NCRPNode> subTreeRoots;
		MyRunnable1(List<NCRPNode> subTrees, Object l){
			lock = l;
			subTreeRoots=subTrees;
		}
		public void run() {
			
			try {
				while(subTreeRoots.size()>1){
					NCRPNode firstNode=subTreeRoots.remove(0);
					NCRPNode secondNode=subTreeRoots.remove(0);
					TreeMerger treeMerger = new TreeMerger();
					if(firstNode!=null && secondNode!=null){
						//Following three lines for Finding the referenceRoot and NonReferenceRoot
						NCRPNode referenceRoot, nonReferenceRoot;
						referenceRoot = treeMerger.findReferenceTree(firstNode,secondNode);
						nonReferenceRoot = (referenceRoot.equals(firstNode)) ? secondNode: firstNode;
						NCRPNode merged=treeMerger.merge(referenceRoot, nonReferenceRoot);
                        subTreeRoots.add(merged); 
						
					}

				}
				synchronized(lock){
					output.add(subTreeRoots.get(0));
				}
			}
			catch (Exception e) {
				e.printStackTrace();
				System.out.println("Merge Error in Merge Service");
			}


		}

	}

}
