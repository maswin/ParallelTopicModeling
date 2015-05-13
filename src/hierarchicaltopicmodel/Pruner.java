package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

public class Pruner {

	public static NCRPNode pruneGenericNodeChains(NCRPNode root) {

		if (root.children.size() == 1) {
			NCRPNode child = root.children.get(0);
			while (child.children.size() == 1) {
				child = child.children.get(0);
			}
			if (!(child.children.size() == 0)) {

				root.children.clear();
				root.children.addAll(child.children);

				for (NCRPNode node : root.children) {
					node.parent = root;
				}
				changeLevel(root, root.getLevel());
			}
		}
		for (NCRPNode node : root.children) {
			pruneGenericNodeChains(node);
		}

		return root;
	}

	private static void changeLevel(NCRPNode root, int level) {
		root.setLevel(level);
		for (NCRPNode child : root.children) {
			changeLevel(child, level++);
		}
	}
}
