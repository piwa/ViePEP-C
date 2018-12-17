package at.ac.tuwien.infosys.viepepc.scheduler.library;

import org.joda.time.DateTime;

/**
 * Created by philippwaibel on 19/10/2016.
 */
public interface HandleOptimizationResult {

    Boolean processResults(OptimizationResult optimize, DateTime tau_t);
}
