package at.ac.tuwien.infosys.viepepc.library.entities.container;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "container_configuration")
@XmlRootElement(name = "ContainerConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class ContainerConfiguration implements Cloneable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @XmlElement
    private String name;
    @XmlElement
    private double cores; //amount of needed VCores
    @XmlElement
    private double ram; //amount of needed memory in mb
    @XmlElement
    private double disc; //amount of needed disc space in mb


    public double getCPUPoints() {
        return cores * 100;
    }

    @Override
    public ContainerConfiguration clone() throws CloneNotSupportedException {
        return (ContainerConfiguration)super.clone();
    }

    @Override
    public String toString() {
        return "ContainerConfiguration{" +
//                "id=" + id +
//                ", name='" + name + '\'' +
                "cores=" + cores +
                ", ram=" + ram +
//                ", disc=" + disc +
                '}';
    }
}
