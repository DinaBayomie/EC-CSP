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

public class appMulitpleSolutions {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("Reading input files....");
		PNMLSerializer ser = new PNMLSerializer();
		NetSystem netSystem = ser.parse(args[0]);
		Log unlabeled = new Log(args[1]);

		System.out.println("Start the log preprocessing phase.......");
		LogPreprocessingManager logManager = new LogPreprocessingManager(unlabeled, netSystem);
		logManager.buildDictionaries();
		// logManager.printDictionaries();

		System.out.println("Creating the model....");
		ECmodelCreator ecModel = new ECmodelCreator(logManager);

		// System.out.println("nVar = " + ecModel.getNvar());
		// System.out.println("nConstraints = " +
		// ecModel.getModel().getNbCstrs());
		List<Solution> solutions = ecModel.buildandRunModel(1);// getAllSolution();
		int i = 0;
		Log labeled = new Log(args[2], true);

		for (Solution solution : solutions) {
			System.out.println(i + "-");
			System.out.println(solution.toString());

			i++;
			ecModel.updateEventsCIds();
			logManager.writeCorrelatedLog(args[3]+"Sol"+i);
			logManager.getUnlabeledLog().setLabeled(true);
			EvaluationManager em = new EvaluationManager(labeled, logManager.getUnlabeledLog());
			StringBuilder consoleText = new StringBuilder();
			try {
				em.execute(consoleText);
				System.out.println(consoleText);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

}
