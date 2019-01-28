package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.uncommons.watchmaker.framework.EvolutionObserver;
import org.uncommons.watchmaker.framework.PopulationData;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class EvolutionLogger implements EvolutionObserver<Chromosome>
{
    @Getter
    @Setter
    private int amountOfGenerations = 0;

    public void populationUpdate(PopulationData data)
    {
        amountOfGenerations = amountOfGenerations + 1;
        log.debug("Time=" + data.getElapsedTime() + "; generation=" + data.getGenerationNumber() + "; best fitness=" + data.getBestCandidateFitness() + ", eliteCount=" + data.getEliteCount());

//        Chromosome chromosome = (Chromosome) data.getBestCandidate();
//        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitSet = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());
//        virtualMachineSchedulingUnitSet.forEach(unit -> log.debug(unit.toString()));
//        log.debug("");
    }

}
