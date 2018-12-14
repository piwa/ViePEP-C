package at.ac.tuwien.infosys.viepepc.library.entities.container;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.service.ServiceTypeJaxbAdapter;
import lombok.Getter;
import lombok.Setter;

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
public class ContainerImage implements Cloneable {

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
	@XmlElement
	private long startupTime;


	public ContainerImage() {
	}

	public ContainerImage(String repoName, String imageName, ServiceType serviceType) {
		this.serviceType = serviceType;
		this.repoName = repoName;
		this.imageName = imageName;
	}


	public ContainerImage clone(ServiceType serviceType) throws CloneNotSupportedException {
		ContainerImage image = (ContainerImage)super.clone();
		image.setServiceType(serviceType);
		return image;
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
