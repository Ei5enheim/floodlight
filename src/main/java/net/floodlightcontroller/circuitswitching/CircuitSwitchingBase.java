/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.circuitswitching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.circuitswitching.ICircuitSwitching;
import net.floodlightcontroller.circuitswitching.CircuitIDGen;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.OFMessageDamper;
import net.floodlightcontroller.util.TimedCache;
import net.floodlightcontroller.util.DelegatedMAC;
import net.floodlightcontroller.keyvaluestore.IKeyValueStoreService;

import org.openflow.protocol.OFType;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayer;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;

import org.renci.doe.pharos.flow.Rules;
import org.renci.doe.pharos.flow.Rule;

import java.util.Comparator;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.io.IOException;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Queue;
import java.util.PriorityQueue;

public abstract class CircuitSwitchingBase implements ICircuitSwitching,
                                             IOFMessageListener,
                                  	     IOFSwitchListener
{
    protected static Logger logger = LoggerFactory.getLogger(CircuitSwitchingBase.class);

    protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; // TODO: find sweet spot
    protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms 
    public static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
    public static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite

    protected IFloodlightProviderService floodlightProvider;
    protected IRestApiService restAPIService;
    protected IRoutingService routingEngine;
    protected IDeviceService deviceManager;
    protected ITopologyService topology;
    protected ICounterStoreService counterStore;
    protected IKeyValueStoreService kvStoreClient;

    protected OFMessageDamper messageDamper;

    protected Queue<DelegatedMAC> blockedSrcMACList;

    // for broadcast loop suppression
    protected boolean broadcastCacheFeature = true;
    public final int prime1 = 2633;  // for hash calculation
    public final static int prime2 = 4357;  // for hash calculation
    public TimedCache<Long> broadcastCache =
        new TimedCache<Long>(100, 5*1000);  // 5 seconds interval;

    // flow-mod - for use in the cookie
    public static final int FORWARDING_APP_ID = 2; // TODO: This must be managed
                                                   // by a global APP_ID class
    public long appCookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

    // Comparator for sorting by SwitchCluster
    public Comparator<SwitchPort> clusterIdComparator =
            new Comparator<SwitchPort>() {
                @Override
                public int compare(SwitchPort d1, SwitchPort d2) {
                    Long d1ClusterId =
                            topology.getL2DomainId(d1.getSwitchDPID());
                    Long d2ClusterId =
                            topology.getL2DomainId(d2.getSwitchDPID());
                    return d1ClusterId.compareTo(d2ClusterId);
                }
            };

    public String getName()
    {
        return ("CircuitSwitchingBase");
    }

    public boolean isCallbackOrderingPrereq(OFType type, String name)
    {
        return (type.equals(OFType.PACKET_IN) && 
                (name.equals("topology") ||
                 name.equals("devicemanager")));
    }

    public boolean isCallbackOrderingPostreq(OFType type, String name)
    {
        return (false);
    }

    /******************

     IOFSwitchListener

     ******************/

    public void addedSwitch (IOFSwitch sw)
    {
        long dpID = sw.getId();
        CircuitIDGen obj = new CircuitIDGen(); 

        if (obj == null) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to allocate new object. System Ran out"
                         + "of memory. switch DPID {}", dpID);
            else
                logger.info("Unable to allocate new object. System Ran out"
                         + "of memory. switch DPID {}", dpID); 
            return;
        } 
        activeSwitches.put(dpID, obj); 
    }

    // we can safely remove the switch without
    // any locks as controller takes care of the
    // mutual exclusion
    public void removedSwitch (IOFSwitch sw)
    {
        long dpID = sw.getId();
        if (activeSwitches.remove(dpID) == null) {
            if (logger.isDebugEnabled())
                logger.debug("No circuitIDGen object found for "
                         + "switch {}", dpID);
            else
                logger.info("No circuitIDGen object found for "
                         + "switch {}", dpID);
        }      
    }
  
    // leave it for now 
    public void switchPortChanged (java.lang.Long switchId) 
    {

    }

    public void addBlockedSrcMACList (List<DelegatedMAC> list)
    {   
        synchronized (blockedSrcMACList) {
            for (DelegatedMAC mac: list) {
                if (!blockedSrcMACList.contains(mac))
                    blockedSrcMACList.add(mac);
            }
        }
    }

    // ICircuitSwitchingService
    public LinkedList<byte[]> getPathID (long srcID, long dstID, long cookie) throws Exception
    {
        boolean isSrcRooted = false;
        CircuitIDGen cidGen = null;

        isSrcRooted = (cookie & ISSRCROOTED_MSK) > 0;
        if (isSrcRooted) {
            cidGen = activeSwitches.get(srcID); 
        } else {
            cidGen = activeSwitches.get(dstID);
        }
    
        if (cidGen == null) {
            if (logger.isDebugEnabled())
                logger.debug("No circuitIDGen object found for "
                         + "switch {} {}", srcID, dstID);
            else
                logger.info("No circuitIDGen object found for "
                         + "switch {} {}", srcID, dstID);
            return (null);
        }
        long circuitID = getAvailableSrcMAC(cidGen);
        
        return (getBytes(dstID, srcID, circuitID, isSrcRooted));
    }


    private long getAvailableSrcMAC (CircuitIDGen cidGen) throws Exception
    {
        long circuitID = 0L;
        DelegatedMAC mac = new DelegatedMAC();    
        int indx = 0;   
 
        circuitID = cidGen.getCircuitID();
        mac.setBaseAddress(circuitID);
        Object[] array = blockedSrcMACList.toArray();
        for (indx = 0; indx < array.length; indx++) {
            DelegatedMAC match = (DelegatedMAC) array[indx];
            if (match.equals(mac)) {
                int endBit = match.getEnd();
                long val = 1L << endBit + 1;
                circuitID += val;
                mac.setBaseAddress(circuitID);
            }
        }
        return circuitID;
    }

    // IOFMessageListener methods

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx)
    {
        switch (msg.getType()) {
            case PACKET_IN:
                IRoutingDecision decision = null;
                if (cntx != null)
                     decision =
                             IRoutingDecision.rtStore.get(cntx,
                                                          IRoutingDecision.CONTEXT_DECISION);

                return this.processPacketInMessage(sw,
                                                   (OFPacketIn) msg,
                                                   decision,
                                                   cntx);
            default:
                break;
        }
        return Command.CONTINUE;
    }

    // internal routines

    /**
     * All subclasses must define this function if they want any specific
     * forwarding action
     * 
     * @param sw
     *            Switch that the packet came in from
     * @param pi
     *            The packet that came in
     * @param decision
     *            Any decision made by a policy engine
     */
    public abstract Command
            processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                   IRoutingDecision decision,
                                   FloodlightContext cntx);

    /**
     * Push routes from back to front
     * @param route Route to push
     * @param match OpenFlow fields to match on
     * @param srcSwPort Source switch port for the first hop
     * @param dstSwPort Destination switch port for final hop
     * @param cookie The cookie to set in each flow_mod
     * @param cntx The floodlight context
     * @param reqeustFlowRemovedNotifn if set to true then the switch would
     * send a flow mod removal notification when the flow mod expires
     * @param doFlush if set to true then the flow mod would be immediately
     *        written to the switch
     * @param flowModCommand flow mod. command to use, e.g. OFFlowMod.OFPFC_ADD,
     *        OFFlowMod.OFPFC_MODIFY etc.
     * @return srcSwitchIincluded True if the source switch is included in this route
     */

    public Integer pushCircuit(Route route, LinkedList<OFMatch> matchList, 
                                LinkedList<Integer> wHintsList,
                                long pinSwitch,
                                OFPacketIn pi,
                                long cookie, 
                                FloodlightContext cntx,
                                boolean reqeustFlowRemovedNotifn,
                                boolean doFlush,
                                short flowModCommand,
                                LinkedList<byte[]> rwHeaders)
    {
        boolean srcSwitchIncluded = false;
        OFMatch match = null;
        int index = 0;
        int outputActionIndex = 0;
        Integer wildcard_hints = 0;
        OFFlowMod fm_backup = null;
        Integer sourceSwOutport = null;
    
        OFFlowMod fm =
                (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                              .getMessage(OFType.FLOW_MOD);
        OFActionOutput action = new OFActionOutput();
        action.setMaxLength((short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        if ((matchList != null) && (!matchList.isEmpty())) {
            match = matchList.get(index);
        } else {
            if (logger.isDebugEnabled())
                logger.debug("MatchList is empty");
            //return (srcSwitchIncluded);
            return (sourceSwOutport);
        }

        if ((wHintsList != null) && (!wHintsList.isEmpty())) {
            wildcard_hints = wHintsList.get(index);
        } else {
            logger.debug("WildCard hints list is empty");
            //return (srcSwitchIncluded);
            return (sourceSwOutport);
        }

        fm.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
            .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setCookie(cookie)
            .setCommand(flowModCommand)
            //.setMatch(match)
            .setActions(actions)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);

        List<NodePortTuple> switchPortList = route.getPath();

        for (int indx = switchPortList.size()-1; indx > 0; indx -= 2) {
            // indx and indx-1 will always have the same switch DPID.
            long switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = floodlightProvider.getSwitches().get(switchDPID);
            if (sw == null) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Unable to push circuit, switch at DPID {} " +
                            "not available", switchDPID);
                }
                //return srcSwitchIncluded;
                return (sourceSwOutport);
            }
            //need to reset it after each iteration
            outputActionIndex = 0;
            // set the match.
            fm.setMatch(wildcard(match, sw, wildcard_hints));

            // set buffer id if it is the source switch
            if (1 == indx) {
                match = matchList.get(index);
                wildcard_hints = wHintsList.get(index); 
                fm.setMatch(wildcard(match, sw, wildcard_hints));
                index++;
                // Set the flag to request flow-mod removal notifications only for the
                // source switch. The removal message is used to maintain the flow
                // cache. Don't set the flag for ARP messages - TODO generalize check
                if ((reqeustFlowRemovedNotifn)
                        && (match.getDataLayerType() != Ethernet.TYPE_ARP)) {
                    fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
                    match.setWildcards(fm.getMatch().getWildcards());
                }
                OFActionDataLayerDestination dstMACRW =
                                    new OFActionDataLayerDestination(rwHeaders.get(index));
                OFActionDataLayerSource srcMACRW =
                                    new OFActionDataLayerSource(rwHeaders.get(index+1));
                // Need to move the output action to the bottom
                fm.getActions().add(0, dstMACRW);
                fm.getActions().add(0, srcMACRW);
                outputActionIndex += 2;
                fm.setLengthU(OFFlowMod.MINIMUM_LENGTH +
                              OFActionOutput.MINIMUM_LENGTH +
                              2*OFActionDataLayer.MINIMUM_LENGTH);
            } else if ((switchPortList.size()-1) == indx) {
                OFActionDataLayerDestination dstMACRW =
                                    new OFActionDataLayerDestination(rwHeaders.get(index));
                OFActionDataLayerSource srcMACRW =
                                    new OFActionDataLayerSource(rwHeaders.get(index+1));
                try {         
                    fm_backup = fm.clone();
                } catch (CloneNotSupportedException e) {
                    logger.error("Failure cloning flow mod {}", e);
                }
                fm.getActions().add(0, dstMACRW);
                fm.getActions().add(0, srcMACRW); 
                outputActionIndex += 2;
                fm.setLengthU(OFFlowMod.MINIMUM_LENGTH +
                              OFActionOutput.MINIMUM_LENGTH + 
                              2*OFActionDataLayer.MINIMUM_LENGTH);
                index++;
            }

            short outPort = switchPortList.get(indx).getPortId();
            short inPort = switchPortList.get(indx-1).getPortId();
            // set input and output ports on the switch
            fm.getMatch().setInputPort(inPort);
            ((OFActionOutput)fm.getActions().get(outputActionIndex)).setPort(outPort);

            try {
                counterStore.updatePktOutFMCounterStoreLocal(sw, fm);
                if (logger.isTraceEnabled()) {
                    logger.trace("Pushing Circuit flowmod routeIndx={} " + 
                            "sw={} inPort={} outPort={}, flowmod {}",
                            new Object[] {indx,
                                          sw,
                                          fm.getMatch().getInputPort(),
                                          outPort, fm.toString()});
                }
                messageDamper.write(sw, fm, cntx);
                if (doFlush) {
                    sw.flush();
                    counterStore.updateFlush();
                }
                if (sw.getId() == pinSwitch) {
                    sourceSwOutport = new Integer(outPort);
                    srcSwitchIncluded = true;
                }
            } catch (IOException e) {
                logger.error("Failure writing flow mod", e);
            }
    
            if ((switchPortList.size()-1) == indx) {
                fm = fm_backup;
            } else {
                try {
                    fm = fm.clone();
                } catch (CloneNotSupportedException e) {
                    logger.error("Failure cloning flow mod", e);
                }
            }
        }
        return (sourceSwOutport);
    }

    private OFPacketIn translatePktHeader (OFPacketIn pi)
    {
        return (pi);
    }

    /*public Integer pushRoute (Route route, OFPacketIn pi, 
                                long pinSwitch, Integer wildcard_hints,
                                long cookie,
                                FloodlightContext cntx,
                                boolean reqeustFlowRemovedNotifn,
                                boolean doFlush,
                                short flowModCommand)
    {
        OFMatch match = new OFMatch();
        boolean srcSwitchIncluded = false;
        Integer sourceSwOutport = null;
        OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                                        .getMessage(OFType.FLOW_MOD);
        OFActionOutput action = new OFActionOutput();
        ArrayList<OFFlowMod> fmList = new ArrayList<OFFlowMod>();
        action.setMaxLength((short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        fm.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
            .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setCookie(cookie)
            .setCommand(flowModCommand)
            .setMatch(match)
            .setActions(actions)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH); 

        List<NodePortTuple> switchPortList = route.getPath();
        for (int indx = switchPortList.size()-1; indx > 0; indx -= 2) {
            // indx and indx-1 will always have the same switch DPID.
            long switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = floodlightProvider.getSwitches().get(switchDPID);
            if (sw == null) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Unable to push route, switch at DPID {} " +
                            "not available", switchDPID);
                }
                return (sourceSwOutport);
            }

            // set the match.
            fm.setMatch(wildcard(match, sw, wildcard_hints));

            // set buffer id if it is the source switch
            if (1 == indx) {
                // Set the flag to request flow-mod removal notifications only for the
                // source switch. The removal message is used to maintain the flow
                // cache. Don't set the flag for ARP messages - TODO generalize check
                if ((reqeustFlowRemovedNotifn)
                        && (match.getDataLayerType() != Ethernet.TYPE_ARP)) {
                    fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
                    match.setWildcards(fm.getMatch().getWildcards());
                }
            }

            short outPort = switchPortList.get(indx).getPortId();
            short inPort = switchPortList.get(indx-1).getPortId();
            // set input and output ports on the switch
            fm.getMatch().setInputPort(inPort);
            ((OFActionOutput)fm.getActions().get(0)).setPort(outPort);

            try {
                counterStore.updatePktOutFMCounterStoreLocal(sw, fm);
                if (logger.isTraceEnabled()) {
                    logger.trace("Pushing Route flowmod routeIndx={} " + 
                            "sw={} inPort={} outPort={}",
                            new Object[] {indx,
                                          sw,
                                          fm.getMatch().getInputPort(),
                                          outPort });
                }
                messageDamper.write(sw, fm, cntx);
                if (doFlush) {
                    sw.flush();
                    counterStore.updateFlush();
                }

                // Push the packet out of the source switch
                if (sw.getId() == pinSwitch) {
                    sourceSwOutport = new Integer(outPort);
                    srcSwitchIncluded = true;
                }
            } catch (IOException e) {
                logger.error("Failure writing flow mod", e);
            }

            try {
                fm = fm.clone();
            } catch (CloneNotSupportedException e) {
                logger.error("Failure cloning flow mod", e);
            }
        }
        return (sourceSwOutport);
    }*/

    private boolean setActionOutputFlowMod (OFFlowMod fm,
                                            List<NodePortTuple> switchPortList,
                                            int wildcard_hints,
                                            long pinSwitch,
                                            Integer sourceSwOutport,
                                            int indx)
    {
        // indx and indx-1 will always have the same switch DPID.
        long switchDPID = switchPortList.get(indx).getNodeId();
        IOFSwitch sw = floodlightProvider.getSwitches().get(switchDPID);
        if (sw == null) {
            if (logger.isWarnEnabled()) {
                logger.warn("Unable to push route, switch at DPID {} " +
                        "not available", switchDPID);
            }
            return (false);
        }

        short outPort = switchPortList.get(indx).getPortId();
        short inPort = switchPortList.get(indx-1).getPortId();
        // set input and output ports on the switch
        fm.getMatch().setInputPort(inPort);
        ((OFActionOutput)fm.getActions().get(0)).setPort(outPort);
        fm.setMatch(wildcard(fm.getMatch(), sw, wildcard_hints));

        if (sw.getId() == pinSwitch) {
            sourceSwOutport = new Integer(outPort);
        }   
        return (true);
    }

    public Integer pushRoute (Route route, OFPacketIn pi, 
                                long pinSwitch, Integer wildcard_hints,
                                long cookie,
                                FloodlightContext cntx,
                                boolean reqeustFlowRemovedNotifn,
                                boolean doFlush,
                                short flowModCommand)
    {
        byte[] packetData = Arrays.copyOf(pi.getPacketData(), 
                                            pi.getPacketData().length);
        OFMatch match = new OFMatch();
        Integer sourceSwOutport = null;
        Rules table = null;
        Link link = null;
        OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                                     .getMessage(OFType.FLOW_MOD);
        OFFlowMod srcSwFm = null;
        OFActionOutput action = new OFActionOutput();
        LinkedList<OFFlowMod> fmList = new LinkedList<OFFlowMod>();
        action.setMaxLength((short)0xffff);
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

        match.loadFromPacket(packetData, pi.getInPort());
        fm.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
            .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setCookie(cookie)
            .setCommand(flowModCommand)
            .setMatch(match)
            .setActions(actions)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH); 
        
        srcSwFm = fm;
        if ((reqeustFlowRemovedNotifn)
                && (match.getDataLayerType() != Ethernet.TYPE_ARP)) {
            try {
                srcSwFm = fm.clone();
            } catch (CloneNotSupportedException e) {
                logger.error("Failure cloning flow mod", e);
            }
            srcSwFm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
            //match.setWildcards(srcSwFm.getMatch().getWildcards());
        }

        List<NodePortTuple> switchPortList = route.getPath();
        setActionOutputFlowMod (srcSwFm, switchPortList,
                                    wildcard_hints, pinSwitch,
                                    sourceSwOutport, 1); 
        fmList.addFirst(srcSwFm);

        for (int indx = 2; indx < switchPortList.size()-1; indx += 2) {
            // indx and indx+1 will always have the same switch DPID.

			// This is the link between the present node and the downstream node
            link = new Link(switchPortList.get(indx-1).getNodeId(),
                            switchPortList.get(indx-1).getPortId(),
                            switchPortList.get(indx).getNodeId(),
                            switchPortList.get(indx).getPortId());
            table = floodlightProvider.getLinkRuleTransTable(link);

            if (table == null) {
                logger.trace("Non-Virtual Link {}", link);
            } else {
                packetData = table.getPacketHeader(packetData);
            }

            fm.getMatch().loadFromPacket(packetData, (short)0x0000);
            setActionOutputFlowMod (srcSwFm, switchPortList,
                                        wildcard_hints, pinSwitch,
                                        sourceSwOutport, 1);
            fmList.addFirst(fm);
            
            try {
                fm = fm.clone();
            } catch (CloneNotSupportedException e) {
                logger.error("Failure cloning flow mod", e);
            }
        }

        pushFlowMods(switchPortList, cntx, fmList, doFlush);
        return (sourceSwOutport);
    }

    private void pushFlowMods (List<NodePortTuple> switchPortList,
                                FloodlightContext cntx,                            
                                List<OFFlowMod> fmList,
                                boolean doFlush)
    {
        OFFlowMod fm = null;
        int i = 0;
        for (int indx = switchPortList.size() -1; indx > 0; indx -=2) {
            long switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = floodlightProvider.getSwitches().get(switchDPID);
            if (sw == null) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Unable to push route, switch at DPID {} " +
                            "not available", switchDPID);
                }
                return;
            }
            fm = fmList.get(i++); 
            try {
                counterStore.updatePktOutFMCounterStoreLocal(sw, fm);
                if (logger.isTraceEnabled()) {
                    logger.trace("Pushing Route flowmod routeIndx={} " + 
                            "sw={} inPort={}",
                            new Object[] {indx,
                            sw,
                            fm.getMatch().getInputPort()});
                }
                messageDamper.write(sw, fm, cntx);
                if (doFlush) {
                    sw.flush();
                    counterStore.updateFlush();
                }
            } catch (IOException e) {
                logger.error("Failure writing flow mod", e);
            }
        }
    }

    protected boolean modifyDLHeaders (OFPacketIn pi, LinkedList<byte[] > list)
    {
        int macAddrLen = 6;
        byte[] packetData = pi.getPacketData();
        byte[] mac = list.get(list.size()-2);   

        if (packetData == null)
            return (false);

        for (int i = 0; i < 2*macAddrLen; i++)
        {
            if (i == macAddrLen)
                mac = list.getLast();
            packetData[i] = mac[i%6]; 
        }

        pi.setPacketData(packetData);
        logger.trace("modified the packet {}", pi.toString());
        return (true);
    }

    protected OFMatch wildcard(OFMatch match, IOFSwitch sw,
                                Integer wildcard_hints)
    {
        if (wildcard_hints != null) {
            return match.clone().setWildcards(wildcard_hints.intValue());
        }
        return match.clone();
    }

    /**
     * Pushes a packet-out to a switch.  The assumption here is that
     * the packet-in was also generated from the same switch.  Thus, if the input
     * port of the packet-in and the outport are the same, the function will not 
     * push the packet-out.
     * @param sw        switch that generated the packet-in, and from which packet-out is sent
     * @param pi        packet-in
     * @param useBufferId  if true, use the bufferId from the packet in and 
     * do not add the packetIn's payload. If false set bufferId to 
     * BUFFER_ID_NONE and use the packetIn's payload 
     * @param outport   output port
     * @param cntx      context of the packet
     */
    protected void pushPacket(IOFSwitch sw, OFPacketIn pi, 
            boolean useBufferId, 
            short outport, FloodlightContext cntx)
    {
        if (pi == null) {
            return;
        }
        // The assumption here is (sw) is the switch that generated the 
        // packet-in. If the input port is the same as output port, then
        // the packet-out should be ignored.
        //$$ disabling the check for now. \\
        if (pi.getInPort() == outport) {
            if (logger.isDebugEnabled()) {
                logger.debug("Attempting to do packet-out to the same " + 
                        "interface as packet-in. Dropping packet. " + 
                        " SrcSwitch={}, pi={}", 
                        new Object[]{sw, pi});
                return;
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace("PacketOut srcSwitch={} pi={}", 
                    new Object[] {sw, pi});
        }
        OFPacketOut po =
            (OFPacketOut) floodlightProvider.getOFMessageFactory()
            .getMessage(OFType.PACKET_OUT);
        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(outport, (short) 0xffff));

        po.setActions(actions)
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
        short poLength =
            (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);

        if (useBufferId) {
            po.setBufferId(pi.getBufferId());
        } else {
            po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        }

        if (po.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }
        po.setInPort(pi.getInPort());
        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, po);
            messageDamper.write(sw, po, cntx);
        } catch (IOException e) {
            logger.error("Failure writing packet out", e);
        }
    }

    /**
     * Write packetout message to sw with output actions to one or more
     * output ports with inPort/outPorts passed in.
     * @param packetData
     * @param sw
     * @param inPort
     * @param ports
     * @param cntx
     */
    public void packetOutMultiPort(byte[] packetData,
            IOFSwitch sw,
            short inPort,
            Set<Integer> outPorts,
            FloodlightContext cntx) {
        //setting actions
        List<OFAction> actions = new ArrayList<OFAction>();

        Iterator<Integer> j = outPorts.iterator();

        while (j.hasNext())
        {
            actions.add(new OFActionOutput(j.next().shortValue(), 
                        (short) 0));
        }

        OFPacketOut po = 
            (OFPacketOut) floodlightProvider.getOFMessageFactory().
            getMessage(OFType.PACKET_OUT);
        po.setActions(actions);
        po.setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH * 
                    outPorts.size()));

        // set buffer-id to BUFFER_ID_NONE, and set in-port to OFPP_NONE
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(inPort);

        // data (note buffer_id is always BUFFER_ID_NONE) and length
        short poLength = (short)(po.getActionsLength() + 
                OFPacketOut.MINIMUM_LENGTH);
        poLength += packetData.length;
        po.setPacketData(packetData);
        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, po);
            if (logger.isTraceEnabled()) {
                logger.trace("write broadcast packet on switch-id={} " + 
                        "interfaces={} packet-out={}",
                        new Object[] {sw.getId(), outPorts, po});
            }
            messageDamper.write(sw, po, cntx);

        } catch (IOException e) {
            logger.error("Failure writing packet out", e);
        }
    }

    /**                            
     * @see packetOutMultiPort     
     * Accepts a PacketIn instead of raw packet data. Note that the inPort
     * and switch can be different than the packet in switch/port
     */ 
    public void packetOutMultiPort(OFPacketIn pi,
            IOFSwitch sw,
            short inPort,
            Set<Integer> outPorts,
            FloodlightContext cntx) {
        packetOutMultiPort(pi.getPacketData(), sw, inPort, outPorts, cntx);
    }

    /** 
     * @see packetOutMultiPort
     * Accepts an IPacket instead of raw packet data. Note that the inPort
     * and switch can be different than the packet in switch/port
     */
    public void packetOutMultiPort(IPacket packet,
            IOFSwitch sw,
            short inPort,
            Set<Integer> outPorts,
            FloodlightContext cntx) {
        packetOutMultiPort(packet.serialize(), sw, inPort, outPorts, cntx);
    }

    protected boolean isInBroadcastCache(IOFSwitch sw, OFPacketIn pi,
            FloodlightContext cntx) {
        // Get the cluster id of the switch.
        // Get the hash of the Ethernet packet.
        if (sw == null) return true;  

        // If the feature is disabled, always return false;
        if (!broadcastCacheFeature) return false;

        Ethernet eth = 
            IFloodlightProviderService.bcStore.get(cntx,
                    IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        Long broadcastHash;
        broadcastHash = topology.getL2DomainId(sw.getId()) * prime1 +
            pi.getInPort() * prime2 + eth.hashCode();
        if (broadcastCache.update(broadcastHash)) {
            sw.updateBroadcastCache(broadcastHash, pi.getInPort());
            return true;
        } else {
            return false;
        }
    }

    protected boolean isInSwitchBroadcastCache(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        if (sw == null) return true;

        // If the feature is disabled, always return false;
        if (!broadcastCacheFeature) return false;

        // Get the hash of the Ethernet packet.
        Ethernet eth =
            IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        long hash =  pi.getInPort() * prime2 + eth.hashCode();

        // some FORWARD_OR_FLOOD packets are unicast with unknown destination mac
        return sw.updateBroadcastCache(hash, pi.getInPort());
    }

    public static boolean
        blockHost(IFloodlightProviderService floodlightProvider,
                SwitchPort sw_tup, long host_mac,
                short hardTimeout, long cookie) {

            if (sw_tup == null) {
                return false;
            }

            IOFSwitch sw = 
                floodlightProvider.getSwitches().get(sw_tup.getSwitchDPID());
            if (sw == null) return false;
            int inputPort = sw_tup.getPort();
            logger.debug("blockHost sw={} port={} mac={}",
                    new Object[] { sw, sw_tup.getPort(), new Long(host_mac) });

            // Create flow-mod based on packet-in and src-switch
            OFFlowMod fm =
                (OFFlowMod) floodlightProvider.getOFMessageFactory()
                .getMessage(OFType.FLOW_MOD);
            OFMatch match = new OFMatch();
            List<OFAction> actions = new ArrayList<OFAction>(); // Set no action to
            // drop
            match.setDataLayerSource(Ethernet.toByteArray(host_mac))
                .setInputPort((short)inputPort)
                .setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_SRC
                        & ~OFMatch.OFPFW_IN_PORT);
            fm.setCookie(cookie)
                .setHardTimeout(hardTimeout)
                .setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
                .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
                .setBufferId(OFPacketOut.BUFFER_ID_NONE)
                .setMatch(match)
                .setActions(actions)
                .setLengthU(OFFlowMod.MINIMUM_LENGTH); // +OFActionOutput.MINIMUM_LENGTH);

            try {
                logger.debug("write drop flow-mod sw={} match={} flow-mod={}",
                        new Object[] { sw, match, fm });
                // TODO: can't use the message damper sine this method is static
                sw.write(fm, null);
            } catch (IOException e) {
                logger.error("Failure writing deny flow mod", e);
                return false;
            }
            return true;

        }

    LinkedList<byte[]> getBytes (long dstID, long srcID,
            long circuitID, boolean isSrcRooted)
    {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        long switchMAC = -1;
        byte array[] = null;
        int index = 0;
        LinkedList<byte[]> rv = new LinkedList<byte[]>();
        byte[] result = new byte[6];

        // Need to set the locally admin bit and the unicast bit 
        circuitID = (circuitID | LAdminMAC_MSK) & UCAST_MSK;

        if (isSrcRooted) {
            switchMAC = srcID & DPID_MSK;
            //first destination then source MAC
            buffer.putLong(circuitID);
            buffer.putLong(switchMAC);
        } else {
            switchMAC = dstID & DPID_MSK;
            buffer.putLong(switchMAC);            
            buffer.putLong(circuitID);
        }
        array = buffer.array();
        for (int i = 2; i < 8; i++)
            result[index++] = array[i];
        rv.add(result);
        result = new byte[6];
        index = 0; 
        for (int i = 10; i < 16; i++)
            result[index++] = array[i];
        rv.add(result);
        return (rv);
    }

    // init data structures
    protected void init()
    {
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                EnumSet.of(OFType.FLOW_MOD),
                OFMESSAGE_DAMPER_TIMEOUT);
        activeSwitches = new ConcurrentHashMap<Long, CircuitIDGen>();
        blockedSrcMACList = new PriorityQueue<DelegatedMAC> (); 
    } 

    public void startUp()
    {
        //register for switch updates
        floodlightProvider.addOFSwitchListener(this);
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

    private final long ISSRCROOTED_MSK = 0x1L;
    private final long DPID_MSK = 0x0000FFFFFFFFFFFFL;
    private final long LAdminMAC_MSK = 0x0000020000000000L;
    private final long UCAST_MSK = 0xFFFFFEFFFFFFFFFFL;
    protected ConcurrentHashMap<Long, CircuitIDGen> activeSwitches;
}
