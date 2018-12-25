package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.OrderMaintainer;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.uncommons.watchmaker.framework.factories.AbstractCandidateFactory;

import java.util.*;

@Slf4j
public abstract class AbstractChromosomeFactory extends AbstractCandidateFactory<Chromosome> {

    protected OrderMaintainer orderMaintainer = new OrderMaintainer();

    protected Map<UUID, Chromosome.Gene> stepGeneMap = new HashMap<>();
    protected Map<ServiceType, ServiceType> clonedServiceTypes = new HashMap<>();

    protected long defaultContainerStartupTime;
    protected long defaultContainerDeployTime;

    protected Chromosome.Gene firstGene;
    protected Chromosome.Gene lastGene;

    private DateTime optimizationStartTime;

    protected boolean withOptimizationTimeout = false;
    ;

    public AbstractChromosomeFactory(long defaultContainerStartupTime, long defaultContainerDeployTime, boolean withOptimizationTimeOut) {
        this.defaultContainerStartupTime = defaultContainerStartupTime;
        this.defaultContainerDeployTime = defaultContainerDeployTime;
        this.withOptimizationTimeout = withOptimizationTimeOut;
    }

    @Override
    public abstract Chromosome generateRandomCandidate(Random random);

    protected List<Chromosome.Gene> createStartChromosome(Element currentElement, DateTime optimizationStartTime) {
        this.firstGene = null;
        this.lastGene = null;
        this.optimizationStartTime = new DateTime(optimizationStartTime);

        List<Chromosome.Gene> subChromosome = new ArrayList<>();
        createStartChromosomeRec(currentElement, optimizationStartTime, subChromosome);

        return subChromosome;

    }

