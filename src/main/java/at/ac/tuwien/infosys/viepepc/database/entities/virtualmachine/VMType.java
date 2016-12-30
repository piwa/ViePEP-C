package at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;


@Entity
@Table(name = "VMType")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VMType implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    protected Long tableId;

    private Long identifier;
    private String name;
    private double costs;
    private int cores;
    private Integer memorySize;
    private String flavor;
    private double ramPoints;
    private String location;
    private long leasingDuration;
    private long deployTime;



    public double getCpuPoints() {
        int i = cores * 100;
        return i - (i/10);       //10% are used for the OS
    }

    public double getRamPoints() {
        return memorySize;
    }


}
