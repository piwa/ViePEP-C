package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.reverseOrder;

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


    public List<ServiceTypeSchedulingUnit> setVMsForServiceSchedulingUnit(List<ServiceTypeSchedulingUnit> requiredServiceTypeList) {

        requiredServiceTypeList.sort(Comparator.comparing(ServiceTypeSchedulingUnit::getDeployStartTime));

        List<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = new ArrayList<>();

        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : requiredServiceTypeList) {
            if (serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit() != null && serviceTypeSchedulingUnit.isFixed()) {
                virtualMachineSchedulingUnits.add(serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit());
                assignVMToServiceTypeUnit(serviceTypeSchedulingUnit, serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit());
            }
        }

        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : requiredServiceTypeList) {
            if (serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit() == null || !serviceTypeSchedulingUnit.isFixed()) {
                boolean vmAssigned = false;
                List<VirtualMachineSchedulingUnit> tempVMList = virtualMachineSchedulingUnits.stream().distinct()
                        .sorted(
                                Comparator.comparing((VirtualMachineSchedulingUnit unit) -> unit.getVirtualMachineInstance().getVmType().getCores())
                                        .thenComparing(Comparator.comparingInt((VirtualMachineSchedulingUnit unit) -> unit.getProcessStepSchedulingUnits().size()).reversed())
//                                reverseOrder(Comparator.comparingInt((VirtualMachineSchedulingUnit unit) -> unit.getProcessStepSchedulingUnits().size()))
//                                        .thenComparing((VirtualMachineSchedulingUnit unit) -> unit.getCloudResourceUsageInterval().getEnd())
//                                        .thenComparing((VirtualMachineSchedulingUnit unit) -> unit.getVirtualMachineInstance().getVmType().getCores())
                        )
                        .collect(Collectors.toList());
                virtualMachineSchedulingUnits = tempVMList;
                for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : tempVMList) {
                    if (virtualMachineSchedulingUnit.getCloudResourceUsageInterval().overlaps(serviceTypeSchedulingUnit.getCloudResourceUsage())) {
                        List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits = optimizationUtility.getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit);
                        allServiceTypeSchedulingUnits.add(serviceTypeSchedulingUnit);
                        List<ServiceTypeSchedulingUnit> maxOverlappingServiceTypes = createIntervalContainerSchedulingList(allServiceTypeSchedulingUnits);
                        boolean result = checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit.getVmType(), maxOverlappingServiceTypes);
                        if (result) {
                            assignVMToServiceTypeUnit(serviceTypeSchedulingUnit, virtualMachineSchedulingUnit);
                            vmAssigned = true;
                            break;
                        }
                    } else {
                        DateTime usageCostPlusDeployment = virtualMachineSchedulingUnit.getCloudResourceUsageInterval().getEnd().plus(virtualMachineDeploymentTime);
                        try {
                            VMType vmType = getFittingVMType(serviceTypeSchedulingUnit);
                            if (usageCostPlusDeployment.isAfter(serviceTypeSchedulingUnit.getCloudResourceUsage().getStart()) && virtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType() == vmType) {
                                assignVMToServiceTypeUnit(serviceTypeSchedulingUnit, virtualMachineSchedulingUnit);
                                vmAssigned = true;
                                break;
                            }
                        } catch (Exception ex) {
                            log.error("error");
                        }

                    }
                }
                if (!vmAssigned) {
                    try {
                        VMType vmType = getFittingVMType(serviceTypeSchedulingUnit);
                        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(false, virtualMachineDeploymentTime, containerDeploymentTime, new VirtualMachineInstance(vmType));
                        assignVMToServiceTypeUnit(serviceTypeSchedulingUnit, virtualMachineSchedulingUnit);
                        virtualMachineSchedulingUnits.add(virtualMachineSchedulingUnit);
                    } catch (Exception ex) {
                        log.error("error");
                    }
                }
            }
        }

        return requiredServiceTypeList;
    }

    private void assignVMToServiceTypeUnit(ServiceTypeSchedulingUnit serviceTypeSchedulingUnit, VirtualMachineSchedulingUnit virtualMachineInstance) {
        serviceTypeSchedulingUnit.setVirtualMachineSchedulingUnit(virtualMachineInstance);
        serviceTypeSchedulingUnit.getContainer().setVirtualMachineInstance(virtualMachineInstance.getVirtualMachineInstance());
        for (Chromosome.Gene gene : serviceTypeSchedulingUnit.getGenes()) {
            gene.getProcessStepSchedulingUnit().setVirtualMachineSchedulingUnit(virtualMachineInstance);
            virtualMachineInstance.getProcessStepSchedulingUnits().add(gene.getProcessStepSchedulingUnit());
        }
    }

    private VMType getFittingVMType(List<VMType> allVMTypes, ProcessStepSchedulingUnit processStepSchedulingUnit) throws VMTypeNotFoundException {

        double scheduledCPUUsage = processStepSchedulingUnit.getProcessStep().getServiceType().getServiceTypeResources().getCpuLoad();
        double scheduledRAMUsage = processStepSchedulingUnit.getProcessStep().getServiceType().getServiceTypeResources().getMemory();

        allVMTypes.sort(Comparator.comparing(VMType::getCores));

        for (VMType vmType : allVMTypes) {
            if (vmType.getCpuPoints() >= scheduledCPUUsage && vmType.getRamPoints() >= scheduledRAMUsage) {
                return vmType;
            }
        }

        throw new VMTypeNotFoundException("Could not find big enough VMType");
    }

    private VMType getFittingVMType(ServiceTypeSchedulingUnit serviceTypeSchedulingUnit) throws VMTypeNotFoundException {

        double scheduledCPUUsage = serviceTypeSchedulingUnit.getContainer().getContainerConfiguration().getCPUPoints();
        double scheduledRAMUsage = serviceTypeSchedulingUnit.getContainer().getContainerConfiguration().getRam();

        List<VMType> allVMTypes = new ArrayList<>(cacheVirtualMachineService.getVMTypes());
        allVMTypes.sort(Comparator.comparing(VMType::getCores));

        for (VMType vmType : allVMTypes) {
            if (vmType.getCpuPoints() >= scheduledCPUUsage && vmType.getRamPoints() >= scheduledRAMUsage) {
                return vmType;
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
        return vmType.getCpuPoints() >= combinedContainerResources.getScheduledCPUUsage() && vmType.getRamPoints() >= combinedContainerResources.getScheduledRAMUsage();
    }

    @NotNull
    public List<VirtualMachineSchedulingUnit> createAvailableVMSchedulingUnitList(Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines) {
        return alreadyScheduledVirtualMachines.stream().filter(unit -> unit.getCloudResourceUsageInterval().getEnd().isAfter(this.optimizationEndTime)).distinct().collect(Collectors.toList());
    }

    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnitForProcessStep(ProcessStepSchedulingUnit processStepSchedulingUnit, Set<VirtualMachineSchedulingUnit> availableVirtualMachineSchedulingUnits, Random random) {
        return getVirtualMachineSchedulingUnitForProcessStep(processStepSchedulingUnit, availableVirtualMachineSchedulingUnits, random, true);
    }

    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnitForProcessStep(ProcessStepSchedulingUnit processStepSchedulingUnit, Set<VirtualMachineSchedulingUnit> availableVirtualMachineSchedulingUnits, Random random, boolean withCheck) {
        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>();
        processStepSchedulingUnits.add(processStepSchedulingUnit);

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
        do {
//            int randomValue = random.nextInt(10);
//            boolean fromAvailableVMs = randomValue < 8;
            boolean fromAvailableVMs = random.nextBoolean();
            if (fromAvailableVMs) {
                List<VirtualMachineSchedulingUnit> availableVMSchedulingUnits = createAvailableVMSchedulingUnitList(availableVirtualMachineSchedulingUnits);
                if (availableVMSchedulingUnits.size() > 0) {
                    int randomPosition = random.nextInt(availableVMSchedulingUnits.size());
                    virtualMachineSchedulingUnit = availableVMSchedulingUnits.get(randomPosition);
                }
            }
            if (!fromAvailableVMs || virtualMachineSchedulingUnit == null) {
                virtualMachineSchedulingUnit = createNewVirtualMachineSchedulingUnit(processStepSchedulingUnit, random);
            }
        } while (withCheck && !checkIfVirtualMachineHasEnoughSpaceForNewProcessSteps(virtualMachineSchedulingUnit, processStepSchedulingUnits));

        return virtualMachineSchedulingUnit;
    }

    @NotNull
    public VirtualMachineSchedulingUnit createNewVirtualMachineSchedulingUnit(ProcessStepSchedulingUnit processStepSchedulingUnit, Random random) {
        try {
            VMType vmType = getFittingVMType(new ArrayList<>(cacheVirtualMachineService.getVMTypes()), processStepSchedulingUnit);
            return new VirtualMachineSchedulingUnit(false, virtualMachineDeploymentTime, containerDeploymentTime, new VirtualMachineInstance(vmType));
        } catch (Exception ex) {
            List<VMType> vmTypes = new ArrayList<>(cacheVirtualMachineService.getVMTypes());
            int randomPosition = random.nextInt(vmTypes.size());
            VMType vmType = vmTypes.get(randomPosition);
            return new VirtualMachineSchedulingUnit(false, virtualMachineDeploymentTime, containerDeploymentTime, new VirtualMachineInstance(vmType));
        }
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

        CombinedContainerResources(Collection<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits) {
            scheduledCPUUsage = serviceTypeSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getCPUPoints()).sum();
            scheduledRAMUsage = serviceTypeSchedulingUnits.stream().mapToDouble(c -> c.getContainer().getContainerConfiguration().getRam()).sum();
        }
    }
}
