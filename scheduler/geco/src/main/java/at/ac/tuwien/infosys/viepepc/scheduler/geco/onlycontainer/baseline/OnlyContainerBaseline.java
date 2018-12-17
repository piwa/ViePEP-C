package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.baseline;

import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.AbstractOnlyContainerOptimization;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer.factory.DeadlineAwareFactory;
import at.ac.tuwien.infosys.viepepc.scheduler.library.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.library.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.scheduler.library.SchedulerAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Future;

@Slf4j
@Component
@Profile("OnlyContainerBaseline")
public class OnlyContainerBaseline extends AbstractOnlyContainerOptimization implements SchedulerAlgorithm {

    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private CacheContainerService containerService;

    @Value("${container.default.startup.time}")
    private long defaultContainerStartupTime;
    @Value("${container.default.deploy.time}")
    private long defaultContainerDeployTime;

    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;

    @Value("${deadline.aware.factory.allowed.penalty.points}")
    private int allowedPenaltyPoints;
    @Value("${slack.webhook}")
    private String slackWebhook;

    private DateTime optimizationTime;

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        List<WorkflowElement> workflowElements = getRunningWorkflowInstancesSorted();

        this.optimizationTime = DateTime.now();

        if (workflowElements.size() == 0) {
            return new OptimizationResult();
        }

        DeadlineAwareFactory factory = new DeadlineAwareFactory(workflowElements, this.optimizationTime, defaultContainerDeployTime, defaultContainerStartupTime, false, optimizationUtility, onlyContainerDeploymentTime, allowedPenaltyPoints, slackWebhook);

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
