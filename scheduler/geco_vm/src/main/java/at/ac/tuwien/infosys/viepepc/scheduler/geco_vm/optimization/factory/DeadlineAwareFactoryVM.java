package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
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
public class DeadlineAwareFactoryVM extends AbstractCandidateFactory<Chromosome2> {

    @Autowired
    private VMSelectionHelper vmSelectionHelper;

    private Chromosome2 chromosome;

    public void initialize(List<ServiceTypeSchedulingUnit> requiredServiceTypeList) {
        this.chromosome = new Chromosome2(requiredServiceTypeList).clone();
    }

    /***
     * Guarantee that the process step order is preserved and that there are no overlapping steps
     */
    @Override
    public Chromosome2 generateRandomCandidate(Random random) {
        Chromosome2 newChromosome = chromosome.clone();
        scheduleVMs(newChromosome, random);

        return newChromosome;
    }

    private void scheduleVMs(Chromosome2 newChromosome, Random random) {

        Set<VirtualMachineSchedulingUnit> alreadyUsedVirtualMachineSchedulingUnits = new HashSet<>();
        for (ServiceTypeSchedulingUnit typeSchedulingUnit : newChromosome.getFlattenChromosome()) {
            VirtualMachineSchedulingUnit machineSchedulingUnit = typeSchedulingUnit.getVirtualMachineSchedulingUnit();
            if (machineSchedulingUnit != null) {
                machineSchedulingUnit.getServiceTypeSchedulingUnits().add(typeSchedulingUnit);
                alreadyUsedVirtualMachineSchedulingUnits.add(machineSchedulingUnit);
            }
        }

//        vmSelectionHelper.checkIfVMIsTooSmall(chromosome.getFlattenChromosome(), "generateRandomCandidate_1");

        for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : newChromosome.getFlattenChromosome()) {
            if (serviceTypeSchedulingUnit.getVirtualMachineSchedulingUnit() == null) {

                VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = vmSelectionHelper.getVirtualMachineSchedulingUnitForProcessStep(serviceTypeSchedulingUnit, alreadyUsedVirtualMachineSchedulingUnits, random, true);
                alreadyUsedVirtualMachineSchedulingUnits.add(virtualMachineSchedulingUnit);

                serviceTypeSchedulingUnit.setVirtualMachineSchedulingUnit(virtualMachineSchedulingUnit);
                virtualMachineSchedulingUnit.getServiceTypeSchedulingUnits().add(serviceTypeSchedulingUnit);
            }
        }

//        vmSelectionHelper.checkIfVMIsTooSmall(chromosome.getFlattenChromosome(), "generateRandomCandidate_2");

        for (VirtualMachineSchedulingUnit alreadyUsedVirtualMachineSchedulingUnit : alreadyUsedVirtualMachineSchedulingUnits) {
            alreadyUsedVirtualMachineSchedulingUnit.getServiceTypeSchedulingUnits().clear();
        }
    }

}
