package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class VirtualMachineSchedulingUnitTest {

    @Test
    public void getVmAvailableTime() {

        List<Interval> intervalsNotOverlapping = new ArrayList<>();
        intervalsNotOverlapping.add(new Interval(new DateTime(5) , new DateTime(15)));
        intervalsNotOverlapping.add(new Interval(new DateTime(1) , new DateTime(10)));
        intervalsNotOverlapping.add(new Interval(new DateTime(20), new DateTime(30)));
        intervalsNotOverlapping.add(new Interval(new DateTime(30), new DateTime(40)));
        intervalsNotOverlapping.add(new Interval(new DateTime(50), new DateTime(60)));

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(0);
        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.mergeAndConsiderDeploymentTime(intervalsNotOverlapping);


        assertTrue(resultingIntervals.size() == 3);

        assertEquals(resultingIntervals.get(0).getStartMillis(), 1);
        assertEquals(resultingIntervals.get(0).getEndMillis(), 15);

        assertEquals(resultingIntervals.get(1).getStartMillis(), 20);
        assertEquals(resultingIntervals.get(1).getEndMillis(), 40);

        assertEquals(resultingIntervals.get(2).getStartMillis(), 50);
        assertEquals(resultingIntervals.get(2).getEndMillis(), 60);

    }

    @Test
    public void getVmAvailableTimeWithDeploymentTimes1() {

        List<Interval> intervalsNotOverlapping = new ArrayList<>();
        intervalsNotOverlapping.add(new Interval(new DateTime(5) , new DateTime(15)));
        intervalsNotOverlapping.add(new Interval(new DateTime(1) , new DateTime(10)));
        intervalsNotOverlapping.add(new Interval(new DateTime(20), new DateTime(30)));
        intervalsNotOverlapping.add(new Interval(new DateTime(30), new DateTime(40)));
        intervalsNotOverlapping.add(new Interval(new DateTime(50), new DateTime(60)));

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(5);
        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.mergeAndConsiderDeploymentTime(intervalsNotOverlapping);


        assertTrue(resultingIntervals.size() == 2);

        assertEquals(resultingIntervals.get(0).getStartMillis(), 1);
        assertEquals(resultingIntervals.get(0).getEndMillis(), 40);

        assertEquals(resultingIntervals.get(1).getStartMillis(), 50);
        assertEquals(resultingIntervals.get(1).getEndMillis(), 60);

    }

    @Test
    public void getVmAvailableTimeWithDeploymentTimes2() {
        List<Interval> intervalsNotOverlapping = new ArrayList<>();
        intervalsNotOverlapping.add(new Interval(new DateTime(5) , new DateTime(15)));
        intervalsNotOverlapping.add(new Interval(new DateTime(1) , new DateTime(10)));
        intervalsNotOverlapping.add(new Interval(new DateTime(20), new DateTime(30)));
        intervalsNotOverlapping.add(new Interval(new DateTime(30), new DateTime(40)));
        intervalsNotOverlapping.add(new Interval(new DateTime(50), new DateTime(60)));

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(4);
        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.mergeAndConsiderDeploymentTime(intervalsNotOverlapping);


        assertTrue(resultingIntervals.size() == 3);

        assertEquals(resultingIntervals.get(0).getStartMillis(), 1);
        assertEquals(resultingIntervals.get(0).getEndMillis(), 15);

        assertEquals(resultingIntervals.get(1).getStartMillis(), 20);
        assertEquals(resultingIntervals.get(1).getEndMillis(), 40);

        assertEquals(resultingIntervals.get(2).getStartMillis(), 50);
        assertEquals(resultingIntervals.get(2).getEndMillis(), 60);
    }

    @Test
    public void getVmAvailableTimeWithDeploymentTimes3() {

        List<Interval> intervalsNotOverlapping = new ArrayList<>();
        intervalsNotOverlapping.add(new Interval(new DateTime(5) , new DateTime(15)));
        intervalsNotOverlapping.add(new Interval(new DateTime(1) , new DateTime(10)));
        intervalsNotOverlapping.add(new Interval(new DateTime(20), new DateTime(30)));
        intervalsNotOverlapping.add(new Interval(new DateTime(30), new DateTime(40)));
        intervalsNotOverlapping.add(new Interval(new DateTime(50), new DateTime(60)));

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(30);
        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.mergeAndConsiderDeploymentTime(intervalsNotOverlapping);


        assertTrue(resultingIntervals.size() == 1);

        assertEquals(resultingIntervals.get(0).getStartMillis(), 1);
        assertEquals(resultingIntervals.get(0).getEndMillis(), 60);

    }
}