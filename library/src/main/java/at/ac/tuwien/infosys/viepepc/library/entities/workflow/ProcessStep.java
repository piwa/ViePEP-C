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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.List;
import java.util.Objects;
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


    private final UUID internId;

    private String workflowName;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime scheduledStartDate;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startDate;

    private int numberOfExecutions;
    private boolean hasToBeExecuted = true;
    private boolean hasToDeployContainer = false;
    private ProcessStepStatus processStepStatus;

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

    public ProcessStep(UUID internId) {
        this.internId = internId;
        this.processStepStatus = ProcessStepStatus.UNUSED;
    }

    public ProcessStep() {
        this(UUID.randomUUID());
    }

    public ProcessStep(String name, ServiceType serviceType, String workflowName) {
        this(UUID.randomUUID());
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
        ProcessStep processStep = new ProcessStep(this.internId);

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

        //VirtualMachineInstance cloneVM = new VirtualMachineInstance();
        //processStep.setScheduledAtVM(this.scheduledAtVM);

        if(container != null) {
            processStep.setContainer(this.container.clone(serviceType));
        }
        else {
            processStep.setContainer(null);
        }

        return processStep;
    }


    @Override
    public String toString() {

        String containerId = "";
        if(container != null) containerId = container.getInternId().toString().substring(0,8);

        return "ProcessStep{" +
                "internId=" + internId.toString().substring(0,8) +
                ", workflowName=" + workflowName +
                ", name=" + name +
                ", scheduledStartDate=" + scheduledStartDate +
                ", startDate=" + startDate +
                ", finishedAt=" + finishedAt +
//                ", numberOfExecutions=" + numberOfExecutions +
//                ", hasToBeExecuted=" + hasToBeExecuted +
//                ", hasToDeployContainer=" + hasToDeployContainer +
                ", processStepStatus=" + processStepStatus +
                ", serviceType=" + serviceType +
                ", container=" + containerId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessStep that = (ProcessStep) o;
        return internId.toString().equals(that.internId.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(internId.toString());
    }

    //    @Override
//    public String toString() {
//        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
//
//        String startDateformat = startDate != null ? startDate.toString() : null;
//        String scheduledStart = scheduledStartDate != null ? scheduledStartDate.toString() : null;
//        String finishedAtformat = finishedAt != null ? finishedAt.toString() : null;
//        String containerName = container != null ? container.getName() : null;
//
//        if(container == null) {
//            return "ProcessStep{" +
//                    "name='" + name + '\'' +
//                    ", serviceType=" + serviceType.getName() +
//                    ", scheduledStart=" + scheduledStart +
//                    ", startTime=" + startDateformat +
//                    ", finishedAt=" + finishedAtformat +
//                    ", lastElement=" + isLastElement() +
//                    '}';
//        }
//        else if(container.isBareMetal()) {
//            return "ProcessStep{" +
//                    "name='" + name + '\'' +
//                    ", serviceType=" + serviceType.getName() +
//                    ", scheduledStart=" + scheduledStart +
//                    ", startTime=" + startDateformat +
//                    ", finishedAt=" + finishedAtformat +
//                    ", container=" + containerName +
//                    ", lastElement=" + isLastElement() +
//                    '}';
//        }
//        else {
//            return "ProcessStep{" +
//                    "name='" + name + '\'' +
//                    ", serviceType=" + serviceType.getName() +
//                    ", startTime=" + startDateformat +
//                    ", finishedAt=" + finishedAtformat +
//                    ", container=" + containerName +
//                    ", lastElement=" + isLastElement() +
//                    '}';
//        }
//    }

    public void setHasBeenExecuted(boolean hasBeenExecuted) {
        if (hasBeenExecuted) {
            finishedAt = DateTime.now();
        } else {
            finishedAt = null;
        }
    }

    public void reset() {
        this.setFinishedAt(null);
        this.setStartDate(null);
        this.setContainer(null);
        this.setHasBeenExecuted(false);
        this.setScheduledStartDate(null);
        this.setHasToDeployContainer(false);
        this.setProcessStepStatus(ProcessStepStatus.UNUSED);
    }

    public boolean isScheduled() {
        return this.getContainer() != null && this.getContainer().getVirtualMachineInstance() != null;
    }
}
