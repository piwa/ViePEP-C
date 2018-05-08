package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.factory;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.OrderMaintainer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.jdbc.Work;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

@Slf4j
public class DeadlineAwareFactory extends AbstractChromosomeFactory {

    @Getter private final List<List<Chromosome.Gene>> template = new ArrayList<>();
    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    private Map<String, DateTime> workflowDeadlines = new HashMap<>();
    @Getter Map<String, DateTime> maxTimeAfterDeadline = new HashMap<>();

    @Value("${deadline.aware.factory.allowed.penalty.points}")
    private int allowedPenaltyPoints;

    public DeadlineAwareFactory(List<WorkflowElement> workflowElementList, DateTime optimizationStartTime, long defaultContainerDeployTime, long defaultContainerStartupTime, boolean withOptimizationTimeOut) {

        super(defaultContainerStartupTime, defaultContainerDeployTime, withOptimizationTimeOut);

        clonedServiceTypes = new HashMap<>();
        for (WorkflowElement workflowElement : workflowElementList) {
            stepGeneMap = new HashMap<>();

            List<Chromosome.Gene> subChromosome = createStartChromosome(workflowElement, new DateTime(optimizationStartTime.getMillis()));

            if(subChromosome.size() == 0) {
                continue;
            }

            subChromosome.forEach(gene -> stepGeneMap.put(gene.getProcessStep().getInternId(), gene));

            fillProcessStepChain(workflowElement, subChromosome);

            template.add(subChromosome);

            workflowDeadlines.put(workflowElement.getName(), workflowElement.getDeadlineDateTime());

            calculateMaxTimeAfterDeadline(workflowElement, subChromosome);

        }
        this.defaultContainerDeployTime = defaultContainerDeployTime;
    }

    private void calculateMaxTimeAfterDeadline(WorkflowElement workflowElement, List<Chromosome.Gene> subChromosome) {

        if(firstGene == null) {
            firstGene = subChromosome.get(0);
        }
        if(lastGene == null) {
            lastGene = subChromosome.get(subChromosome.size() - 1);
        }

        Duration overallDuration = new Duration(firstGene.getExecutionInterval().getStart(), lastGene.getExecutionInterval().getEnd());

        int additionalSeconds = 0;
        DateTime simulatedEnd = null;
        while(true) {

            simulatedEnd = lastGene.getExecutionInterval().getEnd().plusSeconds(additionalSeconds);
            Duration timeDiff = new Duration(workflowElement.getDeadlineDateTime(), simulatedEnd);

            double penalityPoints = 0;
            if(timeDiff.getMillis() > 0) {
                penalityPoints = Math.ceil((timeDiff.getMillis() / overallDuration.getMillis()) * 10);
            }

            if(penalityPoints > allowedPenaltyPoints) {
                break;
            }
            additionalSeconds = additionalSeconds + 10;
        }

        maxTimeAfterDeadline.put(workflowElement.getName(), simulatedEnd.plus(2));      // TODO plus 1 is needed (because of jodatime implementation?), plus 2 is better ;)

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

        Map<Chromosome.Gene, Chromosome.Gene> originalToCloneMap = new HashMap<>();

        for (List<Chromosome.Gene> genes : template) {

            int bufferBound = 0;
            if(genes.size() > 0) {
                DateTime deadline = workflowDeadlines.get(genes.get(0).getProcessStep().getWorkflowName());
                Duration durationToDeadline = new Duration(genes.get(genes.size() - 1).getExecutionInterval().getEnd(), deadline);


                if(durationToDeadline.getMillis() <= 0) {
                    bufferBound = 0;
                } else if(genes.size() == 1) {
                    bufferBound = (int)durationToDeadline.getMillis();
                } else {
                    bufferBound = Math.round(durationToDeadline.getMillis() / (genes.size()));
                }
            }

            List<Chromosome.Gene> subChromosome = new ArrayList<>();
            long intervalDelta = 2;
            for (Chromosome.Gene gene : genes) {

                if (!gene.isFixed() && bufferBound > 0) {
                    intervalDelta = intervalDelta + rand.nextInt(bufferBound);
                }

                Chromosome.Gene newGene = Chromosome.Gene.clone(gene);
                originalToCloneMap.put(gene, newGene);

                newGene.moveIntervalPlus(intervalDelta);
                subChromosome.add(newGene);
            }

            candidate.add(subChromosome);

        }

        for (List<Chromosome.Gene> subChromosome : template) {
            for (Chromosome.Gene originalGene : subChromosome) {
                Chromosome.Gene clonedGene = originalToCloneMap.get(originalGene);
                Set<Chromosome.Gene> originalNextGenes = originalGene.getNextGenes();
                Set<Chromosome.Gene> originalPreviousGenes = originalGene.getPreviousGenes();

                originalNextGenes.stream().map(originalToCloneMap::get).forEach(clonedGene::addNextGene);

                originalPreviousGenes.stream().map(originalToCloneMap::get).forEach(clonedGene::addPreviousGene);
            }
        }

        Chromosome newChromosome = new Chromosome(candidate);
        orderMaintainer.checkAndMaintainOrder(newChromosome);

        return newChromosome;
    }



}
