package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.ApplicationContext;
import org.uncommons.maths.number.ConstantGenerator;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.maths.random.PoissonGenerator;
import org.uncommons.watchmaker.framework.EvolutionaryOperator;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("Duplicates")
public class SpaceAwareDeploymentMutation implements EvolutionaryOperator<Chromosome> {

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
//        if (mutationCount < 1) {
//            throw new IllegalArgumentException("Mutation count must be at least 1.");
//        }
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
    public List<Chromosome> apply(List<Chromosome> selectedCandidates, Random random) {
        List<Chromosome> mutatedCandidates = new ArrayList<>();
        for (Chromosome candidate : selectedCandidates) {
            mutatedCandidates.add(mutate(candidate, random));
        }

        return mutatedCandidates;
    }

    private Chromosome mutate(Chromosome candidate, Random random) {
        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(candidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_1");

        Chromosome newCandidate = candidate.clone();

        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = newCandidate.getFlattenChromosome().stream().filter(unit -> !unit.isFixed()).map(Chromosome.Gene::getProcessStepSchedulingUnit).collect(Collectors.toList());

        if (processStepSchedulingUnits.size() == 0) {
            SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_2");
            return newCandidate;
        }

        int mutationCount = Math.abs(mutationCountVariable.nextValue());
        int counter = 0;
        while (mutationCount > 0 && counter < 100) {
            int index = random.nextInt(processStepSchedulingUnits.size());
            ProcessStepSchedulingUnit processStepSchedulingUnit = processStepSchedulingUnits.get(index);

            VirtualMachineSchedulingUnit oldVirtualMachineSchedulingUnit = processStepSchedulingUnit.getVirtualMachineSchedulingUnit();

            Set<VirtualMachineSchedulingUnit> alreadyScheduledVirtualMachines = newCandidate.getFlattenChromosome().stream().map(g -> g.getProcessStepSchedulingUnit().getVirtualMachineSchedulingUnit()).collect(Collectors.toSet());
            VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = vmSelectionHelper.getVirtualMachineSchedulingUnitForProcessStep(processStepSchedulingUnit, alreadyScheduledVirtualMachines, random);

            if (oldVirtualMachineSchedulingUnit != newVirtualMachineSchedulingUnit) {

                oldVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().remove(processStepSchedulingUnit);
                newVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(processStepSchedulingUnit);
                processStepSchedulingUnit.setVirtualMachineSchedulingUnit(newVirtualMachineSchedulingUnit);

                boolean enoughTimeToDeploy = considerFirstContainerStartTime(newVirtualMachineSchedulingUnit, processStepSchedulingUnit.getGene());

                if (enoughTimeToDeploy) {
                    SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_5");
                    mutationCount = mutationCount - 1;
                } else {
                    newVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().remove(processStepSchedulingUnit);
                    oldVirtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(processStepSchedulingUnit);
                    processStepSchedulingUnit.setVirtualMachineSchedulingUnit(oldVirtualMachineSchedulingUnit);
                }

            }
            counter = counter + 1;
        }

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newCandidate, this.getClass().getSimpleName() + "_spaceAwareDeploymentMutation_6");

        return newCandidate;
    }


    private boolean considerFirstContainerStartTime(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, Chromosome.Gene movedGene) {
        List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits = this.optimizationUtility.getRequiredServiceTypesOneVM(virtualMachineSchedulingUnit);
        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnits) {
            if (serviceTypeSchedulingUnit.getGenes().contains(movedGene)) {
                VirtualMachineStatus virtualMachineStatus = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVirtualMachineStatus();
                DateTime deploymentStartTime = serviceTypeSchedulingUnit.getDeployStartTime();        // TODO is it ok not to consider the vm?
                if ((virtualMachineStatus.equals(VirtualMachineStatus.UNUSED) || virtualMachineStatus.equals(VirtualMachineStatus.SCHEDULED)) && virtualMachineSchedulingUnit.getDeploymentStartTime().isBefore(this.optimizationEndTime)) {
                    return false;
                } else if (deploymentStartTime.isBefore(this.optimizationEndTime)) {
                    return false;
                } else {
                    return true;
                }
            }
        }
        return true;
    }

}
