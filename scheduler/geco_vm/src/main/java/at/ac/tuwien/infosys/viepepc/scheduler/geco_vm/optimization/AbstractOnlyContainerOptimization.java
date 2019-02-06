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
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations.FitnessFunctionStartTime;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations.FitnessFunctionVM;
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
    protected FitnessFunctionStartTime fitnessFunctionStartTime;
    @Autowired
    protected FitnessFunctionVM fitnessFunctionVM;
    @Autowired
    protected OptimizationUtility optimizationUtility;
    @Autowired
    private VMSelectionHelper vmSelectionHelper;
    @Value("${slack.webhook}")
    private String slackWebhook;

    protected OrderMaintainer orderMaintainer = new OrderMaintainer();
    protected DateTime optimizationEndTime;

    protected OptimizationResult createOptimizationResult(Chromosome winner, List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits, EvolutionLogger evolutionLogger) {

        fitnessFunctionStartTime.getFitness(winner, null);
        fitnessFunctionVM.getFitness(winner, null);
        StringBuilder builder = new StringBuilder();
        builder.append("Optimization Result:\n--------------------------- Winner Chromosome ---------------------------- \n").append(winner.toString()).append("\n");
        builder.append("----------------------------- Winner Fitness -----------------------------\n");
        builder.append("Leasing startTime=").append(fitnessFunctionStartTime.getLeasingCost()).append("\n");
        builder.append("Leasing VM=").append(fitnessFunctionVM.getLeasingCost()).append("\n");
        builder.append("Penalty=").append(fitnessFunctionStartTime.getPenaltyCost()).append("\n");
//        builder.append("Early Enactment=").append(fitnessFunctionStartTime.getEarlyEnactmentCost()).append("\n");
        builder.append("Total Fitness=").append(fitnessFunctionStartTime.getLeasingCost() + fitnessFunctionVM.getLeasingCost() + fitnessFunctionStartTime.getPenaltyCost() + fitnessFunctionStartTime.getEarlyEnactmentCost()).append("\n");
        builder.append("----------------------------- Algorithm Stats ----------------------------\n");
        builder.append("Generation Amount=").append(evolutionLogger.getAmountOfGenerations()).append("\n");
        builder.append("----------------------------- Chromosome Checks --------------------------\n");
        boolean notEnoughSpace = false;
        Set<VirtualMachineSchedulingUnit> temp = winner.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toSet());
        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : temp) {
            if(!vmSelectionHelper.checkIfVirtualMachineIsBigEnough(virtualMachineSchedulingUnit)) {
                log.error("not enough space after the optimization on VM=" + virtualMachineSchedulingUnit);
                notEnoughSpace = true;
            }
        }
        if(!notEnoughSpace) {
            builder.append("Space is ok").append("\n");
        }
        orderMaintainer.checkRowAndPrintError(winner, this.getClass().getSimpleName(), "createOptimizationResult");
        builder.append("Order is ok").append("\n");

        log.info(builder.toString());

//        List<ProcessStepSchedulingUnit> processStepSchedulingUnit1s = winner.getFlattenChromosome().stream().map(Chromosome.Gene::getProcessStepSchedulingUnit).collect(Collectors.toList());
//        for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnit1s) {
//            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = processStepSchedulingUnit.getVirtualMachineSchedulingUnit();
//            if(!virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().contains(processStepSchedulingUnit)) {
//                log.error("A ProcessStep is defined for a VM but the VM does not contain it! (at=end); processStepSchedulingUnit=" + processStepSchedulingUnit + ", " + virtualMachineSchedulingUnit);
//            }
//        }

        OptimizationResult optimizationResult = new OptimizationResult();

        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = new HashSet<>();
        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>();
        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : allServiceTypeSchedulingUnits) {

            Container container = serviceTypeSchedulingUnit.getContainer();
            container.setScheduledCloudResourceUsage(serviceTypeSchedulingUnit.getCloudResourceUsage());
            container.setScheduledAvailableInterval(serviceTypeSchedulingUnit.getServiceAvailableTime());
            if(container.getContainerStatus().equals(ContainerStatus.UNUSED)) {
                container.setContainerStatus(ContainerStatus.SCHEDULED);
            }
            container.setVirtualMachineInstance(serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit().getVirtualMachineInstance());
            optimizationResult.getContainers().add(container);

            for (Chromosome.Gene gene : serviceTypeSchedulingUnit.getGenes()) {
                processStepSchedulingUnits.add(gene.getProcessStepSchedulingUnit());
                ProcessStep processStep = gene.getProcessStepSchedulingUnit().getProcessStep();
                processStep.setScheduledStartDate(gene.getExecutionInterval().getStart());
                if(processStep.getProcessStepStatus().equals(ProcessStepStatus.UNUSED)) {
                    processStep.setProcessStepStatus(ProcessStepStatus.SCHEDULED);
                }
                processStep.setContainer(container);

                optimizationResult.getProcessSteps().add(processStep);
            }

            virtualMachineSchedulingUnits.add(serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit());
        }

        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
            VirtualMachineInstance virtualMachineInstance = virtualMachineSchedulingUnit.getVirtualMachineInstance();
            virtualMachineInstance.setScheduledCloudResourceUsage(virtualMachineSchedulingUnit.getCloudResourceUsageInterval());
            virtualMachineInstance.setScheduledAvailableInterval(virtualMachineSchedulingUnit.getVmAvailableInterval());
            virtualMachineInstance.setVmType(virtualMachineSchedulingUnit.getVmType());
            if(virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.UNUSED)) {
                virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.SCHEDULED);
            }
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
