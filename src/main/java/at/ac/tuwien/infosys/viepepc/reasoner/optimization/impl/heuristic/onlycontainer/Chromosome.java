package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Chromosome {

    @Getter private final List<List<Gene>> genes;

    public Chromosome(List<List<Chromosome.Gene>> genes) {
        this.genes = genes;
    }

    public Interval getInterval(int process, int step)
    {
        return genes.get(process).get(step).getExecutionInterval();
    }

    public List<Chromosome.Gene> getRow(int row)
    {
        return genes.get(row);
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder();
        for (List<Chromosome.Gene> row : genes)
        {
            for (Gene cell : row)
            {
                buffer.append(' ');
                buffer.append(cell.getExecutionInterval());
            }
            buffer.append('\n');
        }
        return buffer.toString();

    }

    public int getRowAmount() {
        return genes.size();
    }

    public static void cloneGenes(Chromosome chromosome, List<List<Chromosome.Gene>> offspring) {
        for (List<Chromosome.Gene> subChromosome : chromosome.getGenes()) {
            List<Chromosome.Gene> newSubChromosome = new ArrayList<>();
            for (Chromosome.Gene gene : subChromosome) {
                newSubChromosome.add(Chromosome.Gene.clone(gene));
            }
            offspring.add(newSubChromosome);
        }
    }

    public static void moveAllGenesOfARow(List<Chromosome.Gene> row, int startIndex, long deltaTime, boolean toFuture) {
        for(int i = startIndex; i < row.size(); i++) {
            if(toFuture) {
                row.get(i).moveIntervalPlus(deltaTime);
            }
            else {
                row.get(i).moveIntervalMinus(deltaTime);
            }
        }
    }

    @Getter
    @Setter
    public static class Gene {

        private Interval executionInterval;

        private final boolean fixed;
        private ProcessStep processStep;


        public Gene(ProcessStep processStep, DateTime startTime, boolean fixed) {
            this.fixed = fixed;
            this.processStep = processStep;
            this.executionInterval = new Interval(startTime, startTime.plus(processStep.getExecutionTime()));
        }

        public void moveIntervalPlus(long delta) {
            executionInterval = new Interval(executionInterval.getStart().plus(delta), executionInterval.getEnd().plus(delta));
        }

        public void moveIntervalMinus(long delta) {
            executionInterval = new Interval(executionInterval.getStart().minus(delta), executionInterval.getEnd().minus(delta));
        }

        public static Gene clone(Gene gene) {
            return new Gene(gene.getProcessStep(), gene.getExecutionInterval().getStart(), gene.isFixed());
        }
    }


}
