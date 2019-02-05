package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Data;
import org.joda.time.Interval;

import java.util.Objects;
import java.util.UUID;

@Data
public class ProcessStepSchedulingUnit implements Cloneable {

    private final ProcessStep processStep;
    private final String name;
    private final UUID uid;
    private final String workflowName;
    private VirtualMachineSchedulingUnit virtualMachineSchedulingUnit;
    private Chromosome.Gene gene;

    public ProcessStepSchedulingUnit(ProcessStep processStep) {
        this.processStep = processStep;
        this.name = processStep.getName();
        this.uid = processStep.getInternId();
        this.workflowName = processStep.getWorkflowName();

    }

    public Interval getServiceAvailableTime() {
        return gene.getExecutionInterval();
    }

    @Override
    public ProcessStepSchedulingUnit clone() {
        ProcessStepSchedulingUnit clone = new ProcessStepSchedulingUnit(this.processStep);
        return clone;
    }

    @Override
    public String toString() {
        return "ProcessStepSchedulingUnit{" +
                "uid=" + uid.toString().substring(0,8) +
                ", name='" + name + '\'' +
                ", workflowName='" + workflowName + '\'' +
                ", vmSchedulingUnits=" + virtualMachineSchedulingUnit +
                ", processStep=" + processStep +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessStepSchedulingUnit that = (ProcessStepSchedulingUnit) o;
        return Objects.equals(uid, that.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }
}
