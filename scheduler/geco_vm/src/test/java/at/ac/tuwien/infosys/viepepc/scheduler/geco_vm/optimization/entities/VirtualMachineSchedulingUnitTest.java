package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

public class VirtualMachineSchedulingUnitTest {

//    @Test
//    public void getVmAvailableTime() {
//
//        List<ContainerSchedulingUnit> containerSchedulingUnits = getContainerSchedulingUnits(0);
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(0);
//        virtualMachineSchedulingUnit.setScheduledContainers(containerSchedulingUnits);
//
//        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.getVmAvailableIntervals();
//
//
//        assertTrue(resultingIntervals.size() == 3);
//
//        assertEquals(4, resultingIntervals.get(0).getStartMillis());
//        assertEquals(15, resultingIntervals.get(0).getEndMillis());
//
//        assertEquals(20, resultingIntervals.get(1).getStartMillis());
//        assertEquals(40, resultingIntervals.get(1).getEndMillis());
//
//        assertEquals(50, resultingIntervals.get(2).getStartMillis());
//        assertEquals(60, resultingIntervals.get(2).getEndMillis());
//
//    }
//
//    @Test
//    public void getVmAvailableTimeWithDeploymentTimes1() {
//
//        List<ContainerSchedulingUnit> containerSchedulingUnits = getContainerSchedulingUnits(0);
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(5);
//        virtualMachineSchedulingUnit.setScheduledContainers(containerSchedulingUnits);
//
//        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.getVmAvailableIntervals();
//
//        assertTrue(resultingIntervals.size() == 2);
//
//        assertEquals(4, resultingIntervals.get(0).getStartMillis());
//        assertEquals(40, resultingIntervals.get(0).getEndMillis());
//
//        assertEquals(50, resultingIntervals.get(1).getStartMillis());
//        assertEquals(60, resultingIntervals.get(1).getEndMillis());
//
//    }
//
//    @Test
//    public void getVmAvailableTimeWithDeploymentTimes2() {
//        List<ContainerSchedulingUnit> containerSchedulingUnits = getContainerSchedulingUnits(0);
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(4);
//        virtualMachineSchedulingUnit.setScheduledContainers(containerSchedulingUnits);
//
//        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.getVmAvailableIntervals();
//
//
//        assertTrue(resultingIntervals.size() == 3);
//
//        assertEquals(4, resultingIntervals.get(0).getStartMillis());
//        assertEquals(15, resultingIntervals.get(0).getEndMillis());
//
//        assertEquals(20, resultingIntervals.get(1).getStartMillis());
//        assertEquals(40, resultingIntervals.get(1).getEndMillis());
//
//        assertEquals(50, resultingIntervals.get(2).getStartMillis());
//        assertEquals(60, resultingIntervals.get(2).getEndMillis());
//    }
//
//    @Test
//    public void getVmAvailableTimeWithDeploymentTimes3() {
//
//        List<ContainerSchedulingUnit> containerSchedulingUnits = getContainerSchedulingUnits(0);
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(30);
//        virtualMachineSchedulingUnit.setScheduledContainers(containerSchedulingUnits);
//
//        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.getVmAvailableIntervals();
//
//
//        assertTrue(resultingIntervals.size() == 1);
//
//        assertEquals(4, resultingIntervals.get(0).getStartMillis());
//        assertEquals(60 ,resultingIntervals.get(0).getEndMillis());
//
//    }
//
//    @Test
//    public void testVMDeploymentTimes() {
//
//        List<ContainerSchedulingUnit> containerSchedulingUnits = getContainerSchedulingUnits(3);
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(0);
//        virtualMachineSchedulingUnit.setScheduledContainers(containerSchedulingUnits);
//
//        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.getVmAvailableIntervals();
//
//
//        assertTrue(resultingIntervals.size() == 3);
//
//        assertEquals(1, resultingIntervals.get(0).getStartMillis());
//        assertEquals(15, resultingIntervals.get(0).getEndMillis());
//
//        assertEquals(17, resultingIntervals.get(1).getStartMillis());
//        assertEquals(40, resultingIntervals.get(1).getEndMillis());
//
//        assertEquals(47, resultingIntervals.get(2).getStartMillis());
//        assertEquals(60, resultingIntervals.get(2).getEndMillis());
//
//    }
//
//    @Test
//    public void testVMDeploymentTimes2() {
//
//        List<ContainerSchedulingUnit> containerSchedulingUnits = getContainerSchedulingUnits(5);
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(0);
//        virtualMachineSchedulingUnit.setScheduledContainers(containerSchedulingUnits);
//
//        List<Interval> resultingIntervals = virtualMachineSchedulingUnit.getVmAvailableIntervals();
//
//
//        assertTrue(resultingIntervals.size() == 2);
//
//        assertEquals(-1, resultingIntervals.get(0).getStartMillis());
//        assertEquals(40, resultingIntervals.get(0).getEndMillis());
//
//        assertEquals(45, resultingIntervals.get(1).getStartMillis());
//        assertEquals(60, resultingIntervals.get(1).getEndMillis());
//
//    }
//
//
//    private List<ContainerSchedulingUnit> getContainerSchedulingUnits(long containerDeploymentDuration) {
//        List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();
//        ContainerSchedulingUnit containerSchedulingUnit1 = new ContainerSchedulingUnit(containerDeploymentDuration);
//        containerSchedulingUnit1.setServiceAvailableTime(new Interval(new DateTime(5) , new DateTime(15)));
//        containerSchedulingUnits.add(containerSchedulingUnit1);
//
//        ContainerSchedulingUnit containerSchedulingUnit2 = new ContainerSchedulingUnit(containerDeploymentDuration);
//        containerSchedulingUnit2.setServiceAvailableTime(new Interval(new DateTime(4) , new DateTime(10)));
//        containerSchedulingUnits.add(containerSchedulingUnit2);
//
//        ContainerSchedulingUnit containerSchedulingUnit3 = new ContainerSchedulingUnit(containerDeploymentDuration);
//        containerSchedulingUnit3.setServiceAvailableTime(new Interval(new DateTime(20), new DateTime(30)));
//        containerSchedulingUnits.add(containerSchedulingUnit3);
//
//        ContainerSchedulingUnit containerSchedulingUnit4 = new ContainerSchedulingUnit(containerDeploymentDuration);
//        containerSchedulingUnit4.setServiceAvailableTime(new Interval(new DateTime(30), new DateTime(40)));
//        containerSchedulingUnits.add(containerSchedulingUnit4);
//
//        ContainerSchedulingUnit containerSchedulingUnit5 = new ContainerSchedulingUnit(containerDeploymentDuration);
//        containerSchedulingUnit5.setServiceAvailableTime(new Interval(new DateTime(50), new DateTime(60)));
//        containerSchedulingUnits.add(containerSchedulingUnit5);
//
//        return containerSchedulingUnits;
//    }

}