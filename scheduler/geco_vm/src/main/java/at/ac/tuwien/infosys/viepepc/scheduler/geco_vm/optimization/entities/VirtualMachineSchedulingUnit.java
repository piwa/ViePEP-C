package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
public class VirtualMachineSchedulingUnit implements Cloneable {

    private final UUID uid;
    private final long virtualMachineDeploymentDuration;
    private VirtualMachineInstance virtualMachineInstance;
    private List<ContainerSchedulingUnit> scheduledContainers = new ArrayList<>();

    private VirtualMachineSchedulingUnit(UUID uid, long virtualMachineDeploymentDuration) {
        this.uid = uid;
        this.virtualMachineDeploymentDuration = virtualMachineDeploymentDuration;
    }

    public VirtualMachineSchedulingUnit(long virtualMachineDeploymentDuration) {
        this(UUID.randomUUID(), virtualMachineDeploymentDuration);
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
        VirtualMachineSchedulingUnit clone = new VirtualMachineSchedulingUnit(this.uid, this.virtualMachineDeploymentDuration);
        clone.setVirtualMachineInstance(this.virtualMachineInstance);
        clone.setScheduledContainers(new ArrayList<>(this.scheduledContainers));
        return clone;
    }

    @Override
    public String toString() {
        return "VirtualMachineSchedulingUnit{" +
                "virtualMachineInstance=" + virtualMachineInstance +
                ", availableTimes=" + getVmAvailableInterval() +
                ", deploymentTimes=" + getDeploymentStartTime() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualMachineSchedulingUnit)) return false;
        VirtualMachineSchedulingUnit that = (VirtualMachineSchedulingUnit) o;
        return getVirtualMachineDeploymentDuration() == that.getVirtualMachineDeploymentDuration() &&
                getUid().equals(that.getUid()) &&
                this.getVirtualMachineInstance().equals(that.getVirtualMachineInstance());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUid(), getVirtualMachineDeploymentDuration(), this.getVirtualMachineInstance());
    }

}

