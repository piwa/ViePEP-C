package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.*;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.uncommons.watchmaker.framework.factories.AbstractCandidateFactory;

import javax.xml.ws.Service;
import java.util.*;

@Slf4j
public class Factory extends AbstractCandidateFactory<Chromosome> {

    private final List<List<Chromosome.Gene>> template = new ArrayList<>();
    private Map<ServiceType, ServiceType> clonedServiceTypes = new HashMap<>();
    private Map<UUID, Chromosome.Gene> stepGeneMap = new HashMap<>();

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

            for (Chromosome.Gene gene : subChromosome) {
                stepGeneMap.put(gene.getProcessStep().getInternId(), gene);
            }

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

                for (Chromosome.Gene originalNextGene : originalNextGenes) {
                    clonedGene.addNextGene(originalToCloneMap.get(originalNextGene));
                }

                for (Chromosome.Gene originalPreviousGene : originalPreviousGenes) {
                    clonedGene.addPreviousGene(originalToCloneMap.get(originalPreviousGene));
                }

            }
        }

        return new Chromosome(candidate);
    }


    private DateTime createStartChromosome(Element currentElement, DateTime startTime, List<Chromosome.Gene> chromosome) {
        if (currentElement instanceof ProcessStep) {
            ProcessStep processStep = (ProcessStep) currentElement;
            boolean containerAlreadyDeployed = false;
            boolean isRunning = processStep.getStartDate() != null && processStep.getFinishedAt() == null;

            if (processStep.getStartDate() != null && processStep.getFinishedAt() != null) {
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
                Chromosome.Gene gene = new Chromosome.Gene(getClonedProcessStep((ProcessStep) currentElement), realStartTime, true);
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

                    for(Chromosome.Gene beforeAnd : gene.getPreviousGenes()) {
                        beforeAnd.getNextGenes().add(andMember);
                    }

                    for(Chromosome.Gene afterAnd : andMember.getNextGenes()) {
                        afterAnd.getPreviousGenes().add(andMember);
                    }

                }
            }
        }
    }



    private ProcessStep fillProcessStepChainRec(Element currentElement, ProcessStep previousProcessStep, Map<Chromosome.Gene, List<Chromosome.Gene>> andMembersMap) {
        if (currentElement instanceof ProcessStep) {
            ProcessStep processStep = (ProcessStep) currentElement;

            if(processStep.isHasToBeExecuted()) {
                if (previousProcessStep != null && stepGeneMap.get(previousProcessStep.getInternId()) != null) {
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
