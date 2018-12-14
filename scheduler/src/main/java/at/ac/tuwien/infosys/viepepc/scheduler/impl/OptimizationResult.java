package at.ac.tuwien.infosys.viepepc.scheduler.impl;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Component
@Getter
@Setter
public class OptimizationResult {

    @Value("${min.optimization.interval.ms}")
    private int minTauTDifference;
    private long tauT1;
    private List<ProcessStep> processSteps = new ArrayList<>();
    private List<VirtualMachine> vms = new ArrayList<>();

    public OptimizationResult() {
        tauT1 = DateTime.now().plus(minTauTDifference).getMillis();
    }

    public OptimizationResult(DateTime epocheTime) {
        tauT1 = epocheTime.plusSeconds(minTauTDifference).getMillis();
    }

    public void addProcessStep(ProcessStep processStep) {
        processSteps.add(processStep);
    }

    public void addVirtualMachine(VirtualMachine virtualMachine) {
        vms.add(virtualMachine);
    }
}
