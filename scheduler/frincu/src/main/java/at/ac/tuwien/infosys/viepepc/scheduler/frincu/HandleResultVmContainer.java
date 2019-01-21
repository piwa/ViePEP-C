package at.ac.tuwien.infosys.viepepc.scheduler.frincu;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutor;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.HandleOptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.PrintRunningInfoVmContainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Scope("prototype")
@Component
@Slf4j
@Profile("Frincu")
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
//        optimize.getProcessSteps().stream().filter(ps -> ps.getScheduledAtVM() != null).forEach(ps -> waitingForExecutingVirtualMachines.add(ps.getScheduledAtVM()));
//        optimize.getProcessSteps().stream().filter(ps -> ps.get().getVirtualMachineInstance() != null).forEach(ps -> waitingForExecutingVirtualMachines.add(ps.get().getVirtualMachineInstance()));

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
        Set<VirtualMachine> vmsToStart = new HashSet<>();
        Set<Container> containersToDeploy = new HashSet<>();
        processProcessSteps(optimize, vmsToStart, containersToDeploy, tau_t);
        stringBuilder.append("----------- VM should be used (running or has to be started): ------------\n");
        for (VirtualMachine virtualMachine : vmsToStart) {
            stringBuilder.append(virtualMachine).append("\n");
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

    private void processProcessSteps(OptimizationResult optimize, Set<VirtualMachine> vmsToStart, Set<Container> containersToDeploy, DateTime tau_t) {
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            if (processStep.getScheduledAtVM() != null) {
                vmsToStart.add(processStep.getScheduledAtVM());
            }
            if (processStep.getContainer().getVirtualMachine() != null) {
                vmsToStart.add(processStep.getContainer().getVirtualMachine());
            }
            containersToDeploy.add(processStep.getContainer());
            if (processStep.getContainer() != null) {
                processStep.setScheduledForExecution(true, tau_t, processStep.getContainer());
            } else if (processStep.getScheduledAtVM() != null) {
                processStep.setScheduledForExecution(true, tau_t, processStep.getScheduledAtVM());
            } else {
                processStep.setScheduledForExecution(false, new DateTime(0), (Container) null);
            }
        }
    }

}
