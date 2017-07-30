package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.internal.util.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.*;
import org.openstack4j.openstack.OSFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Time;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 31/03/2017.
 */
@Slf4j
@Component
public class ViePEPOpenStackClientService extends AbstractViePEPCloudService  {


    @Value("${openstack.default.image.id}")
    private String openStackDefaultImageId;
    @Value("${openstack.use.public.ip}")
    private Boolean publicUsage;
    @Value("${openstack.auth.url}")
    private String openstackAuthUrl;
    @Value("${openstack.username}")
    private String openstackUsername;
    @Value("${openstack.password}")
    private String openstackPassword;
    @Value("${openstack.tenant.name}")
    private String openstackTenantName;
    @Value("${openstack.keypair.name}")
    private String openstackKeypairName;

    private OSClient.OSClientV2 os;

    private void setup() {
        os = OSFactory.builderV2()
                .endpoint(openstackAuthUrl)
                .credentials(openstackUsername, openstackPassword)
                .tenantName(openstackTenantName)
                .authenticate();

        log.debug("Successfully connected to " + openstackAuthUrl + " on tenant " + openstackTenantName + " with user " + openstackUsername);
    }

    public VirtualMachine startVM(VirtualMachine virtualMachine) throws VmCouldNotBeStartedException {
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

        log.debug("getFlavor for VM: " + virtualMachine.toString());
        Flavor flavor = os.compute().flavors().get(virtualMachine.getVmType().getFlavor());

        for (Flavor f : os.compute().flavors().list()) {
            if (f.getName().equals(virtualMachine.getVmType().getFlavor())) {
                flavor = f;
                break;
            }
        }

        log.debug("Flavor for VM: " + virtualMachine.toString() + ": " + flavor.getName());

        ServerCreate sc = Builders.server()
                .name(virtualMachine.getName())
                .flavor(flavor)
                .image(openStackDefaultImageId)
                .userData(Base64.encodeAsString(cloudInit))
                .keypairName(openstackKeypairName)
                .addSecurityGroup("default")
                .build();

        boolean bootSuccessfully = false;

        Server server = null;
        int counter = 0;
        while(!bootSuccessfully) {
            counter = counter + 1;
            log.debug("BootAndWaitActive for VM: " + virtualMachine.toString());
            server = os.compute().servers().bootAndWaitActive(sc, 1200000);
            log.debug("BootAndWaitActive DONE for VM: " + virtualMachine.toString());

            if (server.getStatus().equals(Server.Status.ERROR)) {
                ActionResponse r = os.compute().servers().delete(server.getId());
                try {
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(counter >= 5) {
                    throw new VmCouldNotBeStartedException("Could not boot VM: " + virtualMachine.toString());
                }
            }
            else {
                bootSuccessfully = true;
            }
        }

        Map<String, List<? extends Address>> adrMap = server.getAddresses().getAddresses();

        String uri = adrMap.get("private").get(0).getAddr();

        log.debug("VM " + virtualMachine.toString() + " active; IP: " + uri);

        if (publicUsage) {
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

        virtualMachine.setIpAddress(uri);
//        dh.setBTUend(btuEnd);


        log.info("VM with id: " + virtualMachine.getInstanceId() + " and IP " + uri + " was started. Waiting for connection...");

        waitUntilVmIsBooted(virtualMachine);

        virtualMachine.setResourcepool("openstack");
        virtualMachine.setInstanceId(server.getId());
        virtualMachine.getVmType().setCores(flavor.getVcpus());
        virtualMachine.getVmType().setRamPoints(flavor.getRam());
        virtualMachine.setStarted(true);
        virtualMachine.setLeased(true);
        virtualMachine.setStartedAt(DateTime.now());
        //size in GB

        virtualMachine.getVmType().setStorage(flavor.getDisk() * 1024 + 0F);
//        dh.setScheduledForShutdown(false);
        DateTime btuEnd = new DateTime(DateTimeZone.UTC);
//        btuEnd = btuEnd.plusSeconds(BTU);

        log.debug("VM connection with id: " + virtualMachine.getInstanceId() + " and IP " + uri + " established.");


        return virtualMachine;
    }


    public final boolean stopVirtualMachine(VirtualMachine virtualMachine) {
        boolean success = stopVirtualMachine(virtualMachine.getInstanceId());
        if(success) {
            virtualMachine.setIpAddress(null);
        }

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





}
