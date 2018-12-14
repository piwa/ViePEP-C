package at.ac.tuwien.infosys.viepepc.reasoner.frincu;

import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.OptimizationResult;
import org.joda.time.DateTime;

/**
 * Created by philippwaibel on 19/10/2016.
 */
public interface ProcessOptimizationResults {

    Boolean processResults(OptimizationResult optimize, DateTime tau_t);
}
