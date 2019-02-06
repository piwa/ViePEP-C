package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.uncommons.watchmaker.framework.factories.AbstractCandidateFactory;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@SuppressWarnings("Duplicates")
public class DeadlineAwareFactoryVM extends AbstractCandidateFactory<Chromosome> {

    @Autowired
    private VMSelectionHelper vmSelectionHelper;

    private Chromosome chromosome;

    public void initialize(Chromosome chromosome) {
        this.chromosome = chromosome.clone();
    }

    /***
     * Guarantee that the process step order is preserved and that there are no overlapping steps
     */
    @Override
    public Chromosome generateRandomCandidate(Random random) {
        Chromosome newChromosome = chromosome.clone();
        scheduleVMs(newChromosome, random);
        return newChromosome;
    }

    private void scheduleVMs(Chromosome newChromosome, Random random) {

        List<ProcessStepSchedulingUnit> processStepSchedulingUnits = newChromosome.getFlattenChromosome().stream().map(Chromosome.Gene::getProcessStepSchedulingUnit).collect(Collectors.toList());

        Set<VirtualMachineSchedulingUnit> alreadyUsedVirtualMachineSchedulingUnits = processStepSchedulingUnits.stream().map(ProcessStepSchedulingUnit::getVirtualMachineSchedulingUnit).filter(Objects::nonNull).collect(Collectors.toSet());

        for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnits) {
            if (processStepSchedulingUnit.getVirtualMachineSchedulingUnit() == null) {

                VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = vmSelectionHelper.getVirtualMachineSchedulingUnitForProcessStep(processStepSchedulingUnit, alreadyUsedVirtualMachineSchedulingUnits, random, true);
                alreadyUsedVirtualMachineSchedulingUnits.add(virtualMachineSchedulingUnit);

                processStepSchedulingUnit.setVirtualMachineSchedulingUnit(virtualMachineSchedulingUnit);
                virtualMachineSchedulingUnit.getProcessStepSchedulingUnits().add(processStepSchedulingUnit);
            }
        }
    }

}
