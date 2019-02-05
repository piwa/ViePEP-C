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
public class FitnessFunction implements FitnessEvaluator<Chromosome> {

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

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_getFitness_1");

        double leasingCost = 0;

        // calculate the leasing cost
        List<ServiceTypeSchedulingUnit> requiredServiceTypeList = getRequiredServiceTypes(chromosome);
        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : requiredServiceTypeList) {
            Duration deploymentDuration = serviceTypeSchedulingUnit.getServiceAvailableTime().toDuration();
            ContainerConfiguration containerConfiguration = serviceTypeSchedulingUnit.getContainer().getContainerConfiguration();
            leasingCost = leasingCost + (containerConfiguration.getCores() * cpuCost * deploymentDuration.getStandardSeconds() + containerConfiguration.getRam() / 1000 * ramCost * deploymentDuration.getStandardSeconds()) * leasingCostFactor;
        }

        // calculate VM leasing cost
        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(unit -> unit.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toSet());
        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
            VMType vmType = virtualMachineSchedulingUnit.getVmType();
            Duration cloudResourceUsageDuration;
            if (virtualMachineSchedulingUnit.getCloudResourceUsageInterval().getStart().isBefore(optimizationEndTime)) {
                cloudResourceUsageDuration = new Duration(optimizationEndTime, virtualMachineSchedulingUnit.getCloudResourceUsageInterval().getEnd());
            } else {
                cloudResourceUsageDuration = new Duration(virtualMachineSchedulingUnit.getCloudResourceUsageInterval());
            }
            leasingCost = leasingCost + (vmType.getCores() * cpuCost * cloudResourceUsageDuration.getStandardSeconds() + vmType.getRamPoints() / 1000 * ramCost * cloudResourceUsageDuration.getStandardSeconds()) * leasingCostFactor / 100;
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

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(chromosome, this.getClass().getSimpleName() + "_getFitness_2");

        return leasingCost + penaltyCost;// + earlyEnactmentCost;
    }

    @Override
    public boolean isNatural() {
        return false;
    }

    public void setOptimizationEndTime(DateTime optimizationEndTime) {
        this.optimizationEndTime = optimizationEndTime;
    }


    private List<ServiceTypeSchedulingUnit> getRequiredServiceTypes(Chromosome chromosome) {

        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<ProcessStepSchedulingUnit> processStepSchedulingUnitSet = new HashSet<>();
        virtualMachineSchedulingUnits.forEach(unit -> processStepSchedulingUnitSet.addAll(unit.getProcessStepSchedulingUnits()));

        return getRequiredServiceTypesOneVM(processStepSchedulingUnitSet);
    }

    private List<ServiceTypeSchedulingUnit> getRequiredServiceTypesOneVM(Set<ProcessStepSchedulingUnit> processStepSchedulingUnitSet) {

//        Set<ProcessStepSchedulingUnit> processStepSchedulingUnitSet = new HashSet<>(virtualMachineSchedulingUnit.getProcessStepSchedulingUnits());
//        processStepSchedulingUnitSet.addAll(additionalProcessSteps);
        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>(processStepSchedulingUnitSet);
        processStepSchedulingUnits.sort(Comparator.comparing(unit -> unit.getGene().getExecutionInterval().getStart()));

        Map<ServiceType, List<ServiceTypeSchedulingUnit>> requiredServiceTypeMap = new HashMap<>();
        for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnits) {
            Chromosome.Gene gene = processStepSchedulingUnit.getGene();
            requiredServiceTypeMap.putIfAbsent(processStepSchedulingUnit.getProcessStep().getServiceType(), new ArrayList<>());

            boolean overlapFound = false;
            if (!gene.isFixed()) {
                List<ServiceTypeSchedulingUnit> requiredServiceTypes = requiredServiceTypeMap.get(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType());
                for (ServiceTypeSchedulingUnit requiredServiceType : requiredServiceTypes) {
                    Interval overlap = requiredServiceType.getServiceAvailableTime().overlap(gene.getExecutionInterval());
                    if (overlap != null) {
//                    && requiredServiceType.isFixed() == gene.isFixed()) {
//                    if (!gene.isFixed() || requiredServiceType.getContainer().getInternId().equals(processStepSchedulingUnit.getProcessStep().getContainer().getInternId())) {

                        Interval deploymentInterval = requiredServiceType.getServiceAvailableTime();
                        Interval geneInterval = gene.getExecutionInterval();
                        long newStartTime = Math.min(geneInterval.getStartMillis(), deploymentInterval.getStartMillis());
                        long newEndTime = Math.max(geneInterval.getEndMillis(), deploymentInterval.getEndMillis());

                        requiredServiceType.setServiceAvailableTime(new Interval(newStartTime, newEndTime));
                        requiredServiceType.getGenes().add(gene);

                        overlapFound = true;
                        break;
//                    }
                    }
                }
            }

            if (!overlapFound) {
                ServiceTypeSchedulingUnit newServiceTypeSchedulingUnit = new ServiceTypeSchedulingUnit(processStepSchedulingUnit.getProcessStep().getServiceType(), this.containerDeploymentTime, gene.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit(), gene.isFixed());
                newServiceTypeSchedulingUnit.setServiceAvailableTime(gene.getExecutionInterval());
                newServiceTypeSchedulingUnit.addProcessStep(gene);
                if (newServiceTypeSchedulingUnit.isFixed()) {
                    newServiceTypeSchedulingUnit.setContainer(gene.getProcessStepSchedulingUnit().getProcessStep().getContainer());
                }

                requiredServiceTypeMap.get(processStepSchedulingUnit.getProcessStep().getServiceType()).add(newServiceTypeSchedulingUnit);
            }
        }

        List<ServiceTypeSchedulingUnit> returnList = new ArrayList<>();
        requiredServiceTypeMap.forEach((k, v) -> returnList.addAll(v));

        returnList.forEach(unit -> {
            try {
                if (unit.getContainer() == null) {
                    unit.setContainer(optimizationUtility.getContainer(unit.getServiceType(), unit.getGenes().size()));
                } else {
                    unit.setContainer(optimizationUtility.resizeContainer(unit.getContainer(), unit.getGenes().size()));
                }
            } catch (ContainerImageNotFoundException e) {
                log.error("Could not find a fitting container");
            }
        });

        return returnList;
    }


}
