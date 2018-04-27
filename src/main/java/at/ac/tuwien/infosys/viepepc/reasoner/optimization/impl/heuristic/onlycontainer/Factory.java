package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.*;
import org.joda.time.DateTime;
import org.uncommons.watchmaker.framework.factories.AbstractCandidateFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Factory extends AbstractCandidateFactory<Chromosome> {

    private final List<List<Chromosome.Gene>> template = new ArrayList<>();
    private final List<ProcessStep> runningProcesses;
    private final DateTime startTime;

    public Factory(List<WorkflowElement> workflowElementList, List<ProcessStep> runningProcesses, DateTime startTime) {

        this.startTime = startTime;
        this.runningProcesses = runningProcesses;

        for(WorkflowElement workflowElement : workflowElementList) {
            List<Chromosome.Gene> subChromosome = new ArrayList<>();
            createStartChromosome(workflowElement, startTime, subChromosome);
            template.add(subChromosome);
        }





    }

    /***
     * Guarantee that the process step order is preserved and that there are no overlapping steps
     * @param random
     * @return
     */
    @Override
    public Chromosome generateRandomCandidate(Random random) {

        List<List<Chromosome.Gene>> candidate = new ArrayList<>();
        Random rand = new Random();

        for (List<Chromosome.Gene> genes : template) {

            List<Chromosome.Gene> subChromosome = new ArrayList<>();
            long intervalDelta = 0;
            for (Chromosome.Gene gene : genes) {

                if(!gene.isFixed()) {
                    intervalDelta = intervalDelta + rand.nextInt(10000);    // max 10 seconds gap
                }

                Chromosome.Gene newGene = Chromosome.Gene.clone(gene);

                newGene.moveIntervalPlus(intervalDelta);
                subChromosome.add(newGene);
            }

            candidate.add(subChromosome);
        }

        return new Chromosome(candidate);
    }




    private DateTime createStartChromosome(Element currentElement, DateTime startTime, List<Chromosome.Gene> chromosome) {
        if (currentElement instanceof ProcessStep) {
            if (!((ProcessStep) currentElement).hasBeenExecuted() && !runningProcesses.contains(currentElement)) {

                startTime = startTime.plus(100);     // add a small gap
                Chromosome.Gene gene = new Chromosome.Gene((ProcessStep) currentElement, startTime, false);
                chromosome.add(gene);

                return gene.getExecutionInterval().getEnd();
            } else if (runningProcesses.contains(currentElement)) {
                DateTime realStartTime = ((ProcessStep) currentElement).getStartDate();
                if(realStartTime == null) {
                    realStartTime = ((ProcessStep) currentElement).getScheduledStartedAt();
                }
                Chromosome.Gene gene = new Chromosome.Gene((ProcessStep) currentElement, realStartTime, true);
                chromosome.add(gene);

                return gene.getExecutionInterval().getEnd();
            }
            return startTime;
        } else {
            if (currentElement instanceof WorkflowElement) {
                for (Element element : currentElement.getElements()) {
                    startTime = createStartChromosome(element, startTime, chromosome);
                }
            } else if (currentElement instanceof Sequence) {
                for (Element element1 : currentElement.getElements()) {
                    startTime = createStartChromosome(element1, startTime, chromosome);
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {
                DateTime latestEndTime = startTime;
                for (Element element1 : currentElement.getElements()) {
                    DateTime tmpEndTime = createStartChromosome(element1, startTime, chromosome);
                    if (tmpEndTime.isAfter(latestEndTime)) {
                        latestEndTime = tmpEndTime;
                    }
                }
                return latestEndTime;
            } else if (currentElement instanceof LoopConstruct) {

                if ((currentElement.getNumberOfExecutions() < ((LoopConstruct) currentElement).getNumberOfIterationsToBeExecuted())) {
                    for (Element subElement : currentElement.getElements()) {
                        startTime = createStartChromosome(subElement, startTime, chromosome);
                    }
                }


            }
            return startTime;
        }
    }

}
