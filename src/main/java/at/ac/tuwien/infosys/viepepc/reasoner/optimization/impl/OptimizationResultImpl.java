package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Component
@Getter
public class OptimizationResultImpl implements OptimizationResult {

    private long tauT1;
    private List<ProcessStep> processSteps = new ArrayList<>();


}
