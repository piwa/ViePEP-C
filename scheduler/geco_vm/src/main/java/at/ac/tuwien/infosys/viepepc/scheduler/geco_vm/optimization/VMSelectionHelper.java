package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VMTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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

//        log.debug("mergeVirtualMachineSchedulingUnits 1: chromosome=" + chromosome.toString());
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_mergeVirtualMachineSchedulingUnit1_1");

        Map<VirtualMachineInstance, VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitMap = new HashMap<>();
//        log.debug("mergeVirtualMachineSchedulingUnits 2: chromosome=" + chromosome.toString());
        for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
            ContainerSchedulingUnit containerSchedulingUnit = gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit();
            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = containerSchedulingUnit.getScheduledOnVm();

            if (virtualMachineSchedulingUnitMap.containsKey(virtualMachineSchedulingUnit.getVirtualMachineInstance())
//                    && !containerSchedulingUnit.isFixed()
//                  && !gene.isFixed()
                    && virtualMachineSchedulingUnitMap.get(virtualMachineSchedulingUnit.getVirtualMachineInstance()) != virtualMachineSchedulingUnit
            ) {

                VirtualMachineSchedulingUnit otherSchedulingUnit = virtualMachineSchedulingUnitMap.get(virtualMachineSchedulingUnit.getVirtualMachineInstance());

                otherSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);

                virtualMachineSchedulingUnit.getScheduledContainers().remove(containerSchedulingUnit);

                containerSchedulingUnit.setScheduledOnVm(otherSchedulingUnit);
            } else {
                virtualMachineSchedulingUnitMap.put(virtualMachineSchedulingUnit.getVirtualMachineInstance(), virtualMachineSchedulingUnit);
            }
        }

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_mergeVirtualMachineSchedulingUnit_2");

        checkVmSizeAndSolveSpaceIssues(chromosome);

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_mergeVirtualMachineSchedulingUnit_3");
    }

    public void checkVmSizeAndSolveSpaceIssues(Chromosome chromosome) {
        List<Chromosome.Gene> genes = chromosome.getFlattenChromosome();
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = genes.stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());

        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {

            List<IntervalContainerSchedulingUnitHolder> holders = createIntervalContainerSchedulingList(virtualMachineSchedulingUnit);
            for (IntervalContainerSchedulingUnitHolder holder : holders) {

                boolean fitsOnVM = checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit, holder.getContainerSchedulingUnits());
                if (!fitsOnVM) {
                    if (virtualMachineSchedulingUnit.isFixed()) {
                        log.debug("distributed containers 1");
                        log.debug("chromosome=" + chromosome.toString());
                        if(chromosome != null) {
                            SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_checkVmSizeAndSolveSpaceIssues_1");
                        }
                        distributeContainers(virtualMachineSchedulingUnit, holder.getContainerSchedulingUnits());
                        if(chromosome != null) {
                            SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_checkVmSizeAndSolveSpaceIssues_2");
                        }
                    } else {
                        try {
                            log.debug("resize vm");
                            resizeVM(virtualMachineSchedulingUnit, holder.getContainerSchedulingUnits());
                        } catch (VMTypeNotFoundException e) {
                            log.debug("distributed containers 2");
                            distributeContainers(virtualMachineSchedulingUnit, holder.getContainerSchedulingUnits());
                        }
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
        boolean vmFound = false;
        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
        do {
            virtualMachineSchedulingUnit = createVMSchedulingUnit(new HashSet<>(), new HashSet<>(containerSchedulingUnits));

            vmFound = false;

            if (virtualMachineSchedulingUnit.getScheduledContainers().containsAll(containerSchedulingUnits)) {
                vmFound = true;
            } else if (checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, containerSchedulingUnits)) {
                vmFound = true;
            }

        } while (!vmFound);

        return virtualMachineSchedulingUnit;
    }

    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnit(Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits, List<ContainerSchedulingUnit> containerSchedulingUnits) {
        boolean vmFound = false;
        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
        do {
            virtualMachineSchedulingUnit = createVMSchedulingUnit(virtualMachineSchedulingUnits, new HashSet<>(containerSchedulingUnits));

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
        VirtualMachineInstance vm = virtualMachineSchedulingUnit.getVirtualMachineInstance();
        List<Container> containerOnVm = new ArrayList<>();
        containerOnVm.addAll(containerSchedulingUnits.stream().map(ContainerSchedulingUnit::getContainer).collect(Collectors.toList()));
        containerOnVm.addAll(virtualMachineSchedulingUnit.getScheduledContainers().stream().map(ContainerSchedulingUnit::getContainer).collect(Collectors.toList()));

        double scheduledCPUUsage = containerOnVm.stream().mapToDouble(c -> c.getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = containerOnVm.stream().mapToDouble(c -> c.getContainerConfiguration().getRam()).sum();

        List<VMType> allVMTypes = cacheVirtualMachineService.getVMTypes();
        allVMTypes.sort(Comparator.comparing(VMType::getCores).thenComparing(VMType::getRamPoints));

        for (VMType vmType : allVMTypes) {
            if (vmType.getCpuPoints() >= scheduledCPUUsage && vmType.getRamPoints() >= scheduledRAMUsage) {
                virtualMachineSchedulingUnit.getVirtualMachineInstance().setVmType(vmType);
                return;
            }
        }
        throw new VMTypeNotFoundException("Could not find big enough VMType");
    }

    public void distributeContainers(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) {

//        log.debug("distributeContainers 1: virtualMachineSchedulingUnit="+virtualMachineSchedulingUnit.toString());
//        for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits) {
//            log.debug("distributeContainers 1: containerSchedulingUnit="+containerSchedulingUnit.toString());
//        }

        List<VirtualMachineSchedulingUnit> usedVirtualMachineSchedulingUnits = new ArrayList<>();
        List<ContainerSchedulingUnit> fixed = containerSchedulingUnits.stream().filter(ContainerSchedulingUnit::isFixed).collect(Collectors.toList());
        List<ContainerSchedulingUnit> notFixed = containerSchedulingUnits.stream().filter(unit -> !unit.isFixed()).collect(Collectors.toList());

        virtualMachineSchedulingUnit.getScheduledContainers().removeAll(containerSchedulingUnits);
        containerSchedulingUnits.forEach(unit -> unit.setScheduledOnVm(null));

        if(!fixed.isEmpty()) {
            virtualMachineSchedulingUnit.getScheduledContainers().addAll(fixed);
            fixed.forEach(unit -> unit.setScheduledOnVm(virtualMachineSchedulingUnit));
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

//        for (VirtualMachineSchedulingUnit usedVirtualMachineSchedulingUnit : usedVirtualMachineSchedulingUnits) {
//            log.debug("distributeContainers 2: usedVirtualMachineSchedulingUnit="+usedVirtualMachineSchedulingUnit.toString());
//        }
//        for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits) {
//            log.debug("distributeContainers 2: containerSchedulingUnit="+containerSchedulingUnit.toString());
//        }

    }

    private void fillVirtualMachine(List<ContainerSchedulingUnit> notFixed, VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit) {
        ContainerSchedulingUnit lastContainerSchedulingUnit;
        boolean enoughSpace = true;
        do {
            List<ContainerSchedulingUnit> tempList = new ArrayList<>();
            tempList.add(notFixed.get(0));
            enoughSpace = checkEnoughResourcesLeftOnVM(newVirtualMachineSchedulingUnit, tempList);
            if(enoughSpace) {
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
        List<IntervalContainerSchedulingUnitHolder> holders = createIntervalContainerSchedulingList(virtualMachineSchedulingUnit, containerSchedulingUnits);
        for (IntervalContainerSchedulingUnitHolder holder : holders) {
            boolean result = checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit, holder.getContainerSchedulingUnits());
            if (!result) {
                return false;
            }
        }
        return true;
    }

    public boolean checkEnoughResourcesLeftOnVMForOneInterval(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) {

        VirtualMachineInstance vm = virtualMachineSchedulingUnit.getVirtualMachineInstance();
        double scheduledCPUUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getRam()).sum();

        if (vm.getVmType().getCpuPoints() < scheduledCPUUsage || vm.getVmType().getRamPoints() < scheduledRAMUsage) {
            return false;
        }
        return true;
    }

    public VirtualMachineSchedulingUnit createVMSchedulingUnit(Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines, Set<ContainerSchedulingUnit> containerSchedulingUnits) {
        VirtualMachineInstance randomVM;

        List<VirtualMachineInstance> noSchedulingUnitAvailable = cacheVirtualMachineService.getScheduledAndDeployingAndDeployedVMInstances();
        List<VirtualMachineInstance> availableVMs = new ArrayList<>(noSchedulingUnitAvailable);
        availableVMs.addAll(alreadyScheduledVirtualMachines.stream().map(VirtualMachineSchedulingUnit::getVirtualMachineInstance).collect(Collectors.toList()));

        boolean fromAvailableVM = random.nextBoolean();
        Random rand = new Random();
        if (fromAvailableVM && availableVMs.size() > 0) {
            int randomPosition = rand.nextInt(availableVMs.size());
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

            vmTypes = prepareVMTypeList(vmTypes, containerSchedulingUnits);

            int randomPosition = rand.nextInt(vmTypes.size());
            randomVM = new VirtualMachineInstance(vmTypes.get(randomPosition));

            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(virtualMachineDeploymentTime, false);
            virtualMachineSchedulingUnit.setVirtualMachineInstance(randomVM);

            return virtualMachineSchedulingUnit;
        }

    }

    private List<VMType> prepareVMTypeList(List<VMType> vmTypes, Set<ContainerSchedulingUnit> containerSchedulingUnits) {

        double scheduledCPUUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getRam()).sum();

        for (Iterator<VMType> iterator = vmTypes.iterator(); iterator.hasNext(); ) {
            VMType vmType = iterator.next();
            if (vmType.getCpuPoints() < scheduledCPUUsage || vmType.getRamPoints() < scheduledRAMUsage) {
                iterator.remove();
            }
        }

        if (vmTypes.size() == 0) {
            log.error("no fitting vmtype found");
        }

        return vmTypes;
    }

    private List<IntervalContainerSchedulingUnitHolder> createIntervalContainerSchedulingList(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) {
        return createIntervalContainerSchedulingList(virtualMachineSchedulingUnit, new ArrayList<>());
    }

    private List<IntervalContainerSchedulingUnitHolder> createIntervalContainerSchedulingList(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) {
        Set<ContainerSchedulingUnit> tempSchedulingUnits = new HashSet<>(virtualMachineSchedulingUnit.getScheduledContainers());
        tempSchedulingUnits.addAll(containerSchedulingUnits);
        return createIntervalContainerSchedulingList(tempSchedulingUnits);
    }


    private List<IntervalContainerSchedulingUnitHolder> createIntervalContainerSchedulingList(Set<ContainerSchedulingUnit> tempSchedulingUnits) {

        List<IntervalContainerSchedulingUnitHolder> holders = new ArrayList<>();

        for (ContainerSchedulingUnit scheduledContainer : tempSchedulingUnits) {
            Interval resourceRequirementInterval = scheduledContainer.getCloudResourceUsage();

            boolean found = false;
            for (IntervalContainerSchedulingUnitHolder holder : holders) {
                if (holder.getInterval().overlaps(resourceRequirementInterval)) {
                    long startTime = Math.min(resourceRequirementInterval.getStartMillis(), holder.getInterval().getStartMillis());
                    long endTime = Math.max(resourceRequirementInterval.getEndMillis(), holder.getInterval().getEndMillis());
                    holder.setInterval(new Interval(startTime, endTime));
                    holder.getContainerSchedulingUnits().add(scheduledContainer);
                    found = true;
                    break;
                }
            }
            if (!found) {
                IntervalContainerSchedulingUnitHolder holder = new IntervalContainerSchedulingUnitHolder(scheduledContainer.getCloudResourceUsage(), scheduledContainer);
                holders.add(holder);
            }
        }

//        for (IntervalContainerSchedulingUnitHolder holder : holders) {
//            log.debug("createIntervalContainerSchedulingList: holder=" + holder.toString());
//        }

        return holders;
    }


    @Data
    private class IntervalContainerSchedulingUnitHolder {
        private Interval interval;
        private List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();

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

}
