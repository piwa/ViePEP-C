package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.OptimizationUtility;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${fitness.leasing.cost.factor}")
    private double leasingCostFactor = 10;
    @Value("${fitness.penalty.time.factor}")
    private double penaltyTimeFactor = 0.001;

    private double earlyEnactmentTimeFactor = 0.000001;

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

    @Override
    public double getFitness(Chromosome chromosome, List<? extends Chromosome> list) {

        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = new ArrayList<>();
        Map<String, Chromosome.Gene> lastGeneOfProcessList = new HashMap<>();
        optimizationUtility.getRequiredServiceTypesAndLastElements(chromosome, requiredServiceTypeList, lastGeneOfProcessList);

        // calculate the leasing cost
        double leasingCost = 0;
        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : requiredServiceTypeList) {
            try {
                Duration deploymentDuration = serviceTypeSchedulingUnit.getServiceAvailableTime().toDuration();
//                List<Container> containers = optimizationUtility.get(serviceTypeSchedulingUnit.getServiceType(), serviceTypeSchedulingUnit.getProcessSteps().size());
                Container container = optimizationUtility.getContainer(serviceTypeSchedulingUnit.getServiceType(), serviceTypeSchedulingUnit.getProcessSteps().size());

//                List<Container> containers2 = containers.stream().distinct().collect(Collectors.toList());
//                for (Container container1 : containers2) {
                ContainerConfiguration containerConfiguration = container.getContainerConfiguration();
                leasingCost = leasingCost + (containerConfiguration.getCores() * cpuCost * deploymentDuration.getStandardSeconds() + containerConfiguration.getRam() / 1000 * ramCost * deploymentDuration.getStandardSeconds()) * leasingCostFactor;
//                }


            } catch (ContainerImageNotFoundException e) {
                log.error("Exception", e);
            }
        }

        // calculate penalty cost
        double penaltyCost = 0;
        for (Chromosome.Gene lastGeneOfProcess : lastGeneOfProcessList.values()) {
            // get deadline of workflow
            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(lastGeneOfProcess.getProcessStep().getWorkflowName());
            if (workflowElement != null) {
                DateTime deadline = workflowElement.getDeadlineDateTime();
//                deadline = deadline.minusSeconds(30);
                if (lastGeneOfProcess.getExecutionInterval().getEnd().isAfter(deadline)) {
                    Duration duration = new Duration(deadline, lastGeneOfProcess.getExecutionInterval().getEnd());
                    penaltyCost = penaltyCost + workflowElement.getPenalty() * duration.getMillis() * penaltyTimeFactor;
                }
            }
        }

        // prefer earlier enactments
//        double earlyEnactmentCost = 0;
//        for (Chromosome.Gene lastGeneOfProcess : lastGeneOfProcessList) {
//            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(lastGeneOfProcess.getProcessStep().getWorkflowName());
//            if(workflowElement != null) {
//                DateTime deadline = workflowElement.getDeadlineDateTime();
//
//                if (lastGeneOfProcess.getExecutionInterval().getEnd().isBefore(deadline)) {
//                    Duration duration = new Duration(lastGeneOfProcess.getExecutionInterval().getEnd(), deadline);
//
//                    double cost = leasingCost / 100 - duration.getMillis() * earlyEnactmentTimeFactor;
//                    if(cost < 0) {
//                        cost = 0;
//                    }
//
////                    earlyEnactmentCost = earlyEnactmentCost + cost;      // if duration is big its good
//                }
//            }
//        }

        this.leasingCost = leasingCost;
        this.penaltyCost = penaltyCost;
//        this.earlyEnactmentCost = earlyEnactmentCost;

        return leasingCost + penaltyCost;// + earlyEnactmentCost;
    }


    @Override
    public boolean isNatural() {
        return false;
    }


}
