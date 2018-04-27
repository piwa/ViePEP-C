package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uncommons.watchmaker.framework.FitnessEvaluator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class FitnessFunction implements FitnessEvaluator<Chromosome> {

    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;

    private double penaltyTimeFactor = 0.000001;

    private double cpuCost = 0.00001406; // dollar cost for 1 vCPU for 1 second
    private double ramCost = 0.00000353; // dollar cost for 1 GB for 1 second

    @Override
    public double getFitness(Chromosome chromosome, List<? extends Chromosome> list) {

        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = new ArrayList<>();
        List<Chromosome.Gene> lastGeneOfProcessList = new ArrayList<>();
        optimizationUtility.getRequiredServiceTypesAndLastElements(chromosome, requiredServiceTypeList, lastGeneOfProcessList);

        // calculate the leasing cost
        double leasingCost = 0;
        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : requiredServiceTypeList) {
            try {
                ContainerConfiguration containerConfiguration = optimizationUtility.getContainerConfiguration(serviceTypeSchedulingUnit.getServiceType());
//                VMType vmType = optimizationUtility.getFittingVirtualMachine(container.getContainerConfiguration());

//                leasingCost = leasingCost + vmType.getCosts() * optimizationUtility.getAmountOfLeasingBTUs(vmType, serviceTypeSchedulingUnit.getDeploymentInterval());
                leasingCost = leasingCost + containerConfiguration.getCores() * cpuCost + containerConfiguration.getRam() / 1000 * ramCost;
            } catch (ContainerConfigurationNotFoundException e) {
                log.error("Exception", e);
            }
        }

        // calculate penalty cost
        double penaltyCost = 0;
        for (Chromosome.Gene lastGeneOfProcess : lastGeneOfProcessList) {
            // get deadline of workflow
            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(lastGeneOfProcess.getProcessStep().getWorkflowName());
            DateTime deadline = workflowElement.getDeadlineDateTime();

            if(lastGeneOfProcess.getExecutionInterval().getEnd().isAfter(deadline)) {
                Duration duration = new Duration(lastGeneOfProcess.getExecutionInterval().getEnd(), deadline);
                penaltyCost = penaltyCost + workflowElement.getPenalty() * duration.getMillis() * penaltyTimeFactor;
            }

        }

        double overallCost = leasingCost + penaltyCost;
        return overallCost;
    }



    @Override
    public boolean isNatural() {
        return false;
    }



}
