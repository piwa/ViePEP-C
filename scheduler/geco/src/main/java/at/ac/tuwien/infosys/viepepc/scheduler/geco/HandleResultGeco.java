package at.ac.tuwien.infosys.viepepc.scheduler.geco;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutor;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.scheduler.library.HandleOptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
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
@Profile("GeCo")
public class HandleResultGeco implements HandleOptimizationResult {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private ActionExecutor actionExecutor;

    //    private Set<Container> waitingForExecutingContainers = new HashSet<>();
    private boolean printRunningInformation = true;

    @Override
    public Boolean processResults(OptimizationResult optimize, DateTime tau_t) {

        inMemoryCache.getWaitingForExecutingProcessSteps().addAll(optimize.getProcessSteps());
//        optimize.getProcessSteps().stream().filter(ps -> ps.get() != null).forEach(ps -> waitingForExecutingContainers.add(ps.get()));

        actionExecutor.startTimedInvocationViaContainers(optimize.getProcessSteps());

        if (printRunningInformation) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Optimization result:\n");
            printOptimizationResultInformation(optimize, tau_t, stringBuilder);
            log.debug(stringBuilder.toString());
        }

        return true;
    }

    private void printOptimizationResultInformation(OptimizationResult optimize, DateTime tau_t, StringBuilder stringBuilder) {
        Set<Container> containersToDeploy = new HashSet<>();
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            containersToDeploy.add(processStep.getContainer());
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

}
