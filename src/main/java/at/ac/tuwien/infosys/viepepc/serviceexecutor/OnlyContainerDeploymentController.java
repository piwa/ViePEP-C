package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokeException;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.Map;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
@Scope("prototype")
@Slf4j
public class OnlyContainerDeploymentController {

    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private ServiceExecution serviceExecution;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private ViePEPDockerControllerService dockerControllerService;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${use.container}")
    private boolean useDocker;


    @Async
    public void deployContainerAndStartExecution(Container container, List<ProcessStep> processSteps) {

        log.info("Start Container: " + container);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("deploy container");

        boolean success = deployContainer(container);

        if(success) {
            stopWatch.stop();
            log.info("Container deploy duration: " + container.toString() + ": " + stopWatch.getTotalTimeMillis());

            startExecutionsOnContainer(container, processSteps);
//            for (final ProcessStep processStep : processSteps) {
//                try {
//                    serviceExecution.startExecution(processStep, container);
//                } catch (ServiceInvokeException e) {
//                    log.error("Exception while invoking service. Stop VM and reset.", e);
//                    reset(processSteps, container, "Service");
//                }
//            }
        }
        else {
            reset(processSteps, container, "Container");
        }

    }

    @Async
    public void startExecutionsOnContainer(Container container, List<ProcessStep> processSteps) {

        for (final ProcessStep processStep : processSteps) {
            try {
                serviceExecution.startExecution(processStep, container);
            } catch (ServiceInvokeException e) {
                log.error("Exception while invoking service. Stop VM and reset.", e);
                reset(processSteps, container, "Service");
            }
        }

    }

    private boolean deployContainer(Container container) {
        if (container.isRunning()) {
            log.info(container + " already running on vm " + container.getVirtualMachine());
            return true;
        }

        try {
            dockerControllerService.startContainer(container);
            ContainerReportingAction report = new ContainerReportingAction(DateTime.now(), container.getName(), null, Action.START);
            reportDaoService.save(report);
            return true;

        } catch (InterruptedException | DockerException e) {
            log.error("EXCEPTION while deploying Container. Reset execution request.", e);
            return false;
        }
    }


    private void reset(List<ProcessStep> value, Container container, String failureReason) {
        resetContainer(container, failureReason);
        resetProcessSteps(value);
    }

    private void resetProcessSteps(List<ProcessStep> value) {
        for(ProcessStep processStep : value) {
            inMemoryCache.getWaitingForExecutingProcessSteps().remove(processStep);
            inMemoryCache.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
            processStep.reset();
        }
    }

    private void resetContainer(Container container, String failureReason) {
        if(container != null) {
            ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), container.getName(), null, Action.FAILED, failureReason);
            reportDaoService.save(reportContainer);
            container.shutdownContainer();
        }
    }

}
