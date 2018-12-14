package at.ac.tuwien.infosys.viepepc.library.entities.workflow;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;


/**
 * this class represents a LoopConstruct - a complex element in the model
 *
 * @author Waldemar Ankudin modified by Turgay Sahin, Gerta Sheganaku
 */
@XmlRootElement(name = "LoopConstruct")
@Entity(name = "loop_construct")
//@PrimaryKeyJoinColumn(name="identifier")
@Table(name="loop_construct_element")
@DiscriminatorValue(value = "loop")
@Getter
@Setter
public class LoopConstruct extends Element {


    private int numberOfIterationsInWorstCase = 3;
    private int numberOfIterationsToBeExecuted = 1;
//    private int iterations = 0;

    public LoopConstruct(String n) {
        name = n;
        elements = new ArrayList<Element>();
    }

    public LoopConstruct() {
    }
    
    public int getNumberOfExecutions(){
    	return elements.get(elements.size()-1).getNumberOfExecutions();
    }

//    public long calculateQoS() {
//        return elements.get(0).calculateQoS() * numberOfIterationsInWorstCase;
//    }
    
    public long calculateQoS() {
        long executionTime = 0;
        for (Element element : elements) {
        	if(element.getFinishedAt()==null){
        		executionTime += element.calculateQoS();
        	}
        }
        return (executionTime * (numberOfIterationsInWorstCase-getNumberOfExecutions()));
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
                if (current.getFinishedAt().isAfter(lastExecutedMaxElement.getFinishedAt())) {
                    lastExecutedMaxElement = current;
                }
            }
        }
        return lastExecutedMaxElement;
    }

    @Override
    public String toString() {
        return "Loop{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", elements=" + elements +
                ", deadline=" + deadline +
                '}';
    }

}
