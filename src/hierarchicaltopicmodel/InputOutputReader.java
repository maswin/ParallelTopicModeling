package hierarchicaltopicmodel;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class InputOutputReader {

    public String[] getHldaParameters() {
    	   	
    	BufferedReader br = new BufferedReader(new InputStreamReader(System.in)); 
    	String[] inputParameters = new String[11];
    	
    	try {
    		
    		System.out.println("Enter the number of iterations.");
    		inputParameters[4] = br.readLine();
    		
    		System.out.println("Enter the number of iterations after which brief summary of topics is to be displayed everytime.");
    		inputParameters[5] = br.readLine();
    		
    		System.out.println("Enter the number of most probable words to print for each topic after model estimation.");
    		inputParameters[6] = br.readLine();
    		
    		System.out.println("Enter the number of levels in the hLDA Tree to be constructed.");
    		inputParameters[7] = br.readLine();
    		
    		System.out.println("Enter the alpha paramater : Alpha parameter - smoothing over level distributions.");
    		inputParameters[8] = br.readLine();
    		
    		System.out.println("Enter the gamma parameter : Gamma parameter - CRP smoothing parameter");
    		inputParameters[9] = br.readLine();
    		
    		System.out.println("Enter the eta parameter : Eta parameter - smoothing over topic-word distributions");
    		inputParameters[10] = br.readLine();
    		
    	}
    	
    	catch(IOException e) {
    
    		System.out.println("Exception while getting input!!!");
    		e.printStackTrace();
    		
    	}
    	
    	return inputParameters;
    }
    
	public HierarchicalLDA setHldaParameters(String inputParameters[]){

		// Load instance lists
		if (inputParameters[0] == null) {
			System.err.println("Input instance list is required, use --input option");
			System.exit(1);
		}
	
		HierarchicalLDA hlda = new HierarchicalLDA();
		
		// Set Word Count File
		hlda.setWordCountFile(inputParameters[3]);
		
		// Set hyperparameters
		hlda.setAlpha(Double.parseDouble(inputParameters[8]));
		hlda.setGamma(Double.parseDouble(inputParameters[9]));
		hlda.setEta(Double.parseDouble(inputParameters[10]));
		
		// Display preferences
		hlda.setTopicDisplay(Integer.parseInt(inputParameters[5]), Integer.parseInt(inputParameters[6]));
		
		return hlda;
		
	}
	
    public void processInput(String command, String filename) {
    	
    	try {
    		
    		File file = new File(filename);
    		if (!file.exists()) {
    			file.createNewFile();
    			file.setExecutable(true); //linux Change
    		}
    		
    		FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write(command);
			bw.close();
    	}
    	
    	catch(IOException e) {
    		
    		System.out.println("Exception : Error in writing sh file!");
    		e.printStackTrace();
    		
    	}
    	
    	try {
        	
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(filename);
            process.getOutputStream().close();
            
            InputStream inputStream = process.getInputStream();
            
            InputStreamReader inputstreamreader = new InputStreamReader(inputStream);
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
    	
       
}
