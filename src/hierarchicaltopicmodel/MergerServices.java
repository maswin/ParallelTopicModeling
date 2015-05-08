package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;
import hierarchicaltopicmodel.TreeMerger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MergerServices {
	public static List<NCRPNode> subTreeRoots; // The Roots of Sub Trees to be merged
	public static TreeMerger treeMerger; // The TreeMerger who performs the merging
	public final static int NTHREAD = Runtime.getRuntime().availableProcessors(); // Number of threads to create


	//A Constructor for the MergerServices
	public MergerServices(TreeMerger merger, List<NCRPNode> tempSubTreeRoots) {
		subTreeRoots = tempSubTreeRoots;
		treeMerger = merger;
	}

	//The Actual Method that does the Merging using MultiThreading
	public NCRPNode runMergerServices() {
		Object lock = new Object();
		//Till there is Only one root in the subTreeRoots i.e. No more Trees to merge
		System.out.println("Merge Started : "+subTreeRoots.size());
		while (subTreeRoots.size() > 1) {

			Thread[] threads = new Thread[NTHREAD];
			for (int i = 0; i < NTHREAD; i++) {
				threads[i] = new Thread(new MyRunnable1(i+1,lock));
				threads[i].start();
			}
			for (Thread thread : threads) {
				try {
					//A Wait for Joining all threads merging in a single Iteration
					thread.join();

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		}
		return subTreeRoots.get(0);
	}



	class MyRunnable1 implements Runnable {
		int id;
		Object lock;
		MyRunnable1(int temp,Object l){
			id = temp;
			lock = l;
		}
		@Override
		public void run() {
			NCRPNode firstNode = null, secondNode = null;

			try {

				//A Critical Section where Two nodes are extracted from the subTreeRoots
				//The Condition is to make sure if one of threads enter during a state where there 
				//is only one node in subTreeRoots then that is the final Root and hence a return;
				synchronized (lock) {

					if (subTreeRoots.size() > 0){
						firstNode = subTreeRoots.remove(subTreeRoots.size() - 1);
						if (subTreeRoots.size() > 0){
							secondNode = subTreeRoots.remove(subTreeRoots.size() - 1);
						}
						else {
							subTreeRoots.add(firstNode);
							return;
						}
					}else{
						return;
					}

				}
				TreeMerger treeMerger = new TreeMerger();
				if(firstNode!=null && secondNode!=null){
					//Following three lines for Finding the referenceRoot and NonReferenceRoot
					NCRPNode referenceRoot, nonReferenceRoot;
					referenceRoot = treeMerger.findReferenceTree(firstNode,secondNode);
					nonReferenceRoot = (referenceRoot.equals(firstNode)) ? secondNode: firstNode;


					//Constructing the ReferenceTreeMap
					//The Reference Tree Map constructed is stored inside the TreeMerger ( A Property of it)
					treeMerger.constructReferenceTreeMap(referenceRoot);


					//For pre-computing the divergence values
					HashMap<NCRPNode, Double> similarityMap = new HashMap<NCRPNode, Double>();
					similarityMap.put(referenceRoot, treeMerger.findSimilarity(referenceRoot, nonReferenceRoot));
					treeMerger.referenceTreeMap.put(0,(HashMap<NCRPNode, Double>) similarityMap.clone());

					//Finally doing the merging

					Set s = Collections.synchronizedSet(new HashSet(treeMerger.referenceTreeMap.get(referenceRoot.level + 1).keySet()));
					NCRPNode root = treeMerger.compareNodes(referenceRoot,nonReferenceRoot,s);

					//The final Root is added into the subTreeRoots
					synchronized (lock) {

						subTreeRoots.add(root);

					}
				}

			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Merge Error in Merge Service");
			}


		}

	}

}
