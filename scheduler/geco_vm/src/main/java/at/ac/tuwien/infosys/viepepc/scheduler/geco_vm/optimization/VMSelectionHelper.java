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

    @Getter
    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;
    @Getter
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;

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

    public List<ServiceTypeSchedulingUnit> createIntervalContainerSchedulingList(List<ServiceTypeSchedulingUnit> tempSchedulingUnits) {


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

    public boolean checkEnoughResourcesLeftOnVMForOneInterval(VMType vmType, List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits) {
        CombinedContainerResources combinedContainerResources = new CombinedContainerResources(serviceTypeSchedulingUnits);
        return vmType.getCpuPoints() >= combinedContainerResources.getScheduledCPUUsage() && vmType.getRamPoints() >= combinedContainerResources.getScheduledRAMUsage();
    }

    @NotNull
    public List<VirtualMachineSchedulingUnit> createAvailableVMSchedulingUnitList(Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines) {
        return alreadyScheduledVirtualMachines.stream().filter(unit -> unit.getCloudResourceUsageInterval().getEnd().isAfter(this.optimizationEndTime)).distinct().collect(Collectors.toList());
    }


    public VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnitForProcessStep(ServiceTypeSchedulingUnit serviceTypeSchedulingUnit, Set<VirtualMachineSchedulingUnit> availableVirtualMachineSchedulingUnits, Random random, boolean withCheck) {


        List<ServiceTypeSchedulingUnit> maxOverlappingServiceTypes = null;

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
                virtualMachineSchedulingUnit = createNewVirtualMachineSchedulingUnit(serviceTypeSchedulingUnit, random);
            }

            List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits;
            if(virtualMachineSchedulingUnit.getServiceTypeSchedulingUnits().isEmpty()) {
                allServiceTypeSchedulingUnits = optimizationUtility.getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit);
            }
            else {
                allServiceTypeSchedulingUnits = new ArrayList<>(virtualMachineSchedulingUnit.getServiceTypeSchedulingUnits());
            }
            allServiceTypeSchedulingUnits.add(serviceTypeSchedulingUnit);
            maxOverlappingServiceTypes = createIntervalContainerSchedulingList(allServiceTypeSchedulingUnits);
        } while (withCheck && !checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit.getVmType(), maxOverlappingServiceTypes));

        return virtualMachineSchedulingUnit;
    }

    @NotNull
    public VirtualMachineSchedulingUnit createNewVirtualMachineSchedulingUnit(ServiceTypeSchedulingUnit serviceTypeSchedulingUnit, Random random) {
        try {
            VMType vmType = getFittingVMType(serviceTypeSchedulingUnit);
            return new VirtualMachineSchedulingUnit(false, null, virtualMachineDeploymentTime, containerDeploymentTime, new VirtualMachineInstance(vmType));
        } catch (Exception ex) {
            log.error("could not create a VM. serviceTypeSchedulingUnit=" + serviceTypeSchedulingUnit);
            List<VMType> vmTypes = new ArrayList<>(cacheVirtualMachineService.getVMTypes());
            int randomPosition = random.nextInt(vmTypes.size());
            VMType vmType = vmTypes.get(randomPosition);
            return new VirtualMachineSchedulingUnit(false, null, virtualMachineDeploymentTime, containerDeploymentTime, new VirtualMachineInstance(vmType));
        }
    }

    public boolean checkIfVMIsTooSmall(List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits, String position) {

        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = new HashSet<>();
        for (ServiceTypeSchedulingUnit typeSchedulingUnit : allServiceTypeSchedulingUnits) {
            VirtualMachineSchedulingUnit machineSchedulingUnit = typeSchedulingUnit.getVirtualMachineSchedulingUnit();
            if (machineSchedulingUnit != null) {
                machineSchedulingUnit.getServiceTypeSchedulingUnits().add(typeSchedulingUnit);
                virtualMachineSchedulingUnits.add(machineSchedulingUnit);
            }
        }

        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
            List<ServiceTypeSchedulingUnit> maxOverlappingServiceTypes = createIntervalContainerSchedulingList(new ArrayList<>(virtualMachineSchedulingUnit.getServiceTypeSchedulingUnits()));
            if(!checkEnoughResourcesLeftOnVMForOneInterval(virtualMachineSchedulingUnit.getVmType(), maxOverlappingServiceTypes)) {
//                log.error("not enough space after the optimization (at="+position+") on VM=" + virtualMachineSchedulingUnit);
                return true;
            }
        }

        return false;
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
