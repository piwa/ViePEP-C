package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.gpedro.integrations.slack.SlackApi;
import net.gpedro.integrations.slack.SlackMessage;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.List;

@Slf4j
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

    public boolean orderIsOk(List<List<Chromosome.Gene>> chromosome) {

        for (List<Chromosome.Gene> row : chromosome) {
            if (!rowOrderIsOk(row)) {

//                StringBuilder buffer = new StringBuilder();

//                for (Chromosome.Gene cell : row) {
//                    buffer.append("{");
//                    buffer.append("processStep=" + cell.getProcessStep().getName() + ", ");
//                    buffer.append("start=" + cell.getExecutionInterval().getStart().toString() + ", ");
//                    buffer.append("end=" + cell.getExecutionInterval().getEnd().toString() + ", ");
//                    buffer.append("fixed=" + cell.isFixed());
//                    buffer.append("} ");
//                }
//                buffer.append('\n');
//
//                log.error("Order of row is not ok: " + buffer.toString());

//                checkAndMaintainOrder(row);
//                rowOrderIsOk(row);

                return false;
            }
        }

        return true;

    }

    public void checkRowAndPrintError(Chromosome newChromosome, String className, String slackWebhook) {
        int rowCounter = 0;
        for (List<Chromosome.Gene> row : newChromosome.getGenes()) {
            if (!rowOrderIsOk(row)) {
                SlackApi api = new SlackApi(slackWebhook);
                api.call(new SlackMessage("Problem with the order! class=" + className + "; process=" + newChromosome.toString(rowCounter)));
                log.error("Problem with the order! process=" + newChromosome.toString(rowCounter));

                System.exit(1);
            }
            rowCounter++;
        }
    }
}
