package at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.io.Serializable;


@Entity
@Table(name = "vm_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class VMType implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    protected Long tableId;

    @XmlElement
    private Long identifier;
    @XmlElement
    private String name;
    @XmlElement
    private double costs;
    @XmlElement
    private int cores;
    @XmlElement
    private String flavorName;
    @XmlElement(name = "ram")
    private double ramPoints;
    @XmlElement
    private String location;

    private double storage;

    public double getCpuPoints() {
        int i = cores * 100;
        return i - (i / 10);       //10% are used for the OS
    }

    @Override
    public String toString() {
        return "VMType{" +
//                "tableId=" + tableId +
//                ", identifier=" + identifier +
                "name='" + name + '\'' +
//                ", costs=" + costs +
//                ", cores=" + cores +
//                ", flavorName='" + flavorName + '\'' +
//                ", ramPoints=" + ramPoints +
//                ", location='" + location + '\'' +
//                ", storage=" + storage +
                '}';
    }
}
