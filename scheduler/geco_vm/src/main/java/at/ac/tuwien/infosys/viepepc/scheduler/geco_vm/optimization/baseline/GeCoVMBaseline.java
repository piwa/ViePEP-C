package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.baseline;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.AbstractOnlyContainerOptimization;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.EvolutionLogger;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory.DeadlineAwareFactory;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.scheduler.library.SchedulerAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.RandomUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.util.Collections.reverseOrder;

@Slf4j
@Component
@Profile("GeCo_VM_Baseline")
@SuppressWarnings("Duplicates")
public class GeCoVMBaseline extends AbstractOnlyContainerOptimization implements SchedulerAlgorithm {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private VMSelectionHelper vmSelectionHelper;
    @Autowired
    private DeadlineAwareFactory chromosomeFactory;

    @Value("${max.optimization.duration}")
    private long maxOptimizationDuration = 60000;
    @Value("${additional.optimization.time}")
    private long additionalOptimizationTime = 5000;
    @Value("${container.default.startup.time}")
    private long defaultContainerStartupTime;
    @Value("${container.default.deploy.time}")
    private long defaultContainerDeployTime;
    @Value("${container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;
    @Value("${deadline.aware.factory.allowed.penalty.points}")
    private int allowedPenaltyPoints;
    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;


    @Override
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return null;
    }

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();

        if (workflowElements.size() == 0) {
            return new OptimizationResult();
        }

        EvolutionLogger evolutionLogger = new EvolutionLogger();
        evolutionLogger.setAmountOfGenerations(1);

        this.optimizationEndTime = DateTime.now().plus(maxOptimizationDuration).plus(additionalOptimizationTime);
        chromosomeFactory.initialize(workflowElements, this.optimizationEndTime);

        Chromosome baselineChromosome = new Chromosome(chromosomeFactory.getTemplate());
        scheduleVMs(baselineChromosome);

        return createOptimizationResult(baselineChromosome, workflowElements, evolutionLogger);
    }


    private void scheduleVMs(Chromosome newChromosome) {

        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = newChromosome.getFlattenChromosome().stream().map(Chromosome.Gene::getProcessStepSchedulingUnit).collect(Collectors.toList());
        Set<VirtualMachineSchedulingUnit> alreadyUsedVirtualMachineSchedulingUnits = processStepSchedulingUnits.stream().map(ProcessStepSchedulingUnit::getVirtualMachineSchedulingUnit).filter(Objects::nonNull).collect(Collectors.toSet());

        for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnits) {
            if (processStepSchedulingUnit.getVirtualMachineSchedulingUnit() == null) {

                VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = getVirtualMachineSchedulingUnit(alreadyUsedVirtualMachineSchedulingUnits, processStepSchedulingUnit);
                alreadyUsedVirtualMachineSchedulingUnits.add(virtualMachineSchedulingUnit);

                processStepSchedulingUnit.setVirtualMachineSchedulingUnit(virtualMachineSchedulingUnit);
                virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(processStepSchedulingUnit);
            }
        }
    }


    private VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnit(Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines, ProcessStepSchedulingUnit processStepSchedulingUnit) {

        List<VirtualMachineSchedulingUnit> availableVMSchedulingUnits = vmSelectionHelper.createAvailableVMSchedulingUnitList(alreadyScheduledVirtualMachines);

        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>();
        processStepSchedulingUnits.add(processStepSchedulingUnit);

        availableVMSchedulingUnits.sort(reverseOrder(Comparator.comparingInt(unit -> unit.getProcessStepSchedulingUnits().size())));

        if (availableVMSchedulingUnits.size() > 0) {
            for (VirtualMachineSchedulingUnit availableVMSchedulingUnit : availableVMSchedulingUnits) {
                Set<ServiceType> availableServiceTypes = availableVMSchedulingUnit.getProcessStepSchedulingUnits().stream().map(unit -> unit.getProcessStep().getServiceType()).collect(Collectors.toSet());
                if ((availableServiceTypes.size() == 0 || availableServiceTypes.contains(processStepSchedulingUnit.getProcessStep().getServiceType())) &&
                        vmSelectionHelper.checkIfVirtualMachineHasEnoughSpaceForNewProcessSteps(availableVMSchedulingUnit, processStepSchedulingUnits)) {
                    return availableVMSchedulingUnit;
                }
            }
        }

        return vmSelectionHelper.createNewVirtualMachineSchedulingUnit(processStepSchedulingUnit, new Random());
    }

}
