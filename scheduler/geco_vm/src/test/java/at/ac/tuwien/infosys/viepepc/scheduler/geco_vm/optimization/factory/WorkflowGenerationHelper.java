package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.factory;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.*;
import at.ac.tuwien.infosys.viepepc.library.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.service.ServiceTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.util.*;

@Component
@Profile("test")
@Slf4j
public class WorkflowGenerationHelper {

    @Autowired
    private ServiceRegistryReader serviceRegistryReader;
    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;


    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;
    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;

    private double factor = 2.5;

    private JAXBContext context;
    private Unmarshaller unmarshaller;

    public WorkflowGenerationHelper() {
        try {
            context = JAXBContext.newInstance(WorkflowElement.class);
            unmarshaller = context.createUnmarshaller();
        } catch (JAXBException e) {
            log.error("Exception", e);
        }
    }

    public List<WorkflowElement> createAllWorkflowElements() throws JAXBException, ServiceTypeNotFoundException {
        List<WorkflowElement> workflowElementsList = new ArrayList<>();
        workflowElementsList.add(getParallelDifferentServicesProcess());
        workflowElementsList.add(getParallelSameServicesProcess());
        workflowElementsList.add(getSequentialDifferentServicesProcess());
        return workflowElementsList;
    }

    public List<WorkflowElement> createSequentialProcess() throws JAXBException, ServiceTypeNotFoundException {
        List<WorkflowElement> workflowElementsList = new ArrayList<>();
        workflowElementsList.add(getSequentialDifferentServicesProcess());
        return workflowElementsList;
    }

