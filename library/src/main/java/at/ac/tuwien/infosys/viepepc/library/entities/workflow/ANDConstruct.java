package at.ac.tuwien.infosys.viepepc.library.entities.workflow;

import org.joda.time.DateTime;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;


/**
 * this class represents an ANDConstruct - a complex element in the model
 *
 * @author Waldemar Ankudin modified by Turgay Sahin, Mathieu Muench
 */
@XmlRootElement(name = "ANDConstruct")
@Entity(name = "and_construct")
//@PrimaryKeyJoinColumn(name="identifier")
@Table(name="and_construct_element")
@DiscriminatorValue(value = "and")
public class ANDConstruct extends Element {

    /**
     * creates a new object with the name n, the probability 1.0 and a new
     * elements list
     *
     * @param n String
     */
    public ANDConstruct(String n) {
        name = n;
        elements = new ArrayList<Element>();
        probability = 1.0;
    }

    public ANDConstruct() {
    }

    public long calculateQoS() {
        long executionTime = 0;
        for (Element element : elements) {
        	if(element.getFinishedAt() == null){
        		executionTime = Math.max(element.calculateQoS(), executionTime);
        	}
        }
        return executionTime;
    
    }
    
    @Override
	public int getNumberOfExecutions() {
    	int executed = Integer.MAX_VALUE;
		for(Element element : elements) {
			executed = Math.min(executed, element.getNumberOfExecutions());
		}
		return executed;
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
            if (lastExecutedMaxElement == null) {
                if (current.hasBeenExecuted()) {
                    lastExecutedMaxElement = current;
                }
            } else {
                DateTime currentElementFinishedAt = current.getFinishedAt();
                DateTime lastExecutedMaxElementFinished = lastExecutedMaxElement.getFinishedAt();
                if (currentElementFinishedAt != null && lastExecutedMaxElementFinished != null && currentElementFinishedAt.isAfter(lastExecutedMaxElementFinished)) {
                    lastExecutedMaxElement = current;
                }
            }
        }

        return lastExecutedMaxElement;
    }

    @Override
    public String toString() {
        return "AND{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", elements=" + elements +
                ", deadline=" + deadline +
                '}';
    }

	

}