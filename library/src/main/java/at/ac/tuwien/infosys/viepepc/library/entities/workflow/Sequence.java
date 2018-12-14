package at.ac.tuwien.infosys.viepepc.library.entities.workflow;


import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Waldemar Ankudin modified by Turgay Sahin and Mathieu Muench, Gerta Sheganaku
 */

@XmlRootElement(name = "Sequence")
@Entity(name = "sequence")
@DiscriminatorValue(value = "sequence")
public class Sequence extends Element {


    /**
     * sequence element which contains the name and the elements in a sequence
     *
     * @param n String
     */
    public Sequence(String n) {
        name = n;
        elements = new ArrayList<Element>();
    }

    public Sequence() {
        elements = new ArrayList<>();
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
    public int getNumberOfExecutions() {
    	return elements.get(elements.size()-1).getNumberOfExecutions();
    }

    @Override
    public ProcessStep getLastExecutedElement() {
        //return elements.get(elements.size() - 1).getLastExecutedElement();	//TODO: like honestly?
        
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
            	if(current.getFinishedAt() != null) {
            		if (current.getFinishedAt().isAfter(lastExecutedMaxElement.getFinishedAt())) {
            			lastExecutedMaxElement = current;
            		}
            	}
            }
        }
        return lastExecutedMaxElement;
    }

    @Override
    public String toString() {
        return "Sequence{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", elements=" + elements +
                ", deadline=" + deadline +
                '}';
    }
}