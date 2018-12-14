package at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;

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

//    @Autowired
//    @Transient
//    private CacheVirtualMachineService cacheVirtualMachineService;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name="serviceTypeId")
    private ServiceType serviceType;

    private String name;
    private String googleName;
    private String instanceId;
    private String location;
    private boolean leased = false;         // TODO check when it is set
    private String ipAddress;
    private long startupTime;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startedAt;
    private boolean started;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime toBeTerminatedAt;
    private String resourcepool;
    private boolean terminating = false;

    @ManyToOne(cascade = CascadeType.ALL)
    private VMType vmType;

    @ElementCollection
    private List<String> usedPorts = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy="virtualMachine")
    private Set<Container> deployedContainers = new HashSet<>();

    @OneToMany
    private Set<ContainerImage> availableContainerImages = new HashSet<>();


//    public VirtualMachine(String name, Integer numberCores, ServiceType serviceType, String location) {
//        this.name = name;
//        this.serviceType = serviceType;
//        this.location = location;
//        try {
//            this.vmType = cacheVirtualMachineService.getVmTypeFromCore(numberCores, location);
//        } catch (Exception e) {
//        }
//        this.instanceId = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
//    }


    public VirtualMachine(String name, VMType vmType, ServiceType serviceType) {
        this.name = name;
        this.location = vmType.getLocation();
        this.serviceType = serviceType;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
    }

    public VirtualMachine(String name, VMType vmType) {
        this.name = name;
        this.location = vmType.getLocation();
        this.vmType = vmType;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
    }

    public VirtualMachine() {
        this.instanceId = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
    }
    
    public boolean isContainerDeployed(Container container) {
        return deployedContainers.contains(container);
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
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        String startString = startedAt == null ? "NOT_YET" : dtfOut.print(startedAt);
        String toBeTerminatedAtString = toBeTerminatedAt == null ? "NOT_YET" : dtfOut.print(toBeTerminatedAt);
        return "VirtualMachine{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", serviceType=" + serviceType +
                ", leased=" + leased +
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
        this.setLeased(false);
        this.setStarted(false);
        this.setStartedAt(null);
        this.setToBeTerminatedAt(null);
        this.serviceType = null;
        this.setGoogleName(null);
        this.setTerminating(false);
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
