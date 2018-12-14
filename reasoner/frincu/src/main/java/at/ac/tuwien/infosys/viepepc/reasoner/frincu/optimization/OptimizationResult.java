package at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;

import java.util.List;

/**
 * Created by philippwaibel on 19/10/2016.
 */
public interface OptimizationResult {
    long getTauT1();

    List<ProcessStep> getProcessSteps();

    void setTauT1(long tauT1);

    void setProcessSteps(List<ProcessStep> processSteps);

    void addProcessStep(ProcessStep processStep);

    void addVirtualMachine(VirtualMachine virtualMachine);

    List<VirtualMachine> getVms();
}
