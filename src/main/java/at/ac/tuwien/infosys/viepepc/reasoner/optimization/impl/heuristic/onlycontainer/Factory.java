package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.uncommons.watchmaker.framework.factories.AbstractCandidateFactory;

import javax.xml.ws.Service;
import java.util.*;

@Slf4j
public class Factory extends AbstractCandidateFactory<Chromosome> {

    @Getter private final List<List<Chromosome.Gene>> template = new ArrayList<>();
    private Map<ServiceType, ServiceType> clonedServiceTypes = new HashMap<>();
    private Map<UUID, Chromosome.Gene> stepGeneMap = new HashMap<>();
    private OrderMaintainer orderMaintainer = new OrderMaintainer();

    private long defaultContainerStartupTime;
    private long defaultContainerDeployTime;

    public Factory(List<WorkflowElement> workflowElementList, DateTime startTime, long defaultContainerDeployTime, long defaultContainerStartupTime) {

        this.defaultContainerStartupTime = defaultContainerStartupTime;
        this.defaultContainerDeployTime = defaultContainerDeployTime;

        clonedServiceTypes = new HashMap<>();
        for (WorkflowElement workflowElement : workflowElementList) {
            stepGeneMap = new HashMap<>();
            List<Chromosome.Gene> subChromosome = new ArrayList<>();
            createStartChromosome(workflowElement, new DateTime(startTime.getMillis()), subChromosome);

            subChromosome.forEach(gene -> stepGeneMap.put(gene.getProcessStep().getInternId(), gene));


            fillProcessStepChain(workflowElement, subChromosome);

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
                    intervalDelta = intervalDelta + rand.nextInt(60000);    // max 10 seconds gap
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


    private DateTime createStartChromosome(Element currentElement, DateTime startTime, List<Chromosome.Gene> chromosome) {
        if (currentElement instanceof ProcessStep) {
            ProcessStep processStep = (ProcessStep) currentElement;
            boolean containerAlreadyDeployed = false;
            boolean isRunning = processStep.getStartDate() != null && processStep.getFinishedAt() == null;
            boolean isDone = processStep.getStartDate() != null && processStep.getFinishedAt() != null;

            if (isDone) {
                return startTime;
            }
            if (processStep.getScheduledAtContainer() != null && (processStep.getScheduledAtContainer().isDeploying() || processStep.getScheduledAtContainer().isRunning())) {
                containerAlreadyDeployed = true;
            }

            if (processStep.isHasToBeExecuted() && !isRunning && !containerAlreadyDeployed) {

                if(processStep.getScheduledStartedAt() != null) {
                    startTime = processStep.getScheduledStartedAt();
                }
                else {
                    startTime = startTime.plus(defaultContainerDeployTime + defaultContainerStartupTime);
                }

                //startTime = startTime.plus(gapDuration);     // add a small gap

                Chromosome.Gene gene = new Chromosome.Gene(getClonedProcessStep((ProcessStep) currentElement), startTime, false);
                chromosome.add(gene);

                return gene.getExecutionInterval().getEnd();
            } else if (isRunning || containerAlreadyDeployed) {
                DateTime realStartTime = ((ProcessStep) currentElement).getStartDate();
                if (realStartTime == null) {
                    realStartTime = ((ProcessStep) currentElement).getScheduledStartedAt();
                }

                boolean isFixed = isRunning || isDone;
                Chromosome.Gene gene = new Chromosome.Gene(getClonedProcessStep((ProcessStep) currentElement), realStartTime, isFixed);
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
                startTime = latestEndTime;
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



    private void fillProcessStepChain(Element workflowElement, List<Chromosome.Gene> subChromosome) {

        Map<Chromosome.Gene, List<Chromosome.Gene>> andMembersMap = new HashMap<>();

        fillProcessStepChainRec(workflowElement, null, andMembersMap);

        for (Chromosome.Gene gene : subChromosome) {
            if(andMembersMap.containsKey(gene)) {
                List<Chromosome.Gene> andMembers = andMembersMap.get(gene);
                for (Chromosome.Gene andMember : andMembers) {
                    andMember.getNextGenes().addAll(gene.getNextGenes());

                    gene.getPreviousGenes().forEach(beforeAnd -> beforeAnd.getNextGenes().add(andMember));

                    andMember.getNextGenes().forEach(afterAnd -> afterAnd.getPreviousGenes().add(andMember));

                }
            }
        }
    }



    private ProcessStep fillProcessStepChainRec(Element currentElement, ProcessStep previousProcessStep, Map<Chromosome.Gene, List<Chromosome.Gene>> andMembersMap) {
        if (currentElement instanceof ProcessStep) {
            ProcessStep processStep = (ProcessStep) currentElement;

            if(processStep.isHasToBeExecuted()) {
                if (previousProcessStep != null && stepGeneMap.get(previousProcessStep.getInternId()) != null && stepGeneMap.get(processStep.getInternId()) != null) {
                    Chromosome.Gene currentGene = stepGeneMap.get(processStep.getInternId());
                    Chromosome.Gene previousGene = stepGeneMap.get(previousProcessStep.getInternId());

                    previousGene.addNextGene(currentGene);
                    currentGene.addPreviousGene(previousGene);
                }

                return processStep;
            }

            return previousProcessStep;
        } else {
            if (currentElement instanceof WorkflowElement) {
                for (Element element : currentElement.getElements()) {
                    previousProcessStep = fillProcessStepChainRec(element, previousProcessStep, andMembersMap);
                }
            } else if (currentElement instanceof Sequence) {
                for (Element element1 : currentElement.getElements()) {
                    previousProcessStep = fillProcessStepChainRec(element1, previousProcessStep, andMembersMap);
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {

                ProcessStep tempPreviousProcessStep = null;
                List<Chromosome.Gene> geneList = new ArrayList<>();
                ProcessStep lastNotNullTempPreviousProcessStep = null;

                for (Element element1 : currentElement.getElements()) {
//                    ProcessStep firstProcessStepOfBranch = getFirstProcessStepOfBranch(element1);
                    tempPreviousProcessStep = fillProcessStepChainRec(element1, previousProcessStep, andMembersMap);
                    if(tempPreviousProcessStep != null && stepGeneMap.get(tempPreviousProcessStep.getInternId()) != null && tempPreviousProcessStep != previousProcessStep) {
                        geneList.add(stepGeneMap.get(tempPreviousProcessStep.getInternId()));
                        lastNotNullTempPreviousProcessStep = tempPreviousProcessStep;
                    }

                }

                if(lastNotNullTempPreviousProcessStep != null && stepGeneMap.get(lastNotNullTempPreviousProcessStep.getInternId()) != null) {
                    andMembersMap.put(stepGeneMap.get(lastNotNullTempPreviousProcessStep.getInternId()), geneList);
                }

                previousProcessStep = tempPreviousProcessStep;
            } else if (currentElement instanceof LoopConstruct) {

                if ((currentElement.getNumberOfExecutions() < ((LoopConstruct) currentElement).getNumberOfIterationsToBeExecuted())) {
                    for (Element subElement : currentElement.getElements()) {
                        previousProcessStep = fillProcessStepChainRec(subElement, previousProcessStep, andMembersMap);
                    }
                }
            }
            return previousProcessStep;
        }
    }


    private ProcessStep getFirstProcessStepOfBranch(Element currentElement) {
        ProcessStep returnProcessStep = null;
        if (currentElement instanceof ProcessStep) {
            ProcessStep processStep = (ProcessStep) currentElement;

            if (processStep.isHasToBeExecuted()) {
                return processStep;
            }
        } else {
            if (currentElement instanceof WorkflowElement) {
                for (Element element : currentElement.getElements()) {
                    returnProcessStep = getFirstProcessStepOfBranch(element);
                    if(returnProcessStep != null) {
                        return returnProcessStep;
                    }
                }
            } else if (currentElement instanceof Sequence) {
                for (Element element1 : currentElement.getElements()) {
                    returnProcessStep = getFirstProcessStepOfBranch(element1);
                    if(returnProcessStep != null) {
                        return returnProcessStep;
                    }
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {
                for (Element element1 : currentElement.getElements()) {
                    returnProcessStep = getFirstProcessStepOfBranch(element1);
                    if(returnProcessStep != null) {
                        return returnProcessStep;
                    }
                }
            } else if (currentElement instanceof LoopConstruct) {

                if ((currentElement.getNumberOfExecutions() < ((LoopConstruct) currentElement).getNumberOfIterationsToBeExecuted())) {
                    for (Element subElement : currentElement.getElements()) {
                        returnProcessStep = getFirstProcessStepOfBranch(subElement);
                        if(returnProcessStep != null) {
                            return returnProcessStep;
                        }
                    }
                }


            }
        }
        return returnProcessStep;
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
