package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.SpringContext;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.ContainerSchedulingUnit;
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
public class DeadlineAwareFactory extends AbstractCandidateFactory<Chromosome> {

    @Autowired
    private DeadlineAwareFactoryInitializer deadlineAwareFactoryInitializer;
    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private VMSelectionHelper vmSelectionHelper;

    @Value("${slack.webhook}")
    private String slackWebhook;
    @Value("${deadline.aware.factory.allowed.penalty.points}")
    private int allowedPenaltyPoints;
    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;

    private OrderMaintainer orderMaintainer = new OrderMaintainer();
    private Map<UUID, Chromosome.Gene> stepGeneMap = new HashMap<>();
    @Getter
    private List<List<Chromosome.Gene>> template = new ArrayList<>();
    @Getter
    private Map<String, DateTime> maxTimeAfterDeadline = new HashMap<>();
    private Map<String, DateTime> workflowDeadlines = new HashMap<>();
    private DateTime optimizationEndTime;

    private Random random;


    public void initialize(List<WorkflowElement> workflowElementList, DateTime optimizationEndTime) {
        this.stepGeneMap = new HashMap<>();
        this.template = new ArrayList<>();
        this.maxTimeAfterDeadline = new HashMap<>();
        this.workflowDeadlines = new HashMap<>();
        this.optimizationEndTime = new DateTime(optimizationEndTime);

        this.deadlineAwareFactoryInitializer.initialize(optimizationEndTime);

        for (WorkflowElement workflowElement : workflowElementList) {
            stepGeneMap = new HashMap<>();

            List<Chromosome.Gene> subChromosome = deadlineAwareFactoryInitializer.createStartChromosome(workflowElement);
            if (subChromosome.size() == 0) {
                continue;
            }

            subChromosome.forEach(gene -> stepGeneMap.put(gene.getProcessStepSchedulingUnit().getInternId(), gene));
            fillProcessStepChain(workflowElement);

            subChromosome.stream().filter(Chromosome.Gene::isFixed).forEach(this::setAllPrecedingFixed);

            template.add(subChromosome);
            workflowDeadlines.put(workflowElement.getName(), workflowElement.getDeadlineDateTime());
            calculateMaxTimeAfterDeadline(workflowElement, subChromosome);

        }


        orderMaintainer.checkAndMaintainOrder(new Chromosome(template));

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkIfFixedGeneHasContainerSchedulingUnit(new Chromosome(template), this.getClass().getSimpleName() + "_initialize_3");

    }

    /***
     * Guarantee that the process step order is preserved and that there are no overlapping steps
     * @param random
     * @return
     */
    @Override
    public Chromosome generateRandomCandidate(Random random) {
        this.random = random;
        List<List<Chromosome.Gene>> candidate = new ArrayList<>();

        for (List<Chromosome.Gene> row : template) {
            List<Chromosome.Gene> newRow = createClonedRow(row);

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
            candidate.add(newRow);

        }

        Chromosome newChromosome = new Chromosome(candidate);

        scheduleContainerAndVM(newChromosome);
        considerFirstVMAndContainerStartTime(newChromosome);

        vmSelectionHelper.mergeVirtualMachineSchedulingUnits(newChromosome);
//        vmSelectionHelper.checkVmSizeAndSolveSpaceIssues(newChromosome);
        orderMaintainer.checkRowAndPrintError(newChromosome, this.getClass().getSimpleName() + "_generateRandomCandidate_2", "generateRandomCandidate");

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkContainerSchedulingUnits(newChromosome, this.getClass().getSimpleName() + "_generateRandomCandidate_4");

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

    private void scheduleContainerAndVM(Chromosome newChromosome) {

        List<ContainerSchedulingUnit> containerSchedulingUnits = optimizationUtility.createRequiredContainerSchedulingUnits(newChromosome, getFixedContainer(newChromosome));

        Set<VirtualMachineSchedulingUnit> alreadyUsedVirtualMachineSchedulingUnits = containerSchedulingUnits.stream().map(ContainerSchedulingUnit::getScheduledOnVm).filter(Objects::nonNull).collect(Collectors.toSet());

        for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits) {
            if (containerSchedulingUnit.getScheduledOnVm() == null) {

                List<ContainerSchedulingUnit> tempList = new ArrayList<>();
                tempList.add(containerSchedulingUnit);
                VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = vmSelectionHelper.getVirtualMachineSchedulingUnit(alreadyUsedVirtualMachineSchedulingUnits, tempList);

                containerSchedulingUnit.setScheduledOnVm(virtualMachineSchedulingUnit);
                virtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
                alreadyUsedVirtualMachineSchedulingUnits.add(virtualMachineSchedulingUnit);
            }
        }
    }

