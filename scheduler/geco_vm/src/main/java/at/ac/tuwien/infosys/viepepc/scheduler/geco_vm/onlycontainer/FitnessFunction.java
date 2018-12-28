package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.ContainerSchedulingUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
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
@SuppressWarnings("Duplicates")
public class FitnessFunction implements FitnessEvaluator<Chromosome> {

    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;

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

    @Override
    public double getFitness(Chromosome chromosome, List<? extends Chromosome> list) {

        List<ContainerSchedulingUnit> requiredServiceTypeList = new ArrayList<>();
        Map<String, Chromosome.Gene> lastGeneOfProcessList = new HashMap<>();
        optimizationUtility.getRequiredServiceTypesAndLastElements(chromosome, requiredServiceTypeList, lastGeneOfProcessList);

        Map<VirtualMachine, List<Interval>> virtualMachineIntervals = createVirtualMachineIntervalsMap(requiredServiceTypeList);

        // TODO prefer running VMs
        // calculate the leasing cost
        double leasingCost = 0;
        for (Map.Entry<VirtualMachine, List<Interval>> virtualMachineInterval : virtualMachineIntervals.entrySet()) {
            Duration deploymentDuration = new Duration(virtualMachineInterval.getValue().stream().mapToLong(value -> value.toDurationMillis()).sum());
            VMType vmType = virtualMachineInterval.getKey().getVmType();
            leasingCost = leasingCost + (vmType.getCores() * cpuCost * deploymentDuration.getStandardSeconds() + vmType.getRamPoints() / 1000 * ramCost * deploymentDuration.getStandardSeconds()) * leasingCostFactor;
        }

        // calculate penalty cost
        double penaltyCost = 0;
        for (Chromosome.Gene lastGeneOfProcess : lastGeneOfProcessList.values()) {
            // get deadline of workflow
            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(lastGeneOfProcess.getProcessStep().getWorkflowName());
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




    private Map<VirtualMachine, List<Interval>> createVirtualMachineIntervalsMap(List<ContainerSchedulingUnit> requiredServiceTypeList) {

        Map<VirtualMachine, List<Interval>> virtualMachineIntervals = new HashMap<>();

        requiredServiceTypeList.forEach(schedulingUnit -> {
            try {
                VirtualMachine virtualMachine = schedulingUnit.getProcessStepGenes().stream().findFirst().orElseThrow(Exception::new).getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine();

                if (!virtualMachineIntervals.containsKey(virtualMachine)) {
                    virtualMachineIntervals.put(virtualMachine, new ArrayList<>());
                    virtualMachineIntervals.get(virtualMachine).add(schedulingUnit.getServiceAvailableTime());
                } else {
                    Interval vmDeployInterval = virtualMachineIntervals.get(virtualMachine).stream().filter(i -> i.overlap(schedulingUnit.getServiceAvailableTime()) != null).findFirst().orElse(null);
                    if (vmDeployInterval == null) {
                        virtualMachineIntervals.get(virtualMachine).add(schedulingUnit.getServiceAvailableTime());
                    } else {
                        virtualMachineIntervals.get(virtualMachine).remove(vmDeployInterval);
                        Interval newVmDeployInterval = new Interval(vmDeployInterval);
                        newVmDeployInterval = newVmDeployInterval.withStartMillis(Math.min(vmDeployInterval.getStartMillis(), schedulingUnit.getServiceAvailableTime().getStartMillis()));
                        newVmDeployInterval = newVmDeployInterval.withEndMillis(Math.max(vmDeployInterval.getEndMillis(), schedulingUnit.getServiceAvailableTime().getEndMillis()));
                        virtualMachineIntervals.get(virtualMachine).add(newVmDeployInterval);
                    }
                }

            } catch (Exception e) {
                log.error("Exception", e);
            }
        });

        return virtualMachineIntervals;
    }


    @Override
    public boolean isNatural() {
        return false;
    }


}
