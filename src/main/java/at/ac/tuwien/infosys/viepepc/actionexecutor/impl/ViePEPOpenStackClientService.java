package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
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
                .keypairName(openstackKeypairName)
                .addSecurityGroup("default")
                .build();

        Server server = os.compute().servers().boot(sc);

        String uri = server.getAccessIPv4();

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

        virtualMachine.setResourcepool("openstack");
        virtualMachine.setInstanceId(server.getId());
        virtualMachine.setIpAddress(uri);
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
//        dh.setBTUend(btuEnd);


        log.info("VM with id: " + virtualMachine.getInstanceId() + " and IP " + uri + " was started. Waiting for connection...");

        waitUntilVmIsBooted(virtualMachine);

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