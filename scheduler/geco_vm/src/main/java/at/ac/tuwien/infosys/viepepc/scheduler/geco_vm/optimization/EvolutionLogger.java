package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.uncommons.watchmaker.framework.EvolutionObserver;
import org.uncommons.watchmaker.framework.PopulationData;

@Slf4j
public class EvolutionLogger<T> implements EvolutionObserver<T>
{
    @Getter
    private int amountOfGenerations = 0;

    public void populationUpdate(PopulationData<? extends T> data)
    {
        amountOfGenerations = amountOfGenerations + 1;
        log.debug("Time=" + data.getElapsedTime() + "; generation=" + data.getGenerationNumber() + "; best fitness=" + data.getBestCandidateFitness());
    }

}
