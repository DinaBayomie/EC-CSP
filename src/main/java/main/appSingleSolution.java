package main;

import java.util.List;

import org.chocosolver.solver.Solution;
import org.chocosolver.solver.variables.BoolVar;
import org.jbpt.petri.NetSystem;
import org.jbpt.petri.io.PNMLSerializer;

import correlation.ECmodelCreator;
import evaluation.EvaluationManager;
import preprocessing.Log;
import preprocessing.LogPreprocessingManager;

public class appSingleSolution {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("Reading input files....");
		PNMLSerializer ser = new PNMLSerializer();
		NetSystem netSystem = ser.parse(args[0]);
		Log unlabeled = new Log(args[1]);
		

		System.out.println("Start the log preprocessing phase......." + args[1]);
		LogPreprocessingManager logManager = new LogPreprocessingManager(unlabeled, netSystem);
		long start = System.currentTimeMillis();
		logManager.buildDictionaries();
//		 logManager.printDictionaries();
		long end = System.currentTimeMillis();
		System.out.println(" build dics in MS " + (end - start));

		System.out.println("Creating the model....");
		ECmodelCreator ecModel = new ECmodelCreator(logManager);
		Solution solution = ecModel.buildandRunModel();
		if (solution.exists()) {

			System.out.println("Solution found");
			ecModel.updateEventsCIds();
//			System.out.println(solution.toString());
			logManager.writeCorrelatedLog(args[3]);

			Log labeled = new Log(args[2],true);
			logManager.getUnlabeledLog().setLabeled(true);
				EvaluationManager em = new EvaluationManager(labeled, logManager.getUnlabeledLog());
				StringBuilder consoleText= new StringBuilder();
				try {
					em.execute(consoleText);
					System.out.println(consoleText);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		else{
			System.out.print("No solution found");
		}
	}

}
