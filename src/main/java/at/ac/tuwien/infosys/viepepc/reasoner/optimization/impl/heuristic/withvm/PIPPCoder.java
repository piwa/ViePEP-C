package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.withvm;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import io.jenetics.AnyGene;
import io.jenetics.Chromosome;
import io.jenetics.Phenotype;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PIPPCoder extends AbstractHeuristicImpl {

    public OptimizationResult decode(@NotNull Phenotype<AnyGene<VirtualMachine>, Double> result, @NotNull List<ProcessStep> processSteps) {
        OptimizationResult optimizationResult = new OptimizationResultImpl();

        StringBuilder stringBuilder = new StringBuilder("[");
        Chromosome<AnyGene<VirtualMachine>> resultChromosome = result.getGenotype().getChromosome();

        Map<VirtualMachine, List<Container>> vmContainerMap = new HashMap<>();


        Map<ServiceType, Container> serviceTypeContainerMap = new HashMap<>();

        try {
            for (ProcessStep processStep : processSteps) {
                if (!serviceTypeContainerMap.containsKey(processStep.getServiceType())) {
                    serviceTypeContainerMap.put(processStep.getServiceType(), getContainer(processStep));
                }
            }

            for (int i = 0; i < processSteps.size(); i++) {

                VirtualMachine vm = resultChromosome.getGene(i).getAllele();
                ProcessStep processStep = processSteps.get(i);

                Container availableContainer = null;
                for(Container container : vm.getDeployedContainers()) {
                    if(processStep.getServiceType() == container.getContainerImage().getServiceType()) {
                        availableContainer = container;
                        break;
                    }
                }

                if(availableContainer != null) {
                    processStep.setScheduledAtContainer(availableContainer);
                    processStep.getScheduledAtContainer().setVirtualMachine(vm);
                    optimizationResult.addProcessStep(processStep);
                } else if (processStep.getScheduledAtContainer() == null) {
                    // TODO consider that a fitting container could already be in start phase
                    processStep.setScheduledAtContainer(serviceTypeContainerMap.get(processStep.getServiceType()));
                    processStep.getScheduledAtContainer().setVirtualMachine(vm);
                    optimizationResult.addProcessStep(processStep);
                } else if (processStep.getScheduledAtContainer().getVirtualMachine() == null || processStep.getScheduledAtContainer().getVirtualMachine() != vm) {
                    log.error("problem");
                }

                stringBuilder.append(processStep.getName()).append("->").append(vm.getName()).append(",");

            }

        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException e) {
            log.error("Exception", e);
        }

        stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(","));
        stringBuilder.append("]");
        log.info(stringBuilder.toString() + " --> " + result.getFitness());

        return optimizationResult;
    }

}
