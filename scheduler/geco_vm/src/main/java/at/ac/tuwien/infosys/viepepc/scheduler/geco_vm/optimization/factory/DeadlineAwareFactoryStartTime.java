package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ProcessStepSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.VirtualMachineSchedulingUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.uncommons.watchmaker.framework.factories.AbstractCandidateFactory;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Scope("prototype")
@Slf4j
@SuppressWarnings("Duplicates")
public class DeadlineAwareFactoryStartTime extends AbstractCandidateFactory<Chromosome> {

    @Autowired
    private DeadlineAwareFactoryInitializer deadlineAwareFactoryInitializer;
    @Autowired
    private VMSelectionHelper vmSelectionHelper;

    @Value("${slack.webhook}")
    private String slackWebhook;
    @Value("${deadline.aware.factory.allowed.penalty.points}")
    private int allowedPenaltyPoints;
    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;

    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    @Getter
    private List<List<Chromosome.Gene>> template = new ArrayList<>();
    @Getter
    private Map<String, DateTime> maxTimeAfterDeadline = new HashMap<>();
    private Map<String, DateTime> workflowDeadlines = new HashMap<>();
    private DateTime optimizationEndTime;

    private Random random;


    public void initialize(List<WorkflowElement> workflowElementList, DateTime optimizationEndTime) {
        this.template = new ArrayList<>();
        this.maxTimeAfterDeadline = new HashMap<>();
        this.workflowDeadlines = new HashMap<>();
        this.optimizationEndTime = new DateTime(optimizationEndTime);

        this.deadlineAwareFactoryInitializer.initialize(optimizationEndTime);

        for (WorkflowElement workflowElement : workflowElementList) {
            List<Chromosome.Gene> subChromosome = deadlineAwareFactoryInitializer.createStartChromosome(workflowElement);
            if (subChromosome.size() == 0) {
                continue;
            }
            template.add(subChromosome);

            fillProcessStepChain(subChromosome, workflowElement);

            subChromosome.stream().filter(Chromosome.Gene::isFixed).forEach(this::setAllPrecedingFixed);

            workflowDeadlines.put(workflowElement.getName(), workflowElement.getDeadlineDateTime());
            calculateMaxTimeAfterDeadline(workflowElement, subChromosome);
        }

        orderMaintainer.checkAndMaintainOrder(new Chromosome(template));
    }

    /***
     * Guarantee that the process step order is preserved and that there are no overlapping steps
     */
    @Override
    public Chromosome generateRandomCandidate(Random random) {
        this.random = random;
        Chromosome newChromosome = new Chromosome(template).clone();

        for (List<Chromosome.Gene> newRow : newChromosome.getGenes()) {

            int bufferBound = 0;
            if (newRow.size() > 0) {
                DateTime deadline = workflowDeadlines.get(newRow.get(0).getProcessStepSchedulingUnit().getWorkflowName());
                Chromosome.Gene lastProcessStep = getLastProcessStep(newRow);
                Duration durationToDeadline = new Duration(lastProcessStep.getExecutionInterval().getEnd(), deadline);


                if (durationToDeadline.getMillis() <= 0) {
                    bufferBound = 0;
                } else if (newRow.size() == 1) {
                    bufferBound = (int) durationToDeadline.getMillis();
                } else {
                    bufferBound = Math.round(durationToDeadline.getMillis() / (newRow.size()));
                }
            }

            moveNewChromosomeRec(findStartGene(newRow), bufferBound);
        }

        considerFirstVMStartTime(newChromosome);

        orderMaintainer.checkRowAndPrintError(newChromosome, this.getClass().getSimpleName() + "_generateRandomCandidate_2", "generateRandomCandidate");

        return newChromosome;
    }