    private DateTime createStartChromosomeRec(Element currentElement, DateTime startTime, List<Chromosome.Gene> chromosome) {
        if (currentElement instanceof ProcessStep) {

            ProcessStep processStep = (ProcessStep) currentElement;
            boolean isRunning = processStep.getStartDate() != null && processStep.getFinishedAt() == null;
            boolean isDone = processStep.getStartDate() != null && processStep.getFinishedAt() != null;

            if (this.withOptimizationTimeout) {
                if (isRunning == false && processStep.getScheduledStartDate() != null) {
                    isRunning = processStep.getScheduledStartDate().isBefore(this.optimizationStartTime) && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isAfter(this.optimizationStartTime);
                }
                if (isDone == false && processStep.getScheduledStartDate() != null) {
                    isDone = processStep.getScheduledStartDate().isBefore(this.optimizationStartTime) && processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isBefore(this.optimizationStartTime);
                }
            }
            return getStartTimeForProcessStep(processStep, startTime, chromosome, isDone, isRunning);

        } else {
            if (currentElement instanceof WorkflowElement) {
                for (Element element : currentElement.getElements()) {
                    startTime = createStartChromosomeRec(element, startTime, chromosome);
                }
            } else if (currentElement instanceof Sequence) {
                for (Element element1 : currentElement.getElements()) {
                    startTime = createStartChromosomeRec(element1, startTime, chromosome);
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {
                DateTime latestEndTime = startTime;
                for (Element element1 : currentElement.getElements()) {
                    DateTime tmpEndTime = createStartChromosomeRec(element1, startTime, chromosome);
                    if (tmpEndTime.isAfter(latestEndTime)) {
                        latestEndTime = tmpEndTime;
                    }
                }
                startTime = latestEndTime;
            } else if (currentElement instanceof LoopConstruct) {

                if ((currentElement.getNumberOfExecutions() < ((LoopConstruct) currentElement).getNumberOfIterationsToBeExecuted())) {
                    for (Element subElement : currentElement.getElements()) {
                        startTime = createStartChromosomeRec(subElement, startTime, chromosome);
                    }
                }


            }
            return startTime;
        }
    }


    private DateTime getStartTimeForProcessStep(ProcessStep processStep, DateTime startTime, List<Chromosome.Gene> chromosome, boolean isDone, boolean isRunning) {

        boolean containerAlreadyDeployed = false;

        if (isDone) {
            return startTime;
        }
        if (processStep.getContainer() != null && (processStep.getContainer().isDeploying() || processStep.getContainer().isRunning())) {
            containerAlreadyDeployed = true;
        }

        if (processStep.isHasToBeExecuted() && !isRunning && !containerAlreadyDeployed) {

            if (processStep.getScheduledStartDate() != null) {
                startTime = processStep.getScheduledStartDate();
            } else {
                startTime = startTime.plus(defaultContainerDeployTime + defaultContainerStartupTime);
            }

            Chromosome.Gene gene = new Chromosome.Gene(getClonedProcessStep(processStep), startTime, false);
            chromosome.add(gene);

            checkFirstAndLastGene(gene);

            return gene.getExecutionInterval().getEnd();
        } else if (isRunning || containerAlreadyDeployed) {
            DateTime realStartTime = processStep.getStartDate();
            if (realStartTime == null) {
                realStartTime = processStep.getScheduledStartDate();
            }

            boolean isFixed = isRunning || isDone;
            Chromosome.Gene gene = new Chromosome.Gene(getClonedProcessStep(processStep), realStartTime, isFixed);
            chromosome.add(gene);

            checkFirstAndLastGene(gene);

            return gene.getExecutionInterval().getEnd();
        }
        return startTime;
    }

    protected void setAllPrecedingFixed(Chromosome.Gene gene) {
        gene.getPreviousGenes().forEach(prevGene -> {
            prevGene.setFixed(true);
            setAllPrecedingFixed(prevGene);
        });
    }

    private void checkFirstAndLastGene(Chromosome.Gene gene) {
        if (firstGene == null || firstGene.getExecutionInterval().getStart().isAfter(gene.getExecutionInterval().getStart())) {
            firstGene = gene;
        }
        if (lastGene == null || lastGene.getExecutionInterval().getEnd().isBefore(gene.getExecutionInterval().getEnd())) {
            lastGene = gene;
        }
    }


    protected void fillProcessStepChain(Element workflowElement) {
        fillProcessStepChainRec(workflowElement, new ArrayList<>());
    }

    private List<ProcessStep> fillProcessStepChainRec(Element currentElement, List<ProcessStep> previousProcessSteps) {
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
                    List<ProcessStep> ps = fillProcessStepChainRec(element, previousProcessSteps);
                    if (ps != null) {
                        previousProcessSteps = new ArrayList<>();
                        previousProcessSteps.addAll(ps);
                    }
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {
                List<ProcessStep> afterAnd = new ArrayList<>();

                for (Element element1 : currentElement.getElements()) {
                    List<ProcessStep> ps = fillProcessStepChainRec(element1, previousProcessSteps);
                    if (ps != null) {
                        afterAnd.addAll(ps);
                    }

                }

                previousProcessSteps = afterAnd;
            } else if (currentElement instanceof LoopConstruct) {

                if ((currentElement.getNumberOfExecutions() < ((LoopConstruct) currentElement).getNumberOfIterationsToBeExecuted())) {
                    for (Element subElement : currentElement.getElements()) {
                        List<ProcessStep> ps = fillProcessStepChainRec(subElement, previousProcessSteps);
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

    private ProcessStep getClonedProcessStep(ProcessStep processStep) {

        try {
            ServiceType clonedServiceType = clonedServiceTypes.get(processStep.getServiceType());

            if (clonedServiceType == null) {
                clonedServiceType = processStep.getServiceType().clone();
                clonedServiceTypes.put(processStep.getServiceType(), clonedServiceType);
            }

            return processStep.clone(clonedServiceType);
        } catch (CloneNotSupportedException e) {
            log.error("Exception", e);
            return null;
        }
    }

}
