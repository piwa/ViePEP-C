package at.ac.tuwien.infosys.viepepc.database.entities.workflow;


import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.services.adapter.ServiceTypeAdapter;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
import java.util.Date;
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

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startDate;
    private String workflowName;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime scheduledStartedAt;
    private int numberOfExecutions;
    private boolean hasToBeExecuted = true;
    private boolean hasToDeployContainer = false;

    @ManyToOne
    @JoinColumn(name="serviceTypeId")
    @XmlJavaTypeAdapter(ServiceTypeAdapter.class)
    private ServiceType serviceType;

    @ManyToOne
    @JoinColumn(name="scheduleAtVmId")
    private VirtualMachine scheduledAtVM;
    
    @ManyToOne
    @JoinColumn(name="scheduleAtContainerId")
    private Container scheduledAtContainer;

    @XmlElementWrapper(name="restrictedVMs")
    @XmlElement(name="restricted" )
    @ElementCollection(fetch=FetchType.EAGER)
    private List<Integer> restrictedVMs = new ArrayList<>();

    @XmlTransient
    @Override
    public List<Element> getElements() {
        return super.getElements();
    }

    public ProcessStep() {
        internId = UUID.randomUUID();
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

    public void setScheduledForExecution(boolean isScheduled, DateTime tau_t, VirtualMachine vm) {
        this.scheduledStartedAt = tau_t;
        this.scheduledAtVM = vm;
    }

    public void setScheduledForExecution(boolean isScheduled, DateTime tau_t, Container container) {
        this.scheduledStartedAt = tau_t;
        this.scheduledAtContainer = container;
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
        processStep.setScheduledStartedAt(new DateTime(this.scheduledStartedAt));
        processStep.setNumberOfExecutions(this.numberOfExecutions);
        processStep.setHasToBeExecuted(this.hasToBeExecuted);
        processStep.setLastElement(this.isLastElement());
        processStep.setHasToDeployContainer(this.hasToDeployContainer);

        processStep.setServiceType(serviceType);

        //VirtualMachine cloneVM = new VirtualMachine();
        //processStep.setScheduledAtVM(this.scheduledAtVM);

        if(scheduledAtContainer != null) {
            processStep.setScheduledAtContainer(this.scheduledAtContainer.clone(serviceType));
        }
        else {
            processStep.setScheduledAtContainer(null);
        }

        processStep.setRestrictedVMs(this.restrictedVMs);
        processStep.setInternId(this.internId);

        return processStep;
    }




    @Override
    public String toString() {
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        String startDateformat = startDate != null ? startDate.toString() : null;
        String scheduledStart = scheduledStartedAt != null ? scheduledStartedAt.toString() : null;
        String finishedAtformat = finishedAt != null ? finishedAt.toString() : null;
        String vmName = scheduledAtVM != null ? scheduledAtVM.getInstanceId() : null;
        String containerName = scheduledAtContainer != null ? scheduledAtContainer.getName() : null;

        if(scheduledAtContainer == null && scheduledAtVM == null) {
            return "ProcessStep{" +
                    "name='" + name + '\'' +
                    ", serviceType=" + serviceType.getName() +
                    ", scheduledStart=" + scheduledStart +
                    ", startDate=" + startDateformat +
                    ", finishedAt=" + finishedAtformat +
                    ", lastElement=" + isLastElement() +
                    '}';
        }
        else if(scheduledAtContainer.isBareMetal()) {
            return "ProcessStep{" +
                    "name='" + name + '\'' +
                    ", serviceType=" + serviceType.getName() +
                    ", scheduledStart=" + scheduledStart +
                    ", startDate=" + startDateformat +
                    ", finishedAt=" + finishedAtformat +
                    ", scheduledAtContainer=" + containerName +
                    ", lastElement=" + isLastElement() +
                    '}';
        }
        else {
            return "ProcessStep{" +
                    "name='" + name + '\'' +
                    ", serviceType=" + serviceType.getName() +
                    ", startDate=" + startDateformat +
                    ", finishedAt=" + finishedAtformat +
                    ", scheduledAtVM=" + vmName +
                    ", scheduledAtContainer=" + containerName +
                    ", lastElement=" + isLastElement() +
                    '}';
        }
    }

    public void setHasBeenExecuted(boolean hasBeenExecuted) {
        if (hasBeenExecuted) {
            finishedAt = new DateTime();
        } else finishedAt = null;
    }

    public List<Integer> getRestrictedVMs() {
        List<Integer> objects = new ArrayList<>();
        objects.addAll(restrictedVMs);
        return objects;
    }

    public void reset() {
        this.setFinishedAt(null);
        this.setStartDate(null);
        this.setScheduledAtVM(null);
        this.setScheduledAtContainer(null);
        this.setHasBeenExecuted(false);
        this.setScheduledStartedAt(null);
        this.setHasToDeployContainer(false);
    }

    public boolean isScheduled() {
        return this.getScheduledAtContainer() != null && this.getScheduledAtContainer().getVirtualMachine() != null;
    }
}
