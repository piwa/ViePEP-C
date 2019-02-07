package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.baseline;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.AbstractOnlyContainerOptimization;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.EvolutionLogger;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory.DeadlineAwareFactoryStartTime;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.scheduler.library.SchedulerAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
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
    private DeadlineAwareFactoryStartTime chromosomeFactory;

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

        // AllParExceed from Frincu

        this.optimizationEndTime = DateTime.now().plus(100);
        chromosomeFactory.initialize(workflowElements, this.optimizationEndTime);

        Chromosome baselineChromosome = new Chromosome(chromosomeFactory.getTemplate());
        chromosomeFactory.considerFirstVMStartTime(baselineChromosome);
        scheduleVMs(baselineChromosome);

//        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = baselineChromosome.getFlattenChromosome().stream().map(unit -> unit.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toSet());
//        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
//            if (!vmSelectionHelper.checkIfVirtualMachineIsBigEnough(virtualMachineSchedulingUnit)) {
//                if (virtualMachineSchedulingUnit.isFixed()) {
//                    vmSelectionHelper.distributeContainers(virtualMachineSchedulingUnit, virtualMachineSchedulingUnits);
//                } else {
//                    try {
//                        vmSelectionHelper.resizeVM(virtualMachineSchedulingUnit);
//                    } catch (VMTypeNotFoundException e) {
//                        vmSelectionHelper.distributeContainers(virtualMachineSchedulingUnit, virtualMachineSchedulingUnits);
//                    }
//                }
//            }
//        }

        List<ServiceTypeSchedulingUnit> allServiceTypeSchedulingUnits = optimizationUtility.getRequiredServiceTypesVMSeparation(baselineChromosome);

        for (ServiceTypeSchedulingUnit allServiceTypeSchedulingUnit : allServiceTypeSchedulingUnits) {
            VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = allServiceTypeSchedulingUnit.getGenes().get(0).getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit();
            allServiceTypeSchedulingUnit.setVirtualMachineSchedulingUnit(virtualMachineSchedulingUnit);
        }

        return createOptimizationResult(baselineChromosome, allServiceTypeSchedulingUnits);
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

                List<ProcessStepSchedulingUnit> processStepSchedulingUnitsOnVM = new ArrayList<>(availableVMSchedulingUnit.getProcessStepSchedulingUnits());
                processStepSchedulingUnitsOnVM.sort(Comparator.comparing(unit -> unit.getServiceAvailableTime().getStart()));

                boolean canBeUsed = false;
                for (ProcessStepSchedulingUnit stepSchedulingUnit : processStepSchedulingUnitsOnVM) {
                    Interval overlap = stepSchedulingUnit.getServiceAvailableTime().overlap(processStepSchedulingUnit.getServiceAvailableTime());
                    if (overlap != null) {
                        canBeUsed = stepSchedulingUnit.getProcessStep().getServiceType() == processStepSchedulingUnit.getProcessStep().getServiceType();
                        break;
                    }
                }
                if (canBeUsed && vmSelectionHelper.checkIfVirtualMachineHasEnoughSpaceForNewProcessSteps(availableVMSchedulingUnit, processStepSchedulingUnits)) {
                    return availableVMSchedulingUnit;
                }
            }
        }

        return getVirtualMachineSchedulingUnitForProcessStep(processStepSchedulingUnit);
    }


    private VirtualMachineSchedulingUnit getVirtualMachineSchedulingUnitForProcessStep(ProcessStepSchedulingUnit processStepSchedulingUnit) {
        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = new ArrayList<>();
        processStepSchedulingUnits.add(processStepSchedulingUnit);

        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
        List<VMType> vmTypes = new ArrayList<>(cacheVirtualMachineService.getVMTypes());
        vmTypes.removeIf(type -> type.getCores() == 3);
        vmTypes.sort(Comparator.comparing(VMType::getCores));
        int position = 0;
        do {
            VMType vmType = vmTypes.get(position);
            position = position + 1;
            virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(false, null, virtualMachineDeploymentTime, containerDeploymentTime, new VirtualMachineInstance(vmType));
        } while (!vmSelectionHelper.checkIfVirtualMachineHasEnoughSpaceForNewProcessSteps(virtualMachineSchedulingUnit, processStepSchedulingUnits));

        return virtualMachineSchedulingUnit;
    }

}
