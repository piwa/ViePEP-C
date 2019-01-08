package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
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

    public void mergeVirtualMachineSchedulingUnits(Chromosome chromosome) {

        Map<VirtualMachineInstance, VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitMap = new HashMap<>();

        for (Chromosome.Gene gene : chromosome.getFlattenChromosome()) {
            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm();
            if (virtualMachineSchedulingUnitMap.containsKey(virtualMachineSchedulingUnit.getVirtualMachineInstance())) {

                VirtualMachineSchedulingUnit otherSchedulingUnit = virtualMachineSchedulingUnitMap.get(virtualMachineSchedulingUnit.getVirtualMachineInstance());

                ContainerSchedulingUnit containerSchedulingUnit = gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit();

                otherSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().setScheduledOnVm(otherSchedulingUnit);
                virtualMachineSchedulingUnit.getScheduledContainers().remove(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit());

            } else {
                virtualMachineSchedulingUnitMap.put(virtualMachineSchedulingUnit.getVirtualMachineInstance(), virtualMachineSchedulingUnit);
            }
        }

    }

    public void checkVmSizeAndSolveSpaceIssues(Chromosome chromosome) {
        List<Chromosome.Gene> genes = chromosome.getFlattenChromosome();
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = genes.stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());

        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {

            List<IntervalContainerSchedulingUnitHolder> holders = createIntervalContainerSchedulingList(virtualMachineSchedulingUnit);
            for (IntervalContainerSchedulingUnitHolder holder : holders) {

                boolean fitsOnVM = checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, holder.getContainerSchedulingUnits());
                if (!fitsOnVM) {
                    try {
                        resizeVM(virtualMachineSchedulingUnit, holder.getContainerSchedulingUnits());
                    } catch (VMTypeNotFoundException e) {
                        distributeContainers(virtualMachineSchedulingUnit, holder.getContainerSchedulingUnits());
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
            virtualMachineSchedulingUnit = createVMSchedulingUnit(new HashSet<>());

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

    public VirtualMachineSchedulingUnit distributeContainers(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) {

        log.info("distributeContainers 1");

        int halfContainerSchedulingUnits = containerSchedulingUnits.size() / 2;
        List<ContainerSchedulingUnit> containerSchedulingUnits_1 = containerSchedulingUnits.subList(0, halfContainerSchedulingUnits);
        List<ContainerSchedulingUnit> containerSchedulingUnits_2 = containerSchedulingUnits.subList(halfContainerSchedulingUnits, containerSchedulingUnits.size());

        // containerSchedulingUnits_1 keep on virtualMachineSchedulingUnit
        virtualMachineSchedulingUnit.getScheduledContainers().removeAll(containerSchedulingUnits_2);

        // new virtualMachineSchedulingUnit for containerSchedulingUnits_2
        VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = getVirtualMachineSchedulingUnit(containerSchedulingUnits_2);
        containerSchedulingUnits_2.forEach(containerSchedulingUnit -> containerSchedulingUnit.setScheduledOnVm(newVirtualMachineSchedulingUnit));
        newVirtualMachineSchedulingUnit.getScheduledContainers().addAll(containerSchedulingUnits_2);

        log.info("distributeContainers 2");

        return newVirtualMachineSchedulingUnit;
    }

    public boolean checkEnoughResourcesLeftOnVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) {
        return checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, new ArrayList<>());
    }

    public boolean checkEnoughResourcesLeftOnVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) {

        VirtualMachineInstance vm = virtualMachineSchedulingUnit.getVirtualMachineInstance();
        List<IntervalContainerSchedulingUnitHolder> holders = createIntervalContainerSchedulingList(virtualMachineSchedulingUnit, containerSchedulingUnits);

        for (IntervalContainerSchedulingUnitHolder holder : holders) {
            double scheduledCPUUsage = holder.getContainerSchedulingUnits().stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getCPUPoints()).sum();
            double scheduledRAMUsage = holder.getContainerSchedulingUnits().stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getRam()).sum();

            if (vm.getVmType().getCpuPoints() < scheduledCPUUsage || vm.getVmType().getRamPoints() < scheduledRAMUsage) {
                return false;
            }
        }

        return true;
    }

    public VirtualMachineSchedulingUnit createVMSchedulingUnit(Set<VirtualMachineInstance> alreadyScheduledVirtualMachines) {
        return createVMSchedulingUnit(alreadyScheduledVirtualMachines, new ArrayList<>());
    }

    public VirtualMachineSchedulingUnit createVMSchedulingUnit(Set<VirtualMachineInstance> alreadyScheduledVirtualMachines, List<ContainerSchedulingUnit> containerSchedulingUnits) {
        VirtualMachineInstance randomVM;

        List<VirtualMachineInstance> availableVMs = cacheVirtualMachineService.getScheduledAndDeployingAndDeployedVMInstances();
        availableVMs.addAll(alreadyScheduledVirtualMachines);

        Random rand = new Random();
        if (rand.nextInt(2) == 0 && availableVMs.size() > 0) {
            int randomPosition = rand.nextInt(availableVMs.size());
            randomVM = availableVMs.get(randomPosition);
        } else {

            List<VMType> vmTypes = cacheVirtualMachineService.getVMTypes();

            vmTypes = prepareVMTypeList(vmTypes, containerSchedulingUnits);

            int randomPosition = rand.nextInt(vmTypes.size());
            randomVM = new VirtualMachineInstance(vmTypes.get(randomPosition));
        }

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(virtualMachineDeploymentTime);
        virtualMachineSchedulingUnit.setVirtualMachineInstance(randomVM);

        return virtualMachineSchedulingUnit;
    }

    private List<VMType> prepareVMTypeList(List<VMType> vmTypes, List<ContainerSchedulingUnit> containerSchedulingUnits) {

        double scheduledCPUUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = containerSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getRam()).sum();

        for (Iterator<VMType> iterator = vmTypes.iterator(); iterator.hasNext(); ) {
            VMType vmType = iterator.next();
            if (vmType.getCpuPoints() < scheduledCPUUsage || vmType.getRamPoints() < scheduledRAMUsage) {
                iterator.remove();
            }
        }

        if(vmTypes.size() == 0) {
            log.error("no fitting vmtype found");
        }

        return vmTypes;
    }

    public VirtualMachineSchedulingUnit createNewVMSchedulingUnitWithSmallestVMType() {

        VirtualMachineInstance vmInstance = new VirtualMachineInstance(cacheVirtualMachineService.getVMTypes().get(0));

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(virtualMachineDeploymentTime);
        virtualMachineSchedulingUnit.setVirtualMachineInstance(vmInstance);

        return virtualMachineSchedulingUnit;
    }

    private List<IntervalContainerSchedulingUnitHolder> createIntervalContainerSchedulingList(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) {
        return createIntervalContainerSchedulingList(virtualMachineSchedulingUnit, new ArrayList<>());
    }

    private List<IntervalContainerSchedulingUnitHolder> createIntervalContainerSchedulingList(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ContainerSchedulingUnit> containerSchedulingUnits) {

        List<IntervalContainerSchedulingUnitHolder> holders = new ArrayList<>();

        List<ContainerSchedulingUnit> tempSchedulingUnits = new ArrayList<>(virtualMachineSchedulingUnit.getScheduledContainers());
        tempSchedulingUnits.addAll(containerSchedulingUnits);

        for (ContainerSchedulingUnit scheduledContainer : tempSchedulingUnits) {
            Interval resourceRequirementInterval = scheduledContainer.getCloudResourceUsage();

            boolean found = false;
            for (IntervalContainerSchedulingUnitHolder holder : holders) {
                if (holder.getInterval().contains(resourceRequirementInterval)) {
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
    }

}
