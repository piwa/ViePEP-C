package at.ac.tuwien.infosys.viepepc.reasoner.optimization;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;

import java.util.Date;

/**
 * Created by Philipp Hoenisch on 5/5/14. modified by Gerta Sheganaku
 */
public interface ProcessInstancePlacementProblem {

    /**
     * optimizes the process instance placement problem
     * @return the result
     */
    OptimizationResult optimize(Date tau_t) throws ProblemNotSolvedException;

    void initializeParameters();

}
