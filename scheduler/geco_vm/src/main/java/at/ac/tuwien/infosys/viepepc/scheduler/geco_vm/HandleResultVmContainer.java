package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutor;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.scheduler.library.HandleOptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.PrintRunningInfoVmContainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Slf4j
@Component
@Profile("GeCo_VM")
public class HandleResultVmContainer implements HandleOptimizationResult {

    @Autowired
    private ActionExecutor actionExecutor;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private PrintRunningInfoVmContainer printRunningInformationVmContainer;

//    private Set<VirtualMachineInstance> waitingForExecutingVirtualMachines = new HashSet<>();

    private boolean printRunningInformation = true;

    @Override
    public Boolean processResults(OptimizationResult optimize, DateTime tau_t) {

        inMemoryCache.getWaitingForExecutingProcessSteps().addAll(optimize.getProcessSteps());
//        optimize.getProcessStepGenes().stream().filter(ps -> ps.getScheduledAtVM() != null).forEach(ps -> waitingForExecutingVirtualMachines.add(ps.getScheduledAtVM()));
//        optimize.getProcessStepGenes().stream().filter(ps -> ps.getContainer().getVirtualMachineInstance() != null).forEach(ps -> waitingForExecutingVirtualMachines.add(ps.getContainer().getVirtualMachineInstance()));

        actionExecutor.startInvocationViaContainersOnVms(optimize.getProcessSteps());

        printRunningInformationVmContainer.printRunningInformation();

        if (printRunningInformation) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Optimization result:\n");
            printOptimizationResultInformation(optimize, tau_t, stringBuilder);
            log.debug(stringBuilder.toString());
        }

        return true;
    }

    private void printOptimizationResultInformation(OptimizationResult optimize, DateTime tau_t, StringBuilder stringBuilder) {
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
    }

    private void processProcessSteps(OptimizationResult optimize, Set<VirtualMachineInstance> vmsToStart, Set<Container> containersToDeploy, DateTime tau_t) {
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            if (processStep.getContainer().getVirtualMachineInstance() != null) {
                vmsToStart.add(processStep.getContainer().getVirtualMachineInstance());
            }
            containersToDeploy.add(processStep.getContainer());

            processStep.setScheduledForExecution(tau_t, processStep.getContainer());

        }
    }

}
