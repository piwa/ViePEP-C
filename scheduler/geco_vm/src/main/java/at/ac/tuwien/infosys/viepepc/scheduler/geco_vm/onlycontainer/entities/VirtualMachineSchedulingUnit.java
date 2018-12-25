package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import lombok.Data;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class VirtualMachineSchedulingUnit {

    private final long virtualMachineDeploymentTime;
    private VirtualMachine virtualMachine;
    private List<ContainerSchedulingUnit> scheduledContainers = new ArrayList<>();

    public List<Interval> getVmAvailableTime () {
        List<Interval> availableTimes = scheduledContainers.stream().map(ContainerSchedulingUnit::getServiceAvailableTime).collect(Collectors.toList());
        return mergeAndConsiderDeploymentTime(availableTimes);
    }

    public List<DateTime> getListOfDeploymentTimes() {
        List<Interval> mergedIntervals = getVmAvailableTime ();
        List<DateTime> listOfDeploymentTimes = new ArrayList<>();
        mergedIntervals.forEach(interval -> listOfDeploymentTimes.add(interval.getStart().minus(virtualMachineDeploymentTime)));
        return listOfDeploymentTimes;
    }

    private List<Interval> mergeAndConsiderDeploymentTime(List<Interval> intervals) {

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
            if(current.getStartMillis() <= end || current.getStartMillis() - virtualMachineDeploymentTime <= end){
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

class IntervalStartComparator implements Comparator<Interval> {
    @Override
    public int compare(Interval x, Interval y) {
        return x.getStart().compareTo(y.getStart());
    }
}
