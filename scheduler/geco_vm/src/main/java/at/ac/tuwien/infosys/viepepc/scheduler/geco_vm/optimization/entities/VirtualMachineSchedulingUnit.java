package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class VirtualMachineSchedulingUnit implements Cloneable {

    private final UUID uid;
    private final long virtualMachineDeploymentDuration;
    private final boolean fixed;
    private VirtualMachineInstance virtualMachineInstance;
    private Set<ContainerSchedulingUnit> scheduledContainers = new HashSet<>();

    private VirtualMachineSchedulingUnit(UUID uid, long virtualMachineDeploymentDuration, boolean fixed) {
        this.uid = uid;
        this.virtualMachineDeploymentDuration = virtualMachineDeploymentDuration;
        this.fixed = fixed;
    }

    public VirtualMachineSchedulingUnit(long virtualMachineDeploymentDuration, boolean fixed) {
        this(UUID.randomUUID(), virtualMachineDeploymentDuration, fixed);
    }

    public Interval getVmAvailableInterval() {
        List<Interval> containerIntervals = scheduledContainers.stream().map(ContainerSchedulingUnit::getCloudResourceUsage).collect(Collectors.toList());
        return IntervalMergeHelper.mergeIntervals(containerIntervals);
    }

    public Interval getCloudResourceUsageInterval() {
        Interval mergedInterval = getVmAvailableInterval();
        return mergedInterval.withStart(mergedInterval.getStart().minus(virtualMachineDeploymentDuration));
    }

    public DateTime getDeploymentStartTime() {
        return getCloudResourceUsageInterval().getStart();
    }

    public DateTime getDeploymentTimes(Interval interval) {
        return interval.getStart().minus(virtualMachineDeploymentDuration);
    }

    @Override
    public VirtualMachineSchedulingUnit clone() {
        VirtualMachineSchedulingUnit clone = new VirtualMachineSchedulingUnit(this.uid, this.virtualMachineDeploymentDuration, this.fixed);
        clone.setVirtualMachineInstance(this.virtualMachineInstance);
//        clone.setScheduledContainers(new HashSet<>(this.scheduledContainers));
        return clone;
    }

    @Override
    public String toString() {

        String containerIds = scheduledContainers.stream().map(unit -> unit.getUid().toString() + ", ").collect(Collectors.joining());

        return "VirtualMachineSchedulingUnit{" +
                "internId=" + uid.toString() +
                ", fixed=" + fixed +
                ", availableTimes=" + getVmAvailableInterval() +
                ", deploymentTimes=" + getDeploymentStartTime() +
                ", containerAmount=" + scheduledContainers.size() +
                ", containerIds=" + containerIds +
                ", virtualMachineInstance=" + virtualMachineInstance +
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


//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        VirtualMachineSchedulingUnit that = (VirtualMachineSchedulingUnit) o;
//        return virtualMachineDeploymentDuration == that.virtualMachineDeploymentDuration &&
//                fixed == that.fixed &&
//                uid.equals(that.uid);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(uid, virtualMachineDeploymentDuration, fixed);
//    }
}

