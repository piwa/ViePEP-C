package at.ac.tuwien.infosys.viepepc.database.entities.container;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.persistence.*;
import java.util.UUID;

/**
 */

@Entity
@Table(name = "container")
@Getter
@Setter
@AllArgsConstructor
public class Container implements Cloneable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne//(cascade = CascadeType.ALL)
    @JoinColumn(name="containerConfigurationId")
    private ContainerConfiguration containerConfiguration;
    @ManyToOne
    @JoinColumn(name="containerImageId")
    private ContainerImage containerImage;
    @ManyToOne(cascade = CascadeType.ALL)
    private VirtualMachine virtualMachine;
    private long deployCost = 3;
    private String containerID;
    private String serviceName;
    private String externPort;
    private String ipAddress;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startedAt;
    private boolean running = false;
    private boolean bareMetal = false;
    private boolean deploying = false;
    private String providerContainerId = "";

    public Container() {
        containerID = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
    }

    public String getName() {
        return containerConfiguration.getName() + "_" + this.containerImage.getServiceType().getName() + "_" + containerID;
    }

    public void shutdownContainer() {
        if(virtualMachine != null) {
            virtualMachine.undeployContainer(this);
            virtualMachine = null;
        }
        setRunning(false);
        bareMetal = false;
        setDeploying(false);
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void setRunning(boolean running) {
        this.running = running;
    }

    public synchronized boolean isDeploying() {
        return deploying;
    }

    public synchronized void setDeploying(boolean deploying) {
        this.deploying = deploying;
    }

    public Container clone(ServiceType serviceType) throws CloneNotSupportedException {
        Container container = (Container)super.clone();
        container.setContainerConfiguration(this.containerConfiguration.clone());
        container.setContainerImage(this.containerImage.clone(serviceType));
        return container;
    }

    @Override
    public String toString() {
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        String startString = startedAt == null ? "NULL" : startedAt.toString();
        String vmString = virtualMachine == null ? "NULL" : virtualMachine.getInstanceId();

        if(isBareMetal()) {
            return "Container{" +
                    "name='" + getName() + '\'' +
                    ", running=" + running +
                    ", deploying=" + deploying +
                    ", startedAt=" + startString +
                    ", url=" + ipAddress + ":" + externPort +
                    ", serviceType=" + containerImage.getServiceType().getName() +
                    '}';
        }
        else {
            return "Container{" +
                    "name='" + getName() + '\'' +
                    ", running=" + running +
                    ", startedAt=" + startString +
                    ", runningOnVM=" + vmString +
                    ", externalPort=" + externPort +
                    ", serviceType=" + containerImage.getServiceType().getName() +
                    '}';
        }
    }



    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Container other = (Container) obj;
        if (containerID == null) {
            if (other.containerID != null) {
                return false;
            }
        }
        else if (!containerID.equals(other.containerID)) {
            return false;
        }
        if (containerImage == null) {
            if (other.containerImage != null) {
                return false;
            }
        }
        else if (!containerImage.equals(other.containerImage)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        }
        else if (!id.equals(other.id)) {
            return false;
        }

        //  also consider the name here:
        String otherName = other.getName();
        String thisName = this.getName();
        if (thisName == null) {
            if (otherName != null) {
                return false;
            }
        }
        else if (!thisName.equals(otherName)) {
            return false;
        }
        return true;
    }
}
