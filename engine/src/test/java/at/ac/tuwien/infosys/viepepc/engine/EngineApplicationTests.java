package at.ac.tuwien.infosys.viepepc.engine;

import at.ac.tuwien.infosys.viepepc.engine.manager.rest.impl.WorkflowRestServiceImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.library.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.service.ServiceTypeNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.UUID;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class EngineApplicationTests {

    @Autowired
    private ServiceRegistryReader serviceRegistryReader;
    @Autowired
    private WorkflowRestServiceImpl workflowRestServiceImpl;
    private double factor = 2.5;

    @Test
    public void contextLoads() {

    }

    @Test
    public void addWorkflow() {

        try {
            String processName = UUID.randomUUID().toString().substring(0, 8) + "pr1";
            WorkflowElement process1 = getProcess1(processName);
            WorkflowElements listOfWorkflowElements = new WorkflowElements();
            listOfWorkflowElements.getWorkflowElements().add(process1);
            workflowRestServiceImpl.addWorkflow(listOfWorkflowElements);
        } catch (ServiceTypeNotFoundException e) {
            log.error("EXCEPTION", e);
        }
    }


    public WorkflowElement getProcess1(String name) throws ServiceTypeNotFoundException {
        WorkflowElement workflow = new WorkflowElement(name, DateTime.now().getMillis());
        Sequence seq = new Sequence(name + "-seq");
        ProcessStep elem1 = new ProcessStep(name + ".1", serviceRegistryReader.findServiceType("Service1"), workflow.getName());
        seq.addElement(elem1);
        ProcessStep elem2 = new ProcessStep(name + ".2", serviceRegistryReader.findServiceType("Service2"), workflow.getName());
        seq.addElement(elem2);
        ProcessStep elem = new ProcessStep(name + ".3", serviceRegistryReader.findServiceType("Service3"), workflow.getName());
        elem.setLastElement(true);
        seq.addElement(elem);
        workflow.addElement(seq);

        long execDuration = getExecDuration(workflow);
        workflow.setDeadline((long) (DateTime.now().getMillis() + execDuration * factor));

        return workflow;
    }

    private long getExecDuration(Element currentElement) {
        if (currentElement instanceof ProcessStep) {
            return ((ProcessStep) currentElement).getServiceType().getServiceTypeResources().getMakeSpan();
        } else {
            long exec = 0;
            if (currentElement instanceof WorkflowElement) {
                for (Element element : currentElement.getElements()) {
                    exec += getExecDuration(element);
                }
            } else if (currentElement instanceof Sequence) {
                for (Element element1 : currentElement.getElements()) {
                    exec += getExecDuration(element1);
                }
            } else if (currentElement instanceof ANDConstruct || currentElement instanceof XORConstruct) {
                long max = 0;
                for (Element element1 : currentElement.getElements()) {
                    long execDuration = getExecDuration(element1);
                    if (execDuration > max) {
                        max = execDuration;
                    }
                }
                exec += max;
            } else if (currentElement instanceof LoopConstruct) {
                long max = 0;
                for (Element element1 : currentElement.getElements()) {
                    long execDuration = getExecDuration(element1);
                    if (execDuration > max) {
                        max = execDuration;
                    }
                }
                max *= 3;
                exec += max;
            }
            return exec;
        }
    }

}

