package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.io.IOException;
import java.io.File;
import java.io.Serializable;

public class TopicHierarchy {

	private int iterations;
	private int levels;
	private double alpha;
	private double gamma;
	private double eta;
	private String inputDir;

	public TopicHierarchy(int iterations, int levels, double alpha,
			double gamma, double eta, String inputDir) {
		super();
		this.iterations = iterations;
		this.levels = levels;
		this.alpha = alpha;
		this.gamma = gamma;
		this.eta = eta;
		this.inputDir = inputDir;
	}

	public NCRPNode constructTopicTree() {

		File folder = new File(inputDir);
		String[] fileNames = new String[folder.listFiles().length];

		int i = 0;
		for (File f : folder.listFiles())
			fileNames[i++] = f.getAbsolutePath();

		ConcurrentHashMap<Integer, NCRPNode> rootMap = new ConcurrentHashMap<Integer, NCRPNode>();

		new WorkerThread(0, levels, alpha, gamma, eta, 40, folder.listFiles(),
				rootMap).run();

		return rootMap.get(0);
	}

	public NCRPNode constructTopicTreeParallel() {
		File folder = new File(inputDir);
		String[] fileNames = new String[folder.listFiles().length];

		int i = 0;
		for (File f : folder.listFiles())
			fileNames[i++] = f.getAbsolutePath();

		ConcurrentHashMap<Integer, NCRPNode> rootMap = new ConcurrentHashMap<Integer, NCRPNode>();

		int threads = Runtime.getRuntime().availableProcessors();
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		for (i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
			Runnable worker;
			if (i == threads - 1) {
		
				worker = new WorkerThread(i, levels, alpha, gamma, eta, 40,
						Arrays.copyOfRange(folder.listFiles(),
								i * (folder.listFiles().length / threads),
								folder.listFiles().length), rootMap);
			} else {
				worker = new WorkerThread(
						i,
						levels,
						alpha,
						gamma,
						eta,
						40,
						Arrays.copyOfRange(folder.listFiles(),
								i * (folder.listFiles().length / threads),
								(i + 1) * (folder.listFiles().length / threads)),
						rootMap);
			}

			executor.execute(worker);
		}
		executor.shutdown();
		while (!executor.isTerminated()) {
		}

		List<NCRPNode> subTreeRoots = new ArrayList<HierarchicalLDA.NCRPNode>();

		for (Integer key : rootMap.keySet()) {
			subTreeRoots.add(rootMap.get(key));
		}
		TreeMerger tm = new TreeMerger();
		MergerServices mergerService = new MergerServices(tm, subTreeRoots);
		NCRPNode root = null;
		try {
			root = mergerService.runMergerServices();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return root;
	}

	public static void main(String[] args) {

		TopicHierarchy hlda = new TopicHierarchy(4, 4, 10, 1, 0.1, "mydir");
		NCRPNode root = hlda.constructTopicTreeParallel();
		displayCustom(root);
	}

	private static void displayCustom(NCRPNode root) {

		Queue<NCRPNode> queue1 = new LinkedList<HierarchicalLDA.NCRPNode>();
		Queue<NCRPNode> queue2 = new LinkedList<HierarchicalLDA.NCRPNode>();

		System.out.println("size : " + root.children.size());
		queue1.add(root);
		while (!queue1.isEmpty()) {
			System.out.println();
			queue2.clear();
			for (NCRPNode node : queue1) {
				System.out.println();
				System.out.print("ID :" + node.nodeID + " Level :" + node.level
						+ " [");

				for (NCRPNode child : node.children) {
					System.out.print(child.nodeID + " ");
					queue2.add(child);
				}
				System.out.print("] Doc[");

				for (String doc : node.documents) {
					System.out.print(doc + ",");
				}
				System.out.print("]");
			}
			queue1.clear();
			queue1.addAll(queue2);
		}

	}
}
