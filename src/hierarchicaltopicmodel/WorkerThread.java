package hierarchicaltopicmodel;
import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import org.apache.commons.io.FileUtils;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class WorkerThread implements Runnable {
	
	String files[] = null;
	int threadID;
	String[] inputParameters;
	NCRPNode referenceTreeRoot;
	ConcurrentHashMap<Integer,ArrayList<NCRPNode>> rootMap;// = new ConcurrentHashMap<Integer,ArrayList<NCRPNode>>();
	ArrayList<NCRPNode> leaves;// = new ArrayList<NCRPNode>();
	
	WorkerThread(String[] files, int threadID, String[] inputParameters, ConcurrentHashMap<Integer,ArrayList<NCRPNode>> rootMap,ArrayList<NCRPNode> leaves) {
	
		this.files = files.clone();	
		this.threadID = threadID;
		this.inputParameters = inputParameters.clone();
		this.rootMap  = rootMap;
		this.leaves = leaves;
		
	}
	
	
	public void run(){
		
		System.out.println("THREAD "+threadID+" : Starting\n");
		
		File inputDirectory = getInputFiles();
		removeStopWords(inputDirectory);
		
		HierarchicalLDA hlda = constructTopicHierarchy(inputDirectory);
		SubTreeExtractor se = new SubTreeExtractor();
		
		if(threadID == 0 ){
			
			ArrayList<NCRPNode> rootList = new ArrayList<NCRPNode>();
			rootList.add(hlda.getRootNode());
			rootMap.put(threadID, (ArrayList<NCRPNode>) rootList.clone());
			//se.extractLeaves(hlda.getRootNode());
			//int size = se.getLeafSize(); 
			//for(int j = 0; j < size; j++)
	        	//	leaves.add(se.getLeaf(j));
			
		}
		
		else{ 	
			se.extractRoot(hlda.getRootNode());
			int size = se.getRootSize(); 
			ArrayList<NCRPNode> rootList = new ArrayList<NCRPNode>();
			for(int j = 0; j < size; j++)
				rootList.add(se.getRoot(j));
			rootMap.put(threadID,(ArrayList<NCRPNode>) rootList.clone());
		}
		
		System.out.println("THREAD "+threadID+" : Terminating\n");
	
	}
	
	public File getInputFiles(){
			
		//String dirName = "C:\\mallet-2.0.7\\mallet-2.0.7\\Directory"+(threadID+1);
		String dirName = "Directory"+(threadID+1);
		File dir = new File(dirName);
		dir.mkdir();
		
		int noOfFilesToProcess = (files.length) / (Runtime.getRuntime().availableProcessors());
		//int noOfFilesToProcess = (files.length) / (10);
		int startIndex = (threadID*noOfFilesToProcess);
		int endIndex = (threadID*noOfFilesToProcess)+noOfFilesToProcess;
		
		//int startIndex = 0;
		//int endIndex = files.length;
		
		for(int i=startIndex;i<endIndex;i++) {
			
			File source = new File(files[i]);
			File target = new File(dirName+"/"+source.getName());
			
			try {
				FileUtils.copyFile(source, target);			
			}
			
			catch(IOException e) {
				e.printStackTrace();
			}
			
		}
		return dir;
    }
	
	public void removeStopWords(File inputDirectory) {
		
		String command1 = "cd\ncd /home/aswin/mallet-2.0.7/\nbin/mallet import-dir --input " + inputDirectory.getAbsolutePath()+ " --output " + inputDirectory.getAbsolutePath() + ".mallet --keep-sequence --remove-stopwords"; //linux Change
		String filename1 = "/home/aswin/workspace/ParallelTopicModelingProject/"+inputDirectory.getName()+"Features.sh"; //linux Change
		
		InputOutputReader ior = new InputOutputReader();
		ior.processInput(command1, filename1);
		
	}
	
	public HierarchicalLDA constructTopicHierarchy(File inputDirectory) {
		
		inputParameters[0] = inputParameters[1] = (inputDirectory.getAbsolutePath()+ ".mallet");
		inputParameters[3] = inputDirectory.getAbsolutePath()+"WC.txt";
		
		InputOutputReader ior = new InputOutputReader();
		HierarchicalLDA hlda = ior.setHldaParameters(inputParameters);
		
		File file = new File(inputParameters[0]);
		InstanceList instances = InstanceList.load(file);
		InstanceList testing = null;
		
		if (inputParameters[1] != null) {
			testing = InstanceList.load(new File(inputParameters[1]));
		}
		Randoms random = new Randoms(); //Initialize Random Number Generator
		
		//Gathering input files
		String[] fileNames = new String[inputDirectory.listFiles().length];
		int i = 0;
		for(File f : inputDirectory.listFiles()) 
        	fileNames[i++] = f.getAbsolutePath();
		
		// Initialize and start the sampler
		System.out.println("File Size : "+instances.size()+" "+file);
		hlda.initialize(instances, testing , Integer.parseInt(inputParameters[7]), random,fileNames);
		hlda.estimate(Integer.parseInt(inputParameters[4]),inputDirectory.getAbsolutePath()+"Tree.txt",fileNames);
		hlda.writeWordCounts();
		
		return hlda;
		
	}

}

