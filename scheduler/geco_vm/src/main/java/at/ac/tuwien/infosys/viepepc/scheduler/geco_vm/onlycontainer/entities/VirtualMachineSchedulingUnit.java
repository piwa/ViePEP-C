package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class VirtualMachineSchedulingUnit {

    private final UUID uid;
    private final long virtualMachineDeploymentDuration;
    private VirtualMachine virtualMachine;
    private List<ContainerSchedulingUnit> scheduledContainers = new ArrayList<>();

    public VirtualMachineSchedulingUnit(long virtualMachineDeploymentDuration) {
        this.virtualMachineDeploymentDuration = virtualMachineDeploymentDuration;
        this.uid = UUID.randomUUID();
    }

    public List<Interval> getVmAvailableTimes() {
        List<Interval> containerIntervals = scheduledContainers.stream().map(ContainerSchedulingUnit::getResourceRequirementDuration).collect(Collectors.toList());
        return mergeAndConsiderVMDeploymentDuration(containerIntervals);
    }

    public List<DateTime> getListOfDeploymentTimes() {
        List<Interval> mergedIntervals = getVmAvailableTimes();
        List<DateTime> listOfDeploymentTimes = new ArrayList<>();
        mergedIntervals.forEach(interval -> listOfDeploymentTimes.add(interval.getStart().minus(virtualMachineDeploymentDuration)));
        return listOfDeploymentTimes;
    }

    public DateTime getDeploymentTimes(Interval interval) {
        return interval.getStart().minus(virtualMachineDeploymentDuration);
    }

    @Override
    public String toString() {
        return "VirtualMachineSchedulingUnit{" +
                "virtualMachine=" + virtualMachine +
                ", availableTimes=" + getVmAvailableTimes() +
                ", listOfDeploymentTimes=" + getListOfDeploymentTimes() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualMachineSchedulingUnit)) return false;
        VirtualMachineSchedulingUnit that = (VirtualMachineSchedulingUnit) o;
        return getVirtualMachineDeploymentDuration() == that.getVirtualMachineDeploymentDuration() &&
                getUid().equals(that.getUid()) &&
                getVirtualMachine().equals(that.getVirtualMachine());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUid(), getVirtualMachineDeploymentDuration(), getVirtualMachine());
    }

    private List<Interval> mergeAndConsiderVMDeploymentDuration(List<Interval> intervals) {

        if(intervals.size() == 0 || intervals.size() == 1) {
            return intervals;
        }

        Collections.sort(intervals, new IntervalStartComparator());

        Interval first = intervals.get(0);
        long start = first.getStartMillis();
        long end = first.getEndMillis();

        List<Interval> result = new ArrayList<>();

        for(int i = 1; i < intervals.size(); i++){
            Interval current = intervals.get(i);
            if(current.getStartMillis() <= end || current.getStartMillis() - virtualMachineDeploymentDuration <= end){
                end = Math.max(current.getEndMillis(), end);
            }
            else{
                result.add(new Interval(start, end));
                start = current.getStartMillis();
                end = current.getEndMillis();
            }
        }
        result.add(new Interval(start, end));
        return result;
    }
}

