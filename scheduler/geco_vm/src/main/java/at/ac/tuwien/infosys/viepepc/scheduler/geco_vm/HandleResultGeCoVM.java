package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutor;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.ProvisioningSchedule;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStepStatus;
import at.ac.tuwien.infosys.viepepc.scheduler.library.HandleOptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.PrintRunningInfoVmContainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Slf4j
@Component
@Profile({"GeCo_VM","GeCo_VM_Baseline"})
public class HandleResultGeCoVM implements HandleOptimizationResult {

    @Autowired
    private ProvisioningSchedule provisioningSchedule;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheContainerService cacheContainerService;
    @Autowired
    private CacheProcessStepService processStepService;

    @Override
    public Boolean processResults(OptimizationResult optimize, DateTime tau_t) {

        printOptimizationResultInformation(optimize, tau_t);

        synchronized (provisioningSchedule) {
            provisioningSchedule.addAllProcessSteps(optimize.getProcessSteps());
            provisioningSchedule.addAllContainers(optimize.getContainers());
            provisioningSchedule.addAllVirtualMachineInstances(optimize.getVirtualMachineInstances());

            optimize.getVirtualMachineInstances().forEach(virtualMachineInstance -> cacheVirtualMachineService.getAllVMInstancesFromInMemory().put(virtualMachineInstance.getInternId(), virtualMachineInstance));
            optimize.getContainers().forEach(container -> cacheContainerService.getAllContainerInstances().put(container.getInternId(), container));
            optimize.getProcessSteps().forEach(ps -> processStepService.getAllProcessSteps().put(ps.getInternId(), ps));

//            HashMap
//            Set<VirtualMachineInstance> vmSet = cacheVirtualMachineService.getAllVMInstancesFromInMemory();
//            vmSet.forEach(vm -> );
//
//            cacheVirtualMachineService.getAllVMInstancesFromInMemory().removeAll(optimize.getVirtualMachineInstances());
//            cacheContainerService.getAllContainerInstances().removeAll(optimize.getContainers());
//            processStepService.getAllProcessSteps().removeAll(optimize.getProcessSteps());
//
//            cacheVirtualMachineService.getAllVMInstancesFromInMemory().addAll(optimize.getVirtualMachineInstances());
//            cacheContainerService.getAllContainerInstances().addAll(optimize.getContainers());
//            processStepService.getAllProcessSteps().addAll(optimize.getProcessSteps());

            cleanup(optimize.getProcessSteps());
        }
        return true;
    }

    private void printOptimizationResultInformation(OptimizationResult optimize, DateTime tau_t) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Optimization result:\n");

        Set<VirtualMachineInstance> vmsToStart = new HashSet<>();
        Set<Container> containersToDeploy = new HashSet<>();
        processProcessSteps(optimize, vmsToStart, containersToDeploy, tau_t);
        stringBuilder.append("----------- VM should be used (running or has to be started): ------------\n");
        for (VirtualMachineInstance virtualMachineInstance : vmsToStart) {
            stringBuilder.append(virtualMachineInstance).append("\n");
        }

        stringBuilder.append("-------- Container should be used (running or has to be started): --------\n");
        for (Container container : containersToDeploy) {
            stringBuilder.append(container).append("\n");
        }

        stringBuilder.append("-------------------------- Tasks to be started ---------------------------\n");
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            stringBuilder.append(processStep).append("\n");
        }

        log.debug(stringBuilder.toString());
    }

    private void processProcessSteps(OptimizationResult optimize, Set<VirtualMachineInstance> vmsToStart, Set<Container> containersToDeploy, DateTime tau_t) {
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            if (processStep.getContainer().getVirtualMachineInstance() != null) {
                vmsToStart.add(processStep.getContainer().getVirtualMachineInstance());
            }
            containersToDeploy.add(processStep.getContainer());
        }
    }

    private void cleanup(List<ProcessStep> processSteps) {

        Set<UUID> usedProcessSteps = processSteps.stream().map(ps -> ps.getInternId()).collect(Collectors.toSet());
        Set<UUID> usedContainer = processSteps.stream().map(ps -> ps.getContainer().getInternId()).collect(Collectors.toSet());
        Set<UUID> usedVMs = processSteps.stream().map(ps -> ps.getContainer().getVirtualMachineInstance().getInternId()).collect(Collectors.toSet());


        List<ProcessStep> notUsedProcessSteps = provisioningSchedule.getProcessStepsMap().values().stream().filter(ps -> !usedProcessSteps.contains(ps.getInternId())).collect(Collectors.toList());
        Set<UUID> psToRemove = new HashSet<>();
        for (ProcessStep processStep : notUsedProcessSteps) {
            if (processStep.getProcessStepStatus().equals(ProcessStepStatus.SCHEDULED)) {
                psToRemove.add(processStep.getInternId());
            }
        }
        psToRemove.forEach(id -> provisioningSchedule.getProcessStepsMap().remove(id));


        List<Container> notUsedContainers = provisioningSchedule.getContainersMap().values().stream().filter(c -> !usedContainer.contains(c.getInternId())).collect(Collectors.toList());
        Set<UUID> containerToRemove = new HashSet<>();
        for (Container container : notUsedContainers) {
            if (container.getContainerStatus().equals(ContainerStatus.SCHEDULED)) {
                containerToRemove.add(container.getInternId());
                cacheContainerService.getAllContainerInstances().remove(container.getInternId());
            } else {
                Container containerFromSchedule = provisioningSchedule.getContainersMap().get(container.getInternId());
                Interval scheduleInterval = containerFromSchedule.getScheduledCloudResourceUsage();
                Interval newScheduleInterval = null;
                try {
                    newScheduleInterval = scheduleInterval.withEnd(DateTime.now());
                } catch (IllegalArgumentException ex) {
                    newScheduleInterval = scheduleInterval.withEnd(scheduleInterval.getStart().plusMinutes(1));
                }
                containerFromSchedule.setScheduledCloudResourceUsage(newScheduleInterval);
            }
        }
        containerToRemove.forEach(id -> provisioningSchedule.getContainersMap().remove(id));

        List<VirtualMachineInstance> notUsedVMs = provisioningSchedule.getVirtualMachineInstancesMap().values().stream().filter(vm -> !usedVMs.contains(vm.getInternId())).collect(Collectors.toList());
        Set<UUID> vmToRemove = new HashSet<>();
        for (VirtualMachineInstance vm : notUsedVMs) {
            if (vm.getVirtualMachineStatus().equals(VirtualMachineStatus.SCHEDULED)) {
                vmToRemove.add(vm.getInternId());
                cacheVirtualMachineService.getAllVMInstancesFromInMemory().remove(vm.getInternId());
            } else {
                VirtualMachineInstance vmFromSchedule = provisioningSchedule.getVirtualMachineInstancesMap().get(vm.getInternId());
                Interval scheduleInterval = vmFromSchedule.getScheduledCloudResourceUsage();
                Interval newScheduleInterval = null;
                try {
                    newScheduleInterval = scheduleInterval.withEnd(DateTime.now());
                } catch (IllegalArgumentException ex) {
                    newScheduleInterval = scheduleInterval.withEnd(scheduleInterval.getStart().plusMinutes(1));
                }
                vmFromSchedule.setScheduledCloudResourceUsage(newScheduleInterval);
            }
        }
        vmToRemove.forEach(id -> provisioningSchedule.getVirtualMachineInstancesMap().remove(id));

    }

}
