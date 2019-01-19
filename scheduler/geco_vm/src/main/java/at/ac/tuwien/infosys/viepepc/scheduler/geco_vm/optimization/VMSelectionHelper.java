package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
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
    @Autowired
    private OptimizationUtility optimizationUtility;

    @Setter
    @Getter
    private DateTime optimizationEndTime;

    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;


    public void checkVmSizeAndSolveSpaceIssues(Chromosome chromosome) {
        List<Chromosome.Gene> genes = chromosome.getFlattenChromosome();
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = genes.stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toSet());
        Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines = new HashSet<>(virtualMachineSchedulingUnits);

        for (Iterator<VirtualMachineSchedulingUnit> iterator = virtualMachineSchedulingUnits.iterator(); iterator.hasNext(); ) {
            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = iterator.next();

            List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits = optimizationUtility.getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit);
            List<ServiceTypeSchedulingUnit> maxOverlappingServiceTypes = createIntervalContainerSchedulingList(allServiceTypeSchedulingUnits);
            boolean fitsOnVM = checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit.getVmType(), maxOverlappingServiceTypes);
            if (!fitsOnVM) {
                if (virtualMachineSchedulingUnit.isFixed()) {
//                        log.debug("distributed containers 1");
                    distributeContainers(virtualMachineSchedulingUnit, alreadyScheduledVirtualMachines);
                    if (chromosome != null) {
                        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_checkVmSizeAndSolveSpaceIssues");
                    }
                } else {
                    try {
                        resizeVM(virtualMachineSchedulingUnit, maxOverlappingServiceTypes);
                    } catch (VMTypeNotFoundException e) {
                        distributeContainers(virtualMachineSchedulingUnit, alreadyScheduledVirtualMachines);
                    }

                }
            }
        }
    }

    public void distributeContainers(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines) {

        List<ProcessStepSchedulingUnit> notFixed = virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().stream().filter(unit -> !unit.getGene().isFixed()).collect(Collectors.toList());

        virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().removeAll(notFixed);
        if (virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().size() > 0) {
            List<ProcessStepSchedulingUnit> fixed = virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().stream().filter(unit -> unit.getGene().isFixed()).collect(Collectors.toList());
            if (virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().size() != fixed.size() || !checkIfVirtualMachineIsBigEnough(virtualMachineSchedulingUnit)) {
                log.error("problem");
            }
        }

        VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = virtualMachineSchedulingUnit;
        List<VirtualMachineSchedulingUnit> usedVirtualMachineSchedulingUnits = new ArrayList<>();
        usedVirtualMachineSchedulingUnits.add(newVirtualMachineSchedulingUnit);
        while (!notFixed.isEmpty()) {

            fillVirtualMachine(notFixed, newVirtualMachineSchedulingUnit);

            if (!notFixed.isEmpty()) {
                do {
                    newVirtualMachineSchedulingUnit = getVirtualMachineSchedulingUnitForProcessStep(notFixed.get(0), alreadyScheduledVirtualMachines, new Random());
                } while (usedVirtualMachineSchedulingUnits.contains(newVirtualMachineSchedulingUnit) || newVirtualMachineSchedulingUnit.isFixed());
                usedVirtualMachineSchedulingUnits.add(newVirtualMachineSchedulingUnit);
                alreadyScheduledVirtualMachines.add(newVirtualMachineSchedulingUnit);
            }
        }

    }

    private void fillVirtualMachine(List<ProcessStepSchedulingUnit> notFixed, VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit) {
        boolean enoughSpace = true;
        do {
            List<ProcessStepSchedulingUnit> tempList = new ArrayList<>();
            tempList.add(notFixed.get(0));
            enoughSpace = checkIfVirtualMachineHasEnoughSpaceForNewProcessSteps(newVirtualMachineSchedulingUnit, tempList);
            if (enoughSpace) {
                ProcessStepSchedulingUnit processStepSchedulingUnit = notFixed.remove(0);
                newVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(processStepSchedulingUnit);
                processStepSchedulingUnit.setVirtualMachineSchedulingUnit(newVirtualMachineSchedulingUnit);
            }
        } while (enoughSpace && !notFixed.isEmpty());

    }


    public void resizeVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) throws VMTypeNotFoundException {

        List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits = optimizationUtility.getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit);
        List<ServiceTypeSchedulingUnit> maxOverlappingServiceTypes = createIntervalContainerSchedulingList(allServiceTypeSchedulingUnits);
        resizeVM(virtualMachineSchedulingUnit, maxOverlappingServiceTypes);
    }

    private void resizeVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ServiceTypeSchedulingUnit> maxOverlappingServiceTypes) throws VMTypeNotFoundException {

        CombinedContainerResources combinedContainerResources = new CombinedContainerResources(maxOverlappingServiceTypes);

        List<VMType> allVMTypes = cacheVirtualMachineService.getVMTypes();
//        allVMTypes.sort(Comparator.comparing(VMType::getCores).thenComparing(VMType::getRamPoints));
        allVMTypes.sort(Comparator.comparing(VMType::getCores));

        for (VMType vmType : allVMTypes) {
            if (vmType.getCpuPoints() >= combinedContainerResources.getScheduledCPUUsage() && vmType.getRamPoints() >= combinedContainerResources.getScheduledRAMUsage()) {
                virtualMachineSchedulingUnit.setVmType(vmType);
                return;
            }
        }
        throw new VMTypeNotFoundException("Could not find big enough VMType");
    }


    private List<ServiceTypeSchedulingUnit> createIntervalContainerSchedulingList(List<ServiceTypeSchedulingUnit> tempSchedulingUnits) {


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

        List<ServiceTypeSchedulingUnit> maxContainerList = new ArrayList<>();
        List<ServiceTypeSchedulingUnit> containerList = new ArrayList<>();
//        DateTime overlapTime = deployEvents.get(0).getTime();
        containerList.add(deployEvents.get(0).getServiceTypeSchedulingUnit());
        maxContainerList.add(deployEvents.get(0).getServiceTypeSchedulingUnit());
        CombinedContainerResources maxContainerResource = new CombinedContainerResources(maxContainerList);

        while (i < tempSchedulingUnits.size() && j < tempSchedulingUnits.size()) {
            // If next event in sorted order is arrival,
            // increment count of container
            if (deployEvents.get(i).getTime().getMillis() <= undeployEvents.get(j).getTime().getMillis()) {
                containerList.add(deployEvents.get(i).getServiceTypeSchedulingUnit());
                CombinedContainerResources currentContainerResource = new CombinedContainerResources(containerList);
                if (currentContainerResource.getScheduledCPUUsage() > maxContainerResource.getScheduledCPUUsage()) {// || currentContainerResource.getScheduledRAMUsage() > maxContainerResource.getScheduledRAMUsage()) {
                    maxContainerList.clear();
                    maxContainerList.addAll(containerList);
                    maxContainerResource = new CombinedContainerResources(maxContainerList);
//                    overlapTime = deployEvents.get(i).getTime();
                }
                i++; //increment index of arrival array
            } else // If event is exit, decrement count
            {
                containerList.remove(undeployEvents.get(j).getServiceTypeSchedulingUnit());
                j++;
            }
        }

        return maxContainerList;
    }

    public boolean checkIfVirtualMachineIsBigEnough(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit) {
        List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits = optimizationUtility.getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit);
        List<ServiceTypeSchedulingUnit> maxOverlappingServiceTypes = createIntervalContainerSchedulingList(allServiceTypeSchedulingUnits);
        return checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit.getVmType(), maxOverlappingServiceTypes);
    }

    public boolean checkIfVirtualMachineHasEnoughSpaceForNewProcessSteps(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, List<ProcessStepSchedulingUnit> processStepSchedulingUnits) {
        List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits = optimizationUtility.getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit, processStepSchedulingUnits);
        List<ServiceTypeSchedulingUnit> maxOverlappingServiceTypes = createIntervalContainerSchedulingList(allServiceTypeSchedulingUnits);
        return checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit.getVmType(), maxOverlappingServiceTypes);
    }

    private boolean checkEnoughResourcesLeftOnVMForOneInterval(VMType vmType, List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits) {

        CombinedContainerResources combinedContainerResources = new CombinedContainerResources(serviceTypeSchedulingUnits);
        if (vmType.getCpuPoints() < combinedContainerResources.getScheduledCPUUsage() || vmType.getRamPoints() < combinedContainerResources.getScheduledRAMUsage()) {
            return false;
        }
        return true;
    }

    private VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnit(Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines, Random random) {
        VirtualMachineInstance randomVM;

        List<VirtualMachineInstance> noSchedulingUnitAvailable = cacheVirtualMachineService.getScheduledAndDeployingAndDeployedVMInstances().stream().filter(vm -> vm.getScheduledCloudResourceUsage().getEnd().isAfter(this.optimizationEndTime)).collect(Collectors.toList());
        List<VirtualMachineInstance> availableVMs = new ArrayList<>(noSchedulingUnitAvailable);
        Set<VirtualMachineSchedulingUnit> afterOptimizationEndAvailableVMs = alreadyScheduledVirtualMachines.stream().filter(unit -> unit.getCloudResourceUsageInterval().getEnd().isAfter(this.optimizationEndTime)).collect(Collectors.toSet());
        availableVMs.addAll(afterOptimizationEndAvailableVMs.stream().map(VirtualMachineSchedulingUnit::getVirtualMachineInstance).collect(Collectors.toList()));

        boolean fromAvailableVM = random.nextBoolean();
        if (fromAvailableVM && availableVMs.size() > 0) {
            int randomPosition = random.nextInt(availableVMs.size());
            randomVM = availableVMs.get(randomPosition);

            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
            for (VirtualMachineSchedulingUnit alreadyScheduledVirtualMachine : afterOptimizationEndAvailableVMs) {
                if (alreadyScheduledVirtualMachine.getVirtualMachineInstance().equals(randomVM)) {
                    virtualMachineSchedulingUnit = alreadyScheduledVirtualMachine;
                    break;
                }
            }
            if (virtualMachineSchedulingUnit == null) {
                virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(true, virtualMachineDeploymentTime, containerDeploymentTime, randomVM);
            }
            return virtualMachineSchedulingUnit;

        } else {

            List<VMType> vmTypes = new ArrayList<>(cacheVirtualMachineService.getVMTypes());

            int randomPosition = random.nextInt(vmTypes.size());
            VMType vmType = vmTypes.get(randomPosition);

            randomVM = new VirtualMachineInstance(vmType);

            return new VirtualMachineSchedulingUnit(false, virtualMachineDeploymentTime, containerDeploymentTime, randomVM);
        }
    }

    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnitForProcessStep(ProcessStepSchedulingUnit processStepSchedulingUnit, Set<VirtualMachineSchedulingUnit> availableVirtualMachineSchedulingUnits, Random random) {
        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>();
        processStepSchedulingUnits.add(processStepSchedulingUnit);
        return getVirtualMachineSchedulingUnitForProcessSteps(processStepSchedulingUnits, availableVirtualMachineSchedulingUnits, random);
    }

    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnitForProcessSteps(List<ProcessStepSchedulingUnit> processStepSchedulingUnits, Set<VirtualMachineSchedulingUnit> availableVirtualMachineSchedulingUnits, Random random) {
        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
        do {
            virtualMachineSchedulingUnit = getVirtualMachineSchedulingUnit(availableVirtualMachineSchedulingUnits, random);
        } while (!checkIfVirtualMachineHasEnoughSpaceForNewProcessSteps(virtualMachineSchedulingUnit, processStepSchedulingUnits));

        return virtualMachineSchedulingUnit;
    }

    @Data
    private class ContainerEvent {
        private final DateTime time;
        private final ServiceTypeSchedulingUnit serviceTypeSchedulingUnit;
    }

    @Getter
    private class CombinedContainerResources {
        private final double scheduledCPUUsage;
        private final double scheduledRAMUsage;

        public CombinedContainerResources(Collection<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits) {
            scheduledCPUUsage = serviceTypeSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getCPUPoints()).sum();
            scheduledRAMUsage = serviceTypeSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getRam()).sum();
        }
    }
}
