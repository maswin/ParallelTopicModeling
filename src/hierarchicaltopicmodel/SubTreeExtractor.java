package hierarchicaltopicmodel;

import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;
import java.util.ArrayList;

public class SubTreeExtractor {
	
	ArrayList<NCRPNode> roots = new ArrayList<NCRPNode>();
	//ArrayList<NCRPNode> leaves = new ArrayList<NCRPNode>();
	
	public void extractRoot(NCRPNode node) {
		
		int size = node.children.size();
		if(size==0) return;
		else if(size==1) extractRoot(node.children.get(0));
		else {
			for(NCRPNode child: node.children)
				roots.add(child);
		}
		
	}
	
	/*public void extractLeaves(NCRPNode node){
		
		int size = node.children.size();	
		if(size==0){
			leaves.add(node);	
		}
		else{
			int i = 0;
			while(size!=0){
				NCRPNode n = node.children.get(i);
				i++;
				size--;
				extractLeaves(n);
				
			}//end of while
			
		}//end of else

	}*/
	
	public ArrayList<NCRPNode> getRoots() {
		return roots;
	}
	
	/*public ArrayList<NCRPNode> getLeaves() {
		return leaves;
	}*/
	
	/*public NCRPNode getLeaf(int index) {
		return leaves.get(index);
	}*/
	public NCRPNode getRoot(int index) {
		return roots.get(index);
	}
	
	/*public int getLeafSize() {
		return leaves.size();
	}*/
	public int getRootSize() {
		return roots.size();
	}
	
/*	public void printSubTrees(){
		for(int i=0; i<roots.size(); i++)
			System.out.println((roots.get(i)).nodeID);
	}*/
	
}