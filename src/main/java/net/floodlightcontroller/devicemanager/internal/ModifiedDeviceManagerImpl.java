/**
 *    Copyright 2011,2012 Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.devicemanager.internal;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassListener;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.web.DeviceRoutable;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.flowcache.OFMatchReconcile;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.MultiIterator;
import net.floodlightcontroller.keyvaluestore.IKeyValueStoreService;
import static net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl.DeviceUpdate.Change.*;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFMatchWithSwDpid;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeviceManager creates Devices based upon MAC addresses seen in the network.
 * It tracks any network addresses mapped to the Device, and its location
 * within the network.
 * @author readams
 */
public class ModifiedDeviceManagerImpl extends DeviceManagerImpl {
    protected static Logger logger =
            LoggerFactory.getLogger(ModifiedDeviceManagerImpl.class);

    protected IKeyValueStoreService kvStoreService;
    protected String ARPTableName = null;

    // ******************
    // IOFMessageListener
    // ******************

    @Override
    public String getName() {
        return "modifieddevicemanager";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return ((type == OFType.PACKET_IN || type == OFType.FLOW_MOD)
                && name.equals("topology"));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.processPacketInMessage(sw,
                                                   (OFPacketIn) msg, cntx);
            default:
                break;
        }
        return Command.CONTINUE;
    }

    // *****************
    // IFloodlightModule
    // *****************

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IDeviceService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
    getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
        new HashMap<Class<? extends IFloodlightService>,
        IFloodlightService>();
        // We are the class that implements the service
        m.put(IDeviceService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStorageSourceService.class);
        l.add(ITopologyService.class);
        l.add(IRestApiService.class);
        l.add(IThreadPoolService.class);
        l.add(IFlowReconcileService.class);
        l.add(IEntityClassifierService.class);
	l.add(IKeyValueStoreService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext fmc) {
	super.init(fmc);
        this.kvStoreService = fmc.getServiceImpl(IKeyValueStoreService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext fmc) {
	super.startUp(fmc);	
        ARPTableName = kvStoreService.getIP2MACTable();
    }

    // ****************
    // Internal methods
    // ****************

    public String getDomain(int IP)
    {
        return ("0");
    }

    public void updateARPTable(Entity node)
    {
        Integer IP = null;
        boolean result = false;

        IP = node.getIpv4Address();
        if (IP == null) {
	    	logger.info("ip address is null");
        	return;
		}

	if (node.getMacAddress() == 0) {
	    logger.info("dst MAC address is null");
	    return;
	}

        int key = IP;
	//IPv4.toIPv4Address(IP);
        String domainID = getDomain(key); 
        String macAddr = MACAddress.valueOf(node.getMacAddress()).toString(); 
       
        //if (logger.isTraceEnabled()) 
            logger.info ("updating the key {}, IP: {} with value: {}",
                         new Object[] {key, IPv4.fromIPv4Address(IP), macAddr}); 
        result = kvStoreService.addRUpdate(ARPTableName, key, macAddr+","+domainID); 
        if (!result && logger.isDebugEnabled())
           logger.debug("ARP table update failed");  
    }

    protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                             FloodlightContext cntx) {

        Ethernet eth =
                IFloodlightProviderService.bcStore.
                get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

       //if (logger.isTraceEnabled()) {
           logger.info("Received PI: {} from switch {}, port {} *** eth={}",
                        new Object[] { pi, sw.getStringId(), pi.getInPort(), eth});
       //}
        // Extract source entity information
        Entity srcEntity =
                getSourceEntityFromPacket(eth, sw.getId(), pi.getInPort());
        if (srcEntity == null)
            return Command.STOP;

        logger.info ("updating the source entity details");
        updateARPTable(srcEntity);

        // Learn/lookup device information
        Device srcDevice = learnDeviceByEntity(srcEntity);
        if (srcDevice == null)
            return Command.STOP;

        // Store the source device in the context
        fcStore.put(cntx, CONTEXT_SRC_DEVICE, srcDevice);

        // Find the device matching the destination from the entity
        // classes of the source.
        Entity dstEntity = getDestEntityFromPacket(eth);
        
        Device dstDevice = null;
        if (dstEntity != null) {

            if (dstEntity.getIpv4Address() != null) {
                logger.info ("updating the destination entity details");
                updateARPTable(dstEntity);
            }
            dstDevice =
                    findDestByEntity(srcDevice.getEntityClass(), dstEntity);
            if (dstDevice != null)
                fcStore.put(cntx, CONTEXT_DST_DEVICE, dstDevice);
        }
           logger.info("Received PI, device entity details" +
                        " *** srcDev={} *** dstDev={} *** ",
                        srcDevice, dstDevice);
        return Command.CONTINUE;
    }
}
