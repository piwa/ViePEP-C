package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.OrderMaintainer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

import java.util.*;

@Slf4j
public class SimpleFactory extends AbstractChromosomeFactory {

    @Getter
    private final List<List<Chromosome.Gene>> template = new ArrayList<>();
    private Map<UUID, Chromosome.Gene> stepGeneMap = new HashMap<>();
    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    public SimpleFactory(List<WorkflowElement> workflowElementList, DateTime startTime, long defaultContainerDeployTime, long defaultContainerStartupTime, boolean withOptimizationTimeout) {

        super(defaultContainerStartupTime, defaultContainerDeployTime, withOptimizationTimeout);

        clonedServiceTypes = new HashMap<>();
        for (WorkflowElement workflowElement : workflowElementList) {
            stepGeneMap = new HashMap<>();
            List<Chromosome.Gene> subChromosome = createStartChromosome(workflowElement, new DateTime(startTime.getMillis()));

            subChromosome.forEach(gene -> stepGeneMap.put(gene.getProcessStep().getInternId(), gene));

            fillProcessStepChain(workflowElement);

            template.add(subChromosome);
        }
        this.defaultContainerDeployTime = defaultContainerDeployTime;
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

            List<Chromosome.Gene> subChromosome = new ArrayList<>();
            long intervalDelta = 0;
            for (Chromosome.Gene gene : genes) {

                if (!gene.isFixed()) {
                    intervalDelta = intervalDelta + rand.nextInt(60000);    // max 1 minute gap
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
