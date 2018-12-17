package at.ac.tuwien.infosys.viepepc.scheduler.library;

import org.joda.time.DateTime;

import java.util.concurrent.Future;

/**
 * Created by Philipp Hoenisch on 5/5/14. modified by Gerta Sheganaku
 */
public interface SchedulerAlgorithm {

    /**
     * optimizes the process instance placement problem
     *
     * @return the result
     */
    OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException;

    Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException;

    void initializeParameters();

}
