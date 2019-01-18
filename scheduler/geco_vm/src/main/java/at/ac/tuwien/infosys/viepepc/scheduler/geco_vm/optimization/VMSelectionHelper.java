package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VMTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VMSelectionHelper {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;

    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;

    private Random random = new Random();

    public void mergeVirtualMachineSchedulingUnits(Chromosome chromosome) {

        Map<VirtualMachineInstance, VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitMap = new HashMap<>();

        for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
            ContainerSchedulingUnit containerSchedulingUnit = gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit();
            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = containerSchedulingUnit.getScheduledOnVm();

            if (virtualMachineSchedulingUnitMap.containsKey(virtualMachineSchedulingUnit.getVirtualMachineInstance()) && virtualMachineSchedulingUnitMap.get(virtualMachineSchedulingUnit.getVirtualMachineInstance()) != virtualMachineSchedulingUnit) {

                VirtualMachineSchedulingUnit otherSchedulingUnit = virtualMachineSchedulingUnitMap.get(virtualMachineSchedulingUnit.getVirtualMachineInstance());
                otherSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                virtualMachineSchedulingUnit.getScheduledContainers().remove(containerSchedulingUnit);

                containerSchedulingUnit.setScheduledOnVm(otherSchedulingUnit);
            } else {
                virtualMachineSchedulingUnitMap.put(virtualMachineSchedulingUnit.getVirtualMachineInstance(), virtualMachineSchedulingUnit);
            }
        }

        checkVmSizeAndSolveSpaceIssues(chromosome);

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_mergeVirtualMachineSchedulingUnit_3");
    }


    public void checkVmSizeAndSolveSpaceIssues(Chromosome chromosome) {
        List<Chromosome.Gene> genes = chromosome.getFlattenChromosome();
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = genes.stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());

        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {

            List<ContainerSchedulingUnit> holders = createIntervalContainerSchedulingList(virtualMachineSchedulingUnit, new ArrayList<>());


            boolean fitsOnVM = checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit.getVirtualMachineInstance(), holders);
            if (!fitsOnVM) {
                if (virtualMachineSchedulingUnit.isFixed()) {
//                        log.debug("distributed containers 1");
                    distributeContainers(virtualMachineSchedulingUnit, holders);
                    if (chromosome != null) {
                        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_checkVmSizeAndSolveSpaceIssues");
                    }
                } else {
                    try {
//                            log.debug("resize vm");
                        resizeVM(virtualMachineSchedulingUnit, holders);
                    } catch (VMTypeNotFoundException e) {
//                            log.debug("distributed containers 2");
                        distributeContainers(virtualMachineSchedulingUnit, holders);
                    }

                }
            }
        }
    }


    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnit(ContainerSchedulingUnit containerSchedulingUnit) {
        List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();
        containerSchedulingUnits.add(containerSchedulingUnit);
        return getVirtualMachineSchedulingUnit(containerSchedulingUnits);
    }


    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnit(List<ContainerSchedulingUnit> containerSchedulingUnits) {
        return getVirtualMachineSchedulingUnit(new HashSet<>(), containerSchedulingUnits);
    }

    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnit(Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits, List<ContainerSchedulingUnit> containerSchedulingUnits) {
        boolean vmFound = false;
        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
        do {
            virtualMachineSchedulingUnit = createVMSchedulingUnit(virtualMachineSchedulingUnits, new HashSet<>(containerSchedulingUnits), this.random);

            vmFound = false;

            if (virtualMachineSchedulingUnit.getScheduledContainers().containsAll(containerSchedulingUnits)) {
                vmFound = true;
            } else if (checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, containerSchedulingUnits)) {
                vmFound = true;
            }

        } while (!vmFound);

        return virtualMachineSchedulingUnit;
    }


    public void resizeVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) throws VMTypeNotFoundException {

        Set<Container> containerOnVm = new HashSet<>();
        containerOnVm.addAll(containerSchedulingUnits.stream().map(ContainerSchedulingUnit::getContainer).collect(Collectors.toSet()));
        containerOnVm.addAll(virtualMachineSchedulingUnit.getScheduledContainers().stream().map(ContainerSchedulingUnit::getContainer).collect(Collectors.toSet()));

        double scheduledCPUUsage = containerOnVm.stream().mapToDouble(c -> c.getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = containerOnVm.stream().mapToDouble(c -> c.getContainerConfiguration().getRam()).sum();

        List<VMType> allVMTypes = cacheVirtualMachineService.getVMTypes();
//        allVMTypes.sort(Comparator.comparing(VMType::getCores).thenComparing(VMType::getRamPoints));
        allVMTypes.sort(Comparator.comparing(VMType::getCores));

        for (VMType vmType : allVMTypes) {
            if (vmType.getCpuPoints() >= scheduledCPUUsage && vmType.getRamPoints() >= scheduledRAMUsage) {
                virtualMachineSchedulingUnit.getVirtualMachineInstance().setVmType(vmType);
                return;
            }
        }
        throw new VMTypeNotFoundException("Could not find big enough VMType");
    }


    public void distributeContainers(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> tempSchedulingUnits) {

        Set<ContainerSchedulingUnit> containerSchedulingUnits = new HashSet<>(tempSchedulingUnits);
        containerSchedulingUnits.addAll(virtualMachineSchedulingUnit.getScheduledContainers());

        List<VirtualMachineSchedulingUnit> usedVirtualMachineSchedulingUnits = new ArrayList<>();
        List<ContainerSchedulingUnit> fixed = containerSchedulingUnits.stream().filter(ContainerSchedulingUnit::isFixed).collect(Collectors.toList());
        List<ContainerSchedulingUnit> notFixed = containerSchedulingUnits.stream().filter(unit -> !unit.isFixed()).collect(Collectors.toList());

        virtualMachineSchedulingUnit.getScheduledContainers().removeAll(containerSchedulingUnits);
        if( virtualMachineSchedulingUnit.getScheduledContainers().size() != 0) {
            log.error("aha");
        }
        containerSchedulingUnits.forEach(unit -> unit.setScheduledOnVm(null));

        if (!fixed.isEmpty()) {
            virtualMachineSchedulingUnit.getScheduledContainers().addAll(fixed);
            fixed.forEach(unit -> unit.setScheduledOnVm(virtualMachineSchedulingUnit));
        }


        if (!checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, new ArrayList<>())) {
            log.error("problem");
        }

        VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = virtualMachineSchedulingUnit;
        usedVirtualMachineSchedulingUnits.add(newVirtualMachineSchedulingUnit);
        while (!notFixed.isEmpty()) {

            fillVirtualMachine(notFixed, newVirtualMachineSchedulingUnit);

            if (!notFixed.isEmpty()) {
                do {
                    newVirtualMachineSchedulingUnit = getVirtualMachineSchedulingUnit(notFixed.get(0));
                } while (usedVirtualMachineSchedulingUnits.contains(newVirtualMachineSchedulingUnit) || newVirtualMachineSchedulingUnit.isFixed());
                usedVirtualMachineSchedulingUnits.add(newVirtualMachineSchedulingUnit);
            }
        }

    }

    private void fillVirtualMachine(List<ContainerSchedulingUnit> notFixed, VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit) {
        ContainerSchedulingUnit lastContainerSchedulingUnit;
        boolean enoughSpace = true;
        do {
            List<ContainerSchedulingUnit> tempList = new ArrayList<>();
            tempList.add(notFixed.get(0));
            enoughSpace = checkEnoughResourcesLeftOnVM(newVirtualMachineSchedulingUnit, tempList);
            if (enoughSpace) {
                lastContainerSchedulingUnit = notFixed.remove(0);
                newVirtualMachineSchedulingUnit.getScheduledContainers().add(lastContainerSchedulingUnit);
                lastContainerSchedulingUnit.setScheduledOnVm(newVirtualMachineSchedulingUnit);
            }
        } while (enoughSpace && !notFixed.isEmpty());

    }

    public boolean checkEnoughResourcesLeftOnVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) {
        return checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, new ArrayList<>());
    }

    public boolean checkEnoughResourcesLeftOnVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) {
        List<ContainerSchedulingUnit> maxOverlappingContainers = createIntervalContainerSchedulingList(virtualMachineSchedulingUnit, containerSchedulingUnits);
        return checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit.getVirtualMachineInstance(), maxOverlappingContainers);
    }

    public boolean checkEnoughResourcesLeftOnVMForOneInterval(VirtualMachineInstance vm, List<ContainerSchedulingUnit> containerSchedulingUnits) {

        double scheduledCPUUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getRam()).sum();

        if (vm.getVmType().getCpuPoints() < scheduledCPUUsage || vm.getVmType().getRamPoints() < scheduledRAMUsage) {
            return false;
        }
        return true;
    }

    public VirtualMachineSchedulingUnit createVMSchedulingUnit(Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines, Set<ContainerSchedulingUnit> containerSchedulingUnits, Random random) {
        VirtualMachineInstance randomVM;

        List<VirtualMachineInstance> noSchedulingUnitAvailable = cacheVirtualMachineService.getScheduledAndDeployingAndDeployedVMInstances();
        List<VirtualMachineInstance> availableVMs = new ArrayList<>(noSchedulingUnitAvailable);
        availableVMs.addAll(alreadyScheduledVirtualMachines.stream().map(VirtualMachineSchedulingUnit::getVirtualMachineInstance).collect(Collectors.toList()));

//        int fromAvailableVM = random.nextInt(5);
//        if (fromAvailableVM < 3 && availableVMs.size() > 0) {
        boolean fromAvailableVM = random.nextBoolean();
        if (fromAvailableVM && availableVMs.size() > 0) {
            int randomPosition = random.nextInt(availableVMs.size());
            randomVM = availableVMs.get(randomPosition);

            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
            for (VirtualMachineSchedulingUnit alreadyScheduledVirtualMachine : alreadyScheduledVirtualMachines) {
                if (alreadyScheduledVirtualMachine.getVirtualMachineInstance().equals(randomVM)) {
                    virtualMachineSchedulingUnit = alreadyScheduledVirtualMachine;
                    break;
                }
            }
            if (virtualMachineSchedulingUnit == null) {
                virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(virtualMachineDeploymentTime, true);
                virtualMachineSchedulingUnit.setVirtualMachineInstance(randomVM);
            }
            return virtualMachineSchedulingUnit;

        } else {

            List<VMType> vmTypes = new ArrayList<>(cacheVirtualMachineService.getVMTypes());

            VMType vmType = null;
//            try {
//                vmType = getFittingVMType(vmTypes, containerSchedulingUnits);
//            } catch (VMTypeNotFoundException e) {
            int randomPosition = random.nextInt(vmTypes.size());
            vmType = vmTypes.get(randomPosition);
//            }

            randomVM = new VirtualMachineInstance(vmType);

            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(virtualMachineDeploymentTime, false);
            virtualMachineSchedulingUnit.setVirtualMachineInstance(randomVM);

            return virtualMachineSchedulingUnit;
        }
    }

    private VMType getFittingVMType(List<VMType> allVMTypes, Set<ContainerSchedulingUnit> containerSchedulingUnits) throws VMTypeNotFoundException {

        double scheduledCPUUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getRam()).sum();

        allVMTypes.sort(Comparator.comparing(VMType::getCores).thenComparing(VMType::getRamPoints));

        for (VMType vmType : allVMTypes) {
            if (vmType.getCpuPoints() >= scheduledCPUUsage && vmType.getRamPoints() >= scheduledRAMUsage) {
                return vmType;
            }
        }

        throw new VMTypeNotFoundException("Could not find big enough VMType");
    }

    private List<ContainerSchedulingUnit> createIntervalContainerSchedulingList(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) {
        Set<ContainerSchedulingUnit> tempSchedulingUnits = new HashSet<>(virtualMachineSchedulingUnit.getScheduledContainers());
        tempSchedulingUnits.addAll(containerSchedulingUnits);
        return createIntervalContainerSchedulingList(tempSchedulingUnits);
    }


    private Set<Interval> getOverlap(List<Interval> intervals) {
        if (intervals == null) {
            throw new NullPointerException("Input list cannot be null.");
        }

        final Set<Interval> overlaps = new HashSet<>();

        for (int i = 0; i < intervals.size() - 1; i++) {
            final Interval lowerInterval = intervals.get(i);

            for (int j = i + 1; j < intervals.size(); j++) {
                final Interval upperInterval = intervals.get(j);

                if (upperInterval.getStartMillis() < lowerInterval.getEndMillis()) {
                    overlaps.add(new Interval(upperInterval.getStartMillis(), Math.min(lowerInterval.getEndMillis(), upperInterval.getEndMillis())));
                }
            }
        }

        return overlaps;
    }

    private List<ContainerSchedulingUnit> createIntervalContainerSchedulingList(Set<ContainerSchedulingUnit> tempSchedulingUnits) {


        List<ContainerEvent> deployEvents = new ArrayList<>();
        List<ContainerEvent> undeployEvents = new ArrayList<>();

        tempSchedulingUnits.forEach(unit -> {
            Interval cloudResource = unit.getCloudResourceUsage();
            deployEvents.add(new ContainerEvent(cloudResource.getStart(), unit));
            undeployEvents.add(new ContainerEvent(cloudResource.getEnd(), unit));
        });

        deployEvents.sort(Comparator.comparing(ContainerEvent::getTime));
        undeployEvents.sort(Comparator.comparing(ContainerEvent::getTime));

        int i = 1;
        int j = 0;

        List<ContainerSchedulingUnit> maxContainerList = new ArrayList<>();
        List<ContainerSchedulingUnit> containerList = new ArrayList<>();
        DateTime overlapTime = deployEvents.get(0).getTime();
        containerList.add(deployEvents.get(0).getContainerSchedulingUnit());
        maxContainerList.add(deployEvents.get(0).getContainerSchedulingUnit());

        while (i < tempSchedulingUnits.size() && j < tempSchedulingUnits.size()) {
            // If next event in sorted order is arrival,
            // increment count of container
            if (deployEvents.get(i).getTime().getMillis() <= undeployEvents.get(j).getTime().getMillis()) {
                containerList.add(deployEvents.get(i).getContainerSchedulingUnit());
                if (containerList.size() > maxContainerList.size()) {
                    maxContainerList.clear();
                    maxContainerList.addAll(containerList);
                    overlapTime = deployEvents.get(i).getTime();
                }
                i++; //increment index of arrival array
            } else // If event is exit, decrement count
            {
                containerList.remove(undeployEvents.get(j).getContainerSchedulingUnit());
                j++;
            }
        }

        return maxContainerList;

//        List<ContainerSchedulingUnit> schedulingUnitList = new ArrayList<>(tempSchedulingUnits);
//        schedulingUnitList.sort(Comparator.comparing(containerSchedulingUnit -> containerSchedulingUnit.getCloudResourceUsage().getStartMillis()));

//        List<Interval> intervals = schedulingUnitList.stream().map(containerSchedulingUnit -> containerSchedulingUnit.getCloudResourceUsage()).collect(Collectors.toList());
//        Set<Interval> overlappings = getOverlap(intervals);
//
//        List<IntervalContainerSchedulingUnitHolder> holders = new ArrayList<>();
//        overlappings.forEach(interval -> holders.add(new IntervalContainerSchedulingUnitHolder(interval)));
//
//        IntervalContainerSchedulingUnitHolder biggestHolder = null;
//        if(overlappings.size() == 0) {
//            biggestHolder = new IntervalContainerSchedulingUnitHolder(schedulingUnitList.get(0).getCloudResourceUsage(), schedulingUnitList.get(0));
//        }
//        else {
//            for (ContainerSchedulingUnit scheduledContainer : tempSchedulingUnits) {
//                Interval cloudResource = scheduledContainer.getCloudResourceUsage();
//                for (IntervalContainerSchedulingUnitHolder holder : holders) {
//                    if (holder.getInterval().overlaps(cloudResource)) {
//                        holder.getContainerSchedulingUnits().add(scheduledContainer);
//                    }
//                }
//            }
//
//            holders.removeIf(holder -> holder.getContainerSchedulingUnits().size() == 0);
//            biggestHolder = holders.stream().max(Comparator.comparing(holder -> holder.getContainerSchedulingUnits().size())).get();
//        }
//        List<IntervalContainerSchedulingUnitHolder> returnList = new ArrayList<>();
//        returnList.add(biggestHolder);
//        return returnList;


//
//        List<ContainerSchedulingUnit> schedulingUnitList = new ArrayList<>(tempSchedulingUnits);
//        schedulingUnitList.sort(Comparator.comparing(containerSchedulingUnit -> containerSchedulingUnit.getCloudResourceUsage().getStartMillis()));
//
//        long startTimeMillis = schedulingUnitList.get(0).getCloudResourceUsage().getStartMillis();
//        long endTimeMillis = schedulingUnitList.get(0).getCloudResourceUsage().getEndMillis();
//
//        Map<ContainerSchedulingUnit, Interval> cloudResourceUsageMap = new HashMap<>();
//
//        for (ContainerSchedulingUnit scheduledContainer : schedulingUnitList) {
//            cloudResourceUsageMap.put(scheduledContainer, scheduledContainer.getCloudResourceUsage());
//            startTimeMillis = Math.min(startTimeMillis, cloudResourceUsageMap.get(scheduledContainer).getStartMillis());
//            endTimeMillis = Math.max(endTimeMillis, cloudResourceUsageMap.get(scheduledContainer).getEndMillis());
//        }
//
//        long currentStartMillis = startTimeMillis;
//        long currentEndMillis = startTimeMillis;
//        List<IntervalContainerSchedulingUnitHolder> holders = new ArrayList<>();
//        while(currentEndMillis < endTimeMillis) {
//            currentEndMillis = currentStartMillis + 1000;
//            holders.add(new IntervalContainerSchedulingUnitHolder(new Interval(currentStartMillis, currentEndMillis)));
//            currentStartMillis = currentStartMillis + 1000;
//        }
//
//        for (ContainerSchedulingUnit scheduledContainer : tempSchedulingUnits) {
//            for (IntervalContainerSchedulingUnitHolder holder : holders) {
//                if (holder.getInterval().overlaps(cloudResourceUsageMap.get(scheduledContainer))) {
//                    holder.getContainerSchedulingUnits().add(scheduledContainer);
//                }
//            }
//        }
//
//        holders.removeIf(holder -> holder.getContainerSchedulingUnits().size() == 0);
//        Optional<IntervalContainerSchedulingUnitHolder> biggestHolder = holders.stream().max(Comparator.comparing(holder -> holder.getContainerSchedulingUnits().size()));
//
//        List<IntervalContainerSchedulingUnitHolder> returnList = new ArrayList<>();
//        returnList.add(biggestHolder.get());
//        return returnList;

//
//        List<IntervalContainerSchedulingUnitHolder> holders = new ArrayList<>();
//
//        for (ContainerSchedulingUnit scheduledContainer : tempSchedulingUnits) {
//            Interval resourceRequirementInterval = scheduledContainer.getCloudResourceUsage();
//
//            boolean found = false;
//            for (IntervalContainerSchedulingUnitHolder holder : holders) {
//                if (holder.getInterval().overlaps(resourceRequirementInterval)) {
//                    long startTime = Math.min(resourceRequirementInterval.getStartMillis(), holder.getInterval().getStartMillis());
//                    long endTime = Math.max(resourceRequirementInterval.getEndMillis(), holder.getInterval().getEndMillis());
//                    holder.setInterval(new Interval(startTime, endTime));
//                    holder.getContainerSchedulingUnits().add(scheduledContainer);
//                    found = true;
//                }
//            }
//            if (!found) {
//                IntervalContainerSchedulingUnitHolder holder = new IntervalContainerSchedulingUnitHolder(scheduledContainer.getCloudResourceUsage(), scheduledContainer);
//                holders.add(holder);
//            }
//        }
//        return holders;
    }


    @Data
    private class IntervalContainerSchedulingUnitHolder {
        private Interval interval;
        private List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();

        public IntervalContainerSchedulingUnitHolder(Interval interval) {
            this.interval = interval;
        }

        public IntervalContainerSchedulingUnitHolder(Interval interval, ContainerSchedulingUnit scheduledContainer) {
            this.interval = interval;
            containerSchedulingUnits.add(scheduledContainer);
        }

        @Override
        public String toString() {

            String containerIds = containerSchedulingUnits.stream().map(unit -> unit.getUid().toString() + ", ").collect(Collectors.joining());

            return "IntervalContainerSchedulingUnitHolder{" +
                    "interval=" + interval.toString() +
                    ", containerIds=" + containerIds +
                    '}';
        }
    }

    @Data
    private class ContainerEvent {
        private final DateTime time;
        private final ContainerSchedulingUnit containerSchedulingUnit;


    }
}
