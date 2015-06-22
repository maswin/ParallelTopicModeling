package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import org.apache.commons.io.FileUtils;

import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorkerThread implements Runnable {

	// String files[] = null;
	private int threadID;
	private int levels;
	private double alpha;
	private double gamma;
	private double eta;
	private int iterations;

	File[] documents = null;

	ConcurrentHashMap<Integer, NCRPNode> rootMap;// = new
													// ConcurrentHashMap<Integer,ArrayList<NCRPNode>>();

	public WorkerThread(int threadID, int levels, double alpha, double gamma,
			double eta, int iterations, File[] documents,
			ConcurrentHashMap<Integer, NCRPNode> rootMap) {
		super();
		this.threadID = threadID;
		this.levels = levels;
		this.alpha = alpha;
		this.gamma = gamma;
		this.eta = eta;
		this.iterations = iterations;
		this.documents = documents;

		this.rootMap = rootMap;
	}

	@Override
	public void run() {

		System.out.println("THREAD " + threadID + " : Starting\n");

		File inputDirectory = getInputFiles();
		removeStopWords(inputDirectory);

		HierarchicalLDA hlda = constructTopicHierarchy(inputDirectory);
		// SubTreeExtractor se = new SubTreeExtractor();

		rootMap.put(threadID, hlda.getRootNode());

		System.out.println("THREAD " + threadID + " : Terminating\n");

	}

	public File getInputFiles() {

		// String dirName =
		// "C:\\mallet-2.0.7\\mallet-2.0.7\\Directory"+(threadID+1);
		String dirName = "var/Directory" + (threadID + 1);
		File dir = new File(dirName);
		dir.mkdir();

		for (int i = 0; i < documents.length; i++) {

			File source = documents[i];
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
				+ "\\bin\\mallet import-dir --input "
				+ inputDirectory.getAbsolutePath() + " --output "
				+ inputDirectory.getAbsolutePath()
				+ ".mallet --keep-sequence --remove-stopwords";
		String filename = inputDirectory.getName() + "Features.bat";

		try {

			File file = new File(filename);
			if (!file.exists()) {
				file.createNewFile();
				file.setExecutable(true); // linux Change
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			bw.write(command);
			bw.close();
		}

		catch (IOException e) {

			System.out.println("Exception : Error in writing sh file!");
			e.printStackTrace();

		}

		try {

			Runtime runtime = Runtime.getRuntime();
			Process process = runtime.exec(filename);
			process.getOutputStream().close();

			InputStream inputStream = process.getInputStream();

			InputStreamReader inputstreamreader = new InputStreamReader(
					inputStream);
			BufferedReader br = new BufferedReader(inputstreamreader);

			String strLine = "";
			while ((strLine = br.readLine()) != null)
				System.out.println(strLine);

		}

		catch (IOException ioException) {

			System.out.println("Exception: Error in executing bat file!");
			ioException.printStackTrace();

		}

	}

	public HierarchicalLDA constructTopicHierarchy(File inputDirectory) {

		String malletFile = inputDirectory.getAbsolutePath() + ".mallet";
		// inputParameters[3] = inputDirectory.getAbsolutePath()+"WC.txt";

		HierarchicalLDA hlda = new HierarchicalLDA();

		// Set Word Count File
		hlda.setWordCountFile(inputDirectory.getAbsolutePath() + "WC.txt");

		// Set hyperparameters
		hlda.setAlpha(alpha);
		hlda.setGamma(gamma);
		hlda.setEta(eta);

		// Display preferences
		// hlda.setTopicDisplay(Integer.parseInt(inputParameters[5]),
		// Integer.parseInt(inputParameters[6]));

		// HierarchicalLDA hlda = ior.setHldaParameters(inputParameters);

		File file = new File(malletFile);
		InstanceList instances = InstanceList.load(file);
		InstanceList testing = null;

		if (malletFile != null) {
			testing = InstanceList.load(new File(malletFile));
		}
		Randoms random = new Randoms(); // Initialize Random Number Generator

		// Gathering input files
		String[] fileNames = new String[inputDirectory.listFiles().length];
		int i = 0;
		for (File f : inputDirectory.listFiles())
			fileNames[i++] = f.getAbsolutePath();

		// Initialize and start the sampler
		System.out.println("File Size : " + instances.size() + " " + file);

		hlda.initialize(instances, testing, this.levels, random, fileNames);
		hlda.estimate(iterations,
				inputDirectory.getAbsolutePath() + "Tree.txt", fileNames);
		hlda.writeWordCounts();

		return hlda;

	}

}
