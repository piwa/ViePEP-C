package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.uncommons.watchmaker.framework.FitnessEvaluator;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@SuppressWarnings("Duplicates")
public class FitnessFunction implements FitnessEvaluator<Chromosome> {

    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;

    @Value("${fitness.leasing.cost.factor}")
    private double leasingCostFactor = 10;
    @Value("${fitness.penalty.time.factor}")
    private double penaltyTimeFactor = 0.001;

    @Value("${fitness.cost.cpu}")
    private double cpuCost = 14; // dollar cost for 1 vCPU for 1 second
    @Value("${fitness.cost.ram}")
    private double ramCost = 3; // dollar cost for 1 GB for 1 second

    @Getter
    private double leasingCost = 0;
    @Getter
    private double penaltyCost = 0;
    @Getter
    private double earlyEnactmentCost = 0;

    @Setter
    private DateTime optimizationEndTime;

    @Override
    public double getFitness(Chromosome chromosome, List<? extends Chromosome> list) {

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome);

        Map<String, Chromosome.Gene> lastGeneOfProcessList = optimizationUtility.getLastElements(chromosome);
        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = genes.stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());
        List<VirtualMachineInstance> runningButNotUsedVirtualMachineInstances;
        List<VirtualMachineInstance> scheduledVirtualMachineInstances = virtualMachineSchedulingUnits.stream().map(VirtualMachineSchedulingUnit::getVirtualMachineInstance).collect(Collectors.toList());

        runningButNotUsedVirtualMachineInstances = cacheVirtualMachineService.getDeployingAndDeployedVMInstances().stream().filter(virtualMachine -> !scheduledVirtualMachineInstances.contains(virtualMachine)).collect(Collectors.toList());

        // TODO prefer running VMs
        // calculate the leasing cost
        double leasingCost = 0;
        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
            VMType vmType = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
            Duration cloudResourceUsageDuration = new Duration(virtualMachineSchedulingUnit.getCloudResourceUsageInterval());
            leasingCost = leasingCost + (vmType.getCores() * cpuCost * cloudResourceUsageDuration.getStandardSeconds() + vmType.getRamPoints() / 1000 * ramCost * cloudResourceUsageDuration.getStandardSeconds()) * leasingCostFactor;

        }
        for (VirtualMachineInstance runningButNotUsedVirtualMachineInstance : runningButNotUsedVirtualMachineInstances) {
            VMType vmType = runningButNotUsedVirtualMachineInstance.getVmType();
            Duration cloudResourceUsageDuration = new Duration(runningButNotUsedVirtualMachineInstance.getDeploymentStartTime(), this.optimizationEndTime);
            leasingCost = leasingCost + (vmType.getCores() * cpuCost * cloudResourceUsageDuration.getStandardSeconds() + vmType.getRamPoints() / 1000 * ramCost * cloudResourceUsageDuration.getStandardSeconds()) * leasingCostFactor;
        }

        // calculate penalty cost
        double penaltyCost = 0;
        for (Chromosome.Gene lastGeneOfProcess : lastGeneOfProcessList.values()) {
            // get deadline of workflow
            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(lastGeneOfProcess.getProcessStepSchedulingUnit().getWorkflowName());
            if (workflowElement != null) {
                DateTime deadline = workflowElement.getDeadlineDateTime();
                if (lastGeneOfProcess.getExecutionInterval().getEnd().isAfter(deadline)) {
                    Duration duration = new Duration(deadline, lastGeneOfProcess.getExecutionInterval().getEnd());
                    penaltyCost = penaltyCost + workflowElement.getPenalty() * duration.getMillis() * penaltyTimeFactor;
                }
            }
        }


        this.leasingCost = leasingCost;
        this.penaltyCost = penaltyCost;

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome);

        return leasingCost + penaltyCost;
    }

    @Override
    public boolean isNatural() {
        return false;
    }

    public void setOptimizationEndTime(DateTime optimizationEndTime) {
        this.optimizationEndTime = optimizationEndTime;
    }

}
