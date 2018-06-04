package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.gpedro.integrations.slack.SlackApi;
import net.gpedro.integrations.slack.SlackMessage;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
public abstract class AbstractOnlyContainerOptimization {

    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    protected PlacementHelper placementHelper;
    @Autowired
    protected FitnessFunction fitnessFunction;
    @Autowired
    protected OptimizationUtility optimizationUtility;

    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    protected DateTime optimizationTime;

    protected OptimizationResult createOptimizationResult(Chromosome winner, List<WorkflowElement> workflowElements) {
        OptimizationResult optimizationResult = new OptimizationResultImpl() ;

        List<Element> flattenWorkflowList = new ArrayList<>();
        for (WorkflowElement workflowElement : workflowElements) {
            placementHelper.getFlattenWorkflow(flattenWorkflowList, workflowElement);
        }

        fitnessFunction.getFitness(winner, null);
        StringBuilder builder = new StringBuilder();
        builder.append("Optimization Result:\n--------------------------- Winner Chromosome ---------------------------- \n").append(winner.toString()).append("\n");
        builder.append("----------------------------- Winner Fitness -----------------------------\n");
        builder.append("Leasing=").append(fitnessFunction.getLeasingCost()).append("\n");
        builder.append("Penalty=").append(fitnessFunction.getPenaltyCost()).append("\n");
        builder.append("Early Enactment=").append(fitnessFunction.getEarlyEnactmentCost()).append("\n");
        builder.append("Total Fitness=").append(fitnessFunction.getLeasingCost() + fitnessFunction.getPenaltyCost() + fitnessFunction.getEarlyEnactmentCost()).append("\n");
        log.info(builder.toString());

        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnitList = optimizationUtility.getRequiredServiceTypes(winner);
        Duration duration = new Duration(optimizationTime, DateTime.now());

        orderMaintainer.checkRowAndPrintError(winner, this.getClass().getSimpleName());

        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnitList) {

            try {

                ProcessStep psHasToDeployContainer = serviceTypeSchedulingUnit.getFirstGene().getProcessStep();

                List<Container> containers = optimizationUtility.getContainer(serviceTypeSchedulingUnit.getServiceType(), serviceTypeSchedulingUnit.getProcessSteps().size());
                containers.forEach(c -> c.setBareMetal(true));

                for (Chromosome.Gene processStepGene : serviceTypeSchedulingUnit.getProcessSteps()) {
                    if(!processStepGene.isFixed()) {
                        ProcessStep processStep = processStepGene.getProcessStep();
                        if (processStep.getStartDate() != null && processStep.getScheduledAtContainer() != null && (processStep.getScheduledAtContainer().isRunning() == true || processStep.getScheduledAtContainer().isDeploying() == true)) {

                        } else {
                            DateTime scheduledStartTime = processStepGene.getExecutionInterval().getStart();

                            ProcessStep realProcessStep = null;

                            for (Element element : flattenWorkflowList) {
                                if (element instanceof ProcessStep && ((ProcessStep) element).getInternId().equals(processStep.getInternId())) {
                                    realProcessStep = (ProcessStep) element;
                                }
                            }

                            boolean alreadyDeploying = false;
                            if (realProcessStep.getScheduledAtContainer() != null && (realProcessStep.getScheduledAtContainer().isDeploying() || realProcessStep.getScheduledAtContainer().isRunning())) {
                                alreadyDeploying = true;
                            }

                            if (realProcessStep.getStartDate() == null && !alreadyDeploying) {
                                realProcessStep.setScheduledForExecution(true, scheduledStartTime, containers.remove(0));
                                if(psHasToDeployContainer.getInternId().equals(processStep.getInternId())) {
                                    realProcessStep.setHasToDeployContainer(true);
                                }
                                else {
                                    realProcessStep.setHasToDeployContainer(false);
                                }
                                optimizationResult.addProcessStep(realProcessStep);
                            }
                        }
                    }
                }

            } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException e) {
                log.error("Exception", e);
            }

        }

        return optimizationResult;
    }


    public List<WorkflowElement> getRunningWorkflowInstancesSorted() {
        List<WorkflowElement> list = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        list.sort(Comparator.comparing(Element::getDeadline));
        return list;
    }

}
