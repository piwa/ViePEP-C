package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic;

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
import java.util.List;

@Component
@Slf4j
public class PIPPCoder extends AbstractHeuristicImpl {

    public OptimizationResult decode(@NotNull Phenotype<AnyGene<VirtualMachine>, Double> result, @NotNull List<ProcessStep> processSteps) {
        OptimizationResult optimizationResult = new OptimizationResultImpl();

        StringBuilder stringBuilder = new StringBuilder("[");
        Chromosome<AnyGene<VirtualMachine>> resultChromosome = result.getGenotype().getChromosome();
        for (int i = 0; i < processSteps.size(); i++) {
            try {
                VirtualMachine vm = resultChromosome.getGene(i).getAllele();
                ProcessStep processStep = processSteps.get(i);

                if (processStep.getScheduledAtContainer() == null) {
                    processStep.setScheduledAtContainer(getContainer(processStep));
                    processStep.getScheduledAtContainer().setVirtualMachine(vm);
                    optimizationResult.addProcessStep(processStep);
                }
                else {
                    if(processStep.getScheduledAtContainer().getVirtualMachine() == null || processStep.getScheduledAtContainer().getVirtualMachine() != vm) {
                        log.error("problem");

                    }
                }
                stringBuilder.append(processStep.getName()).append("->").append(vm.getName()).append(",");
            } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException e) {
                log.error("Exception", e);
            }
        }

        stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(","));
        stringBuilder.append("]");
        log.info(stringBuilder.toString() + " --> " + result.getFitness());

        return optimizationResult;
    }

}
