package main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

import org.chocosolver.solver.Solution;
import org.chocosolver.solver.variables.BoolVar;
import org.jbpt.petri.NetSystem;
import org.jbpt.petri.io.PNMLSerializer;

import correlation.ECmodelCreator;
import correlation.ECmodelCreatorInt;
import evaluation.EvaluationManager;
import preprocessing.Log;
import preprocessing.LogPreprocessingManager;
import relationsRM.SuccessorConfiguration;

public class appMulitpleSolutionsIntEncoding {

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub

		System.out.println("Reading input files....");
		PNMLSerializer ser = new PNMLSerializer();
		NetSystem netSystem = ser.parse(args[0]);
		Log unlabeled = new Log(args[1]);

		String[] processName = args[0].split("/");

		String outputFileName = args[3];
		String folderName = args[4];
		System.out.println(processName[processName.length - 1] + "- Start the log preprocessing phase.......");
		LogPreprocessingManager logManager = new LogPreprocessingManager(unlabeled, netSystem,
				SuccessorConfiguration.Successor_Sets);
		logManager.buildDictionaries();
		// logManager.printDictionaries();
		File fileS = new File("./resources/" + folderName + "/Stat-" + outputFileName + ".txt");
		fileS.createNewFile();
		PrintStream outStat = new PrintStream(fileS);

		File fileE = new File("./resources/" + folderName + "/Error-" + outputFileName + ".txt");
		fileE.createNewFile();
		PrintStream outErr = new PrintStream(fileE);
		System.setErr(outErr);

		System.out.println("Creating the model....");
		ECmodelCreatorInt ecModel = new ECmodelCreatorInt(logManager, processName[processName.length - 1]);
		// ecModel.printConstraints();
		// System.out.println("nVar = " + ecModel.getNvar());
		// System.out.println("nConstraints = " +
		// ecModel.getModel().getNbCstrs());
		// StringBuilder consoleError = new StringBuilder();
		int maxSolutions = 5;
		if (args[5] != null && !args[5].isEmpty())
			maxSolutions = Integer.parseInt(args[5]);
		try {
			List<Solution> solutions = ecModel.buildandRunModel(maxSolutions, outStat);// getAllSolution();
			System.out.println(processName[processName.length - 1] + "- Finsih solving and starting evaluation ");
			// List<Solution> solutions = ecModel.buildandRunModelOpt1();
			int i = 0;
			Log labeled = new Log(args[2], true);

			for (Solution s : solutions) {
				// System.out.println(i + "-");
				// System.out.println(solution.toString());

				i++;
				Log temp = new Log(logManager.getUnlabeledLog());
				ecModel.updateEventsCIds(s, temp);

				logManager.writeCorrelatedLog(temp, outputFileName + "Sol" + i);
				temp.setLabeled(true);
				EvaluationManager em = new EvaluationManager(labeled, temp);
				StringBuilder consoleText = new StringBuilder();
				// consoleText.append("Process name " +
				// processName[processName.length]);
				try {
					//
					em.execute2(consoleText);
					System.out.println(consoleText);
					File file = new File("./resources/" + folderName + "/Eval-" + outputFileName + ".txt");
					
					try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
						writer.append(consoleText);
						writer.flush();
					} catch (IOException e) {
						e.printStackTrace();

					}

				} catch (Exception e) {
					// TODO Auto-generated catch block

					e.printStackTrace();

				}

			}
		} catch (Exception e) {
			e.printStackTrace();

		}
	}

}
