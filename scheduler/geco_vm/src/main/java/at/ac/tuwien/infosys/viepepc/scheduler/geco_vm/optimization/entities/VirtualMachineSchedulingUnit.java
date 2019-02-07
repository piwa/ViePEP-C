package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class VirtualMachineSchedulingUnit implements Cloneable {

    private final UUID uid;
    private final long virtualMachineDeploymentDuration;
    private final long containerDeploymentDuration;
    private final boolean fixed;
    private final DateTime fixedStartTime;
    private final VirtualMachineInstance virtualMachineInstance;
    private VMType vmType;
    private Set<ProcessStepSchedulingUnit> processStepSchedulingUnits = new HashSet<>();
    private Set<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits = new HashSet<>();
    private String origin = "unknown";

    private VirtualMachineSchedulingUnit(UUID uid, boolean fixed, DateTime fixedStartTime, long virtualMachineDeploymentDuration, long containerDeploymentDuration, VirtualMachineInstance virtualMachineInstance) {
        this.uid = uid;
        this.virtualMachineDeploymentDuration = virtualMachineDeploymentDuration;
        this.containerDeploymentDuration = containerDeploymentDuration;
        this.fixed = fixed;
        this.virtualMachineInstance = virtualMachineInstance;
        this.vmType = virtualMachineInstance.getVmType();
        this.fixedStartTime = fixedStartTime;
    }

    public VirtualMachineSchedulingUnit(boolean fixed, DateTime fixedStartTime, long virtualMachineDeploymentDuration, long containerDeploymentDuration, VirtualMachineInstance virtualMachineInstance) {
        this(UUID.randomUUID(), fixed, fixedStartTime, virtualMachineDeploymentDuration, containerDeploymentDuration, virtualMachineInstance);
    }

    public VirtualMachineSchedulingUnit(boolean fixed, DateTime fixedStartTime, long virtualMachineDeploymentDuration, long containerDeploymentDuration, VirtualMachineInstance virtualMachineInstance, String origin) {
        this(UUID.randomUUID(), fixed, fixedStartTime, virtualMachineDeploymentDuration, containerDeploymentDuration, virtualMachineInstance);
        this.origin = origin;
    }

    public Interval getVmAvailableInterval() {
        List<Interval> intervals = null;
        if (serviceTypeSchedulingUnits.isEmpty()) {
            intervals = processStepSchedulingUnits.stream().map(ProcessStepSchedulingUnit::getServiceAvailableTime).collect(Collectors.toList());
        } else {
            intervals = serviceTypeSchedulingUnits.stream().map(ServiceTypeSchedulingUnit::getServiceAvailableTime).collect(Collectors.toList());
        }
        List<Interval> processStepIntervals = intervals.stream().map(interval -> interval.withStart(interval.getStart().minus(containerDeploymentDuration))).collect(Collectors.toList());

        Interval returnValue = IntervalMergeHelper.mergeIntervals(processStepIntervals);
        if(isFixed() && fixedStartTime != null) {
            try {
                returnValue = returnValue.withStart(fixedStartTime.plus(virtualMachineDeploymentDuration));
            } catch (IllegalArgumentException ex) {
                returnValue = new Interval(fixedStartTime.plus(virtualMachineDeploymentDuration), fixedStartTime.plus(virtualMachineDeploymentDuration).plusMinutes(1));
            }
        }

        return returnValue;
    }

    public Interval getCloudResourceUsageInterval() {
        Interval mergedInterval = getVmAvailableInterval();
        return mergedInterval.withStart(mergedInterval.getStart().minus(virtualMachineDeploymentDuration));
    }

    public DateTime getDeploymentStartTime() {
        return getCloudResourceUsageInterval().getStart();
    }

    @Override
    public VirtualMachineSchedulingUnit clone() {
        VirtualMachineSchedulingUnit clone = new VirtualMachineSchedulingUnit(this.uid, this.fixed, this.fixedStartTime, this.virtualMachineDeploymentDuration, this.containerDeploymentDuration, this.virtualMachineInstance);
        clone.setVmType(this.vmType);
        return clone;
    }

    @Override
    public String toString() {

        String processStepIds = processStepSchedulingUnits.stream().map(unit -> unit.getUid().toString().substring(0, 8) + ", ").collect(Collectors.joining());

        return "VirtualMachineSchedulingUnit{" +
                "internId=" + uid.toString().substring(0, 8) +
                ", fixed=" + fixed +
                ", availableTimes=" + getVmAvailableInterval() +
                ", deploymentTimes=" + getDeploymentStartTime() +
                ", processStepAmount=" + processStepSchedulingUnits.size() +
                ", processStepIds=" + processStepIds +
                ", virtualMachineInstance=" + virtualMachineInstance +
                ", origin=" + origin +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VirtualMachineSchedulingUnit that = (VirtualMachineSchedulingUnit) o;
        return uid.equals(that.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }

}

