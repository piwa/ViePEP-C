package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.baseline;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.AbstractOnlyContainerOptimization;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.factory.DeadlineAwareFactory;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.factory.SimpleFactory;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.onlycontainer.ServiceTypeSchedulingUnit;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.heuristic.withvm.AbstractHeuristicImpl;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

@Slf4j
public class OnlyContainerBaseline extends AbstractOnlyContainerOptimization implements ProcessInstancePlacementProblem {

    @Autowired
    private OptimizationUtility optimizationUtility;

    @Value("${container.default.startup.time}")
    private long defaultContainerStartupTime;
    @Value("${container.default.deploy.time}")
    private long defaultContainerDeployTime;

    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;

    private DateTime optimizationTime;

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {


        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();

        this.optimizationTime = DateTime.now();

        if (workflowElements.size() == 0) {
            return new OptimizationResultImpl();
        }

        DeadlineAwareFactory factory = new DeadlineAwareFactory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime, false, optimizationUtility, onlyContainerDeploymentTime);

        return createOptimizationResult(new Chromosome(factory.getTemplate()), workflowElements);
    }

    @Override
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return null;
    }


    @Override
    public void initializeParameters() {

    }
}
