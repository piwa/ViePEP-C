package at.ac.tuwien.infosys.viepepc.database.entities.workflow;

import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@XmlRootElement(name = "WorkflowElement")
@Entity(name = "WorkflowElement")
//@PrimaryKeyJoinColumn(name="identifier")
@Table(name="WorkflowElement")
@DiscriminatorValue(value = "workflow")
@Getter
@Setter
public class WorkflowElement extends Element {
	
    private DateTime arrivedAt;
    private double penalty = 200;

    public WorkflowElement(String name, long date, double penalty) {
        this.name = name;
        this.elements = new ArrayList<>();
        this.deadline = date;
        this.penalty=penalty;
    }

    public WorkflowElement() {
        elements = new ArrayList<>();
    }

    
    @Override
    public int getNumberOfExecutions() {
    	return elements.get(elements.size()-1).getNumberOfExecutions();
    }
    
    public long calculateQoS() {
        long executionTime = 0;
        for (Element element : elements) {
        	if(element.getFinishedAt() == null){
                executionTime += element.calculateQoS();
        	}
        }
        return executionTime;
    }

    @Override
    public ProcessStep getLastExecutedElement() {
        List<Element> allChildren = new ArrayList<>();
        for (Element element : elements) {
            allChildren.add(element.getLastExecutedElement());
        }
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
                ", deadline=" + deadline +
                '}';
    }

	public double getPenaltyPerViolation() {
		return penalty;
	}

}
