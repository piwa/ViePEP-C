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

    private double leasingCostFactor = 1;
    private double penaltyTimeFactor = 0.01;
    private double earlyEnactmentTimeFactor = 0.000001;

    private double cpuCost = 14; // dollar cost for 1 vCPU for 1 second
    private double ramCost =  3; // dollar cost for 1 GB for 1 second

    @Getter private double leasingCost = 0;
    @Getter private double penaltyCost = 0;
    @Getter private double earlyEnactmentCost = 0;

    @Override
    public double getFitness(Chromosome chromosome, List<? extends Chromosome> list) {

        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = new ArrayList<>();
        List<Chromosome.Gene> lastGeneOfProcessList = new ArrayList<>();
        optimizationUtility.getRequiredServiceTypesAndLastElements(chromosome, requiredServiceTypeList, lastGeneOfProcessList);

        // calculate the leasing cost
        double leasingCost = 0;
        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : requiredServiceTypeList) {
            try {
                Duration deploymentDuration = serviceTypeSchedulingUnit.getDeploymentInterval().toDuration();
                ContainerConfiguration containerConfiguration = optimizationUtility.getContainerConfiguration(serviceTypeSchedulingUnit.getServiceType());
                leasingCost = leasingCost + (containerConfiguration.getCores() * cpuCost * deploymentDuration.getStandardSeconds()  + containerConfiguration.getRam() / 1000 * ramCost * deploymentDuration.getStandardSeconds()) * leasingCostFactor;
            } catch (ContainerConfigurationNotFoundException e) {
                log.error("Exception", e);
            }
        }

        // calculate penalty cost
        double penaltyCost = 0;
        for (Chromosome.Gene lastGeneOfProcess : lastGeneOfProcessList) {
            // get deadline of workflow
            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(lastGeneOfProcess.getProcessStep().getWorkflowName());
            if(workflowElement != null) {
                DateTime deadline = workflowElement.getDeadlineDateTime();

                if (lastGeneOfProcess.getExecutionInterval().getEnd().isAfter(deadline)) {
                    Duration duration = new Duration(deadline, lastGeneOfProcess.getExecutionInterval().getEnd());
                    penaltyCost = penaltyCost + workflowElement.getPenalty() * duration.getMillis() * penaltyTimeFactor;
                }
            }
        }

        // prefer earlier enactments
        double earlyEnactmentCost = 0;
        for (Chromosome.Gene lastGeneOfProcess : lastGeneOfProcessList) {
            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(lastGeneOfProcess.getProcessStep().getWorkflowName());
            if(workflowElement != null) {
                DateTime deadline = workflowElement.getDeadlineDateTime();

                if (lastGeneOfProcess.getExecutionInterval().getEnd().isBefore(deadline)) {
                    Duration duration = new Duration(lastGeneOfProcess.getExecutionInterval().getEnd(), deadline);

                    double cost = leasingCost / 100 - duration.getMillis() * earlyEnactmentTimeFactor;
                    if(cost < 0) {
                        cost = 0;
                    }

//                    earlyEnactmentCost = earlyEnactmentCost + cost;      // if duration is big its good
                }
            }
        }

        this.leasingCost = leasingCost;
        this.penaltyCost = penaltyCost;
        this.earlyEnactmentCost = earlyEnactmentCost;

        return leasingCost + penaltyCost + earlyEnactmentCost;
    }



    @Override
    public boolean isNatural() {
        return false;
    }



}
