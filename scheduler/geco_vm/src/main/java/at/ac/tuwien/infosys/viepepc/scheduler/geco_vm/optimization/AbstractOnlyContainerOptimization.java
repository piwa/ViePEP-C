package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
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
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations.FitnessFunctionStartTime;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations.FitnessFunctionVM;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("Duplicates")
public abstract class AbstractOnlyContainerOptimization {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheContainerService cacheContainerService;
    @Autowired
    private CacheProcessStepService processStepService;
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

    protected OptimizationResult createOptimizationResult(Chromosome winner, List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits) {

        fitnessFunctionStartTime.getFitness(winner, null);
        fitnessFunctionVM.getFitness(new Chromosome2(allServiceTypeSchedulingUnits), null);
        StringBuilder builder = new StringBuilder();
        builder.append("Optimization Result:\n--------------------------- Winner Chromosome ---------------------------- \n").append(winner.toString()).append("\n");
        builder.append("----------------------------- Winner Fitness -----------------------------\n");
        builder.append("Leasing startTime=").append(fitnessFunctionStartTime.getLeasingCost()).append("\n");
        builder.append("Leasing VM=").append(fitnessFunctionVM.getLeasingCost()).append("\n");
        builder.append("Penalty=").append(fitnessFunctionStartTime.getPenaltyCost()).append("\n");
        builder.append("Total Fitness=").append(fitnessFunctionStartTime.getLeasingCost() + fitnessFunctionVM.getLeasingCost() + fitnessFunctionStartTime.getPenaltyCost() + fitnessFunctionStartTime.getEarlyEnactmentCost()).append("\n");
        builder.append("----------------------------- Chromosome Checks --------------------------\n");
        boolean notEnoughSpace = vmSelectionHelper.checkIfVMIsTooSmall(allServiceTypeSchedulingUnits, "createOptimizationResult");

        if(!notEnoughSpace) {
            builder.append("Space is ok").append("\n");
        }
        orderMaintainer.checkRowAndPrintError(winner, this.getClass().getSimpleName(), "createOptimizationResult");
        builder.append("Order is ok").append("\n");

        log.info(builder.toString());

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

            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit();
            virtualMachineSchedulingUnit.getServiceTypeSchedulingUnits().add(serviceTypeSchedulingUnit);
            virtualMachineSchedulingUnits.add(virtualMachineSchedulingUnit);
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

//        setTimesIfNeeded(optimizationResult);

        return optimizationResult;
    }


    private void setTimesIfNeeded(OptimizationResult optimizationResult) {
        List<VirtualMachineInstance> fixedVirtualMachines = cacheVirtualMachineService.getDeployingAndDeployedVMInstances();
        for (VirtualMachineInstance virtualMachineInstance : optimizationResult.getVirtualMachineInstances()) {
            if(fixedVirtualMachines.contains(virtualMachineInstance)) {
                VirtualMachineInstance fixedVM = fixedVirtualMachines.get(fixedVirtualMachines.indexOf(virtualMachineInstance));
                Interval cloudInterval = virtualMachineInstance.getScheduledCloudResourceUsage().withStart(fixedVM.getScheduledCloudResourceUsage().getStart());
                virtualMachineInstance.setScheduledCloudResourceUsage(cloudInterval);
                Interval availableInterval = virtualMachineInstance.getScheduledAvailableInterval().withStart(fixedVM.getScheduledAvailableInterval().getStart());
                virtualMachineInstance.setScheduledAvailableInterval(availableInterval);
            }
        }

        List<Container> fixedContainers = cacheContainerService.getDeployingAndDeployedContainers();
        for (Container container : optimizationResult.getContainers()) {
            if(fixedContainers.contains(container)) {
                Container fixedContainer = fixedContainers.get(fixedContainers.indexOf(container));
                Interval cloudInterval = container.getScheduledCloudResourceUsage().withStart(fixedContainer.getScheduledCloudResourceUsage().getStart());
                container.setScheduledCloudResourceUsage(cloudInterval);
                Interval availableInterval = container.getScheduledAvailableInterval().withStart(fixedContainer.getScheduledAvailableInterval().getStart());
                container.setScheduledAvailableInterval(availableInterval);
            }
        }

        List<ProcessStep> fixedProcessSteps = processStepService.getDeployingProcessSteps();
        fixedProcessSteps.addAll(processStepService.getRunningProcessSteps());
        for (ProcessStep ps : optimizationResult.getProcessSteps()) {
            if(fixedProcessSteps.contains(ps)) {
                ProcessStep fixedPs = fixedProcessSteps.get(fixedProcessSteps.indexOf(ps));
                ps.setScheduledStartDate(fixedPs.getScheduledStartDate());
            }
        }

    }


    public List<WorkflowElement> getRunningWorkflowInstancesSorted() {
        List<WorkflowElement> list = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        list.sort(Comparator.comparing(Element::getDeadline));
        return list;
    }

}
