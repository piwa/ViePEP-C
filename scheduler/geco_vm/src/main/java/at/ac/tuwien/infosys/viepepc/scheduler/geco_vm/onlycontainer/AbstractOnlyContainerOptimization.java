package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.operations.FitnessFunction;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@SuppressWarnings("Duplicates")
public abstract class AbstractOnlyContainerOptimization {

    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    protected WorkflowUtilities workflowUtilities;
    @Autowired
    protected FitnessFunction fitnessFunction;
    @Autowired
    protected OptimizationUtility optimizationUtility;
    @Value("${slack.webhook}")
    private String slackWebhook;

    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    protected DateTime optimizationEndTime;

    // TODO
    protected OptimizationResult createOptimizationResult(Chromosome winner, List<WorkflowElement> workflowElements) {
        OptimizationResult optimizationResult = new OptimizationResult();

        List<Element> flattenWorkflowList = new ArrayList<>();
        for (WorkflowElement workflowElement : workflowElements) {
            workflowUtilities.getFlattenWorkflow(flattenWorkflowList, workflowElement);
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

        List<ContainerSchedulingUnit> containerSchedulingUnitList = optimizationUtility.createRequiredContainerSchedulingUnits(winner);

        orderMaintainer.checkRowAndPrintError(winner, this.getClass().getSimpleName(), slackWebhook);

//        for (ContainerSchedulingUnit containerSchedulingUnits : containerSchedulingUnitList) {
//
//
//            ProcessStep psHasToDeployContainer = containerSchedulingUnits.getFirstGene().getProcessStep();
//
//            Container container = containerSchedulingUnits.getProcessStepGenes().get(0).getProcessStep().getContainer();
//            container.setBareMetal(false);
//
//            for (Chromosome.Gene processStepGene : containerSchedulingUnits.getProcessStepGenes()) {
//                if (!processStepGene.isFixed()) {
//                    ProcessStep processStep = processStepGene.getProcessStep();
//                    if (processStep.getStartTime() != null && (processStep.getContainer().isRunning() == true || processStep.getContainer().isDeploying() == true)) {
//
//                    } else {
//                        DateTime scheduledStartTime = processStepGene.getExecutionInterval().getStart();
//
//                        ProcessStep realProcessStep = null;
//
//                        for (Element element : flattenWorkflowList) {
//                            if (element instanceof ProcessStep && ((ProcessStep) element).getInternId().equals(processStep.getInternId())) {
//                                realProcessStep = (ProcessStep) element;
//                            }
//                        }
//
//                        boolean alreadyDeploying = false;
//                        if (realProcessStep.getContainer() != null && (realProcessStep.getContainer().isDeploying() || realProcessStep.getContainer().isRunning())) {
//
//                            if (realProcessStep.getContainer().getStartedAt().isAfter(optimizationEndTime)) {
//                                alreadyDeploying = true;
//                            }
//
//                        }
//
//                        if (realProcessStep.getStartTime() == null && !alreadyDeploying) {
//                            realProcessStep.setScheduledForExecution(true, scheduledStartTime, container);
//                            if (psHasToDeployContainer.getInternId().equals(processStep.getInternId())) {
//                                realProcessStep.setHasToDeployContainer(true);
//                            } else {
//                                realProcessStep.setHasToDeployContainer(false);
//                            }
//                            optimizationResult.addProcessStep(realProcessStep);
//                        }
//                    }
//                }
//            }
//        }

        return optimizationResult;
    }


    public List<WorkflowElement> getRunningWorkflowInstancesSorted() {
        List<WorkflowElement> list = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        list.sort(Comparator.comparing(Element::getDeadline));
        return list;
    }

}
