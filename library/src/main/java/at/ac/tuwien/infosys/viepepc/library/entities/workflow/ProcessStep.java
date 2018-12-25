package at.ac.tuwien.infosys.viepepc.library.entities.workflow;


import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.services.adapter.ServiceTypeAdapter;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Represents the smallest element of the workflow model.
 *
 * @author Waldemar Ankudin modified by Turgay Sahin and Mathieu Muench, Gerta Sheganaku
 */
@XmlRootElement(name = "ProcessStep")
@Entity(name = "process_step")
//@PrimaryKeyJoinColumn(name="identifier")
@Table(name="process_step_element")
@DiscriminatorValue(value = "process_step")
@Getter
@Setter
public class ProcessStep extends Element implements Cloneable {


    private UUID internId;

    private String workflowName;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime scheduledStartDate;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startDate;

    private int numberOfExecutions;
    private boolean hasToBeExecuted = true;
    private boolean hasToDeployContainer = false;

    @ManyToOne
    @JoinColumn(name="serviceTypeId")
    @XmlJavaTypeAdapter(ServiceTypeAdapter.class)
    private ServiceType serviceType;

    @ManyToOne
    @JoinColumn(name="containerId")
    private Container container;

    @XmlTransient
    @Override
    public List<Element> getElements() {
        return super.getElements();
    }

    public ProcessStep() {
        internId = UUID.randomUUID();
    }

    public ProcessStep(String name, ServiceType serviceType, String workflowName) {
        this.name = name;
        this.serviceType = serviceType;
        this.workflowName = workflowName;
    }

    public long calculateQoS() {
    	return getRemainingExecutionTime(DateTime.now());
    }

    public boolean hasBeenExecuted() {
        return this.finishedAt != null;
    }

    public long getExecutionTime() {
        return serviceType.getServiceTypeResources().getMakeSpan();
    }


    public long getRemainingExecutionTime(DateTime date) {
        long time = date.getMillis();
        if (startDate != null) {
            time = startDate.getMillis();
        }
        long difference = date.getMillis() - time;
        long remaining = serviceType.getServiceTypeResources().getMakeSpan() - difference;
        return remaining > 0 ? remaining : serviceType.getServiceTypeResources().getMakeSpan() ;
    }


    public void setScheduledForExecution(DateTime scheduledStartDate, Container container) {
        this.scheduledStartDate = scheduledStartDate;
        this.container = container;
    }

    @Override
    public ProcessStep getLastExecutedElement() {
        return this;
    }

    public void setStartDate(DateTime date){
    	this.startDate = date;
    	if(date != null) {
    		numberOfExecutions++;
    	}
    }

    @Override
    public ProcessStep clone() throws CloneNotSupportedException {

        ServiceType serviceType = this.serviceType.clone();
        return this.clone(serviceType);
    }


    public ProcessStep clone(ServiceType serviceType) throws CloneNotSupportedException {
        ProcessStep processStep = new ProcessStep();

        processStep.setName(this.name);
        processStep.setStartDate(new DateTime(this.startDate));
        processStep.setFinishedAt(new DateTime(this.finishedAt));
        processStep.setWorkflowName(this.workflowName);
        processStep.setScheduledStartDate(new DateTime(this.scheduledStartDate));
        processStep.setNumberOfExecutions(this.numberOfExecutions);
        processStep.setHasToBeExecuted(this.hasToBeExecuted);
        processStep.setLastElement(this.isLastElement());
        processStep.setHasToDeployContainer(this.hasToDeployContainer);

        processStep.setServiceType(serviceType);

        //VirtualMachine cloneVM = new VirtualMachine();
        //processStep.setScheduledAtVM(this.scheduledAtVM);

        if(container != null) {
            processStep.setContainer(this.container.clone(serviceType));
        }
        else {
            processStep.setContainer(null);
        }
        processStep.setInternId(this.internId);

        return processStep;
    }




    @Override
    public String toString() {
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        String startDateformat = startDate != null ? startDate.toString() : null;
        String scheduledStart = scheduledStartDate != null ? scheduledStartDate.toString() : null;
        String finishedAtformat = finishedAt != null ? finishedAt.toString() : null;
        String containerName = container != null ? container.getName() : null;

        if(container == null) {
            return "ProcessStep{" +
                    "name='" + name + '\'' +
                    ", serviceType=" + serviceType.getName() +
                    ", scheduledStart=" + scheduledStart +
                    ", startDate=" + startDateformat +
                    ", finishedAt=" + finishedAtformat +
                    ", lastElement=" + isLastElement() +
                    '}';
        }
        else if(container.isBareMetal()) {
            return "ProcessStep{" +
                    "name='" + name + '\'' +
                    ", serviceType=" + serviceType.getName() +
                    ", scheduledStart=" + scheduledStart +
                    ", startDate=" + startDateformat +
                    ", finishedAt=" + finishedAtformat +
                    ", container=" + containerName +
                    ", lastElement=" + isLastElement() +
                    '}';
        }
        else {
            return "ProcessStep{" +
                    "name='" + name + '\'' +
                    ", serviceType=" + serviceType.getName() +
                    ", startDate=" + startDateformat +
                    ", finishedAt=" + finishedAtformat +
                    ", container=" + containerName +
                    ", lastElement=" + isLastElement() +
                    '}';
        }
    }

    public void setHasBeenExecuted(boolean hasBeenExecuted) {
        if (hasBeenExecuted) {
            finishedAt = new DateTime();
        } else finishedAt = null;
    }

    public void reset() {
        this.setFinishedAt(null);
        this.setStartDate(null);
        this.setContainer(null);
        this.setHasBeenExecuted(false);
        this.setScheduledStartDate(null);
        this.setHasToDeployContainer(false);
    }

    public boolean isScheduled() {
        return this.getContainer() != null && this.getContainer().getVirtualMachine() != null;
    }
}
