package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Duration;

import java.util.List;

@Slf4j
public class OrderMaintainer {

    @Getter private Chromosome.Gene firstGene;
    @Getter private Chromosome.Gene secondGene;

    public void checkAndMaintainOrder(Chromosome chromosome) {
        for (List<Chromosome.Gene> row : chromosome.getGenes()) {
            checkAndMaintainOrder(row);
        }
    }

    public void checkAndMaintainOrder(List<Chromosome.Gene> rowOffspring) {
        while (!rowOrderIsOk(rowOffspring)) {
            long duration = new Duration(firstGene.getExecutionInterval().getEnd(), secondGene.getExecutionInterval().getStart()).getMillis();
            duration = Math.abs(duration);
            secondGene.moveIntervalPlus(duration);
        }
    }

    public boolean rowOrderIsOk(List<Chromosome.Gene> row) {

        for (int i = 0; i < row.size(); i++) {
            Chromosome.Gene currentGene = row.get(i);

            for(Chromosome.Gene previousGene : currentGene.getPreviousGenes()) {
                if (previousGene != null) {
                    if (currentGene.getExecutionInterval().getStart().isBefore(previousGene.getExecutionInterval().getEnd())) {
                        this.firstGene = previousGene;
                        this.secondGene = currentGene;
                        return false;
                    }
                }
            }

            for(Chromosome.Gene nextGene : currentGene.getNextGenes()){
                if (nextGene != null) {
                    if (currentGene.getExecutionInterval().getEnd().isAfter(nextGene.getExecutionInterval().getStart())) {
                        this.firstGene = currentGene;
                        this.secondGene = nextGene;
                        return false;
                    }
                }
            }

        }
        return true;
    }

    public boolean orderIsOk(List<List<Chromosome.Gene>> chromosome) {

        for (List<Chromosome.Gene> row : chromosome) {
            if(!rowOrderIsOk(row)) {
                rowOrderIsOk(row);
                return false;
            }
        }

        return true;

    }

}
