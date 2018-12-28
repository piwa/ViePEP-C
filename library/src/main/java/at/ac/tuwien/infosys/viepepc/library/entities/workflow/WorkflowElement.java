package at.ac.tuwien.infosys.viepepc.library.entities.workflow;

import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@XmlRootElement(name = "workflowElement")
@Entity(name = "WorkflowElement")
//@PrimaryKeyJoinColumn(name="identifier")
@Table(name="WorkflowElement")
@DiscriminatorValue(value = "workflow")
@Getter
@Setter
public class WorkflowElement extends Element {
	
    private DateTime arrivedAt;
    private double penalty = 200;

    public WorkflowElement(String name, long deadline, double penalty) {
        this.name = name;
        this.elements = new ArrayList<>();
        this.deadline = deadline;
        this.penalty=penalty;
    }

    public WorkflowElement(String name, long deadline) {
        this.name = name;
        this.elements = new ArrayList<>();
        this.deadline = deadline;
    }

    public WorkflowElement() {
        elements = new ArrayList<>();
    }

    
    @Override
    public int getNumberOfExecutions() {
    	return elements.get(elements.size()-1).getNumberOfExecutions();
    }
    
    public long calculateQoS() {
        long executionTime = elements.stream().filter(element -> element.getFinishedAt() == null).mapToLong(Element::calculateQoS).sum();
        return executionTime;
    }

    @Override
    public ProcessStep getLastExecutedElement() {
        List<Element> allChildren = elements.stream().map(Element::getLastExecutedElement).collect(Collectors.toList());
        ProcessStep lastExecutedMaxElement = null;
        for (Element allChild : allChildren) {
            ProcessStep current = (ProcessStep) allChild;
            if (lastExecutedMaxElement == null && current != null) {
                if (current.hasBeenExecuted()) {
                    lastExecutedMaxElement = current;
                }
            } else if (current != null) {
                if (current.getFinishedAt().isAfter(lastExecutedMaxElement.getFinishedAt())) {
                    lastExecutedMaxElement = current;
                }
            }
        }
        return lastExecutedMaxElement;
    }

    @Override
    public String toString() {
        return "Workflow{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", arrivedAt='" + arrivedAt + '\'' +
                ", elements=" + elements +
                ", deadline=" + (new DateTime(deadline)).toString() +
                '}';
    }

	public double getPenaltyPerViolation() {
		return penalty;
	}

}
