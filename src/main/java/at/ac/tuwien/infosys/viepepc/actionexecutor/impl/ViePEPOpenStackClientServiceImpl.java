package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPOpenStackClientService;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.internal.util.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.compute.FloatingIP;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ServerCreate;
import org.openstack4j.openstack.OSFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 31/03/2017.
 */
@Slf4j
@Component
public class ViePEPOpenStackClientServiceImpl implements ViePEPOpenStackClientService {


    @Value("${openstack.default.image.id}")
    private String openStackDefaultImageId;
    @Value("${openstack.use.public.ip}")
    private Boolean PUBLICIPUSAGE;
    @Value("${openstack.auth.url}")
    private String OPENSTACK_AUTH_URL;
    @Value("${openstack.username}")
    private String OPENSTACK_USERNAME;
    @Value("${openstack.password}")
    private String OPENSTACK_PASSWORD;
    @Value("${openstack.tenant.name}")
    private String OPENSTACK_TENANT_NAME;
    @Value("${openstack.keypair.name}")
    private String OPENSTACK_KEYPAIR_NAME;

    private OSClient.OSClientV2 os;

    private void setup() {
        os = OSFactory.builderV2()
                .endpoint(OPENSTACK_AUTH_URL)
                .credentials(OPENSTACK_USERNAME, OPENSTACK_PASSWORD)
                .tenantName(OPENSTACK_TENANT_NAME)
                .authenticate();

        log.debug("Successfully connected to " + OPENSTACK_AUTH_URL + " on tenant " + OPENSTACK_TENANT_NAME + " with user " + OPENSTACK_USERNAME);
    }

    @Override
    public VirtualMachine startVM(VirtualMachine virtualMachine) {
        setup();

        if (virtualMachine == null) {
            virtualMachine = new VirtualMachine();
            virtualMachine.getVmType().setFlavor("m1.large");
        }

        String cloudInit = "";
        try {
            cloudInit = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("docker-config/cloud-init"), "UTF-8");
        } catch (IOException e) {
            log.error("Could not load cloud init file");
        }

        Flavor flavor = os.compute().flavors().get(virtualMachine.getVmType().getFlavor());

        for (Flavor f : os.compute().flavors().list()) {
            if (f.getName().equals(virtualMachine.getVmType().getFlavor())) {
                flavor = f;
                break;
            }
        }

        ServerCreate sc = Builders.server()
                .name(virtualMachine.getName())
                .flavor(flavor)
                .image(openStackDefaultImageId)
                .userData(Base64.encodeAsString(cloudInit))
                .keypairName(OPENSTACK_KEYPAIR_NAME)
                .addSecurityGroup("default")
                .build();

        Server server = os.compute().servers().boot(sc);

        String uri = server.getAccessIPv4();

        if (PUBLICIPUSAGE) {
            FloatingIP freeIP = null;

            for (FloatingIP ip : os.compute().floatingIps().list()) {
                if (ip.getFixedIpAddress() == null) {
                    freeIP = ip;
                    break;
                }
            }
            if (freeIP == null) {
                freeIP = os.compute().floatingIps().allocateIP("cloud");
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                //TODO remove if openstack behaves again
            }

            ActionResponse ipresponse = os.compute().floatingIps().addFloatingIP(server, freeIP.getFloatingIpAddress());
            if (!ipresponse.isSuccess()) {
                log.error("IP could not be retrieved:" + ipresponse.getFault());
            }
            uri = freeIP.getFloatingIpAddress();
        }

        virtualMachine.setResourcepool("openstack");
        virtualMachine.setName(server.getId());
        virtualMachine.setIpAddress(uri);
        virtualMachine.getVmType().setCores(flavor.getVcpus());
        virtualMachine.getVmType().setRamPoints(flavor.getRam());
        //size in GB

        virtualMachine.getVmType().setStorage(flavor.getDisk() * 1024 + 0F);
//        dh.setScheduledForShutdown(false);
        DateTime btuEnd = new DateTime(DateTimeZone.UTC);
//        btuEnd = btuEnd.plusSeconds(BTU);
//        dh.setBTUend(btuEnd);


        log.info("VM with id: " + virtualMachine.getName() + " and IP " + uri + " was started. Waiting for connection...");

        //wait until the dockerhost is available
        Boolean connection = false;
        while (!connection) {
            try {
                TimeUnit.SECONDS.sleep(1);
                final DockerClient docker = DefaultDockerClient.builder().
                        uri(URI.create("http://" + virtualMachine.getIpAddress() + ":2375")).
                        connectTimeoutMillis(100000).build();
                docker.ping();
                connection = true;
            } catch (InterruptedException | DockerException e) {
                log.debug("VM is not available yet.", e);
            }
        }

        log.debug("VM connection with id: " + virtualMachine.getName() + " and IP " + uri + " established.");

//        dhr.save(dh);
//        sar.save(new ScalingActivity("host", new DateTime(DateTimeZone.UTC), "", "startVM", dh.getName()));

        return virtualMachine;
    }

    @Override
    public final boolean stopVirtualMachine(VirtualMachine virtualMachine) {
        boolean success = stopVirtualMachine(virtualMachine.getName());
        if(success) {
            virtualMachine.setIpAddress(null);
        }
//        dhr.delete(dh);
//        sar.save(new ScalingActivity("host", new DateTime(DateTimeZone.UTC), "", "stopWM", dh.getName()));

        return success;

    }

    public final boolean stopVirtualMachine(final String id) {
        setup();
        ActionResponse r = os.compute().servers().delete(id);

        if (!r.isSuccess()) {
            log.error("VM with id: " + id + " could not be stopped: " +  r.getFault());
        } else {
            log.info("VM with id: " + id + " terminated");
        }

        return r.isSuccess();
    }

    @Override
    public boolean checkAvailabilityofDockerhost(String url) {
        final DockerClient docker = DefaultDockerClient.builder().uri("http://" + url + ":2375").connectTimeoutMillis(5000).build();
        try {
            return docker.ping().equals("OK");
        } catch (DockerException | InterruptedException e) {
            return false;
        }
    }



}
