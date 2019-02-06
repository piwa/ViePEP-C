package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.uncommons.watchmaker.framework.FitnessEvaluator;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@SuppressWarnings("Duplicates")
public class FitnessFunctionStartTime implements FitnessEvaluator<Chromosome> {

    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;

    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;

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

        double leasingCost = 0;

        // calculate Container leasing cost
        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = optimizationUtility.getRequiredServiceTypes(chromosome, false);
        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : requiredServiceTypeList) {
            Duration deploymentDuration = serviceTypeSchedulingUnit.getServiceAvailableTime().toDuration();
            ContainerConfiguration containerConfiguration = serviceTypeSchedulingUnit.getContainer().getContainerConfiguration();
            leasingCost = leasingCost + (containerConfiguration.getCores() * cpuCost * deploymentDuration.getStandardSeconds() + containerConfiguration.getRam() / 1000 * ramCost * deploymentDuration.getStandardSeconds()) * leasingCostFactor;
        }

        // calculate penalty cost
        Map<String, Chromosome.Gene> lastGeneOfProcessList = optimizationUtility.getLastElements(chromosome);
        double penaltyCost = 0;
        for (Chromosome.Gene lastGeneOfProcess : lastGeneOfProcessList.values()) {
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
