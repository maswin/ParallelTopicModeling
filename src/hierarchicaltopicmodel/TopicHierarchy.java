package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;

import cc.mallet.types.InstanceList;



public class TopicHierarchy {

	public static void main(String args[]) throws NumberFormatException, IOException  {

		long startTime = System.nanoTime();
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		int threads = Runtime.getRuntime().availableProcessors();
		ExecutorService executor = Executors.newFixedThreadPool(threads);

		String folderName = null;

		try {
			System.out.println("Enter the input directory name : ");	
			folderName = br.readLine();	
		}catch(IOException e) {	
			e.printStackTrace();
		}

		File folder = new File(folderName);
		String[] fileNames = new String[folder.listFiles().length];

		int i = 0;
		for(File f : folder.listFiles()) 
			fileNames[i++] = f.getAbsolutePath();

		InputOutputReader ior = new InputOutputReader();
		String[] inputParameters = ior.getHldaParameters();

		ConcurrentHashMap<Integer,ArrayList<NCRPNode>> subTreeRoots = new ConcurrentHashMap<Integer,ArrayList<NCRPNode>>();
		ArrayList<NCRPNode> leaves = new ArrayList<NCRPNode>();

		for (i = 0; i < threads; i++) {

			Runnable worker = new WorkerThread(fileNames,i,inputParameters,subTreeRoots,leaves);
			executor.execute(worker);

		}
		executor.shutdown();
		while (!executor.isTerminated()) {
		}

		ArrayList<NCRPNode> subTreeRootList = new ArrayList<NCRPNode>();
		for(i=0;i<subTreeRoots.size();i++) {
			ArrayList<NCRPNode> al = subTreeRoots.get(i);
			for(int j=0;j<al.size();j++){
				subTreeRootList.add(al.get(j));
			}
		}
		System.out.println("SubTree Count : "+subTreeRootList.size());

		TreeMerger tm = new TreeMerger();
		NCRPNode root ;

		long startMergeTime = System.nanoTime();
		MergerServices ms = new MergerServices(tm, subTreeRootList);
		root = ms.runMergerServices();
		long endMergeTime = System.nanoTime();

		System.out.println("Merge Algorithm Running Time " + (endMergeTime-startMergeTime)); 

		System.out.println("\nMERGE SUCCESSFUL\n");

		HierarchicalLDA hlda = new HierarchicalLDA();
		PrintWriter pw = new PrintWriter("MergedOutputFile.txt");
		hlda.printNode(root, 0, pw);
		pw.close();

		displayDocuments(root,0);
		long endTime = System.nanoTime();
		System.out.println("Total Running Time = " + (endTime-startTime));
	}

	public static void displayDocuments(NCRPNode node, int indent) {

		for (int i=0; i<node.documents.size(); i++) {

			String s = node.documents.get(i);
			int index = s.lastIndexOf("\\");
			s = s.substring(index+1, s.length());

			for (int j=0; j<indent; j++) 
				System.out.print("  ");

			//System.out.println(node.nodeID+"/"+node.customers+" : ");
			System.out.println(s);
		}	
		System.out.println();

		for (NCRPNode child: node.children) {
			displayDocuments(child, indent + 1);
		}

	}


}
class test implements Serializable{
	int a;
	int customers;
	public ArrayList<String> documents;
	public ArrayList<NCRPNode> children;
	public NCRPNode parent;
	int level;

	int mergeId;

	int totalTokens;
	public synchronized void setParent(NCRPNode parent) {
		this.parent = parent;
	}
	int[] typeCounts;

	public int nodeID;
	HashMap<String, Integer> wordCount;
	test(int tmp){
		a = tmp;
	}

}
