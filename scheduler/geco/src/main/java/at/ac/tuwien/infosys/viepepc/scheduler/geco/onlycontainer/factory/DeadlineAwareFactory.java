package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.ServiceTypeSchedulingUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;

import java.util.*;

@Slf4j
public class DeadlineAwareFactory extends AbstractChromosomeFactory {


    private OptimizationUtility optimizationUtility;
    @Getter
    private final List<List<Chromosome.Gene>> template = new ArrayList<>();

    private Map<String, DateTime> workflowDeadlines = new HashMap<>();
    @Getter
    Map<String, DateTime> maxTimeAfterDeadline = new HashMap<>();
    private DateTime optimizationStartTime;

    private String slackWebhook;
    private int allowedPenaltyPoints;
    private long onlyContainerDeploymentTime;

    public DeadlineAwareFactory(List<WorkflowElement> workflowElementList, DateTime optimizationStartTime, long defaultContainerDeployTime, long defaultContainerStartupTime, boolean withOptimizationTimeOut, OptimizationUtility optimizationUtility, long onlyContainerDeploymentTime, int allowedPenaltyPoints, String slackWebhook) {

        super(defaultContainerStartupTime, defaultContainerDeployTime, withOptimizationTimeOut);

        this.slackWebhook = slackWebhook;
        this.allowedPenaltyPoints = allowedPenaltyPoints;
        this.optimizationUtility = optimizationUtility;
        this.onlyContainerDeploymentTime = onlyContainerDeploymentTime;
        this.optimizationStartTime = new DateTime(optimizationStartTime);

        clonedServiceTypes = new HashMap<>();
        for (WorkflowElement workflowElement : workflowElementList) {
            stepGeneMap = new HashMap<>();

            List<Chromosome.Gene> subChromosome = createStartChromosome(workflowElement, new DateTime(optimizationStartTime.getMillis()));

            if (subChromosome.size() == 0) {
                continue;
            }

            subChromosome.forEach(gene -> stepGeneMap.put(gene.getProcessStep().getInternId(), gene));
            fillProcessStepChain(workflowElement);

            subChromosome.forEach(gene -> {
                if (gene.isFixed()) {
                    setAllPrecedingFixed(gene);
                }
            });

            template.add(subChromosome);
            workflowDeadlines.put(workflowElement.getName(), workflowElement.getDeadlineDateTime());
            calculateMaxTimeAfterDeadline(workflowElement, subChromosome);
        }
        this.defaultContainerDeployTime = defaultContainerDeployTime;

        considerFirstContainerStartTime(new Chromosome(template), true);
        orderMaintainer.checkRowAndPrintError(new Chromosome(template), this.getClass().getSimpleName() + "_constructor", slackWebhook);
        return;
    }

