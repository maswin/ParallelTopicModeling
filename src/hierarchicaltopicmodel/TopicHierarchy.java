package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;

import mpi.MPI;
import cc.mallet.types.InstanceList;



public class TopicHierarchy {

	public static void main(String args[]) throws NumberFormatException, IOException, InterruptedException  {

		MPI.Init(args);
		int id = MPI.COMM_WORLD.Rank();
		int size = MPI.COMM_WORLD.Size();
		System.out.println("Started Id : "+id+"/"+size);

		if(id==0){
			long startTime = System.nanoTime();
			BufferedReader br = new BufferedReader(new FileReader("inputFolder.txt"));
			
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
			int length[] = new int[1];
			
			//Send File Names & inputParamters - 1
			//File Names Tag - 1,2
			//inputParametersTag - 3,4
			
			for(i=1;i<size;i++){
				
				length[0] = fileNames.length;
				MPI.COMM_WORLD.Send(length,0,1,MPI.INT,i,size+1);
				MPI.COMM_WORLD.Send(fileNames,0,fileNames.length,MPI.OBJECT,i,size+2);
				
				length[0] = inputParameters.length;
				MPI.COMM_WORLD.Send(length,0,1,MPI.INT,i,size+3);
				MPI.COMM_WORLD.Send(inputParameters,0, inputParameters.length,MPI.OBJECT,i,size+4);
			}
			
			ConcurrentHashMap<Integer,ArrayList<NCRPNode>> subTreeRoots = new ConcurrentHashMap<Integer,ArrayList<NCRPNode>>();
			ArrayList<NCRPNode> leaves = new ArrayList<NCRPNode>();

			/*for (i = 0; i < size; i++) {

				Runnable worker = new WorkerThread(fileNames,i,inputParameters,subTreeRoots,leaves);
				executor.execute(worker);

			}
			executor.shutdown();
			while (!executor.isTerminated()) {
			}*/

			ArrayList<NCRPNode> rootList = new ArrayList<NCRPNode>();
			rootList = new ArrayList<NCRPNode>();
			
			MPJWorkerThread MPJWThread = new MPJWorkerThread(fileNames,id,size,inputParameters);
			rootList = MPJWThread.formTrees();
			NCRPNode rootListArray[];
			subTreeRoots.put(0, rootList);
			NCRPNode[] node = new NCRPNode[1];
			
			//Receive RootList
			for(i=1;i<size;i++){
				
				
				MPI.COMM_WORLD.Recv(length, 0, 1, MPI.INT, i, size+5);
				rootListArray = new NCRPNode[length[0]];

				rootList = new ArrayList<NCRPNode>();
				for(int j=0;j<length[0];j++){
					node = new NCRPNode[1];
					System.out.println("Thread "+i+" Received "+j);
					MPI.COMM_WORLD.Recv(node, 0, 1, MPI.OBJECT, i, size+6+j);
					
					rootList.add(node[0]);
				}
				
				
				subTreeRoots.put(i, rootList);
			}
			//AddReceived root list to subTreeRootsList
			
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

			//displayDocuments(root,0);
			long endTime = System.nanoTime();
			System.out.println("Total Running Time = " + (endTime-startTime));
		}else{
			String[] fileNames;
			int[] fileNamesLength = new int[1];
			String[] inputParameters = null;
			int[] inputParametersLength = new int[1];
			
			//Receive Filenames and inputParameters - 1 
			MPI.COMM_WORLD.Recv(fileNamesLength, 0, 1, MPI.INT, 0, size+1);
			fileNames = new String[fileNamesLength[0]];
			MPI.COMM_WORLD.Recv(fileNames, 0, fileNamesLength[0], MPI.OBJECT, 0, size+2);
			
			MPI.COMM_WORLD.Recv(inputParametersLength, 0, 1, MPI.INT, 0, size+3);
			inputParameters = new String[inputParametersLength[0]];
			MPI.COMM_WORLD.Recv(inputParameters, 0, inputParametersLength[0], MPI.OBJECT, 0, size+4);
			
			ArrayList<NCRPNode> rootList = new ArrayList<NCRPNode>();
			MPJWorkerThread MPJWThread = new MPJWorkerThread(fileNames,id,size,inputParameters);
			rootList = MPJWThread.formTrees();
			
			NCRPNode rootListArray[] = rootList.toArray(new NCRPNode[rootList.size()]);
			//SendRootList
			int length[] = new int[1];
			length[0] = rootList.size();
			NCRPNode node[] = new NCRPNode[1];
			MPI.COMM_WORLD.Send(length, 0, 1, MPI.INT, 0, size+5);
			for(int i=0;i<length[0];i++){
				System.out.println("Thread "+id+" Sending "+i);
				node[0] = rootList.get(i);
				MPI.COMM_WORLD.Send(node, 0, 1, MPI.OBJECT, 0, size+6+i);
				
			}
			
		}
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
