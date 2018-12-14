package at.ac.tuwien.infosys.viepepc.library.entities.workflow;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Random;


/**
 * @author Waldemar Ankudin modified by Turgay Sahin, Mathieu Muench
 */
@XmlRootElement(name = "XORConstruct")
@Entity(name = "xor_construct")
//@PrimaryKeyJoinColumn(name="identifier")
@Table(name="xor_construct_element")
public class XORConstruct extends Element {

    /**
     * creatres a new object with the name n and a new elements list
     *
     * @param n String
     */
    public XORConstruct(String n) {
        name = n;
        elements = new ArrayList<Element>();
    }

    public XORConstruct() {
        elements = new ArrayList<Element>();
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
       	int executed = 0;
   		for(Element element : elements) {
   	   		executed += element.getNumberOfExecutions();
   		}
   		return executed;
   	}


    @Override
    public ProcessStep getLastExecutedElement() {
        Element nextXOR = getParent().getNextXOR();
        if (nextXOR != null) {
            return (ProcessStep) nextXOR.getLastExecutedElement();
        } else {
            Random random = new Random();
            int i = random.nextInt(elements.size());
            Element subelement1 = elements.get(i);
            return subelement1.getLastExecutedElement();
        }
    }

    @Override
    public String toString() {
        return "XOR{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", elements=" + elements +
                ", deadline=" + deadline +
                '}';
    }
}