    private Map<ProcessStepSchedulingUnit, ContainerSchedulingUnit> getFixedContainer(Chromosome newChromosome) {
        Map<ProcessStepSchedulingUnit, ContainerSchedulingUnit> fixedContainerSchedulingUnitMap = new HashMap<>();

        for (Chromosome.Gene gene : newChromosome.getFlattenChromosome()) {
            if(gene.isFixed()) {
                ProcessStepSchedulingUnit processStepSchedulingUnit = gene.getProcessStepSchedulingUnit();
                ContainerSchedulingUnit containerSchedulingUnit = processStepSchedulingUnit.getContainerSchedulingUnit();
                fixedContainerSchedulingUnitMap.put(processStepSchedulingUnit, containerSchedulingUnit);
            }
        }

        return fixedContainerSchedulingUnitMap;
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

    private void considerFirstVMAndContainerStartTime(Chromosome newChromosome) {

        boolean redo = true;

        while (redo) {
            List<ContainerSchedulingUnit> containerSchedulingUnits = newChromosome.getGenes().stream().flatMap(List::stream).map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit()).collect(Collectors.toList());

            redo = false;
            for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits) {
                try {
                    DateTime containerDeploymentStartTime = containerSchedulingUnit.getDeployStartTime();
                    VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = containerSchedulingUnit.getScheduledOnVm();

                    DateTime vmDeploymentStartTime = virtualMachineSchedulingUnit.getDeploymentTimes(virtualMachineSchedulingUnit.getVmAvailableInterval());

                    if (vmDeploymentStartTime.isBefore(this.optimizationEndTime)) {
                        for (Chromosome.Gene gene : containerSchedulingUnit.getProcessStepGenes()) {
                            if (!gene.isFixed()) {
                                long deltaTime = new Duration(vmDeploymentStartTime, this.optimizationEndTime).getMillis();

                                gene.moveIntervalPlus(deltaTime);
                                orderMaintainer.checkAndMaintainOrder(newChromosome);

                                redo = true;
                            }
                        }
                    }
                    else if(containerDeploymentStartTime.isBefore(this.optimizationEndTime)) {
                        for (Chromosome.Gene gene : containerSchedulingUnit.getProcessStepGenes()) {
                            if (containerDeploymentStartTime.isBefore(this.optimizationEndTime) && !gene.isFixed()) {
                                long deltaTime = new Duration(containerDeploymentStartTime, this.optimizationEndTime).getMillis();

                                gene.moveIntervalPlus(deltaTime);
                                orderMaintainer.checkAndMaintainOrder(newChromosome);

                                redo = true;
                            }
                        }
                    }
                    if (redo) {

                        List<VirtualMachineSchedulingUnit> vmSchedulingUnits = containerSchedulingUnits.stream().map(ContainerSchedulingUnit::getScheduledOnVm).collect(Collectors.toList());
                        Collection<ContainerSchedulingUnit> fixedContainerSchedulingUnits = getFixedContainer(newChromosome).values();
                        for (VirtualMachineSchedulingUnit vmSchedulingUnit : vmSchedulingUnits) {
                            vmSchedulingUnit.getScheduledContainers().removeIf(containerSchedulingUnit1 -> !fixedContainerSchedulingUnits.contains(containerSchedulingUnit1));
                        }

                        scheduleContainerAndVM(newChromosome);

//                        for (Chromosome.Gene gene : newChromosome.getFlattenChromosome()) {
//                            if(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit() == null || gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getScheduledContainers().size() == 0) {
//                                log.error("Exception");
//                            }
//                        }

                        break;
                    }
                } catch (Exception e) {
                    log.error("No matching interval found", e);
                }
            }
        }
    }

    private List<Chromosome.Gene> createClonedRow(List<Chromosome.Gene> row) {


        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkIfFixedGeneHasContainerSchedulingUnit(row, this.getClass().getSimpleName() + "_cloneRow_1");


        List<Chromosome.Gene> newRow = new ArrayList<>();
        Map<Chromosome.Gene, Chromosome.Gene> originalToCloneMap = new HashMap<>();

        for (Chromosome.Gene gene : row) {
            Chromosome.Gene newGene = gene.clone();
            originalToCloneMap.put(gene, newGene);
            newRow.add(newGene);
        }

//        Map<ProcessStepSchedulingUnit, ProcessStepSchedulingUnit> originalToCloneProcessStepSchedulingMap = new HashMap<>();
        Map<ContainerSchedulingUnit, ContainerSchedulingUnit> originalToCloneContainerSchedulingMap = new HashMap<>();
        Map<VirtualMachineSchedulingUnit, VirtualMachineSchedulingUnit> originalToCloneVirtualMachineSchedulingMap = new HashMap<>();
        for (List<Chromosome.Gene> subChromosome : template) {
            for (Chromosome.Gene originalGene : subChromosome) {
                Chromosome.Gene clonedGene = originalToCloneMap.get(originalGene);
                if (clonedGene != null) {
                    Set<Chromosome.Gene> originalNextGenes = originalGene.getNextGenes();
                    Set<Chromosome.Gene> originalPreviousGenes = originalGene.getPreviousGenes();

                    originalNextGenes.stream().map(originalToCloneMap::get).forEachOrdered(clonedGene::addNextGene);
                    originalPreviousGenes.stream().map(originalToCloneMap::get).forEachOrdered(clonedGene::addPreviousGene);

                    ProcessStepSchedulingUnit originalProcessStepSchedulingUnit = originalGene.getProcessStepSchedulingUnit();
                    ContainerSchedulingUnit originalContainerSchedulingUnit = originalProcessStepSchedulingUnit.getContainerSchedulingUnit();

                    ProcessStepSchedulingUnit clonedProcessStepSchedulingUnit = originalProcessStepSchedulingUnit.clone();
                    clonedGene.setProcessStepSchedulingUnit(clonedProcessStepSchedulingUnit);
//                    originalToCloneProcessStepSchedulingMap.putIfAbsent(originalProcessStepSchedulingUnit, clonedProcessStepSchedulingUnit);

                    if(originalContainerSchedulingUnit != null) {
                        originalToCloneVirtualMachineSchedulingMap.putIfAbsent(originalContainerSchedulingUnit.getScheduledOnVm(), originalContainerSchedulingUnit.getScheduledOnVm().clone());
                        originalToCloneContainerSchedulingMap.putIfAbsent(originalContainerSchedulingUnit, originalContainerSchedulingUnit.clone());

                        ContainerSchedulingUnit clonedContainerSchedulingUnit = originalToCloneContainerSchedulingMap.get(originalContainerSchedulingUnit);
                        VirtualMachineSchedulingUnit clonedVirtualMachineSchedulingUnit = originalToCloneVirtualMachineSchedulingMap.get(originalContainerSchedulingUnit.getScheduledOnVm());

                        clonedProcessStepSchedulingUnit.setContainerSchedulingUnit(clonedContainerSchedulingUnit);

//                        clonedVirtualMachineSchedulingUnit.getScheduledContainers().remove(originalContainerSchedulingUnit);
                        clonedVirtualMachineSchedulingUnit.getScheduledContainers().add(clonedContainerSchedulingUnit);

                        clonedContainerSchedulingUnit.setScheduledOnVm(clonedVirtualMachineSchedulingUnit);
//                        clonedContainerSchedulingUnit.getProcessStepGenes().remove(originalGene);
                        clonedContainerSchedulingUnit.getProcessStepGenes().add(clonedGene);

                    }
                }
            }
        }

        SpringContext.getApplicationContext().getBean(OptimizationUtility.class).checkIfFixedGeneHasContainerSchedulingUnit(newRow, this.getClass().getSimpleName() + "_cloneRow_2");


        return newRow;
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

        for (Chromosome.Gene gene : startGenes) {
            moveNewChromosomeRec(gene.getNextGenes(), bufferBound);
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


    private void setAllPrecedingFixed(Chromosome.Gene gene) {
        gene.getPreviousGenes().forEach(prevGene -> {
            prevGene.setFixed(true);
            deadlineAwareFactoryInitializer.setContainerAndVMSchedulingUnit(prevGene.getProcessStepSchedulingUnit());
            setAllPrecedingFixed(prevGene);
        });
    }


    private void fillProcessStepChain(Element workflowElement) {
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


}
