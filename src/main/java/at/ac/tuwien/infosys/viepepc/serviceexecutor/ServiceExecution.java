package at.ac.tuwien.infosys.viepepc.serviceexecutor;


import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ProcessStepElementRepository;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ProcessStepDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.Reasoning;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.dto.InvocationResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
@Scope("prototype")
@Slf4j
public class ServiceExecution{

    @Autowired
    private ServiceInvoker serviceInvoker;
    @Autowired
    private ProcessStepElementRepository processStepElementRepository;

    @Value("${simulate}")
    private boolean simulate;

//    @Async
    public void startExecution(ProcessStep processStep, VirtualMachine virtualMachine) {
        processStep.setStartDate(DateTime.now());
        log.info("Task-Start: " + processStep);

        processStepElementRepository.save(processStep);

        if (simulate) {
            try {
                Thread.sleep(processStep.getExecutionTime());
            } catch (InterruptedException e) {
                log.error("EXCEPTION", e);
            }
        } else {
            InvocationResultDTO invoke = serviceInvoker.invoke(virtualMachine, processStep);
        }

//        finaliseExecution(processStep);
    }

//    @Async
	public void startExecution(ProcessStep processStep, Container container) {
        processStep.setStartDate(DateTime.now());
		log.info("Task-Start: " + processStep);

        processStepElementRepository.save(processStep);

        if (simulate) {
            try {
                Thread.sleep(processStep.getExecutionTime());
            } catch (InterruptedException e) {
            }
        } else {
            InvocationResultDTO invoke = serviceInvoker.invoke(container, processStep);
        }

//        finaliseExecution(processStep);
        	
	}

}
