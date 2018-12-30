package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.factory;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.ContainerSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.VirtualMachineSchedulingUnit;
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
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private OptimizationUtility optimizationUtility;

    @Value("${slack.webhook}")
    private String slackWebhook;
    @Value("${deadline.aware.factory.allowed.penalty.points}")
    private int allowedPenaltyPoints;
    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;
    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;

    private OrderMaintainer orderMaintainer = new OrderMaintainer();
    private Map<UUID, Chromosome.Gene> stepGeneMap = new HashMap<>();
    @Getter
    private List<List<Chromosome.Gene>> template = new ArrayList<>();
    @Getter
    private Map<String, DateTime> maxTimeAfterDeadline = new HashMap<>();
    private Map<String, DateTime> workflowDeadlines = new HashMap<>();
    private DateTime optimizationEndTime;

    private DeadlineAwareFactoryInitializer deadlineAwareFactoryInitializer;

    private Random random;


    public void initialize(List<WorkflowElement> workflowElementList, DateTime optimizationEndTime) {


        this.stepGeneMap = new HashMap<>();
        this.template = new ArrayList<>();
        this.maxTimeAfterDeadline = new HashMap<>();
        this.workflowDeadlines = new HashMap<>();
        this.optimizationEndTime = new DateTime(optimizationEndTime);

        this.deadlineAwareFactoryInitializer = new DeadlineAwareFactoryInitializer(optimizationEndTime, containerDeploymentTime, virtualMachineDeploymentTime);

        for (WorkflowElement workflowElement : workflowElementList) {
            stepGeneMap = new HashMap<>();

            List<Chromosome.Gene> subChromosome = deadlineAwareFactoryInitializer.createStartChromosome(workflowElement);

            if (subChromosome.size() == 0) {
                continue;
            }

            subChromosome.forEach(gene -> stepGeneMap.put(gene.getProcessStep().getInternId(), gene));
            fillProcessStepChain(workflowElement);

            subChromosome.stream().filter(Chromosome.Gene::isFixed).forEach(gene -> setAllPrecedingFixed(gene));

            template.add(subChromosome);
            workflowDeadlines.put(workflowElement.getName(), workflowElement.getDeadlineDateTime());
            calculateMaxTimeAfterDeadline(workflowElement, subChromosome);
        }

        orderMaintainer.checkRowAndPrintError(new Chromosome(template), this.getClass().getSimpleName() + "_constructor", slackWebhook);
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

//        orderMaintainer.checkRowAndPrintError(new Chromosome(template), this.getClass().getSimpleName() + "_generateRandomCandidate_1", slackWebhook);

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

            moveNewChromosomeRec(findStartGene(newRow), bufferBound);
            candidate.add(newRow);
        }

        Chromosome newChromosome = new Chromosome(candidate);

        scheduleContainerAndVM(newChromosome);
        considerFirstVMAndContainerStartTime(newChromosome);

        orderMaintainer.checkRowAndPrintError(newChromosome, this.getClass().getSimpleName() + "_generateRandomCandidate_2", slackWebhook);
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

        List<VirtualMachineInstance> virtualMachineInstanceList = cacheVirtualMachineService.getAllVMInstancesFromInMemory();

        List<ContainerSchedulingUnit> containerSchedulingUnits = optimizationUtility.createRequiredContainerSchedulingUnits(newChromosome, this.deadlineAwareFactoryInitializer.getFixedContainerSchedulingUnitMap());

        boolean vmFound = false;
        for (ContainerSchedulingUnit containerSchedulingUnit : containerSchedulingUnits) {
            if (containerSchedulingUnit.getScheduledOnVm() == null) {

                VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = null;
                do {

                    VirtualMachineInstance randomVM = getRandomVirtualMachineInstance(this.deadlineAwareFactoryInitializer.getVirtualMachineSchedulingUnitMap().keySet());
                    virtualMachineSchedulingUnit = this.deadlineAwareFactoryInitializer.getVirtualMachineSchedulingUnitMap().get(randomVM);

                    if (virtualMachineSchedulingUnit == null) {
                        virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(virtualMachineDeploymentTime);
                        virtualMachineSchedulingUnit.setVirtualMachineInstance(randomVM);
                    }

                    vmFound = false;
                    if (virtualMachineSchedulingUnit.getScheduledContainers().contains(containerSchedulingUnit)) {
                        vmFound = true;
                    } else if (enoughResourcesLeftOnVM(virtualMachineSchedulingUnit, containerSchedulingUnit)) {
                        vmFound = true;
                    }

                } while (!vmFound);

                containerSchedulingUnit.setScheduledOnVm(virtualMachineSchedulingUnit);
                virtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
//                if (!this.deadlineAwareFactoryInitializer.getVirtualMachineSchedulingUnitMap().containsKey(virtualMachineSchedulingUnit.getVirtualMachineInstance())) {
                    this.deadlineAwareFactoryInitializer.getVirtualMachineSchedulingUnitMap().putIfAbsent(virtualMachineSchedulingUnit.getVirtualMachineInstance(), virtualMachineSchedulingUnit);
//                }
            }
        }
    }

    private VirtualMachineInstance getRandomVirtualMachineInstance(Set<VirtualMachineInstance> alreadyScheduledVirtualMachines) {

        List<VirtualMachineInstance> availableVMs = cacheVirtualMachineService.getScheduledAndDeployingAndDeployedVMInstances();
        availableVMs.addAll(alreadyScheduledVirtualMachines);

        Random rand = new Random();
        if (rand.nextInt(2) == 0 && availableVMs.size() > 0) {
            int randomPosition = rand.nextInt(availableVMs.size());
            return availableVMs.get(randomPosition);
        } else {
            int randomPosition = rand.nextInt(cacheVirtualMachineService.getVMTypes().size());
            return new VirtualMachineInstance(cacheVirtualMachineService.getVMTypes().get(randomPosition));
        }
    }

    private boolean enoughResourcesLeftOnVM(VirtualMachineSchedulingUnit virtualMachineSchedulingUnit, ContainerSchedulingUnit containerSchedulingUnit) {

        VirtualMachineInstance vm = virtualMachineSchedulingUnit.getVirtualMachineInstance();
        List<Container> containerOnVm = new ArrayList<>();
        containerOnVm.add(containerSchedulingUnit.getContainer());
        containerOnVm.addAll(virtualMachineSchedulingUnit.getScheduledContainers().stream().map(ContainerSchedulingUnit::getContainer).collect(Collectors.toList()));

        double scheduledCPUUsage = containerOnVm.stream().mapToDouble(c -> c.getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = containerOnVm.stream().mapToDouble(c -> c.getContainerConfiguration().getRam()).sum();

        if (vm.getVmType().getCpuPoints() < scheduledCPUUsage || vm.getVmType().getRamPoints() < scheduledRAMUsage) {
            return false;
        }


        return true;
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
            List<ContainerSchedulingUnit> containerSchedulingUnits = newChromosome.getGenes().stream().flatMap(List::stream).map(gene -> gene.getProcessStep().getContainerSchedulingUnit()).collect(Collectors.toList());

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
                        Collection<ContainerSchedulingUnit> fixedContainerSchedulingUnits = this.deadlineAwareFactoryInitializer.getFixedContainerSchedulingUnitMap().values();
                        vmSchedulingUnits.forEach(vmSchedulingUnit ->
                                vmSchedulingUnit.getScheduledContainers().removeIf(containerSchedulingUnit1 ->  !fixedContainerSchedulingUnits.contains(containerSchedulingUnit1))
                        );

                        scheduleContainerAndVM(newChromosome);

                        break;
                    }
                } catch (Exception e) {
                    log.error("No matching interval found", e);
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

        Map<UUID, List<Chromosome.Gene>> map = new HashMap<>();
        for (ContainerSchedulingUnit containerSchedulingUnit : this.deadlineAwareFactoryInitializer.getFixedContainerSchedulingUnitMap().values()) {
            for (Chromosome.Gene processStepGene : containerSchedulingUnit.getProcessStepGenes()) {
                if (originalToCloneMap.containsKey(processStepGene)) {
                    map.putIfAbsent(containerSchedulingUnit.getUid(),new ArrayList<>());
                    map.get(containerSchedulingUnit.getUid()).add(originalToCloneMap.get(processStepGene));
                }
            }
            containerSchedulingUnit.getProcessStepGenes().removeAll(originalToCloneMap.keySet());
        }

        for (ContainerSchedulingUnit containerSchedulingUnit : this.deadlineAwareFactoryInitializer.getFixedContainerSchedulingUnitMap().values()) {
            if(map.containsKey(containerSchedulingUnit.getUid())) {
                containerSchedulingUnit.getProcessStepGenes().addAll(map.get(containerSchedulingUnit.getUid()));
            }
        }

        return newRow;
    }

    private void moveNewChromosomeRec(Set<Chromosome.Gene> startGenes, int bufferBound) {

        for (Chromosome.Gene gene : startGenes) {

            if (!gene.isFixed()) {

                Chromosome.Gene latestPreviousGene = gene.getLatestPreviousGene();

                if (latestPreviousGene != null) {
                    DateTime newStartTime = new DateTime(latestPreviousGene.getExecutionInterval().getEnd().getMillis() + 1);
                    DateTime newEndTime = newStartTime.plus(gene.getProcessStep().getServiceType().getServiceTypeResources().getMakeSpan());
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
