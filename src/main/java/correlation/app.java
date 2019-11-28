package correlation;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.BoolVar;

public class app {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Model model = new Model("my first problem");
		BoolVar v1 = model.boolVar("c.1_e.1");
		BoolVar v2 = model.boolVar("c.2_e.1");
		BoolVar v3 = model.boolVar("c.2_e.2");
		BoolVar v4 = model.boolVar("c.1_e.2");
		
		model.arithm(v1, "!=",v2).post();
		model.arithm(v3, "!=",v4).post();
		model.arithm(v2, "!=",v3).post();
		model.arithm(v1, "!=",v4).post();
		Solver solver = model.getSolver();
		Solution solution = solver.findSolution();
		System.out.print(solution.toString());
	
	
	/**MaxNCases =2 
		NofEvents = 6
		startEventsN=[1,2]
		## C1: e1:a1,e3:b3,e5:d5 C2: e2:a2, e4:c4, e6:d6
		
		# build from the log 
		xorDict = {"e.1":{"e.2"},"e.2":{"e.1"},"e.3":{"e.4"},"e.4":{"e.3"},"e.5":{"e.6"},"e.6":{"e.5"}}
		# represent the possible successor of an event[build from the log so it will keep track to the order]. N.B: {e.1:{e.3,e.4}} that means either e.3 or e.4 or both can follw e.1 must happens after e1 based on the direct successor relation
		# Responded existence constraint 
		directSuccessorDict = {"e.1":{"e.3","e.4"},"e.2":{"e.3","e.4"},"e.3":{"e.5","e.6"},"e.4":{"e.5","e.6"}}
		**/
	
	
	}

}
