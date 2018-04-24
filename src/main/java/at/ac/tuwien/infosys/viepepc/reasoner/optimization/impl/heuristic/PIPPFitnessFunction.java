package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import io.jenetics.AnyGene;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PIPPFitnessFunction extends AbstractHeuristicImpl implements Function<Genotype<AnyGene<VirtualMachine>>, Double> {

    @Autowired
    private ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    private CacheContainerService cacheContainerService;

    @Value("${container.default.startup.time}")
    private long defaultContainerStartupTime;
    @Value("${container.default.deploy.time}")
    private long defaultContainerDeployTime;
    @Value("${vm.default.startup.time}")
    private long defaultVMStartupTime;

    private double containerDeployTimeFactor = 0.01;
    private double vmLeasingPreferenceFactor = 0.01;
    private double freeCPUResourcesFactor = 0.001;          // gerta -> 0.01
    private double freeRAMResourcesFactor = 0.001;
    private double scheduledServiceImportanceFactor = 0.001;
    private double containerStartupTimeFactor = 0.01;
    private double penaltyTimeFactor = 0.000001;

    @Setter private List<ProcessStep> alreadyScheduledProcessSteps;
    @Setter private List<ProcessStep> notScheduledProcessSteps;
    @Setter public DateTime epocheTime;

//    public PIPPFitnessFunction(List<ProcessStep> processStepList, DateTime epocheTime) {
//        this.processStepList = processStepList;
//        this.epocheTime = epocheTime;
//    }

    @Override
    public Double apply(Genotype<AnyGene<VirtualMachine>> genotype) {

        Chromosome<AnyGene<VirtualMachine>> chromosome = genotype.getChromosome();

        Map<VirtualMachine, List<ProcessStep>> vmToProcessMap = createVirtualMachineListMap(chromosome, notScheduledProcessSteps, alreadyScheduledProcessSteps);
        Map<String, Set<ProcessStep>> workflowProcessStep = new HashMap<>();
        Set<String> consideredWorkflow = new HashSet<>();

        double overallCost = 0.0;

        // TODO prefer already running VMs, before it was done by Term 4 -> solved by new term 8?
        // TODO consider container start time. Prefer already running container. -> solved by new term 9?

        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            consideredWorkflow.addAll(entry.getValue().stream().map(ps -> ps.getWorkflowName()).collect(Collectors.toSet()));
            for(ProcessStep processStep : entry.getValue()) {
                if(!workflowProcessStep.containsKey(processStep.getWorkflowName())) {
                    workflowProcessStep.put(processStep.getWorkflowName(), new HashSet<>());
                }
                workflowProcessStep.get(processStep.getWorkflowName()).add(processStep);
            }
        }

        // Term 1
        double leasingCost = 0;
        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            leasingCost = leasingCost + entry.getKey().getVmType().getCosts() * getAmountOfLeasingBTUs(entry.getKey(), entry.getValue());
        }

        // Term 2
        double penaltyCost = 0;
        for(Map.Entry<String, Set<ProcessStep>> workflowMap : workflowProcessStep.entrySet()) {

            Set<ProcessStep> processSteps = workflowMap.getValue();
            processSteps.addAll(placementHelper.getRunningProcessSteps(workflowMap.getKey()));

            long remainingExecutionTimeAndDeployTimes = 0;
            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(workflowMap.getKey());
            if(workflowElement != null && workflowElement.getElements() != null) {
                Element rootElement = workflowElement.getElements().get(0);
                remainingExecutionTimeAndDeployTimes = getRemainingWorkflowExecutionTimeAndDeployTimes(rootElement);

                long executionTimeViolation = workflowElement.getDeadline() - (epocheTime.getMillis() + remainingExecutionTimeAndDeployTimes);

                if (executionTimeViolation < 0) {
                    executionTimeViolation = executionTimeViolation * (-1);
                    penaltyCost = penaltyCost + workflowElement.getPenalty() * executionTimeViolation * penaltyTimeFactor;
                }
            }
        }

        // Term 3
        double containerAvailableValue = 0;
        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            for(ProcessStep ps : entry.getValue()) {
                try {
                    ContainerImage image = containerImageRegistryReader.findContainerImage(ps.getServiceType());
                    if(!entry.getKey().getAvailableContainerImages().contains(image)) {
                        containerAvailableValue = containerAvailableValue + defaultContainerDeployTime * containerDeployTimeFactor;
                    }
                } catch (ContainerImageNotFoundException e) {
                    log.error("Container Image not found");
                    return Double.MAX_VALUE; // solution invalid!
                }
            }
        }

        // Term 4    BTU cost not considered anymore
        double remainingLeasingDurationValue = 0;
