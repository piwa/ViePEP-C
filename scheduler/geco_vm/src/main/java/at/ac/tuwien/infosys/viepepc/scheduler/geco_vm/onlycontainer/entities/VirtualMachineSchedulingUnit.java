package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

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
public class VirtualMachineSchedulingUnit {

    private final UUID uid;
    private final long virtualMachineDeploymentDuration;
    private VirtualMachineInstance virtualMachineInstance;
    private List<ContainerSchedulingUnit> scheduledContainers = new ArrayList<>();

    public VirtualMachineSchedulingUnit(long virtualMachineDeploymentDuration) {
        this.virtualMachineDeploymentDuration = virtualMachineDeploymentDuration;
        this.uid = UUID.randomUUID();
    }

    public Interval getVmAvailableInterval() {
        List<Interval> containerIntervals = scheduledContainers.stream().map(ContainerSchedulingUnit::getCloudResourceUsage).collect(Collectors.toList());
        return IntervalMergeHelper.mergeAndConsiderVMDeploymentDuration(containerIntervals);
    }

    public Interval getCloudResourceUsageInterval() {
        Interval mergedInterval = getVmAvailableInterval();
        return mergedInterval.withStart(mergedInterval.getStart().minus(virtualMachineDeploymentDuration));
    }

    public DateTime getListOfDeploymentStartTime() {
        return getCloudResourceUsageInterval().getStart();
    }

    public DateTime getDeploymentTimes(Interval interval) {
        return interval.getStart().minus(virtualMachineDeploymentDuration);
    }

    @Override
    public String toString() {
        return "VirtualMachineSchedulingUnit{" +
                "virtualMachineInstance=" + virtualMachineInstance +
                ", availableTimes=" + getVmAvailableInterval() +
                ", listOfDeploymentTimes=" + getListOfDeploymentStartTime() +
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

