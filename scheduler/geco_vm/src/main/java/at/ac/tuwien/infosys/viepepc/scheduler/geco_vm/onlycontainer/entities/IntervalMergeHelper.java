package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class IntervalMergeHelper {

    public static Interval mergeAndConsiderVMDeploymentDuration(List<Interval> intervals) {

        if (intervals.size() == 0) {
            return null;
        } else if (intervals.size() == 1) {
            return intervals.get(0);
        }

        Interval mergedInterval = new Interval(intervals.get(0));
        for (Interval interval : intervals) {
            mergedInterval = mergedInterval.withStartMillis(Math.min(mergedInterval.getStartMillis(), interval.getStartMillis()));
            mergedInterval = mergedInterval.withEndMillis(Math.max(mergedInterval.getEndMillis(), interval.getEndMillis()));
        }

        return mergedInterval;
    }

}
