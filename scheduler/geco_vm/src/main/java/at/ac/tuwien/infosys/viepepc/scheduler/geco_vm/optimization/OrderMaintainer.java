package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.List;

@Slf4j
@SuppressWarnings("Duplicates")
public class OrderMaintainer {

    @Getter
    private Chromosome.Gene firstGene;
    @Getter
    private Chromosome.Gene secondGene;
    private boolean moveBack = false;


    public boolean checkAllStartTimesBeforeTime(DateTime optimizationStartTime, List<Chromosome.Gene> subChromosome) {
        for (Chromosome.Gene gene : subChromosome) {
            if (!gene.isFixed() && gene.getExecutionInterval().getStart().isBefore(optimizationStartTime)) {
                return false;
            }
        }
        return true;
    }

    public void checkAndMaintainOrder(Chromosome chromosome) {
        for (List<Chromosome.Gene> row : chromosome.getGenes()) {
            checkAndMaintainOrder(row);
        }
    }

    public void checkAndMaintainOrder(List<Chromosome.Gene> rowOffspring) {
        while (!rowOrderIsOk(rowOffspring)) {
            long duration = new Duration(firstGene.getExecutionInterval().getEnd(), secondGene.getExecutionInterval().getStart()).getMillis();
            duration = Math.abs(duration);
            if (moveBack && !firstGene.isFixed()) {
                firstGene.moveIntervalMinus(duration);
            }
            secondGene.moveIntervalPlus(duration);
        }
    }

    public boolean rowOrderIsOk(List<Chromosome.Gene> row) {

        for (int i = 0; i < row.size(); i++) {
            Chromosome.Gene currentGene = row.get(i);

            for (Chromosome.Gene previousGene : currentGene.getPreviousGenes()) {
                if (previousGene != null) {
                    if (currentGene.getExecutionInterval().getStart().isBefore(previousGene.getExecutionInterval().getEnd())) {
                        this.firstGene = previousGene;
                        this.secondGene = currentGene;
                        this.moveBack = true;
                        return false;
                    }
                }
            }

            for (Chromosome.Gene nextGene : currentGene.getNextGenes()) {
                if (nextGene != null) {
                    if (currentGene.getExecutionInterval().getEnd().isAfter(nextGene.getExecutionInterval().getStart())) {
                        this.firstGene = currentGene;
                        this.secondGene = nextGene;
                        this.moveBack = false;
                        return false;
                    }
                }
            }

        }
        return true;
    }

    public boolean orderIsOk(Chromosome chromosome) {
        return orderIsOk(chromosome.getGenes());
    }

    public boolean orderIsOk(List<List<Chromosome.Gene>> chromosome) {

        for (List<Chromosome.Gene> row : chromosome) {
            if (!rowOrderIsOk(row)) {
                return false;
            }
        }
        return true;
    }

    public void checkRowAndPrintError(Chromosome newChromosome, String className, String methodName) {
        for (int i = 0; i < newChromosome.getGenes().size(); i++) {
            if (!rowOrderIsOk(newChromosome.getRow(i))) {
                log.error("Problem with the order in class=" + className + ", methodName=" + methodName + ", process=" + newChromosome.toString(i) + ", chromosome=" + newChromosome.toString());
                System.exit(1);
            }
        }
    }
}
