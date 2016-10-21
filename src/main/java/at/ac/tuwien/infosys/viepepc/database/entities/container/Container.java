package at.ac.tuwien.infosys.viepepc.database.entities.container;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import javax.persistence.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 */

@Entity
@Table(name = "Container")
@Getter
@Setter
@AllArgsConstructor
public class Container {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private ContainerConfiguration containerConfiguration;

    @ManyToOne
    private ContainerImage containerImage;

    @ManyToOne
    private VirtualMachine virtualMachine;

    private Date startedAt;
    private boolean running = false;

    @Value("${simulation.container.deploy.time}")
    private long deployTime;
    private long deployCost = 3;
    private String containerID;
    private String serviceName;
    private Integer externPort;

    public Container() {
    }

    public String getName() {
        return containerConfiguration.getName() + "_" + this.containerImage.getServiceType().getName();
    }

    public void shutdownContainer() {
        virtualMachine = null;
        running = false;
        startedAt = null;
    }

    @Override
    public String toString() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String startString = startedAt == null ? "NULL" : simpleDateFormat.format(startedAt);
        String vmString = virtualMachine == null ? "NULL" : virtualMachine.getName();
        return "Container{" +
                "id=" + id +
                ", name='" + getName() + '\'' +
                ", running=" + running +
                ", startedAt=" + startString +
                ", runningOnVM=" + vmString +
                '}';
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = getName().hashCode();
        result += prime * result + ((containerID == null) ? 0 : containerID.hashCode());
        result = prime * result + ((containerImage == null) ? 0 : containerImage.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
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
