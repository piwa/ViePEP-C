package at.ac.tuwien.infosys.viepepc.library.entities.workflow;

import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.io.Serializable;
import java.util.List;


/**
 * this class represents a complex element of the model complex elements are:
 * <ul>
 * <li>ANDConstruct
 * <li>Sequence
 * <li>XORConstruct
 * <li>Loop
 * </ul>
 */
@XmlSeeAlso({WorkflowElement.class, XORConstruct.class, ANDConstruct.class, LoopConstruct.class, ProcessStep.class,
        Sequence.class})
@Entity
@Table(name = "element")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "TYPE")
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
@Setter
public abstract class Element implements Serializable {

    /**
     * primary identifier in db
     */
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    protected long id;

    // name of the element
    @XmlElement(name = "name")
    protected String name;

    @XmlTransient
    @ManyToOne
    private Element parent;

    @XmlElementWrapper(name = "elementsList")
    @XmlElement(name = "elements")
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "parent", orphanRemoval = true)
    @OrderColumn
    protected List<Element> elements;


    // execution probability of this element
    // in a loop-pattern it's the number of executions      TODO is this true? If yes can it be an own property?
    protected double probability;

    @XmlElement(name = "deadline")
    protected long deadline;

    @XmlTransient
    @ManyToOne
    private Element nextXOR;

    protected DateTime finishedAt = null;

    @XmlElement(name = "lastElement")
    private boolean lastElement;

    /**
     * adds an element to the list of subelements
     *
     * @param elem Element
     */
    public void addElement(Element elem) {
        elements.add(elem);
        elem.setParent(this);
    }

    public abstract ProcessStep getLastExecutedElement();

    public abstract long calculateQoS();
    
    public abstract int getNumberOfExecutions();


    @Override
    public String toString() {
        return "Element{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", elements=" + elements +
                ", deadline=" + deadline +
                '}';
    }

    public String toStringWithoutElements() {
        return "Element{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", deadline=" + deadline +
                '}';
    }


    public DateTime getDeadlineDateTime() {
        return new DateTime(deadline);
    }


}