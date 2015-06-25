package gui;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.JTree;
import javax.swing.JScrollPane;

public class TreeView extends JFrame {

	private JPanel contentPane;

	/**
	 * Create the frame.
	 */
	public TreeView(NCRPNode topicTreeRoot, int topWords) {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(400, 400, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);
		
		JScrollPane scrollPane = new JScrollPane();
		contentPane.add(scrollPane, BorderLayout.CENTER);
		

		DefaultMutableTreeNode root = generateTree(topicTreeRoot,topWords);

		JTree tree = new JTree(root);

		 scrollPane.setViewportView(tree);

		
	}

	private DefaultMutableTreeNode generateTree(NCRPNode topicTreeRoot, int topWords) {

		DefaultMutableTreeNode root = new DefaultMutableTreeNode(
				generateTopWords(topWords,topicTreeRoot.getWordCount()));
		
		HashSet<String> childDocuments = new HashSet<>();
		
		for(NCRPNode child : topicTreeRoot.children){
			root.add(generateTree(child,topWords));
			childDocuments.addAll(child.documents);
		}
		
		HashSet<String> rootDocuments = new HashSet<>(topicTreeRoot.documents);
		
		rootDocuments.removeAll(childDocuments);
		
		for(String doc : rootDocuments){
			root.add(new DefaultMutableTreeNode(doc));
		}
		
		return root;
	}

	private String generateTopWords(int n, HashMap<String, Integer> wordCountMap) {

		PriorityQueue<Integer> heap = new PriorityQueue<>(
				new Comparator<Integer>() {

					@Override
					public int compare(Integer o1, Integer o2) {
						return o2 - o1;
					}
				});
		for (String s : wordCountMap.keySet()) {
			heap.add(wordCountMap.get(s));
		}

		List<Integer> maxCount = new ArrayList<>();

		for (int i = 0; i < n && !heap.isEmpty(); i++) {
			maxCount.add(heap.poll());
		}
	
		String output = "";
		for (String s : wordCountMap.keySet()) {
			if (maxCount.contains(wordCountMap.get(s))) {
				output = output + " " + s;
			}
		}
		return output;
	}
}