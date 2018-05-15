package at.ac.tuwien.infosys.viepepc.reasoner;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 19/10/2016.
 */
public interface ProcessOptimizationResults {

    Boolean processResults(OptimizationResult optimize, DateTime tau_t);
}
