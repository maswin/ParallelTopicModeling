package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;

import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

public class MPJWorkerThread {

	String files[] = null;
	int id;
	int size;
	String[] inputParameters;
	ConcurrentHashMap<Integer, ArrayList<NCRPNode>> rootMap;// = new
															// ConcurrentHashMap<Integer,ArrayList<NCRPNode>>();
	ArrayList<NCRPNode> leaves;// = new ArrayList<NCRPNode>();

	public MPJWorkerThread(String[] files, int id, int size,
			String[] inputParameters) {
		this.files = files.clone();
		this.id = id;
		this.size = size;
		this.inputParameters = inputParameters.clone();
	}

	public ArrayList<NCRPNode> formTrees() {
		System.out.println("THREAD " + id + " : Starting\n");

		File inputDirectory = getInputFiles();
		removeStopWords(inputDirectory);

		HierarchicalLDA hlda = constructTopicHierarchy(inputDirectory);

		ArrayList<NCRPNode> rootList = new ArrayList<NCRPNode>();

		rootList.add(hlda.getRootNode());

		System.out.println("THREAD " + id + " : Terminating\n");
		return rootList;
	}

	public File getInputFiles() {

		// String dirName =
		// "C:\\mallet-2.0.7\\mallet-2.0.7\\Directory"+(threadID+1);
		String dirName = "var/Directory" + (id + 1);
		File dir = new File(dirName);
		dir.mkdir();

		int noOfFilesToProcess = (files.length) / (size);
		// int noOfFilesToProcess = (files.length) / (10);
		int startIndex = (id * noOfFilesToProcess);
		int endIndex = (id * noOfFilesToProcess) + noOfFilesToProcess;

		// int startIndex = 0;
		// int endIndex = files.length;

		for (int i = startIndex; i < endIndex; i++) {

			File source = new File(files[i]);
			File target = new File(dirName + "/" + source.getName());

			try {
				FileUtils.copyFile(source, target);
			}

			catch (IOException e) {
				e.printStackTrace();
			}

		}
		return dir;
	}

	public void removeStopWords(File inputDirectory) {
		Map<String, String> env = System.getenv();
		String malletHome = env.get("MALLET_HOME");
		
		String command =  malletHome
				+ "/bin/mallet import-dir --input "
				+ inputDirectory.getAbsolutePath() + " --output "
				+ inputDirectory.getAbsolutePath()
				+ ".mallet --keep-sequence --remove-stopwords";
		String filename = "var/"+inputDirectory.getName() + "Features.sh";
		
		InputOutputReader ior = new InputOutputReader();
		ior.processInput(command, filename);

	}

	public HierarchicalLDA constructTopicHierarchy(File inputDirectory) {

		inputParameters[0] = inputParameters[1] = (inputDirectory
				.getAbsolutePath() + ".mallet");
		inputParameters[3] = inputDirectory.getAbsolutePath() + "WC.txt";

		InputOutputReader ior = new InputOutputReader();
		HierarchicalLDA hlda = ior.setHldaParameters(inputParameters);

		File file = new File(inputParameters[0]);
		InstanceList instances = InstanceList.load(file);
		InstanceList testing = null;

		if (inputParameters[1] != null) {
			testing = InstanceList.load(new File(inputParameters[1]));
		}
		Randoms random = new Randoms(); // Initialize Random Number Generator

		// Gathering input files
		String[] fileNames = new String[inputDirectory.listFiles().length];
		int i = 0;
		for (File f : inputDirectory.listFiles())
			fileNames[i++] = f.getAbsolutePath();

		// Initialize and start the sampler
		System.out.println("File Size : " + instances.size() + " " + file);
		hlda.initialize(instances, testing,
				Integer.parseInt(inputParameters[7]), random, fileNames);
		hlda.estimate(Integer.parseInt(inputParameters[4]),
				inputDirectory.getAbsolutePath() + "Tree.txt", fileNames);
		hlda.writeWordCounts();

		return hlda;

	}

}
