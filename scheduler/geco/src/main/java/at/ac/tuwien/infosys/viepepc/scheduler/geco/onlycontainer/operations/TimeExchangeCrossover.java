package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.operations;

import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.OrderMaintainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Interval;
import org.uncommons.maths.number.NumberGenerator;
import org.uncommons.watchmaker.framework.operators.AbstractCrossover;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
public class TimeExchangeCrossover extends AbstractCrossover<Chromosome> {

    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    /**
     * Single-point cross-over.
     */
    public TimeExchangeCrossover() {
        this(1);
    }


    /**
     * Multiple-point cross-over (fixed number of points).
     *
     * @param crossoverPoints The fixed number of cross-overs applied to each
     *                        pair of parents.
     */
    public TimeExchangeCrossover(int crossoverPoints) {
        super(crossoverPoints);
    }


    /**
     * Multiple-point cross-over (variable number of points).
     *
     * @param crossoverPointsVariable Provides the (possibly variable) number of
     *                                cross-overs applied to each pair of parents.
     */
    public TimeExchangeCrossover(NumberGenerator<Integer> crossoverPointsVariable) {
        super(crossoverPointsVariable);
    }


    @Override
    protected List<Chromosome> mate(Chromosome parent1, Chromosome parent2, int numberOfCrossoverPoints, Random random) {

        List<List<Chromosome.Gene>> offspring1 = new ArrayList<>();
        Chromosome.cloneGenes(parent1, offspring1);
        Chromosome offspring1Chromosome = new Chromosome(offspring1);

        List<List<Chromosome.Gene>> offspring2 = new ArrayList<>();
        Chromosome.cloneGenes(parent2, offspring2);
        Chromosome offspring2Chromosome = new Chromosome(offspring2);

        for (int i = 0; i < numberOfCrossoverPoints; i++) {
            int rowIndex = random.nextInt(offspring1.size());
            List<Chromosome.Gene> rowOffspring1 = offspring1Chromosome.getRow(rowIndex);
            List<Chromosome.Gene> rowOffspring2 = offspring2Chromosome.getRow(rowIndex);

            int bound = rowOffspring1.size() - 1;
            int crossoverStartIndex = 0;
            if (bound > 0) {         // the nextInt can only be performed for bound >= 1
                crossoverStartIndex = random.nextInt(bound);
            }
            int crossoverEndIndex = crossoverStartIndex + random.nextInt(rowOffspring1.size() - crossoverStartIndex);
            if (crossoverEndIndex >= rowOffspring1.size()) {
                crossoverEndIndex = rowOffspring1.size() - 1;
            }

            for (int j = crossoverStartIndex; j <= crossoverEndIndex; j++) {

                Interval rowOffspring1Interval = rowOffspring1.get(j).getExecutionInterval();
                Interval rowOffspring2Interval = rowOffspring2.get(j).getExecutionInterval();

                Interval tempInterval = new Interval(rowOffspring1Interval.getStart().getMillis(), rowOffspring1Interval.getEnd().getMillis());
                rowOffspring1.get(j).setExecutionInterval(new Interval(rowOffspring2Interval.getStart().getMillis(), rowOffspring2Interval.getEnd().getMillis()));
                rowOffspring2.get(j).setExecutionInterval(tempInterval);
            }


            orderMaintainer.checkAndMaintainOrder(rowOffspring1);
            orderMaintainer.checkAndMaintainOrder(rowOffspring2);

        }

        orderMaintainer.checkAndMaintainOrder(offspring1Chromosome);
        orderMaintainer.checkAndMaintainOrder(offspring2Chromosome);

//        if (!orderMaintainer.orderIsOk(offspring1Chromosome.getGenes())) {
//            log.error("Order is not ok in offspring1: " + offspring1Chromosome.toString());
//        }
//        if (!orderMaintainer.orderIsOk(offspring2Chromosome.getGenes())) {
//            log.error("Order is not ok in offspring2: " + offspring2Chromosome.toString());
//        }

        List<Chromosome> result = new ArrayList<>(2);
        result.add(offspring1Chromosome);
        result.add(offspring2Chromosome);
        return result;
    }


}