    private void calculateMaxTimeAfterDeadline(WorkflowElement workflowElement, List<Chromosome.Gene> subChromosome) {

        if (deadlineAwareFactoryInitializer.getFirstGene() == null) {
            deadlineAwareFactoryInitializer.setFirstGene(subChromosome.get(0));
        }
        if (deadlineAwareFactoryInitializer.getLastGene() == null) {
            deadlineAwareFactoryInitializer.setLastGene(subChromosome.get(subChromosome.size() - 1));
        }

        Duration overallDuration = new Duration(deadlineAwareFactoryInitializer.getFirstGene().getExecutionInterval().getStart(), deadlineAwareFactoryInitializer.getLastGene().getExecutionInterval().getEnd());

        int additionalSeconds = 0;
        DateTime simulatedEnd = null;
        while (true) {

            simulatedEnd = deadlineAwareFactoryInitializer.getLastGene().getExecutionInterval().getEnd().plusSeconds(additionalSeconds);
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

        maxTimeAfterDeadline.put(workflowElement.getName(), simulatedEnd.plus(2));      // plus 1 is needed (because of jodatime implementation?), plus 2 is better ;)

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


    public void considerFirstVMStartTime(Chromosome newChromosome) {

        boolean redo = true;

        while (redo) {
            List<ProcessStepSchedulingUnit> processStepSchedulingUnits = newChromosome.getFlattenChromosome().stream().map(Chromosome.Gene::getProcessStepSchedulingUnit).collect(Collectors.toList());

            redo = false;
            for (ProcessStepSchedulingUnit processStepSchedulingUnit : processStepSchedulingUnits) {
                Interval processStepAvailableInterval = processStepSchedulingUnit.getGene().getExecutionInterval();
                DateTime deploymentStartTime = processStepAvailableInterval.getStart().minus(containerDeploymentTime).minus(virtualMachineDeploymentTime);

                Chromosome.Gene gene = processStepSchedulingUnit.getGene();
                if (deploymentStartTime.isBefore(this.optimizationEndTime) && !gene.isFixed()) {

                    long deltaTime = new Duration(deploymentStartTime, this.optimizationEndTime).getMillis();

                    gene.moveIntervalPlus(deltaTime);
                    orderMaintainer.checkAndMaintainOrder(newChromosome);

                    redo = true;
                    break;
                }
            }
        }
    }


    private void moveNewChromosomeRec(Set<Chromosome.Gene> startGenes, int bufferBound) {

        for (Chromosome.Gene gene : startGenes) {

            if (!gene.isFixed()) {
                Chromosome.Gene latestPreviousGene = gene.getLatestPreviousGene();
                if (latestPreviousGene != null) {
                    DateTime newStartTime = new DateTime(latestPreviousGene.getExecutionInterval().getEnd().getMillis() + 1);
                    DateTime newEndTime = newStartTime.plus(gene.getProcessStepSchedulingUnit().getProcessStep().getServiceType().getServiceTypeResources().getMakeSpan());
                    gene.setExecutionInterval(new Interval(newStartTime, newEndTime));
                }
                if (bufferBound > 0) {
                    int intervalDelta = random.nextInt(bufferBound - 1) + 1;
                    gene.moveIntervalPlus(intervalDelta);
                }
            }
        }

        startGenes.forEach(gene -> moveNewChromosomeRec(gene.getNextGenes(), bufferBound));
    }

    private Set<Chromosome.Gene> findStartGene(List<Chromosome.Gene> rowParent2) {
        return rowParent2.stream().filter(gene -> gene.getPreviousGenes() == null || gene.getPreviousGenes().isEmpty()).collect(Collectors.toSet());
    }


    private void setAllPrecedingFixed(Chromosome.Gene gene) {
        gene.getPreviousGenes().forEach(prevGene -> {
            prevGene.setFixed(true);
            setAllPrecedingFixed(prevGene);
        });
    }


    private void fillProcessStepChain(List<Chromosome.Gene> subChromosome, Element workflowElement) {
        Map<UUID, Chromosome.Gene> stepGeneMap = new HashMap<>();
        subChromosome.forEach(gene -> stepGeneMap.put(gene.getProcessStepSchedulingUnit().getUid(), gene));
        fillProcessStepChainRec(stepGeneMap, workflowElement, new ArrayList<>());
    }

    private List<ProcessStep> fillProcessStepChainRec(Map<UUID, Chromosome.Gene> stepGeneMap, Element currentElement, List<ProcessStep> previousProcessSteps) {
        if (currentElement instanceof ProcessStep) {
            ProcessStep processStep = (ProcessStep) currentElement;

            if (processStep.isHasToBeExecuted()) {
                if (previousProcessSteps != null && !previousProcessSteps.isEmpty()) {
                    for (ProcessStep previousProcessStep : previousProcessSteps) {
                        if (previousProcessStep != null && stepGeneMap.get(previousProcessStep.getInternId()) != null && stepGeneMap.get(processStep.getInternId()) != null) {
                            Chromosome.Gene currentGene = stepGeneMap.get(processStep.getInternId());
                            Chromosome.Gene previousGene = stepGeneMap.get(previousProcessStep.getInternId());

                            previousGene.addNextGene(currentGene);
                            currentGene.addPreviousGene(previousGene);
                        }
                    }
                }
                List<ProcessStep> psList = new ArrayList<>();
                psList.add(processStep);
                return psList;
            }

            return null;
        } else {
            if (currentElement instanceof WorkflowElement || currentElement instanceof Sequence) {
                for (Element element : currentElement.getElements()) {
                    List<ProcessStep> ps = fillProcessStepChainRec(stepGeneMap, element, previousProcessSteps);
                    if (ps != null) {
                        previousProcessSteps = new ArrayList<>();
                        previousProcessSteps.addAll(ps);
                    }
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {
                List<ProcessStep> afterAnd = new ArrayList<>();

                for (Element element1 : currentElement.getElements()) {
                    List<ProcessStep> ps = fillProcessStepChainRec(stepGeneMap, element1, previousProcessSteps);
                    if (ps != null) {
                        afterAnd.addAll(ps);
                    }

                }

                previousProcessSteps = afterAnd;
            } else if (currentElement instanceof LoopConstruct) {

                if ((currentElement.getNumberOfExecutions() < ((LoopConstruct) currentElement).getNumberOfIterationsToBeExecuted())) {
                    for (Element subElement : currentElement.getElements()) {
                        List<ProcessStep> ps = fillProcessStepChainRec(stepGeneMap, subElement, previousProcessSteps);
                        if (ps != null) {
                            previousProcessSteps = new ArrayList<>();
                            previousProcessSteps.addAll(ps);
                        }
                    }
                }
            }
            return previousProcessSteps;
        }
    }


}
