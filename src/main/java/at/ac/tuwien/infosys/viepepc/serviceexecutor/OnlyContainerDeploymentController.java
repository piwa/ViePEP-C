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
import at.ac.tuwien.infosys.viepepc.watchdog.Message;
import at.ac.tuwien.infosys.viepepc.watchdog.ServiceExecutionStatus;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 40000;

    private ProcessStep processStep;
    private Container container;

    public OnlyContainerDeploymentController(ProcessStep processStep) {
        this.processStep = processStep;
        this.container = processStep.getScheduledAtContainer();
    }

    @Override
    public void run() {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start("deploy container");

        boolean success = deployContainer(container);

        if (success) {
            stopWatch.stop();
            log.debug("Container deploy duration: " + container.toString() + ": " + stopWatch.getTotalTimeMillis());


//            if(DateTime.now().isBefore(processStep.getScheduledStartedAt().minus(2000))) {
//                taskScheduler.schedule(() -> {
//                    try {
//                        serviceExecution.startExecution(processStep, container);
//                    } catch (ServiceInvokeException e) {
//                        log.error("Exception while invoking service. Reset.", e);
//                        reset("Service");
//                    }
//                }, processStep.getScheduledStartedAt().toDate());
//            }
//            else {

            if (DateTime.now().isBefore(processStep.getScheduledStartedAt().minusSeconds(2))) {
                Duration duration = new Duration(DateTime.now(), processStep.getScheduledStartedAt().minusSeconds(2));
                try {
                    TimeUnit.MILLISECONDS.sleep(duration.getMillis());
                } catch (InterruptedException e) {
                    log.error("Exception while invoking service. Reset.", e);
                }
            }


            try {
                serviceExecution.startExecution(processStep, container);
            } catch (ServiceInvokeException e) {
                log.error("Exception while invoking service. Reset.", e);
                reset("Service");
            }
//            }
        } else {
            reset("Container");
        }

    }


    private boolean deployContainer(Container container) {
        synchronized (container) {
            if (container.isRunning()) {
                log.debug("Container already running: " + container);
                return true;
            }

            try {
                log.info("Deploy new container: " + container);
                dockerControllerService.startContainer(container);
                ContainerReportingAction report = null;
                if (simulate) {
                    report = new ContainerReportingAction(DateTime.now().plus(onlyContainerDeploymentTime), container.getName(), container.getContainerConfiguration().getName(), null, Action.START);
                } else {
                    report = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), null, Action.START);
                }
                reportDaoService.save(report);
                return true;

            } catch (InterruptedException | DockerException e) {
                log.error("EXCEPTION while deploying Container. Reset execution request.", e);
                return false;
            }
        }
    }


    private void reset(String failureReason) {
        if (container != null) {
            ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), null, Action.FAILED, failureReason);
            reportDaoService.save(reportContainer);
            container.shutdownContainer();
        }

        inMemoryCache.getWaitingForExecutingProcessSteps().remove(processStep);
        inMemoryCache.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
        processStep.reset();
    }

}
