package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpi.MPI;
import mpi.Request;

public class MergerServices {
	public static List<NCRPNode> subTreeRoots; // The Roots of Sub Trees to be merged
	public static TreeMerger treeMerger; // The TreeMerger who performs the merging
	public final static int NTHREAD = Runtime.getRuntime().availableProcessors(); // Number of threads to create
	public static List<List<NCRPNode>> equivalenceClasses;
	public static List<NCRPNode> output;
	
	//A Constructor for the MergerServices
	public MergerServices(TreeMerger merger, List<NCRPNode> tempSubTreeRoots) {
		subTreeRoots = tempSubTreeRoots;
		treeMerger = merger;
	}

	public NCRPNode runMergerServiceMaster() throws IOException, InterruptedException {
		Object lock = new Object();
		treeMerger=new TreeMerger();
	
		double simMatrix[][]; 
		DisjointSet<NCRPNode> disjointSet = new DisjointSet<NCRPNode>();
		boolean[] mergeIndicator = new boolean[1];
		while(subTreeRoots.size() > 1){
			System.out.println("SubTree reminaing : "+subTreeRoots.size());
			simMatrix=new double[subTreeRoots.size()][subTreeRoots.size()];
			
			for(int i=0; i<subTreeRoots.size(); i++){
				
				//Testing Purpose
				//System.out.println("Testing for the node : "+subTreeRoots.get(i).nodeID);
				
				double min=Double.MAX_VALUE;
				List<NCRPNode> similarNodes = new ArrayList<NCRPNode>();
				for(int j=0; j<subTreeRoots.size(); j++){
					if(j>i){
						simMatrix[i][j]=treeMerger.findSimilarity(subTreeRoots.get(i), subTreeRoots.get(j));
						simMatrix[j][i]=simMatrix[i][j];
					}
					if(j!=i){
						if(simMatrix[i][j]<min){
							min=simMatrix[i][j];
							similarNodes.clear();
							similarNodes.add(subTreeRoots.get(j));
						}
						else if(simMatrix[i][j]==min){
							similarNodes.add(subTreeRoots.get(j));
						}
					}else{
						simMatrix[i][j] = 0;
					}

				}
				if(!disjointSet.contains(subTreeRoots.get(i))){
					disjointSet.makeSet(subTreeRoots.get(i));
				}
				for(NCRPNode node : similarNodes){
					if(!disjointSet.contains(node)){
						disjointSet.makeSet(node);
					}
					disjointSet.union(subTreeRoots.get(i), node);
					
					//Testing Purpose
					//System.out.println("Union : "+subTreeRoots.get(i).nodeID+" : "+node.nodeID);
				}
			}
			
			//Testing Purpose - Similarity Matrix
			for(int i=0; i<subTreeRoots.size(); i++){
				for(int j=0; j<subTreeRoots.size(); j++){
					System.out.print(simMatrix[i][j]+"\t");
				}
				System.out.println();
			}
					
					
			equivalenceClasses = disjointSet.getAllSets();
			
			subTreeRoots.clear();
			
			//Till there is Only one root in the subTreeRoots i.e. No more Trees to merge
			System.out.println("Number of Equivalence Classes : "+equivalenceClasses.size());
			
			for(List<NCRPNode> mergeList : equivalenceClasses){
				
				while(mergeList.size() > 1){	
					int activeSlave = 0;
					while(activeSlave <  MPI.COMM_WORLD.Size()-1 && mergeList.size()> 1){
						activeSlave++;
						
						System.out.println("Activating slave : "+activeSlave);
						//wake slave
						mergeIndicator[0] = true;
						MPI.COMM_WORLD.Isend(mergeIndicator, 0,1, MPI.BOOLEAN,activeSlave,activeSlave);
						
						NCRPNode[] roots = new NCRPNode[2];
						roots[0] = mergeList.remove(0);
						roots[1] = mergeList.remove(0);
						
						MPI.COMM_WORLD.Isend(roots,0,2, MPI.OBJECT,activeSlave,activeSlave);
						System.out.println("Sending trees");
					}
					System.out.println("Waiting for merging");
					
					while(activeSlave != 0){
						
						NCRPNode[] recievedRoot = new NCRPNode[1];
						
						MPI.COMM_WORLD.Recv(recievedRoot, 0, 1, MPI.OBJECT,activeSlave , activeSlave);
						
						mergeList.add(recievedRoot[0]);
						activeSlave--;
					}
				}
				
				subTreeRoots.add(mergeList.get(0));
				
			}
		}
		System.out.println("Sending terminating signal");
		//terminate slave
		for(int i=1; i < MPI.COMM_WORLD.Size();i++){
			MPI.COMM_WORLD.Isend(mergeIndicator, 0,1, MPI.BOOLEAN,i,i);
		}
		return subTreeRoots.get(0);
		
	}
	//The Actual Method that does the Merging using MultiThreading
	public NCRPNode runMergerServices() throws IOException, InterruptedException {
		Object lock = new Object();
		treeMerger=new TreeMerger();
		boolean flag=false;
		double simMatrix[][]; 
		DisjointSet<NCRPNode> disjointSet = new DisjointSet<NCRPNode>();
		
		while(!flag){
			System.out.println("SubTree reminaing : "+subTreeRoots.size());
			simMatrix=new double[subTreeRoots.size()][subTreeRoots.size()];
			
			for(int i=0; i<subTreeRoots.size(); i++){
				
				//Testing Purpose
				//System.out.println("Testing for the node : "+subTreeRoots.get(i).nodeID);
				
				double min=Double.MAX_VALUE;
				List<NCRPNode> similarNodes = new ArrayList<NCRPNode>();
				for(int j=0; j<subTreeRoots.size(); j++){
					if(j>i){
						simMatrix[i][j]=treeMerger.findSimilarity(subTreeRoots.get(i), subTreeRoots.get(j));
						simMatrix[j][i]=simMatrix[i][j];
					}
					if(j!=i){
						if(simMatrix[i][j]<min){
							min=simMatrix[i][j];
							similarNodes.clear();
							similarNodes.add(subTreeRoots.get(j));
						}
						else if(simMatrix[i][j]==min){
							similarNodes.add(subTreeRoots.get(j));
						}
					}else{
						simMatrix[i][j] = 0;
					}

				}
				if(!disjointSet.contains(subTreeRoots.get(i))){
					disjointSet.makeSet(subTreeRoots.get(i));
				}
				for(NCRPNode node : similarNodes){
					if(!disjointSet.contains(node)){
						disjointSet.makeSet(node);
					}
					disjointSet.union(subTreeRoots.get(i), node);
					
					//Testing Purpose
					//System.out.println("Union : "+subTreeRoots.get(i).nodeID+" : "+node.nodeID);
				}
			}
			
			//Testing Purpose - Similarity Matrix
			for(int i=0; i<subTreeRoots.size(); i++){
				for(int j=0; j<subTreeRoots.size(); j++){
					System.out.print(simMatrix[i][j]+"\t");
				}
				System.out.println();
			}
					
					
			equivalenceClasses = disjointSet.getAllSets();
			output = new ArrayList<NCRPNode>();
			
			//Till there is Only one root in the subTreeRoots i.e. No more Trees to merge
			System.out.println("Number of Equivalence Classes : "+equivalenceClasses.size());
			
			while (equivalenceClasses.size() > 0) {

				Thread[] threads = new Thread[NTHREAD];
				int activeThreadCount = 0;
				for (int i = 0; i < NTHREAD; i++) {
					threads[i] = new Thread(new MyRunnable(equivalenceClasses.remove(0),lock));
					threads[i].start();
					activeThreadCount++;
					if(equivalenceClasses.isEmpty())
						break;

				}
				for(int i=0; i<activeThreadCount; i++){
					threads[i].join();
				}
			}
			
			if(output.size()==1)
				flag=true;
			subTreeRoots=output;
		}
		return subTreeRoots.get(0);
	}	
	public void runMergerServiceSlave() throws IOException, InterruptedException {
boolean[] flag = new boolean[1];
		
		//recieve start signal Master
		Request flagStatus = MPI.COMM_WORLD.Irecv(flag, 0, 1, MPI.BOOLEAN, 0, MPI.COMM_WORLD.Rank());
		flagStatus.Wait();
		
		while(flag[0]){
			System.out.println("Slave woke up : " + MPI.COMM_WORLD.Rank());
			NCRPNode[] roots = new NCRPNode[2];
			//recieve trees to merge
			flagStatus = MPI.COMM_WORLD.Irecv(roots, 0, 2, MPI.OBJECT, 0, MPI.COMM_WORLD.Rank());
			flagStatus.Wait();
			
			System.out.println("Slave merging : " + MPI.COMM_WORLD.Rank());
			//perform merging
			roots[0] = new TreeMerger().mergeTrees(roots[0],roots[1]);
			
			//send merged root to Master
			MPI.COMM_WORLD.Send(roots, 0,1, MPI.OBJECT,0, MPI.COMM_WORLD.Rank());
			
			System.out.println("Slave sleeping : " + MPI.COMM_WORLD.Rank());
			//wait for next signal
			flagStatus = MPI.COMM_WORLD.Irecv(flag, 0, 1, MPI.BOOLEAN, 0, MPI.COMM_WORLD.Rank());
			flagStatus.Wait();
		}
		
		
	}
	class MyRunnable implements Runnable {
		Object lock;
		List<NCRPNode> subTreeRoots;
		MyRunnable(List<NCRPNode> subTrees, Object l){
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
						
						//Merge the trees
						NCRPNode merged = treeMerger.mergeTrees(referenceRoot, nonReferenceRoot);
                        subTreeRoots.add(merged); 
						
					}

				}
				synchronized(lock){
					output.add(subTreeRoots.get(0));
				}
			}
			catch (Exception e) {
				e.printStackTrace();
				System.out.println("Error in Merge Service");
			}


		}

	}

}
