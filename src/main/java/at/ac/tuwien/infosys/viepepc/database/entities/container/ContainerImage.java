package at.ac.tuwien.infosys.viepepc.database.entities.container;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ServiceTypeJaxbAdapter;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * @author Gerta Sheganaku
 */
@XmlRootElement(name = "ContainerImage")
@XmlAccessorType(XmlAccessType.FIELD)
@Entity
@Table(name = "container_image")
@Getter
@Setter
public class ContainerImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@XmlTransient
	private Long id;

	@XmlElement
	private String imageName;
	@XmlElement
	private String repoName;
	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name="serviceTypeId")
	@XmlElement
    @XmlJavaTypeAdapter(ServiceTypeJaxbAdapter.class)
    private ServiceType serviceType;
	@XmlElement
	private long deployTime;

	public ContainerImage() {
	}

	public ContainerImage(String repoName, String imageName, ServiceType serviceType) {
		this.serviceType = serviceType;
		this.repoName = repoName;
		this.imageName = imageName;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((imageName == null) ? 0 : imageName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ContainerImage other = (ContainerImage) obj;
		if (imageName == null) {
			if (other.imageName != null)
				return false;
		} else if (!imageName.equals(other.imageName))
			return false;
		return true;
	}

}
