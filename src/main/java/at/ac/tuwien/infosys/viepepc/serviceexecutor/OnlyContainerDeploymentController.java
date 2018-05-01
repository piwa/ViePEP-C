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
@Slf4j
public class OnlyContainerDeploymentController implements Runnable {

    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private ServiceExecution serviceExecution;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private ViePEPDockerControllerService dockerControllerService;

    private ProcessStep processStep;
    private Container container;

    public OnlyContainerDeploymentController(ProcessStep processStep) {
        this.processStep = processStep;
        this.container = processStep.getScheduledAtContainer();
    }

    @Override
    public void run() {

        log.info("Start Container: " + container);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("deploy container");

        boolean success = deployContainer(container);

        if(success) {
            stopWatch.stop();
            log.info("Container deploy duration: " + container.toString() + ": " + stopWatch.getTotalTimeMillis());

            try {
                serviceExecution.startExecution(processStep, container);
            } catch (ServiceInvokeException e) {
                log.error("Exception while invoking service. Reset.", e);
                reset("Service");
            }
        }
        else {
            reset("Container");
        }

    }


    private boolean deployContainer(Container container) {
        synchronized (container) {
            if (container.isRunning()) {
                log.info(container + " already running");
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
    }


    private void reset(String failureReason) {
        if(container != null) {
            ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), container.getName(), null, Action.FAILED, failureReason);
            reportDaoService.save(reportContainer);
            container.shutdownContainer();
        }

        inMemoryCache.getWaitingForExecutingProcessSteps().remove(processStep);
        inMemoryCache.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
        processStep.reset();
    }

}
