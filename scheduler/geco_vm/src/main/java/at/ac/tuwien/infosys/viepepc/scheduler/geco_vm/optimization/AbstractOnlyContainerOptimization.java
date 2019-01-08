package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStepStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations.FitnessFunction;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.stream.Collectors;

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

    protected OptimizationResult createOptimizationResult(Chromosome winner, List<WorkflowElement> workflowElements, EvolutionLogger evolutionLogger) {


//        List<Element> flattenWorkflowList = new ArrayList<>();
//        for (WorkflowElement workflowElement : workflowElements) {
//            workflowUtilities.getFlattenWorkflow(flattenWorkflowList, workflowElement);
//        }

        fitnessFunction.getFitness(winner, null);
        StringBuilder builder = new StringBuilder();
        builder.append("Optimization Result:\n--------------------------- Winner Chromosome ---------------------------- \n").append(winner.toString()).append("\n");
        builder.append("----------------------------- Winner Fitness -----------------------------\n");
        builder.append("Leasing=").append(fitnessFunction.getLeasingCost()).append("\n");
        builder.append("Penalty=").append(fitnessFunction.getPenaltyCost()).append("\n");
        builder.append("Early Enactment=").append(fitnessFunction.getEarlyEnactmentCost()).append("\n");
        builder.append("Total Fitness=").append(fitnessFunction.getLeasingCost() + fitnessFunction.getPenaltyCost() + fitnessFunction.getEarlyEnactmentCost()).append("\n");
        builder.append("----------------------------- Algorithm Stats -----------------------------\n");
        builder.append("Generation Amount=" + evolutionLogger.getAmountOfGenerations());
        log.info(builder.toString());

        orderMaintainer.checkRowAndPrintError(winner, this.getClass().getSimpleName(), slackWebhook);

        OptimizationResult optimizationResult = new OptimizationResult();

        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>();
        for (Chromosome.Gene gene : winner.getFlattenChromosome()) {
            processStepSchedulingUnits.add(gene.getProcessStepSchedulingUnit());
            ProcessStep processStep = gene.getProcessStepSchedulingUnit().getProcessStep();
            Container container = gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getContainer();
            processStep.setScheduledStartDate(gene.getExecutionInterval().getStart());
            processStep.setProcessStepStatus(ProcessStepStatus.SCHEDULED);
            processStep.setContainer(container);

            optimizationResult.getProcessSteps().add(processStep);
        }

        Set<ContainerSchedulingUnit> containerSchedulingUnits = processStepSchedulingUnits.stream().map(ProcessStepSchedulingUnit::getContainerSchedulingUnit).collect(Collectors.toSet());
        for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits) {
            Container container = containerSchedulingUnit.getContainer();
            container.setScheduledCloudResourceUsage(containerSchedulingUnit.getCloudResourceUsage());
            container.setScheduledAvailableInterval(containerSchedulingUnit.getServiceAvailableTime());
            container.setContainerStatus(ContainerStatus.SCHEDULED);
            container.setVirtualMachineInstance(containerSchedulingUnit.getScheduledOnVm().getVirtualMachineInstance());

            optimizationResult.getContainers().add(container);
        }

        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = containerSchedulingUnits.stream().map(ContainerSchedulingUnit::getScheduledOnVm).collect(Collectors.toSet());
        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
            VirtualMachineInstance virtualMachineInstance = virtualMachineSchedulingUnit.getVirtualMachineInstance();
            virtualMachineInstance.setScheduledCloudResourceUsage(virtualMachineSchedulingUnit.getCloudResourceUsageInterval());
            virtualMachineInstance.setScheduledAvailableInterval(virtualMachineSchedulingUnit.getVmAvailableInterval());
            virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.SCHEDULED);
            virtualMachineSchedulingUnit.getScheduledContainers().forEach(container -> virtualMachineInstance.getDeployedContainers().add(container.getContainer()));

            optimizationResult.getVirtualMachineInstances().add(virtualMachineInstance);
        }

        return optimizationResult;
    }


    public List<WorkflowElement> getRunningWorkflowInstancesSorted() {
        List<WorkflowElement> list = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        list.sort(Comparator.comparing(Element::getDeadline));
        return list;
    }

}
