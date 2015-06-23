package gui;

import hierarchicaltopicmodel.AD_DA_Classifier;
import hierarchicaltopicmodel.TopicHierarchy;
import hierarchicaltopicmodel.HierarchicalLDA.NCRPNode;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JTabbedPane;
import javax.swing.JDesktopPane;
import javax.swing.SwingConstants;

import java.awt.Color;

import javax.swing.JTextField;

import javax.swing.JTextPane;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JButton;

public class Main extends JFrame {

	private JPanel contentPane;
	private JTextField textField_Levels;
	private JTextField textField_Iterations;
	private JTextField textField_Alpha;
	private JTextField textField_Eta;
	private JTextField textField_Gamma;
	private JTextField textField_InputDirectory;
	private JTextField textField_TopWords;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					Main frame = new Main();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public Main() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 500, 478);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.TOP);
		contentPane.add(tabbedPane, BorderLayout.CENTER);

		JDesktopPane desktopPane = new JDesktopPane();
		desktopPane.setBackground(Color.WHITE);
		tabbedPane.addTab("Parallel HLDA", null, desktopPane, null);

		JButton btnNewButton = new JButton("Parallel HLDA");
		btnNewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {

				TopicHierarchy hlda = new TopicHierarchy(Integer
						.parseInt(textField_Iterations.getText()), Integer
						.parseInt(textField_Levels.getText()), Double
						.parseDouble(textField_Alpha.getText()), Double
						.parseDouble(textField_Gamma.getText()), Double
						.parseDouble(textField_Eta.getText()),
						textField_InputDirectory.getText());
				NCRPNode root = hlda.constructTopicTreeParallel();
				TreeView frame = new TreeView(root, Integer.parseInt(textField_TopWords.getText()));
				frame.setExtendedState(Frame.MAXIMIZED_BOTH);
				frame.setVisible(true);
			}
		});
		btnNewButton.setBounds(251, 295, 155, 23);
		desktopPane.add(btnNewButton);

		JTextPane txtpnNumberOfLevels = new JTextPane();
		txtpnNumberOfLevels.setEditable(false);
		txtpnNumberOfLevels
				.setText("Number of Levels in hLDA Tree to be constructed ");
		txtpnNumberOfLevels.setBounds(37, 37, 277, 20);
		desktopPane.add(txtpnNumberOfLevels);

		textField_Levels = new JTextField();
		textField_Levels.setText("2");
		textField_Levels.setBounds(320, 37, 86, 20);
		desktopPane.add(textField_Levels);
		textField_Levels.setColumns(10);

		JTextPane txtpnNumberOfIterations = new JTextPane();
		txtpnNumberOfIterations.setEditable(false);
		txtpnNumberOfIterations.setText("Number of Iterations");
		txtpnNumberOfIterations.setBounds(37, 73, 244, 20);
		desktopPane.add(txtpnNumberOfIterations);

		textField_Iterations = new JTextField();
		textField_Iterations.setText("10000");
		textField_Iterations.setBounds(320, 73, 86, 20);
		desktopPane.add(textField_Iterations);
		textField_Iterations.setColumns(10);

		JTextPane txtpnAlphaParameter = new JTextPane();
		txtpnAlphaParameter.setEditable(false);
		txtpnAlphaParameter.setText("Alpha Parameter");
		txtpnAlphaParameter.setBounds(37, 104, 202, 20);
		desktopPane.add(txtpnAlphaParameter);

		textField_Alpha = new JTextField();
		textField_Alpha.setText("1");
		textField_Alpha.setBounds(320, 104, 86, 20);
		desktopPane.add(textField_Alpha);
		textField_Alpha.setColumns(10);

		JTextPane txtpnEtaParameter = new JTextPane();
		txtpnEtaParameter.setEditable(false);
		txtpnEtaParameter.setText("Eta Parameter");
		txtpnEtaParameter.setBounds(37, 135, 244, 20);
		desktopPane.add(txtpnEtaParameter);

		textField_Eta = new JTextField();
		textField_Eta.setText("1");
		textField_Eta.setBounds(320, 135, 86, 20);
		desktopPane.add(textField_Eta);
		textField_Eta.setColumns(10);

		JTextPane txtpnGammaParameter = new JTextPane();
		txtpnGammaParameter.setEditable(false);
		txtpnGammaParameter.setText("Gamma Parameter");
		txtpnGammaParameter.setBounds(37, 166, 244, 20);
		desktopPane.add(txtpnGammaParameter);

		textField_Gamma = new JTextField();
		textField_Gamma.setText("0.1");
		textField_Gamma.setBounds(320, 166, 86, 20);
		desktopPane.add(textField_Gamma);
		textField_Gamma.setColumns(10);

		JTextPane txtpnInputDocumentsDirectory = new JTextPane();
		txtpnInputDocumentsDirectory.setEditable(false);
		txtpnInputDocumentsDirectory.setText("Input Documents Directory");
		txtpnInputDocumentsDirectory.setBounds(37, 207, 244, 20);
		desktopPane.add(txtpnInputDocumentsDirectory);

		textField_InputDirectory = new JTextField();
		textField_InputDirectory.setText("mydir");
		textField_InputDirectory.setColumns(10);
		textField_InputDirectory.setBounds(320, 207, 86, 20);
		desktopPane.add(textField_InputDirectory);

		JButton btnNewButton_1 = new JButton("Train AD/DA Classifier");
		btnNewButton_1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {

				TopicHierarchy hlda = new TopicHierarchy(Integer
						.parseInt(textField_Iterations.getText()), Integer
						.parseInt(textField_Levels.getText()), Double
						.parseDouble(textField_Alpha.getText()), Double
						.parseDouble(textField_Gamma.getText()), Double
						.parseDouble(textField_Eta.getText()),
						textField_InputDirectory.getText());
				
				NCRPNode root = hlda.constructTopicTree();
				
				List<NCRPNode> rootList = new LinkedList<NCRPNode>();
				rootList.add(root);
				
				AD_DA_Classifier classifier = new AD_DA_Classifier();
				classifier.train(rootList);
				try {
					classifier.saveModel();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		btnNewButton_1.setBounds(146, 347, 164, 23);
		desktopPane.add(btnNewButton_1);
		
		JTextPane txtpnNoOfTop = new JTextPane();
		txtpnNoOfTop.setText("No of Top Words to Display");
		txtpnNoOfTop.setEditable(false);
		txtpnNoOfTop.setBounds(37, 248, 244, 20);
		desktopPane.add(txtpnNoOfTop);
		
		textField_TopWords = new JTextField();
		textField_TopWords.setText("4");
		textField_TopWords.setColumns(10);
		textField_TopWords.setBounds(320, 248, 86, 20);
		desktopPane.add(textField_TopWords);
		
		JButton btnSequentialHlda = new JButton("Sequential HLDA");
		btnSequentialHlda.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				TopicHierarchy hlda = new TopicHierarchy(Integer
						.parseInt(textField_Iterations.getText()), Integer
						.parseInt(textField_Levels.getText()), Double
						.parseDouble(textField_Alpha.getText()), Double
						.parseDouble(textField_Gamma.getText()), Double
						.parseDouble(textField_Eta.getText()),
						textField_InputDirectory.getText());
				NCRPNode root = hlda.constructTopicTree();
				TreeView frame = new TreeView(root, Integer.parseInt(textField_TopWords.getText()));
				frame.setExtendedState(Frame.MAXIMIZED_BOTH);
				frame.setVisible(true);
				
				
			}
		});
		btnSequentialHlda.setBounds(37, 295, 148, 23);
		desktopPane.add(btnSequentialHlda);
	}
}
