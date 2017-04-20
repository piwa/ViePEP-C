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


/**
 * Represents the smallest element of the workflow model.
 *
 * @author Waldemar Ankudin modified by Turgay Sahin and Mathieu Muench, Gerta Sheganaku
 */
@XmlRootElement(name = "ProcessStep")
@Entity(name = "ProcessStep")
@PrimaryKeyJoinColumn(name="identifier")
@Table(name="ProcessStepElement")
@DiscriminatorValue(value = "process_step")
@Getter
@Setter
@NoArgsConstructor
public class ProcessStep extends Element {

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startDate;
    private String workflowName;
    private boolean isScheduled;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime scheduledStartedAt;
    private int numberOfExecutions;
    private boolean hasToBeExecuted = true;

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

    public long calculateQoS() {
    	return getRemainingExecutionTime(new Date());
    }

    public boolean hasBeenExecuted() {
        return this.finishedAt != null;
    }

    public long getExecutionTime() {
        return serviceType.getServiceTypeResources().getMakeSpan();
    }


    public long getRemainingExecutionTime(Date date) {
        long time = date.getTime();
        if (startDate != null) {
            time = startDate.getMillis();
        }
        long difference = date.getTime() - time;
        long remaining = serviceType.getServiceTypeResources().getMakeSpan() - difference;
        return remaining > 0 ? remaining : serviceType.getServiceTypeResources().getMakeSpan() ;
    }

    public void setScheduledForExecution(boolean isScheduled, DateTime tau_t, VirtualMachine vm) {
        this.isScheduled = isScheduled;
        this.scheduledStartedAt = tau_t;
        this.scheduledAtVM = vm;
    }

    public void setScheduledForExecution(boolean isScheduled, DateTime tau_t, Container container) {
        this.isScheduled = isScheduled;
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
    public String toString() {
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        String startDateformat = startDate != null ? dtfOut.print(startDate) : null;
        String finishedAtformat = finishedAt != null ? dtfOut.print(finishedAt) : null;
        String vmName = scheduledAtVM != null ? scheduledAtVM.getInstanceId() : null;
        String dockerName = scheduledAtContainer != null ? scheduledAtContainer.getName() : null;
        return "ProcessStep{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", serviceType=" + serviceType +
                ", startDate=" + startDateformat +
                ", finishedAt=" + finishedAtformat +
                ", scheduledAtVM=" + vmName +
                ", scheduledAtContainer=" + dockerName +
                ", lastElement=" + isLastElement() +
                '}';
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
        this.setScheduled(false);
        this.setScheduledStartedAt(null);
    }
}
