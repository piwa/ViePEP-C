package at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.heuristic.onlycontainer.baseline;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.heuristic.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.heuristic.onlycontainer.AbstractOnlyContainerOptimization;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.heuristic.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.heuristic.onlycontainer.factory.DeadlineAwareFactory;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@Slf4j
public class OnlyContainerBaseline extends AbstractOnlyContainerOptimization implements ProcessInstancePlacementProblem {
//public class OnlyContainerBaseline extends AbstractContainerProvisioningImpl implements ProcessInstancePlacementProblem {

    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private CacheContainerService containerService;

    @Value("${container.default.startup.time}")
    private long defaultContainerStartupTime;
    @Value("${container.default.deploy.time}")
    private long defaultContainerDeployTime;

    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;

    @Value("${deadline.aware.factory.allowed.penalty.points}")
    private int allowedPenaltyPoints;
    @Value("${slack.webhook}")
    private String slackWebhook;

    private DateTime optimizationTime;

    private Map<Container, List<ProcessStep>> containerProcessMap = new HashMap<>();

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {


        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();
        List<Container> runningContainers = inMemoryCache.getRunningContainers();

        this.optimizationTime = DateTime.now();

        if (workflowElements.size() == 0) {
            return new OptimizationResultImpl();
        }

        DeadlineAwareFactory factory = new DeadlineAwareFactory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime, false, optimizationUtility, onlyContainerDeploymentTime, allowedPenaltyPoints, slackWebhook);

        return createOptimizationResult(new Chromosome(factory.getTemplate()), workflowElements);

        /*
        OptimizationResult optimizationResult = new OptimizationResultImpl();

        try {
            List<WorkflowElement> nextWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(nextWorkflowInstances);
            List<ProcessStep> runningSteps = getAllRunningSteps(nextWorkflowInstances);
            List<Container> runningContainers = inMemoryCache.getRunningContainers();

            if (nextProcessSteps == null || nextProcessSteps.size() == 0) {
                return optimizationResult;
            }

            for (ProcessStep processStep : nextProcessSteps) {
                boolean deployed = false;
                Container container = getContainer(processStep);

                boolean containerFound = false;
                for (Container runningContainer : runningContainers) {
                    if (runningContainer.getContainerImage().getServiceType().getName().equalsIgnoreCase(container.getContainerImage().getServiceType().getName())) {

                        double requiredCpu = 0;
                        double requiredRam = 0;
                        for (ProcessStep runningStep : runningSteps) {
                            if (runningStep.getScheduledAtContainer() == runningContainer) {
                                requiredCpu = requiredCpu + runningStep.getServiceType().getServiceTypeResources().getCpuLoad();
                                requiredRam = requiredRam + runningStep.getServiceType().getServiceTypeResources().getMemory();
                            }
                        }

                        if(containerProcessMap.containsKey(runningContainer)) {
                            for (ProcessStep runningStep : containerProcessMap.get(runningContainer)) {
                                if (runningStep.getScheduledAtContainer() == runningContainer) {
                                    requiredCpu = requiredCpu + runningStep.getServiceType().getServiceTypeResources().getCpuLoad();
                                    requiredRam = requiredRam + runningStep.getServiceType().getServiceTypeResources().getMemory();
                                }
                            }
                        }

                        requiredCpu = requiredCpu + processStep.getServiceType().getServiceTypeResources().getCpuLoad();
                        requiredRam = requiredRam + processStep.getServiceType().getServiceTypeResources().getMemory();

                        if (requiredCpu <= runningContainer.getContainerConfiguration().getCPUPoints() && requiredRam <= runningContainer.getContainerConfiguration().getRam()) {
                            container = runningContainer;
                            containerFound = true;
                            break;
                        }
                    }
                }
                if(!containerFound) {
                    runningContainers.add(container);
                }

                if(!containerProcessMap.containsKey(container)) {
                    containerProcessMap.put(container, new ArrayList<>());
                }
                containerProcessMap.get(container).add(processStep);

                processStep.setScheduledAtContainer(container);

                optimizationResult.addProcessStep(processStep);
            }
        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found");
            throw new ProblemNotSolvedException();
        } catch (Exception ex) {
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
*/

    }

    @Override
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return null;
    }


    @Override
    public void initializeParameters() {

    }
}
