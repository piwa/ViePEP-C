package at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.Interval;

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
public class VirtualMachineInstance implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private final UUID internId;

    private String instanceId;

    private String googleName;

    private String ipAddress;

    @Columns(columns={@Column(name="sCloudUsageStartTime"),@Column(name="sCloudUsageEndTime")})
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInterval")
    private Interval scheduledCloudResourceUsage;
    @Columns(columns={@Column(name="sAvailableStartTime"),@Column(name="sAvailableEndTime")})
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInterval")
    private Interval scheduledAvailableInterval;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime deploymentStartTime;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startTime;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime terminationTime;

    private String resourcepool;

    private VirtualMachineStatus virtualMachineStatus;

    @ManyToOne()
    private VMType vmType;

    @ElementCollection
    private List<String> usedPorts = new ArrayList<>();

    @Transient
//    @OneToMany(cascade = CascadeType.ALL, mappedBy="virtualMachineInstance")
    private Set<Container> deployedContainers = new HashSet<>();

    @Transient
//    @OneToMany
    private Set<ContainerImage> availableContainerImages = new HashSet<>();

    public VirtualMachineInstance() {
        this.internId = UUID.randomUUID();
        this.instanceId = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
        this.virtualMachineStatus = VirtualMachineStatus.UNUSED;
    }

    public VirtualMachineInstance(VMType vmType) {
        this.internId = UUID.randomUUID();
        this.vmType = vmType;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
        this.virtualMachineStatus = VirtualMachineStatus.UNUSED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VirtualMachineInstance that = (VirtualMachineInstance) o;
        return internId.toString().equals(that.internId.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(internId.toString());
    }

    @Override
    public String toString() {
        return "VirtualMachineInstance{" +
//                "id=" + id +
                "internId=" + internId.toString().substring(0,8) +
//                ", instanceId='" + instanceId + '\'' +
//                ", googleName='" + googleName + '\'' +
//                ", ipAddress='" + ipAddress + '\'' +
                ", scheduledCloudResourceUsage=" + scheduledCloudResourceUsage +
                ", scheduledAvailableInterval=" + scheduledAvailableInterval +
                ", deploymentStartTime=" + deploymentStartTime +
                ", startTime=" + startTime +
                ", terminationTime=" + terminationTime +
//                ", resourcepool='" + resourcepool + '\'' +
                ", virtualMachineStatus=" + virtualMachineStatus +
                ", vmType=" + vmType.toString() +
//                ", usedPorts=" + usedPorts +
//                ", deployedContainers=" + deployedContainers +
//                ", availableContainerImages=" + availableContainerImages +
                '}';
    }

    public String getURI() {
        return "http://" + this.ipAddress;
    }

    public void terminate() {
        this.setVirtualMachineStatus(VirtualMachineStatus.TERMINATED);
        this.setTerminationTime(DateTime.now());
        this.setGoogleName(null);
        this.setIpAddress(null);
        this.availableContainerImages = new HashSet<>();
    }

    public String getGoogleName() {
        if(googleName == null || googleName.isEmpty()) {
            googleName = "eval-" + this.instanceId.substring(0,6) + "-" + UUID.randomUUID().toString().substring(0,6);
        }
        return googleName;
    }

    public void undeployContainer(Container container) {
        this.deployedContainers.remove(container);
    }



}
