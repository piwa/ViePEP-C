package at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: sauron
 * Date: 05.02.14
 * Time: 14:39
 * To change this template use File | Settings | File Templates.
 */
@Entity
@Table(name = "virtual_machine")
@Getter
@Setter
public class VirtualMachine implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String googleName;
    private String instanceId;
    private String location;

    private String ipAddress;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private Map<UUID, Interval> scheduledDeployTime;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startDate;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime terminationDate;

    private String resourcepool;

    private VirtualMachineStatus virtualMachineStatus;

    @ManyToOne(cascade = CascadeType.ALL)
    private VMType vmType;

    @ElementCollection
    private List<String> usedPorts = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy="virtualMachine")
    private Set<Container> deployedContainers = new HashSet<>();

    @OneToMany
    private Set<ContainerImage> availableContainerImages = new HashSet<>();


    public VirtualMachine(String name, VMType vmType) {
        this();
        this.name = name;
        this.location = vmType.getLocation();
        this.vmType = vmType;
    }

    public VirtualMachine() {
        this.instanceId = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
        this.virtualMachineStatus = VirtualMachineStatus.UNUSED;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        VirtualMachine other = (VirtualMachine) obj;

        return (this.id != null) && (other.id != null) && (this.id.intValue() == other.id.intValue());

    }

    @Override
    public String toString() {
//        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        String startString = startDate == null ? "NOT_YET" : startDate.toString();
        String toBeTerminatedAtString = terminationDate == null ? "NOT_YET" : terminationDate.toString();
        return "VirtualMachine{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", startedAt=" + startString +
                ", terminateAt=" + toBeTerminatedAtString +
                ", location=" + location +
                ", googleName=" + getGoogleName() +
                ", ip adress=" + ipAddress +
                '}';
    }

    public String getURI() {
        return "http://" + this.ipAddress;
    }

    public void terminate() {
        this.setVirtualMachineStatus(VirtualMachineStatus.TERMINATED);
        this.setTerminationDate(DateTime.now());
        this.setGoogleName(null);
        this.setIpAddress(null);
        this.availableContainerImages = new HashSet<>();
    }

    public String getGoogleName() {
        if(Strings.isNullOrEmpty(googleName)) {
            googleName = "eval-" + this.getName().replace('_', '-') + "-" + UUID.randomUUID().toString().substring(0,6);
        }
        return googleName;
    }

    public void undeployContainer(Container container) {
        this.deployedContainers.remove(container);
    }

}
