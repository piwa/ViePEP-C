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
import at.ac.tuwien.infosys.viepepc.scheduler.library.HandleOptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.PrintRunningInfoVmContainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Slf4j
@Component
@Profile("GeCo_VM")
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

            cacheVirtualMachineService.getAllVMInstancesFromInMemory().addAll(optimize.getVirtualMachineInstances());
            cacheContainerService.getAllContainerInstances().addAll(optimize.getContainers());
            processStepService.getAllProcessSteps().addAll(optimize.getProcessSteps());

            cleanup();
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

    private void cleanup() {
        provisioningSchedule.cleanup();

        Set<UUID> usedContainer = provisioningSchedule.getContainersMap().keySet();//processStepService.getAllProcessSteps().stream().map(processStep -> processStep.getContainer().getUid()).collect(Collectors.toSet());
        Set<UUID> usedVMs = provisioningSchedule.getVirtualMachineInstancesMap().keySet();//processStepService.getAllProcessSteps().stream().map(processStep -> processStep.getContainer().getVirtualMachineInstance().getUid()).collect(Collectors.toSet());
        cacheContainerService.getAllContainerInstances().removeIf(entry -> !usedContainer.contains(entry.getInternId()) && (entry.getContainerStatus().equals(ContainerStatus.SCHEDULED) || entry.getContainerStatus().equals(ContainerStatus.UNUSED)));
        cacheVirtualMachineService.getAllVMInstancesFromInMemory().removeIf(entry -> !usedVMs.contains(entry.getInternId()) && (entry.getVirtualMachineStatus().equals(VirtualMachineStatus.SCHEDULED) || entry.getVirtualMachineStatus().equals(VirtualMachineStatus.UNUSED)));

    }

}
