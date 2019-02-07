package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.ApplicationContext;
import org.uncommons.maths.number.ConstantGenerator;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.maths.random.PoissonGenerator;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;

import java.security.Provider;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("Duplicates")
public class SpaceAwareDeploymentMutation implements EvolutionaryOperator<Chromosome2> {

    private final NumberGenerator<Integer> mutationCountVariable;
    private final DateTime optimizationEndTime;
    private OrderMaintainer orderMaintainer = new OrderMaintainer();
    private VMSelectionHelper vmSelectionHelper;
    private OptimizationUtility optimizationUtility;

    /**
     * Default is one mutation per candidate.
     *
     * @param poissonGenerator
     * @param optimizationEndTime
     */
    public SpaceAwareDeploymentMutation(PoissonGenerator poissonGenerator, DateTime optimizationEndTime) {
        this(poissonGenerator.nextValue(), optimizationEndTime);
    }

    /**
     * @param mutationCount The constant number of mutations
     *                      to apply to each row in a Sudoku solution.
     */
    public SpaceAwareDeploymentMutation(int mutationCount, DateTime optimizationEndTime) {
        this(new ConstantGenerator<>(mutationCount), optimizationEndTime);
    }

    /**
     * Typically the mutation count will be from a Poisson distribution.
     * The mutation amount can be from any discrete probability distribution
     * and can include negative values.
     *
     * @param mutationCount A random variable that provides a number
     *                      of mutations that will be applied to each row in an individual.
     */
    public SpaceAwareDeploymentMutation(NumberGenerator<Integer> mutationCount, DateTime optimizationEndTime) {
        if (mutationCount.nextValue() < 1) {
            mutationCount = new ConstantGenerator<>(1);
        }
        this.mutationCountVariable = mutationCount;
        this.optimizationEndTime = optimizationEndTime;

        ApplicationContext context = SpringContext.getApplicationContext();
        this.vmSelectionHelper = context.getBean(VMSelectionHelper.class);
        this.optimizationUtility = context.getBean(OptimizationUtility.class);
    }

    @Override
    public List<Chromosome2> apply(List<Chromosome2> selectedCandidates, Random random) {
        List<Chromosome2> mutatedCandidates = new ArrayList<>();
        for (Chromosome2 candidate : selectedCandidates) {
            mutatedCandidates.add(mutate(candidate, random));
        }

        return mutatedCandidates;
    }

    private Chromosome2 mutate(Chromosome2 candidate, Random random) {
        Chromosome2 newCandidate = candidate.clone();

        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits = newCandidate.getFlattenChromosome().stream().filter(unit -> !unit.isFixed()).collect(Collectors.toList());

        if (serviceTypeSchedulingUnits.size() == 0) {
           return newCandidate;
        }

        int mutationCount = Math.abs(mutationCountVariable.nextValue());
        int counter = 0;
        while (mutationCount > 0 && counter < 100) {
            int index = random.nextInt(serviceTypeSchedulingUnits.size());
            ServiceTypeSchedulingUnit serviceTypeSchedulingUnit = serviceTypeSchedulingUnits.get(index);

            boolean mutationPerformed = performDeploymentMutation(newCandidate, serviceTypeSchedulingUnit, random);
            if(mutationPerformed) {
                mutationCount = mutationCount - 1;
            }
            counter = counter + 1;
        }

        return newCandidate;
    }

    public boolean performDeploymentMutation(Chromosome2 newCandidate, ServiceTypeSchedulingUnit serviceTypeSchedulingUnit, Random random) {
        VirtualMachineSchedulingUnit oldVirtualMachineSchedulingUnit = serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit();

//        Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines = newCandidate.getFlattenChromosome().stream().map(ServiceTypeSchedulingUnit::getVirtualMachineSchedulingUnit).collect(Collectors.toSet());

        Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines = new HashSet<>();
        for (ServiceTypeSchedulingUnit typeSchedulingUnit : newCandidate.getFlattenChromosome()) {
            VirtualMachineSchedulingUnit machineSchedulingUnit = typeSchedulingUnit.getVirtualMachineSchedulingUnit();
            if (machineSchedulingUnit != null) {
                machineSchedulingUnit.getServiceTypeSchedulingUnits().add(typeSchedulingUnit);
                alreadyScheduledVirtualMachines.add(machineSchedulingUnit);
            }
        }

        VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = vmSelectionHelper.getVirtualMachineSchedulingUnitForProcessStep(serviceTypeSchedulingUnit, alreadyScheduledVirtualMachines, random, true);

        if (oldVirtualMachineSchedulingUnit != newVirtualMachineSchedulingUnit) {

//            oldVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().remove(processStepSchedulingUnit);
//            newVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(processStepSchedulingUnit);
            serviceTypeSchedulingUnit.setVirtualMachineSchedulingUnit(newVirtualMachineSchedulingUnit);
//            vmSelectionHelper.checkIfVMIsTooSmall(newCandidate.getFlattenChromosome(), "performDeploymentMutation");
//            boolean enoughTimeToDeploy = considerFirstContainerStartTime(newVirtualMachineSchedulingUnit, processStepSchedulingUnit.getGene());

//            if (enoughTimeToDeploy) {
//                return true;//mutationCount = mutationCount - 1;
//            } else {
//                serviceTypeSchedulingUnit.setVirtualMachineSchedulingUnit(oldVirtualMachineSchedulingUnit);
//            }
            for (VirtualMachineSchedulingUnit alreadyUsedVirtualMachineSchedulingUnit : alreadyScheduledVirtualMachines) {
                alreadyUsedVirtualMachineSchedulingUnit.getServiceTypeSchedulingUnits().clear();
            }

            return true;
        }
        return false;
    }

//    private boolean considerFirstContainerStartTime(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, Chromosome.Gene movedGene) {
//        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits = this.optimizationUtility.getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit);
//        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnits) {
//            if (serviceTypeSchedulingUnit.getGenes().contains(movedGene)) {
//                VirtualMachineStatus virtualMachineStatus = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVirtualMachineStatus();
//                DateTime deploymentStartTime = serviceTypeSchedulingUnit.getDeployStartTime();        // TODO is it ok not to consider the vm?
//                if ((virtualMachineStatus.equals(VirtualMachineStatus.UNUSED) || virtualMachineStatus.equals(VirtualMachineStatus.SCHEDULED)) && virtualMachineSchedulingUnit.getDeploymentStartTime().isBefore(this.optimizationEndTime)) {
//                    return false;
//                } else if (deploymentStartTime.isBefore(this.optimizationEndTime)) {
//                    return false;
//                } else {
//                    return true;
//                }
//            }
//        }
//        return true;
//    }

}
