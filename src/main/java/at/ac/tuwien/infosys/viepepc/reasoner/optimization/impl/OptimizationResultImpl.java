package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Component
@Getter
@Setter
public class OptimizationResultImpl implements OptimizationResult {

    private long tauT1;
    private List<ProcessStep> processSteps = new ArrayList<>();
    private List<VirtualMachine> vms = new ArrayList<>();

    public OptimizationResultImpl() {
        tauT1 = new DateTime().plusSeconds(30).getMillis();
    }

    @Override
    public void addProcessStep(ProcessStep processStep) {
        processSteps.add(processStep);
    }

    @Override
    public void addVirtualMachine(VirtualMachine virtualMachine) {
        vms.add(virtualMachine);
    }
}
