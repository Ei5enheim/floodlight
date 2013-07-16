/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.circuitswitching.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.circuitswitching.CircuitSwitchingBase;
import net.floodlightcontroller.circuitswitching.ICircuitSwitching;
import net.floodlightcontroller.circuitswitching.CircuitIDGen;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.keyvaluestore.IKeyValueStoreService;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class CircuitSwitchingImpl extends CircuitSwitchingBase implements IFloodlightModule
{
    protected static Logger log = LoggerFactory.getLogger(CircuitSwitchingImpl.class);

    /**************************
        IFloodlightModule 
     *************************/
    public String getName()
    {
        return "circuitswitchingimpl";
    }

    public int getId()
    {
        return 0;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices()
    {
        Collection <Class<? extends IFloodlightService>> list =
                            new ArrayList<Class<? extends IFloodlightService>>();

        list.add(ICircuitSwitching.class);
        return (list);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls()
    {
        Map <Class<? extends IFloodlightService>, IFloodlightService> map =
                            new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();

        map.put(ICircuitSwitching.class, this);
        return (map);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies()
    {
        Collection<Class <? extends IFloodlightService>> collection =
                                new ArrayList<Class<? extends IFloodlightService>>();
        collection.add(IFloodlightProviderService.class);
        collection.add(IRoutingService.class);
        collection.add(ITopologyService.class);
        collection.add(ICounterStoreService.class);
        collection.add(IRestApiService.class);
        collection.add(IKeyValueStoreService.class);
        return (collection);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException
    {
        super.init();
        this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        this.routingEngine = context.getServiceImpl(IRoutingService.class);
        this.topology = context.getServiceImpl(ITopologyService.class);
        this.counterStore = context.getServiceImpl(ICounterStoreService.class);
        this.restAPIService = context.getServiceImpl(IRestApiService.class);
        this.kvStoreClient = context.getServiceImpl(IKeyValueStoreService.class);

        // read our config options
        Map<String, String> configOptions = context.getConfigParams(this);
        try {
            String idleTimeout = configOptions.get("idletimeout");
            if (idleTimeout != null) {
                FLOWMOD_DEFAULT_IDLE_TIMEOUT = Short.parseShort(idleTimeout);
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing flow idle timeout, " +
                     "using default of {} seconds",
                     FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        }
        try {
            String hardTimeout = configOptions.get("hardtimeout");
            if (hardTimeout != null) {
                FLOWMOD_DEFAULT_HARD_TIMEOUT = Short.parseShort(hardTimeout);
            }
        } catch (NumberFormatException e) {
            log.warn("Error parsing flow hard timeout, " +
                     "using default of {} seconds",
                     FLOWMOD_DEFAULT_HARD_TIMEOUT);
        }
        log.debug("FlowMod idle timeout set to {} seconds", 
                  FLOWMOD_DEFAULT_IDLE_TIMEOUT);
        log.debug("FlowMod hard timeout set to {} seconds", 
                  FLOWMOD_DEFAULT_HARD_TIMEOUT);
    }

    @Override
    public void startUp(FloodlightModuleContext context)
    {
        super.startUp();
        PathIDStoreName = kvStoreClient.getPathIDStore();
    }

    // CircuitSwitching API
    @Override
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                          IRoutingDecision decision, 
                                          FloodlightContext cntx) {
    
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, 
                                   IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        
        // If a decision has been made we obey it
        // otherwise we just forward
        // Imagine a firewall module running before the forwarding module and decides that
        // the packet needs to be dropped then we need to respect that.
        // Interesting point this one.
        if (decision != null) {
            if (log.isTraceEnabled()) {
                log.trace("Forwaring decision={} was made for PacketIn={}",
                        decision.getRoutingAction().toString(),
                        pi);
            }
            
            switch(decision.getRoutingAction()) {
                case NONE:
                    // don't do anything
                    return Command.CONTINUE;
                case FORWARD_OR_FLOOD:
                case FORWARD:
                    doForwardFlow(sw, pi, cntx, false);
                    return Command.CONTINUE;
                case MULTICAST:
                    // treat as broadcast
                    doFlood(sw, pi, cntx);
                    return Command.CONTINUE;
                case DROP:
                    doDropFlow(sw, pi, decision, cntx);
                    return Command.CONTINUE;
                default:
                    log.error("Unexpected decision made for this packet-in={}",
                            pi, decision.getRoutingAction());
                    return Command.CONTINUE;
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("No decision was made for PacketIn={}, forwarding",
                        pi);
            }
            
            if (eth.isBroadcast() || eth.isMulticast()) {
                // For now we treat multicast as broadcast
                doFlood(sw, pi, cntx);
            } else {
                doForwardFlow(sw, pi, cntx, false);
            }
        }
        return Command.CONTINUE;
    }
    
    protected void doDropFlow(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx)
    {
        // initialize match structure and populate it using the packet
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        if (decision.getWildcards() != null) {
            match.setWildcards(decision.getWildcards());
        }
        
        // Create flow-mod based on packet-in and src-switch
        OFFlowMod fm =
                (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                              .getMessage(OFType.FLOW_MOD);
        List<OFAction> actions = new ArrayList<OFAction>(); // Set no action to
                                                            // drop
        long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);
        
        fm.setCookie(cookie)
          .setHardTimeout((short) 0)
          .setIdleTimeout((short) 5)
          .setBufferId(OFPacketOut.BUFFER_ID_NONE)
          .setMatch(match)
          .setActions(actions)
          .setLengthU(OFFlowMod.MINIMUM_LENGTH); // +OFActionOutput.MINIMUM_LENGTH);

        try {
            if (log.isDebugEnabled()) {
                log.debug("write drop flow-mod sw={} match={} flow-mod={}",
                          new Object[] { sw, match, fm });
            }
            messageDamper.write(sw, fm, cntx);
        } catch (IOException e) {
            log.error("Failure writing drop flow mod", e);
        }
    }

    // Utility methods
    private OFMatch getMatchClone (OFMatch match)
    {
        OFMatch cn = null;
        cn = match.clone();
        if (cn != null)
            return (cn);
        if (log.isTraceEnabled())
            log.trace("Error in cloning match structure");
        return (match);
    }

    private long byteArray2Long(byte[] array)
    {
        long value = 0;
        
        for (int i = 0; i < 6; i++)
        {
            value = (value << 8) | (array[i] & 0x00FF);    
        }
        return (value);     
    }

    private byte[] long2ByteArray (long v)
    {
         byte[] result = new byte[6];
         long mask = 0x00000000000000FFL;
         long temp = 0;
         
         for (int i = 5; i >= 0; i--)
          {
              temp =  v & mask;
              result[i] = (byte) (temp >> (8 * (5-i)));
              mask = mask << 8;
          }  
          return (result);
    }

    private LinkedList<byte[]> addRGetPathID (long dstSwID, long srcSwID,
                                                int cookie) 
    {
        boolean result = false;
        String key = String.valueOf(dstSwID) + "," + String.valueOf(srcSwID);
        List <Object> valuesList = (List <Object>)kvStoreClient.get(
                                                        PathIDStoreName, key);
        // values are going to be in an array and they are going 
        // to be in pairs [dstmac followed by the source]
        long srcMac = 0, dstMac = 0;
        
        if (valuesList == null)
            valuesList = new ArrayList<Object>();

        if (valuesList.size() > 0) {
            // TODO Multipath support,
            // For now single path, but we will go multipath later
            dstMac = (Long) valuesList.get(0);
            srcMac = (Long) valuesList.get(1);
            if (log.isTraceEnabled())
                log.trace("PATHID {},{} has already been generated in the past",
                            dstMac, srcMac);
            LinkedList<byte[]> rv = new LinkedList<byte[]>();
            rv.add(long2ByteArray(dstMac));
            rv.add(long2ByteArray(srcMac));
            return (rv);      
        }
        
        //TODO: cookie is zero for now
        LinkedList<byte[]> dlHeaders = getPathID(dstSwID, srcSwID, cookie);
        valuesList.add(byteArray2Long(dlHeaders.get(0)));
        valuesList.add(byteArray2Long(dlHeaders.get(1)));
        if (log.isTraceEnabled())
            log.trace("PATHID {},{} is being updated", valuesList.get(0),
                                                        valuesList.get(1));
        result = kvStoreClient.addRUpdate(PathIDStoreName, key, valuesList); 
        if (!result && log.isDebugEnabled())
           log.debug("PATHID store update failed");  
        
        return dlHeaders;
    }
   
    private LinkedList<OFMatch> getMatchList (OFPacketIn pi, long dstSwId, long srcSwId,
                                              LinkedList<byte[]> rwHeaders)
    {
        LinkedList<OFMatch> matchList = new LinkedList<OFMatch>(); 
        LinkedList<byte[]> dlHeader = null;

        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort()); 
        rwHeaders.addFirst(match.getDataLayerDestination());
        rwHeaders.addLast(match.getDataLayerSource());
        matchList.addLast(match);
        
        //cookie is zero for now
        dlHeader = addRGetPathID(srcSwId, dstSwId, 0);
        match = getMatchClone(match);
        match.setDataLayerDestination(dlHeader.get(0));
        match.setDataLayerSource(dlHeader.get(1));
        rwHeaders.addLast(dlHeader.get(0));
        rwHeaders.addLast(dlHeader.get(1)); 
        matchList.addFirst(match);
        return (matchList); 
    }

    private boolean checkForAP(IDevice device, IOFSwitch sw)
    {
        long dpID = -1;

        for (SwitchPort ap : device.getAttachmentPoints()) {
            dpID = ap.getSwitchDPID();
            if (sw.getId() == dpID)
                return (true);
        }
        return (false);
    }

    protected void doForwardFlow(IOFSwitch sw, OFPacketIn pi, 
                                 FloodlightContext cntx,
                                 boolean requestFlowRemovedNotifn)
    {
        LinkedList<byte[]> pinSwitchRWHeaders = null;
        Integer srcSwOutport = null;
        boolean pinSwitchFound = false;
        // Check if we have the location of the destination
        IDevice dstDevice = 
                IDeviceService.fcStore.
                    get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
        
        if (dstDevice != null) {
            IDevice srcDevice =
                    IDeviceService.fcStore.
                        get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
            Long srcIsland = topology.getL2DomainId(sw.getId());
            
            if (srcDevice == null) {
                log.debug("No device entry found for source device");
                return;
            }
            if (srcIsland == null) {
                log.debug("No openflow island found for source {}/{}", 
                          sw.getStringId(), pi.getInPort());
                return;
            }
            
            //TODO remove this check if possible
            if (!checkForAP(srcDevice, sw)) {
                log.info("******received packetin for device {} on non-home"+
                            " switch {}*****", srcDevice, sw);
                return;
            }
            // Validate that we have a destination known on the same island
            // Validate that the source and destination are not on the same switchport
            boolean on_same_island = false;
            boolean on_same_if = false;
            long dstSwDpid = -1;
            for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
                dstSwDpid = dstDap.getSwitchDPID();
                Long dstIsland = topology.getL2DomainId(dstSwDpid);
                if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
                    on_same_island = true;
                    if ((sw.getId() == dstSwDpid) &&
                        (pi.getInPort() == dstDap.getPort())) {
                        on_same_if = true;
                    }
                    break;
                }
            }
            
            if (!on_same_island) {
                // Flood since we don't know the dst device
                if (log.isTraceEnabled()) {
                    log.trace("No first hop island found for destination " + 
                              "device {}, Action = flooding", dstDevice);
                }
                doFlood(sw, pi, cntx);
                return;
            }            
            
            if (on_same_if) {
                if (log.isTraceEnabled()) {
                    log.trace("Both source and destination are on the same " + 
                              "switch/port {}/{}, Action = NOP", 
                              sw.toString(), pi.getInPort());
                }
                return;
            }

            /*
             * for full fledged demo, need to have same 
             * circuit ID throught out the network. Should be possible by 
             * having a home dpid inside device. what if a device roams
             * and the concurrency?
             */
            LinkedList<byte[]> rwHeaders = null; 
            LinkedList<OFMatch> matchList = null;
            LinkedList<Integer> wildCards_List = null;
            long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);
            // if there is prior routing decision use wildcard
            Integer wildcard_hints = null;
            IRoutingDecision decision = null;
            if (cntx != null) {
                decision = IRoutingDecision.rtStore
                                            .get(cntx, IRoutingDecision.CONTEXT_DECISION);
            }
            if (decision != null) {
                wildcard_hints = decision.getWildcards();
            } else {
                // L2 only wildcard if there is no prior route decision
                wildcard_hints = ((Integer) sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS))
                                                .intValue()
                                                & ~OFMatch.OFPFW_IN_PORT
                                                & ~OFMatch.OFPFW_DL_VLAN
                                                & ~OFMatch.OFPFW_DL_SRC
                                                & ~OFMatch.OFPFW_DL_DST
                                                & ~OFMatch.OFPFW_NW_SRC_MASK
                                                & ~OFMatch.OFPFW_NW_DST_MASK;
            }

            // Install all the routes where both src and dst have attachment
            // points.  Since the lists are stored in sorted order we can 
            // traverse the attachment points in O(m+n) time
            SwitchPort[] srcDaps = srcDevice.getAttachmentPoints();
            Arrays.sort(srcDaps, clusterIdComparator);
            SwitchPort[] dstDaps = dstDevice.getAttachmentPoints();
            Arrays.sort(dstDaps, clusterIdComparator);

            int iSrcDaps = 0, iDstDaps = 0;

            while ((iSrcDaps < srcDaps.length) && (iDstDaps < dstDaps.length)) {
                SwitchPort srcDap = srcDaps[iSrcDaps];
                SwitchPort dstDap = dstDaps[iDstDaps];
                Long srcCluster = 
                        topology.getL2DomainId(srcDap.getSwitchDPID());
                Long dstCluster = 
                        topology.getL2DomainId(dstDap.getSwitchDPID());

                int srcVsDest = srcCluster.compareTo(dstCluster);
                if (srcVsDest == 0) {
                    if (!srcDap.equals(dstDap) && 
                        (srcCluster != null) && 
                        (dstCluster != null)) {
                        Route route = 
                                routingEngine.getRoute(srcDap.getSwitchDPID(),
                                                       (short)srcDap.getPort(),
                                                       dstDap.getSwitchDPID(),
                                                       (short)dstDap.getPort(), 0); //cookie = 0, i.e., default route
                        if (route != null) {
                            if (log.isTraceEnabled()) {
                                log.trace("pushCircuit match={} route={} " + 
                                          "destination={}:{}",
                                          new Object[] {matchList.get(0), route, 
                                                        dstDap.getSwitchDPID(),
                                                        dstDap.getPort()});
                            }
                            long srcDpid = srcDap.getSwitchDPID();
                            long dstDpid = dstDap.getSwitchDPID();

                            if (srcDpid == dstDpid) {
                                srcSwOutport = pushRoute(route, pi, sw.getId(), wildcard_hints,
                                                            cookie, cntx, requestFlowRemovedNotifn,
                                                            false, OFFlowMod.OFPFC_ADD);
                                if (srcSwOutport != null) 
                                    pinSwitchFound = true;   
                                continue;
                            } 

                            rwHeaders = new LinkedList<byte[]>();
                            matchList = getMatchList(pi, dstDap.getSwitchDPID(), 
                                                     srcDap.getSwitchDPID(), 
                                                     rwHeaders);
                            wildCards_List = new LinkedList<Integer>();

                            wildCards_List.addFirst(wildcard_hints);
                            // match only the pathID except at the source switch
                            wildcard_hints = new Integer(wildcard_hints);
                            wildcard_hints = wildcard_hints.intValue() | OFMatch.OFPFW_DL_VLAN
                                             | OFMatch.OFPFW_NW_SRC_MASK
                                             | OFMatch.OFPFW_NW_DST_MASK;
                            wildCards_List.addFirst(wildcard_hints);
                            srcSwOutport = pushCircuit(route, matchList, wildCards_List, sw.getId(),
                                                        pi, cookie, cntx, requestFlowRemovedNotifn,
                                                        false, OFFlowMod.OFPFC_ADD, rwHeaders);
                            if (srcSwOutport != null)
                                pinSwitchRWHeaders = rwHeaders;
                                pinSwitchFound = true;
                        }
                    }
                    iSrcDaps++;
                    iDstDaps++;
                } else if (srcVsDest < 0) {
                    iSrcDaps++;
                } else {
                    iDstDaps++;
                }
            }
            //TODO need to push the packet here
            if (pinSwitchFound && (pinSwitchRWHeaders != null)) {
                if (modifyDLHeaders(pi, pinSwitchRWHeaders))
                    pushPacket(sw, pi, false, (short) srcSwOutport.intValue(), cntx);
            } else if (pinSwitchFound){
                pushPacket(sw, pi, false, (short) srcSwOutport.intValue(), cntx);
            }
        } else {
            // Flood since we don't know the dst device
            doFlood(sw, pi, cntx);
        }
    }

    protected void doFlood(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx)
    {
        log.info("Flooding the broadcast packet {}", pi);
        if (topology.isIncomingBroadcastAllowed(sw.getId(),
                                                pi.getInPort()) == false) {
            if (log.isTraceEnabled()) {
                log.trace("doFlood, drop broadcast packet, pi={}, " + 
                          "from a blocked port, srcSwitch=[{},{}], linkInfo={}",
                          new Object[] {pi, sw.getId(),pi.getInPort()});
            }
            return;
        }

        // Set Action to flood
        OFPacketOut po = 
            (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        List<OFAction> actions = new ArrayList<OFAction>();
        if (sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)) {
            actions.add(new OFActionOutput(OFPort.OFPP_FLOOD.getValue(), 
                                           (short)0xFFFF));
        } else {
            actions.add(new OFActionOutput(OFPort.OFPP_ALL.getValue(), 
                                           (short)0xFFFF));
        }
        po.setActions(actions);
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set buffer-id, in-port and packet-data based on packet-in
        short poLength = (short)(po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(pi.getInPort());
        byte[] packetData = pi.getPacketData();
        poLength += packetData.length;
        po.setPacketData(packetData);
        po.setLength(poLength);
        
        try {
            if (log.isTraceEnabled()) {
                log.trace("Writing flood PacketOut switch={} packet-in={} packet-out={}",
                          new Object[] {sw, pi, po});
            }
            messageDamper.write(sw, po, cntx);
        } catch (IOException e) {
            log.error("Failure writing PacketOut switch={} packet-in={} packet-out={}",
                    new Object[] {sw, pi, po}, e);
        }            

        return;
    }
    private String PathIDStoreName;
}
