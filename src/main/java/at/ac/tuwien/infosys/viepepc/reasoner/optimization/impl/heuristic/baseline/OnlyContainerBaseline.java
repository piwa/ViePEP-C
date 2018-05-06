package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.baseline;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.factory.SimpleFactory;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.withvm.AbstractHeuristicImpl;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OnlyContainerBaseline extends AbstractHeuristicImpl implements ProcessInstancePlacementProblem {

    @Autowired
    private OptimizationUtility optimizationUtility;

    @Value("${container.default.startup.time}")
    private long defaultContainerStartupTime;
    @Value("${container.default.deploy.time}")
    private long defaultContainerDeployTime;

    private DateTime optimizationTime;

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {


        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();

        this.optimizationTime = DateTime.now();

        if (workflowElements.size() == 0) {
            return new OptimizationResultImpl();
        }

        SimpleFactory factory = new SimpleFactory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime);

        return createOptimizationResult(new Chromosome(factory.getTemplate()), workflowElements);
    }

    private OptimizationResult createOptimizationResult(Chromosome winner, List<WorkflowElement> workflowElements) {
        OptimizationResult optimizationResult = new OptimizationResultImpl() ;

        List<Element> flattenWorkflowList = new ArrayList<>();
        for (WorkflowElement workflowElement : workflowElements) {
            placementHelper.getFlattenWorkflow(flattenWorkflowList, workflowElement);
        }

        System.out.println(winner.toString());

        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnitList = optimizationUtility.getRequiredServiceTypes(winner);
        Duration duration = new Duration(optimizationTime, DateTime.now());

        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnitList) {

            try {

                Container container = optimizationUtility.getContainer(serviceTypeSchedulingUnit.getServiceType());
                container.setBareMetal(true);

                for (Chromosome.Gene processStepGene : serviceTypeSchedulingUnit.getProcessSteps()) {
                    ProcessStep processStep = processStepGene.getProcessStep();
                    if(processStep.getStartDate() != null && processStep.getScheduledAtContainer() != null && (processStep.getScheduledAtContainer().isRunning() == true || processStep.getScheduledAtContainer().isDeploying() == true)) {

                    }
                    else {
                        DateTime scheduledStartTime = processStepGene.getExecutionInterval().getStart();
                        scheduledStartTime = scheduledStartTime.plus(duration);

                        ProcessStep realProcessStep = null;

                        for (Element element : flattenWorkflowList) {
                            if(element instanceof ProcessStep && ((ProcessStep) element).getInternId().equals(processStep.getInternId())) {
                                realProcessStep = (ProcessStep) element;
                            }
                        }

                        if(realProcessStep == null) {
                            log.error("Big Problem");
                        }
                        else {
                            boolean alreadyDeploying = false;
                            if(realProcessStep.getScheduledAtContainer() != null && (realProcessStep.getScheduledAtContainer().isDeploying() || realProcessStep.getScheduledAtContainer().isRunning())) {
                                alreadyDeploying = true;
                            }

                            if(realProcessStep.getStartDate() == null && !alreadyDeploying) {
                                realProcessStep.setScheduledForExecution(true, scheduledStartTime, container);
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

    @Override
    public void initializeParameters() {

    }
}