    public List<WorkflowElement> createParallelSameServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
        List<WorkflowElement> workflowElementsList = new ArrayList<>();
        workflowElementsList.add(getParallelSameServicesProcess());
        return workflowElementsList;
    }

    public List<WorkflowElement> createParallelDifferentServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
        List<WorkflowElement> workflowElementsList = new ArrayList<>();
        workflowElementsList.add(getParallelDifferentServicesProcess());
        return workflowElementsList;
    }

    private WorkflowElement getParallelSameServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
        WorkflowElement processObject = (WorkflowElement) unmarshaller.unmarshal(new StringReader(parallelSameServicesString));
        preProcess(processObject);
        processObject.setDeadline((long) (DateTime.now().getMillis() + getExecDuration(processObject) * factor));
        return processObject;
    }

    private WorkflowElement getParallelDifferentServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
        WorkflowElement processObject = (WorkflowElement) unmarshaller.unmarshal(new StringReader(parallelDifferentServicesString));
        preProcess(processObject);
        processObject.setDeadline((long) (DateTime.now().getMillis() + getExecDuration(processObject) * factor));
        return processObject;
    }

    private WorkflowElement getSequentialDifferentServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
        WorkflowElement processObject = (WorkflowElement) unmarshaller.unmarshal(new StringReader(sequentialDifferentServicesString));
        preProcess(processObject);
        processObject.setDeadline((long) (DateTime.now().getMillis() + getExecDuration(processObject) * factor));
        return processObject;
    }

    public void set_vmDeploying_containerScheduled(ProcessStep processStep) throws Exception {
        DateTime serviceExecutionStartTime = DateTime.now().plus(this.virtualMachineDeploymentTime).plus(this.containerDeploymentTime);
        Interval containerScheduledAvailableInterval = new Interval(serviceExecutionStartTime, serviceExecutionStartTime.plus(processStep.getServiceType().getServiceTypeResources().getMakeSpan()));
        Interval vmScheduledAvailableInterval = new Interval(containerScheduledAvailableInterval.getStart().minus(this.containerDeploymentTime), serviceExecutionStartTime.plus(processStep.getServiceType().getServiceTypeResources().getMakeSpan()));

        processStep.setStartDate(serviceExecutionStartTime);
        processStep.setScheduledStartDate(serviceExecutionStartTime);

        Container container = optimizationUtility.getContainer(processStep.getServiceType(), 1);
        container.setScheduledAvailableInterval(containerScheduledAvailableInterval);
        container.setScheduledCloudResourceUsage(containerScheduledAvailableInterval.withStart(containerScheduledAvailableInterval.getStart().minus(containerDeploymentTime)));
        container.setStartDate(containerScheduledAvailableInterval.getStart());
        container.setContainerStatus(ContainerStatus.SCHEDULED);

        VirtualMachineInstance vm = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
        vm.setScheduledAvailableInterval(vmScheduledAvailableInterval);
        vm.setScheduledCloudResourceUsage(vmScheduledAvailableInterval.withStart(vmScheduledAvailableInterval.getStart().minus(virtualMachineDeploymentTime)));
        vm.setStartTime(vmScheduledAvailableInterval.getStart());
        vm.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYING);

        container.setVirtualMachineInstance(vm);
        processStep.setContainer(container);
    }

    public void set_vmDeployed_containerDeployed(ProcessStep processStep) throws Exception {
        DateTime serviceExecutionStartTime = DateTime.now();
        Interval containerScheduledAvailableInterval = new Interval(serviceExecutionStartTime, serviceExecutionStartTime.plus(processStep.getServiceType().getServiceTypeResources().getMakeSpan()));
        Interval vmScheduledAvailableInterval = new Interval(containerScheduledAvailableInterval.getStart().minus(this.containerDeploymentTime), serviceExecutionStartTime.plus(processStep.getServiceType().getServiceTypeResources().getMakeSpan()));

        processStep.setStartDate(serviceExecutionStartTime);
        processStep.setScheduledStartDate(serviceExecutionStartTime);

        Container container = optimizationUtility.getContainer(processStep.getServiceType(), 1);
        container.setScheduledAvailableInterval(containerScheduledAvailableInterval);
        container.setScheduledCloudResourceUsage(containerScheduledAvailableInterval.withStart(containerScheduledAvailableInterval.getStart().minus(containerDeploymentTime)));
        container.setStartDate(containerScheduledAvailableInterval.getStart());
        container.setContainerStatus(ContainerStatus.DEPLOYED);

        VirtualMachineInstance vm = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
        vm.setScheduledAvailableInterval(vmScheduledAvailableInterval);
        vm.setScheduledCloudResourceUsage(vmScheduledAvailableInterval.withStart(vmScheduledAvailableInterval.getStart().minus(virtualMachineDeploymentTime)));
        vm.setStartTime(vmScheduledAvailableInterval.getStart());
        vm.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);

        container.setVirtualMachineInstance(vm);
        processStep.setContainer(container);
    }

    public void set_vmDeployed_containerDeploying(ProcessStep processStep) throws Exception {
        DateTime serviceExecutionStartTime = DateTime.now().plus(this.containerDeploymentTime);
        Interval containerScheduledAvailableInterval = new Interval(serviceExecutionStartTime, serviceExecutionStartTime.plus(processStep.getServiceType().getServiceTypeResources().getMakeSpan()));
        Interval vmScheduledAvailableInterval = new Interval(containerScheduledAvailableInterval.getStart().minus(this.containerDeploymentTime), serviceExecutionStartTime.plus(processStep.getServiceType().getServiceTypeResources().getMakeSpan()));

        processStep.setStartDate(serviceExecutionStartTime);
        processStep.setScheduledStartDate(serviceExecutionStartTime);

        Container container = optimizationUtility.getContainer(processStep.getServiceType(), 1);
        container.setScheduledAvailableInterval(containerScheduledAvailableInterval);
        container.setScheduledCloudResourceUsage(containerScheduledAvailableInterval.withStart(containerScheduledAvailableInterval.getStart().minus(containerDeploymentTime)));
        container.setStartDate(containerScheduledAvailableInterval.getStart());
        container.setContainerStatus(ContainerStatus.DEPLOYING);

        VirtualMachineInstance vm = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
        vm.setScheduledAvailableInterval(vmScheduledAvailableInterval);
        vm.setScheduledCloudResourceUsage(vmScheduledAvailableInterval.withStart(vmScheduledAvailableInterval.getStart().minus(virtualMachineDeploymentTime)));
        vm.setStartTime(vmScheduledAvailableInterval.getStart());
        vm.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);

        container.setVirtualMachineInstance(vm);
        processStep.setContainer(container);
    }

    private void preProcess(Element parent) throws ServiceTypeNotFoundException {
        if (parent == null) {
            return;
        }
        List<Element> elements = parent.getElements();
        if (elements == null || elements.size() == 0) {
            return;
        }
        for (Element element : elements) {
            element.setParent(parent);
            if (element instanceof XORConstruct) {
                XORConstruct element2 = (XORConstruct) element;
                int size = element2.getElements().size();
                Random random = new Random();
//                int i = random.nextInt(size);
                int i = 0;
                Element subelement1 = element2.getElements().get(i);
                setAllOthersToNotExecuted(element2.getElements(), subelement1);
                element.getParent().setNextXOR(subelement1);
            } else if (element instanceof LoopConstruct) {
                ((LoopConstruct) element).setNumberOfIterationsInWorstCase(3);
                Random random = new Random();
//                int i = random.nextInt(((LoopConstruct) element).getNumberOfIterationsInWorstCase()) + 1;
                int i = ((LoopConstruct) element).getNumberOfIterationsInWorstCase();
                ((LoopConstruct) element).setNumberOfIterationsToBeExecuted(i);
                // ((LoopConstruct) element).setIterations(1);
            }  //TODO: CHECK just ignore loops?
            else if (element instanceof ProcessStep) {
                ProcessStep processStep = (ProcessStep) element;
                if (processStep.getServiceType().getServiceTypeResources() == null) {
                    processStep.setServiceType(serviceRegistryReader.findServiceType(processStep.getServiceType().getName()));
                }
            }
            preProcess(element);
        }
    }

    private void setAllOthersToNotExecuted(List<Element> elements, Element ignore) {
        if (elements == null) {
            return;
        }
        for (Element element : elements) {
            if (!element.getName().equals(ignore.getName())) {
                if (element instanceof ProcessStep) {
                    ((ProcessStep) element).setHasToBeExecuted(false);
                } else {
                    setAllOthersToNotExecuted(element.getElements(), ignore);
                }
            }
        }
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

    private String sequentialDifferentServicesString =
                    "        <workflowElement>\n" +
                    "            <id>0</id>\n" +
                    "            <name>c9f42961pr1</name>\n" +
                    "            <elementsList>\n" +
                    "                <elements xsi:type=\"sequence\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                    "                    <id>0</id>\n" +
                    "                    <name>c9f42961pr1-seq</name>\n" +
                    "                    <elementsList>\n" +
                    "                        <elements xsi:type=\"processStep\">\n" +
                    "                            <id>0</id>\n" +
                    "                            <name>c9f42961pr1.1</name>\n" +
                    "                            <probability>0.0</probability>\n" +
                    "                            <deadline>0</deadline>\n" +
                    "                            <lastElement>false</lastElement>\n" +
                    "                            <workflowName>c9f42961pr1</workflowName>\n" +
                    "                            <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                            <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                            <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                            <serviceType>Service1</serviceType>\n" +
                    "                        </elements>\n" +
                    "                        <elements xsi:type=\"processStep\">\n" +
                    "                            <id>0</id>\n" +
                    "                            <name>c9f42961pr1.2</name>\n" +
                    "                            <probability>0.0</probability>\n" +
                    "                            <deadline>0</deadline>\n" +
                    "                            <lastElement>false</lastElement>\n" +
                    "                            <workflowName>c9f42961pr1</workflowName>\n" +
                    "                            <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                            <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                            <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                            <serviceType>Service2</serviceType>\n" +
                    "                        </elements>\n" +
                    "                        <elements xsi:type=\"processStep\">\n" +
                    "                            <id>0</id>\n" +
                    "                            <name>c9f42961pr1.3</name>\n" +
                    "                            <probability>0.0</probability>\n" +
                    "                            <deadline>0</deadline>\n" +
                    "                            <lastElement>true</lastElement>\n" +
                    "                            <workflowName>c9f42961pr1</workflowName>\n" +
                    "                            <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                            <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                            <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                            <serviceType>Service3</serviceType>\n" +
                    "                        </elements>\n" +
                    "                    </elementsList>\n" +
                    "                    <probability>0.0</probability>\n" +
                    "                    <deadline>0</deadline>\n" +
                    "                    <lastElement>false</lastElement>\n" +
                    "                </elements>\n" +
                    "            </elementsList>\n" +
                    "            <probability>0.0</probability>\n" +
                    "            <deadline>1545924159519</deadline>\n" +
                    "            <lastElement>false</lastElement>\n" +
                    "            <penalty>200.0</penalty>\n" +
                    "        </workflowElement>";

    private String parallelDifferentServicesString =
                    "        <workflowElement>\n" +
                    "            <id>0</id>\n" +
                    "            <name>b25ee3d2pr3</name>\n" +
                    "            <elementsList>\n" +
                    "                <elements xsi:type=\"sequence\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                    "                    <id>0</id>\n" +
                    "                    <name>b25ee3d2pr3-seq</name>\n" +
                    "                    <elementsList>\n" +
                    "                        <elements xsi:type=\"andConstruct\">\n" +
                    "                            <id>0</id>\n" +
                    "                            <name>b25ee3d2pr3-1-AND</name>\n" +
                    "                            <elementsList>\n" +
                    "                                <elements xsi:type=\"processStep\">\n" +
                    "                                    <id>0</id>\n" +
                    "                                    <name>b25ee3d2pr3.1.1</name>\n" +
                    "                                    <probability>0.0</probability>\n" +
                    "                                    <deadline>0</deadline>\n" +
                    "                                    <lastElement>true</lastElement>\n" +
                    "                                    <workflowName>b25ee3d2pr3</workflowName>\n" +
                    "                                    <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                                    <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                                    <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                                    <serviceType>Service1</serviceType>\n" +
                    "                                </elements>\n" +
                    "                                <elements xsi:type=\"processStep\">\n" +
                    "                                    <id>0</id>\n" +
                    "                                    <name>b25ee3d2pr3.1.2</name>\n" +
                    "                                    <probability>0.0</probability>\n" +
                    "                                    <deadline>0</deadline>\n" +
                    "                                    <lastElement>true</lastElement>\n" +
                    "                                    <workflowName>b25ee3d2pr3</workflowName>\n" +
                    "                                    <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                                    <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                                    <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                                    <serviceType>Service2</serviceType>\n" +
                    "                                </elements>\n" +
                    "                                <elements xsi:type=\"processStep\">\n" +
                    "                                    <id>0</id>\n" +
                    "                                    <name>b25ee3d2pr3.1.3</name>\n" +
                    "                                    <probability>0.0</probability>\n" +
                    "                                    <deadline>0</deadline>\n" +
                    "                                    <lastElement>true</lastElement>\n" +
                    "                                    <workflowName>b25ee3d2pr3</workflowName>\n" +
                    "                                    <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                                    <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                                    <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                                    <serviceType>Service3</serviceType>\n" +
                    "                                </elements>\n" +
                    "                            </elementsList>\n" +
                    "                            <probability>1.0</probability>\n" +
                    "                            <deadline>0</deadline>\n" +
                    "                            <lastElement>false</lastElement>\n" +
                    "                        </elements>\n" +
                    "                    </elementsList>\n" +
                    "                    <probability>0.0</probability>\n" +
                    "                    <deadline>0</deadline>\n" +
                    "                    <lastElement>false</lastElement>\n" +
                    "                </elements>\n" +
                    "            </elementsList>\n" +
                    "            <probability>0.0</probability>\n" +
                    "            <deadline>1545924351917</deadline>\n" +
                    "            <lastElement>false</lastElement>\n" +
                    "            <penalty>200.0</penalty>\n" +
                    "        </workflowElement>";

    private String parallelSameServicesString =
            "        <workflowElement>\n" +
                    "            <id>0</id>\n" +
                    "            <name>55b1bf13pr10</name>\n" +
                    "            <elementsList>\n" +
                    "                <elements xsi:type=\"sequence\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                    "                    <id>0</id>\n" +
                    "                    <name>55b1bf13pr10-seq</name>\n" +
                    "                    <elementsList>\n" +
                    "                        <elements xsi:type=\"andConstruct\">\n" +
                    "                            <id>0</id>\n" +
                    "                            <name>55b1bf13pr10-1-AND</name>\n" +
                    "                            <elementsList>\n" +
                    "                                <elements xsi:type=\"processStep\">\n" +
                    "                                    <id>0</id>\n" +
                    "                                    <name>55b1bf13pr10.1.1</name>\n" +
                    "                                    <probability>0.0</probability>\n" +
                    "                                    <deadline>0</deadline>\n" +
                    "                                    <lastElement>true</lastElement>\n" +
                    "                                    <workflowName>55b1bf13pr10</workflowName>\n" +
                    "                                    <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                                    <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                                    <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                                    <serviceType>Service1</serviceType>\n" +
                    "                                </elements>\n" +
                    "                                <elements xsi:type=\"processStep\">\n" +
                    "                                    <id>0</id>\n" +
                    "                                    <name>55b1bf13pr10.1.2</name>\n" +
                    "                                    <probability>0.0</probability>\n" +
                    "                                    <deadline>0</deadline>\n" +
                    "                                    <lastElement>true</lastElement>\n" +
                    "                                    <workflowName>55b1bf13pr10</workflowName>\n" +
                    "                                    <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                                    <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                                    <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                                    <serviceType>Service1</serviceType>\n" +
                    "                                </elements>\n" +
                    "                                <elements xsi:type=\"processStep\">\n" +
                    "                                    <id>0</id>\n" +
                    "                                    <name>55b1bf13pr10.1.3</name>\n" +
                    "                                    <probability>0.0</probability>\n" +
                    "                                    <deadline>0</deadline>\n" +
                    "                                    <lastElement>true</lastElement>\n" +
                    "                                    <workflowName>55b1bf13pr10</workflowName>\n" +
                    "                                    <numberOfExecutions>0</numberOfExecutions>\n" +
                    "                                    <hasToBeExecuted>true</hasToBeExecuted>\n" +
                    "                                    <hasToDeployContainer>false</hasToDeployContainer>\n" +
                    "                                    <serviceType>Service1</serviceType>\n" +
                    "                                </elements>\n" +
                    "                            </elementsList>\n" +
                    "                            <probability>1.0</probability>\n" +
                    "                            <deadline>0</deadline>\n" +
                    "                            <lastElement>false</lastElement>\n" +
                    "                        </elements>\n" +
                    "                    </elementsList>\n" +
                    "                    <probability>0.0</probability>\n" +
                    "                    <deadline>0</deadline>\n" +
                    "                    <lastElement>false</lastElement>\n" +
                    "                </elements>\n" +
                    "            </elementsList>\n" +
                    "            <probability>0.0</probability>\n" +
                    "            <deadline>1545768006578</deadline>\n" +
                    "            <lastElement>false</lastElement>\n" +
                    "            <penalty>200.0</penalty>\n" +
                    "        </workflowElement>";

}