//        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
//            long remainingLeasingDuration = getRemainingLeasingDurationIncludingScheduled(epocheTime, entry.getKey(), entry.getValue());
//            remainingLeasingDurationValue = remainingLeasingDurationValue + vmLeasingPreferenceFactor * remainingLeasingDuration;
//        }

        // Term 5
        double freeResourcesValue = 0;
        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            try {

                VirtualMachine vm = entry.getKey();
                List<ProcessStep> processSteps = entry.getValue();
                List<ContainerConfiguration> containerConfigurations = getContainerConfigurations(vm, processSteps);

                double requiredCPU = containerConfigurations.stream().mapToDouble(c -> c.getCPUPoints()).sum();
                double requiredRam = containerConfigurations.stream().mapToDouble(c -> c.getRam()).sum();
                requiredCPU = requiredCPU + vm.getDeployedContainers().stream().mapToDouble(c -> c.getContainerConfiguration().getCPUPoints()).sum();
                requiredRam = requiredRam + vm.getDeployedContainers().stream().mapToDouble(c -> c.getContainerConfiguration().getRam()).sum();
                double freeCPU = vm.getVmType().getCpuPoints() - requiredCPU;
                double freeRam = vm.getVmType().getRamPoints() - requiredRam;

                freeResourcesValue = freeResourcesValue + freeCPUResourcesFactor * freeCPU + freeRAMResourcesFactor * freeRam;


            } catch (ContainerConfigurationNotFoundException e) {
                log.error("Container Configuration not found");
                return Double.MAX_VALUE; // solution invalid!
            }

        }

        // Term 6
        // already guaranteed

        // Term 7
        double scheduledServiceImportance = 0;
        for(Map.Entry<String, Set<ProcessStep>> workflowMap : workflowProcessStep.entrySet()) {
            WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(workflowMap.getKey());
            if(workflowElement == null) {
                log.error("WorkflowElement not found: " + workflowMap.getKey());
            }
            scheduledServiceImportance = scheduledServiceImportance + scheduledServiceImportanceFactor * (workflowElement.getDeadline() - epocheTime.getMillis()) / 1000;
        }


        // Prefer already running vms: Term 8 (new)
        double preferRunningVms = 0;
        Set<VirtualMachine> scheduledAndStartedVMs = cacheVirtualMachineService.getStartedAndScheduledForStartVMs();
        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            if(!scheduledAndStartedVMs.contains(entry.getKey())) {
                preferRunningVms = preferRunningVms + defaultVMStartupTime * vmLeasingPreferenceFactor;
            }
        }

        // Prefer vms where the container is already running: Term 9 (new)
        double runningContainer = 0;
        for (Map.Entry<VirtualMachine, List<ProcessStep>> entry : vmToProcessMap.entrySet()) {
            List<ServiceType> runningServiceTypes = new ArrayList<>();

//            List<ServiceType> runningServiceTypes = entry.getKey().getDeployedContainers().stream().map(container -> container.getContainerImage().getServiceType()).collect(Collectors.toList());
            for(Container container : entry.getKey().getDeployedContainers()) {
                runningServiceTypes.add(container.getContainerImage().getServiceType());
            }

            for(ProcessStep ps : entry.getValue()) {
                if(!runningServiceTypes.contains(ps.getServiceType())) {
                    runningContainer = runningContainer + defaultContainerStartupTime * containerStartupTimeFactor;
                }
            }
        }

        overallCost = leasingCost + penaltyCost + containerAvailableValue + remainingLeasingDurationValue + freeResourcesValue + scheduledServiceImportance + preferRunningVms + runningContainer;
        return overallCost;
    }

       private long getRemainingWorkflowExecutionTimeAndDeployTimes(Element currentElement) {
        if (currentElement instanceof ProcessStep) {
            if (!((ProcessStep) currentElement).hasBeenExecuted()) {
//                return ((ProcessStep) currentElement).getServiceType().getServiceTypeResources().getMakeSpan();
                return getRemainingExecutionTimeAndDeployTimes((ProcessStep) currentElement);
            }
        } else {
            long exec = 0;
            if (currentElement instanceof WorkflowElement) {
                for (Element element : currentElement.getElements()) {
                    exec = exec + getRemainingWorkflowExecutionTimeAndDeployTimes(element);
                }
            } else if (currentElement instanceof Sequence) {
                for (Element element1 : currentElement.getElements()) {
                    exec = exec + getRemainingWorkflowExecutionTimeAndDeployTimes(element1);
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {
                long max = 0;
                for (Element element1 : currentElement.getElements()) {
                    long execDuration = getRemainingWorkflowExecutionTimeAndDeployTimes(element1);
                    if (execDuration > max) {
                        max = execDuration;
                    }
                }
                exec += max;
            } else if (currentElement instanceof LoopConstruct) {
                long max = 0;
                for (Element element1 : currentElement.getElements()) {
                    long execDuration = getRemainingWorkflowExecutionTimeAndDeployTimes(element1);
                    if (execDuration > max) {
                        max = execDuration;
                    }
                }
                max = max * 3;
                exec = exec + max;
            }
            return exec;
        }
        return 0;
    }


    public long getRemainingExecutionTimeAndDeployTimes(ProcessStep processStep) {
        long remainingExecutionTime = processStep.getRemainingExecutionTime(epocheTime);
        if (processStep.isScheduled()) {
            remainingExecutionTime += placementHelper.getRemainingSetupTime(processStep.getScheduledAtContainer(), epocheTime);
        }
        else {
            remainingExecutionTime += defaultContainerStartupTime + defaultContainerDeployTime + defaultVMStartupTime;
        }
        if (remainingExecutionTime < 0) {
            remainingExecutionTime = 0;
        }
        return remainingExecutionTime;

    }

    private int getAmountOfLeasingBTUs(VirtualMachine vm, List<ProcessStep> processSteps){
        long overallExecutionTime = 0;
        for(ProcessStep ps : processSteps) {
            overallExecutionTime = overallExecutionTime + ps.getExecutionTime();
        }


        long remainingBTU = getRemainingLeasingDuration(epocheTime, vm);

        int leasedBTUs = 1;
        while(leasedBTUs * vm.getVmType().getLeasingDuration() + remainingBTU < overallExecutionTime) {
            leasedBTUs = leasedBTUs + 1;
        }

        return leasedBTUs;
    }

}