    private void calculateMaxTimeAfterDeadline(WorkflowElement workflowElement, List<Chromosome.Gene> subChromosome) {

        if (firstGene == null) {
            firstGene = subChromosome.get(0);
        }
        if (lastGene == null) {
            lastGene = subChromosome.get(subChromosome.size() - 1);
        }

        Duration overallDuration = new Duration(firstGene.getExecutionInterval().getStart(), lastGene.getExecutionInterval().getEnd());

        int additionalSeconds = 0;
        DateTime simulatedEnd = null;
        while (true) {

            simulatedEnd = lastGene.getExecutionInterval().getEnd().plusSeconds(additionalSeconds);
            Duration timeDiff = new Duration(workflowElement.getDeadlineDateTime(), simulatedEnd);

            double penalityPoints = 0;
            if (timeDiff.getMillis() > 0) {
                penalityPoints = Math.ceil((timeDiff.getMillis() / overallDuration.getMillis()) * 10);
            }

            if (penalityPoints > allowedPenaltyPoints) {
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

        orderMaintainer.checkRowAndPrintError(new Chromosome(template), this.getClass().getSimpleName() + "_generateRandomCandidate_1", slackWebhook);

        for (List<Chromosome.Gene> row : template) {


            List<Chromosome.Gene> newRow = createClonedRow(row);

            int bufferBound = 0;
            if (row.size() > 0) {
                DateTime deadline = workflowDeadlines.get(row.get(0).getProcessStep().getWorkflowName());
                Chromosome.Gene lastProcessStep = getLastProcessStep(row);
                Duration durationToDeadline = new Duration(lastProcessStep.getExecutionInterval().getEnd(), deadline);


                if (durationToDeadline.getMillis() <= 0) {
                    bufferBound = 0;
                } else if (row.size() == 1) {
                    bufferBound = (int) durationToDeadline.getMillis();
                } else {
                    bufferBound = Math.round(durationToDeadline.getMillis() / (row.size()));
                }
            }

            moveNewChromosome(findStartGene(newRow), bufferBound, rand);

            candidate.add(newRow);

        }


        Chromosome newChromosome = new Chromosome(candidate);

        orderMaintainer.checkRowAndPrintError(newChromosome, this.getClass().getSimpleName() + "_generateRandomCandidate_2", slackWebhook);


        return newChromosome;
    }

    private Chromosome.Gene getLastProcessStep(List<Chromosome.Gene> row) {

        Chromosome.Gene lastGene = null;
        for (Chromosome.Gene gene : row) {
            if (lastGene == null || lastGene.getExecutionInterval().getEnd().isBefore(gene.getExecutionInterval().getEnd())) {
                lastGene = gene;
            }
        }
        return lastGene;
    }

    private void considerFirstContainerStartTime(Chromosome newChromosome, boolean moveNextGeneOnlyIfNeeded) {

        boolean redo = true;

        while (redo) {
            List<ServiceTypeSchedulingUnit> serviceTypeSchedulingUnits = this.optimizationUtility.getRequiredServiceTypes(newChromosome);

            redo = false;
            for (ServiceTypeSchedulingUnit serviceTypeSchedulingUnit : serviceTypeSchedulingUnits) {
                DateTime deploymentStartTime = serviceTypeSchedulingUnit.getDeployStartTime();

                if (deploymentStartTime.isBefore(this.optimizationStartTime)) {

                    for (Chromosome.Gene gene : serviceTypeSchedulingUnit.getProcessSteps()) {
                        DateTime geneStartTime = gene.getExecutionInterval().getStart().minus(this.onlyContainerDeploymentTime);
                        if (geneStartTime.isBefore(this.optimizationStartTime) && !gene.isFixed()) {
                            long deltaTime = new Duration(geneStartTime, this.optimizationStartTime).getMillis();

                            gene.moveIntervalPlus(deltaTime);
                            orderMaintainer.checkAndMaintainOrder(newChromosome);

                            redo = true;
                        }
                    }
                    if (redo) {
                        break;
                    }
                }
            }
        }
    }

    private List<Chromosome.Gene> createClonedRow(List<Chromosome.Gene> row) {
        List<Chromosome.Gene> newRow = new ArrayList<>();
        Map<Chromosome.Gene, Chromosome.Gene> originalToCloneMap = new HashMap<>();

        for (Chromosome.Gene gene : row) {
            Chromosome.Gene newGene = Chromosome.Gene.clone(gene);
            originalToCloneMap.put(gene, newGene);
            newRow.add(newGene);
        }

        for (List<Chromosome.Gene> subChromosome : template) {
            for (Chromosome.Gene originalGene : subChromosome) {
                Chromosome.Gene clonedGene = originalToCloneMap.get(originalGene);
                if (clonedGene != null) {
                    Set<Chromosome.Gene> originalNextGenes = originalGene.getNextGenes();
                    Set<Chromosome.Gene> originalPreviousGenes = originalGene.getPreviousGenes();

                    originalNextGenes.stream().map(originalToCloneMap::get).forEachOrdered(clonedGene::addNextGene);

                    originalPreviousGenes.stream().map(originalToCloneMap::get).forEachOrdered(clonedGene::addPreviousGene);
                }
            }
        }
        return newRow;
    }

    private void moveNewChromosome(Set<Chromosome.Gene> startGenes, int bufferBound, Random rand) {

        for (Chromosome.Gene gene : startGenes) {

            if (!gene.isFixed()) {

                Chromosome.Gene latestPreviousGene = gene.getLatestPreviousGene();

                if (latestPreviousGene != null) {
                    DateTime newStartTime = new DateTime(latestPreviousGene.getExecutionInterval().getEnd().getMillis() + 1);
                    DateTime newEndTime = newStartTime.plus(gene.getProcessStep().getExecutionTime());
                    gene.setExecutionInterval(new Interval(newStartTime, newEndTime));
                }
                if (bufferBound > 0) {
                    int intervalDelta = rand.nextInt(bufferBound - 1) + 1;
                    gene.moveIntervalPlus(intervalDelta);
                }
            }
        }

        for (Chromosome.Gene gene : startGenes) {
            moveNewChromosome(gene.getNextGenes(), bufferBound, rand);
        }
    }

    private Set<Chromosome.Gene> findStartGene(List<Chromosome.Gene> rowParent2) {
        Set<Chromosome.Gene> startGenes = new HashSet<>();
        for (Chromosome.Gene gene : rowParent2) {
            if (gene.getPreviousGenes() == null || gene.getPreviousGenes().isEmpty()) {
                startGenes.add(gene);
            }
        }
        return startGenes;
    }

}
