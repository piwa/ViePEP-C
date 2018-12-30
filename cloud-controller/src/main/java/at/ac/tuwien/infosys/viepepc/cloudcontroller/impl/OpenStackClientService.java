package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by philippwaibel on 31/03/2017.
 */
@Slf4j
@Component
public class OpenStackClientService extends AbstractViePEPCloudService {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;

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

    public VirtualMachineInstance startVM(VirtualMachineInstance virtualMachineInstance) throws VmCouldNotBeStartedException {

        try {

            setup();

            if (virtualMachineInstance == null) {
                virtualMachineInstance = new VirtualMachineInstance(cacheVirtualMachineService.getDefaultVMType());
                virtualMachineInstance.getVmType().setFlavorName("m1.large");
            }

            String cloudInit = "";
            try {
                cloudInit = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("docker-config/cloud-init"), "UTF-8");
            } catch (IOException e) {
                log.error("Could not load cloud init file");
            }

            log.debug("getFlavor for VM: " + virtualMachineInstance.toString());
            Flavor flavor = os.compute().flavors().get(virtualMachineInstance.getVmType().getFlavorName());

            for (Flavor f : os.compute().flavors().list()) {
                if (f.getName().equals(virtualMachineInstance.getVmType().getFlavorName())) {
                    flavor = f;
                    break;
                }
            }

            log.debug("Flavor for VM: " + virtualMachineInstance.toString() + ": " + flavor.getName());

            ServerCreate sc = Builders.server()
                    .name(virtualMachineInstance.getInstanceId())
                    .flavor(flavor)
                    .image(openStackDefaultImageId)
                    .userData(Base64.encodeAsString(cloudInit))
                    .keypairName(openstackKeypairName)
                    .addSecurityGroup("default")
                    .build();


            log.debug("BootAndWaitActive for VM: " + virtualMachineInstance.toString());
            Server server = os.compute().servers().bootAndWaitActive(sc, 600000);
            if (server.getStatus().equals(Server.Status.ERROR)) {
                ActionResponse r = os.compute().servers().delete(server.getId());
                log.error("Could not boot VM: " + virtualMachineInstance.toString());
                throw new VmCouldNotBeStartedException("Could not boot VM: " + virtualMachineInstance.toString());
            }

            log.debug("BootAndWaitActive DONE for VM: " + virtualMachineInstance.toString());

            Map<String, List<? extends Address>> adrMap = server.getAddresses().getAddresses();

            String uri = adrMap.get("private").get(0).getAddr();

            log.debug("VM " + virtualMachineInstance.toString() + " active; IP: " + uri);

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

            virtualMachineInstance.setIpAddress(uri);
//        dh.setBTUend(btuEnd);


            log.info("VM with id: " + virtualMachineInstance.getInstanceId() + " and IP " + uri + " was started. Waiting for connection...");


            waitUntilVmIsBooted(virtualMachineInstance);


            virtualMachineInstance.setResourcepool("openstack");
            virtualMachineInstance.setInstanceId(server.getId());
            virtualMachineInstance.getVmType().setCores(flavor.getVcpus());
            virtualMachineInstance.getVmType().setRamPoints(flavor.getRam());
            virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);
            virtualMachineInstance.setStartTime(DateTime.now());
            //size in GB

            virtualMachineInstance.getVmType().setStorage(flavor.getDisk() * 1024 + 0F);
//        dh.setScheduledForShutdown(false);
            DateTime btuEnd = new DateTime(DateTimeZone.UTC);
//        btuEnd = btuEnd.plusSeconds(BTU);

            log.debug("VM connection with id: " + virtualMachineInstance.getInstanceId() + " and IP " + uri + " established.");


        } catch (Exception e) {
            log.error("Exception while booting VM", e);
            throw new VmCouldNotBeStartedException(e);
        }

        return virtualMachineInstance;
    }


    public final boolean stopVirtualMachine(VirtualMachineInstance virtualMachineInstance) {
        boolean success = stopVirtualMachine(virtualMachineInstance.getInstanceId());
        if (success) {
            virtualMachineInstance.setIpAddress(null);
        }

        return success;

    }

    public final boolean stopVirtualMachine(final String id) {
        setup();
        ActionResponse r = os.compute().servers().delete(id);

        if (!r.isSuccess()) {
            log.error("VM with id: " + id + " could not be stopped: " + r.getFault());
        } else {
            log.info("VM with id: " + id + " terminated");
        }

        return r.isSuccess();
    }


}
