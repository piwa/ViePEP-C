package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl;

import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Component
@Slf4j
public class ProcessInstancePlacementProblemImpl implements ProcessInstancePlacementProblem {
    @Override
    public OptimizationResult optimize(Date tau_t) {
        return null;
    }

    @Override
    public void initializeParameters() {

    }
}
