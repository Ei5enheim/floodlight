/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topovalidation.internal;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.renci.doe.pharos.flow.Rules;
import org.renci.doe.pharos.flow.Rule;

import org.openflow.protocol.OFType;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionDataLayer;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;

import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.topology.IOFFlowspace;
import net.floodlightcontroller.topovalidation.ITopoValidationService;
import net.floodlightcontroller.topovalidation.internal.NodePortTuplePlusPkt;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.topovalidation.internal.TopoLock;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.util.IPv4Address;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.util.OFMessageDamper;
import net.floodlightcontroller.topovalidation.internal.TopoLock;

public class TopologyValidationSrvImpl implements ITopoValidationService,
                                                IFloodlightModule,
                                                IOFMessageListener
{
    protected static Logger log = LoggerFactory.getLogger(TopologyValidationSrvImpl.class);
    protected IFloodlightProviderService floodlightProvider;
    protected ConcurrentHashMap<NodePortTuplePlusPkt, TopoLock> map;
    protected ScheduledExecutorService ses;
    protected ILinkDiscoveryService linkInfo;
    protected OFMessageDamper messageDamper;
    protected ICounterStoreService counterStore;
    protected IThreadPoolService threadPool;

    /**************************
        IMessageListeners
     *************************/
    public String getName()
    {
        return "topologyvalidation";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name)
    {
        return (false);
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name)
    {
        // TODO Auto-generated method stub
        return (type.equals(OFType.PACKET_IN)); 
    }

    /**************************
      IFloodlightModule
     *************************/
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices()
    {
        Collection <Class<? extends IFloodlightService>> list =
                            new ArrayList<Class<? extends IFloodlightService>>();

        list.add(ITopoValidationService.class);
        return (list);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls()
    {
        Map <Class<? extends IFloodlightService>, IFloodlightService> map =
                            new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();

        map.put(ITopoValidationService.class, this);
        return (map);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies()
    {
        Collection<Class <? extends IFloodlightService>> collection =
                                new ArrayList<Class<? extends IFloodlightService>>();
        collection.add(IFloodlightProviderService.class);
        //collection.add(ITopologyService.class);
        collection.add(ICounterStoreService.class);
        collection.add(ILinkDiscoveryService.class);
        return (collection);
    }

    @Override
    public void init (FloodlightModuleContext context) throws FloodlightModuleException
    {
        this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        this.threadPool = context.getServiceImpl(IThreadPoolService.class);
        this.linkInfo = context.getServiceImpl(ILinkDiscoveryService.class);
        this.messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                                            EnumSet.of(OFType.PACKET_OUT),
                                            OFMESSAGE_DAMPER_TIMEOUT);
        this.counterStore = context.getServiceImpl(ICounterStoreService.class);
        this.map = new ConcurrentHashMap<NodePortTuplePlusPkt, TopoLock> ();
    }

    @Override
    public void startUp (FloodlightModuleContext context)
    {
        ses = threadPool.getScheduledExecutor();
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

    // IOFMessageListener Interface
    public Command receive (IOFSwitch sw, OFMessage msg,
                            FloodlightContext cntx)
    {
		
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                    IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        OFPacketIn pktIn = (OFPacketIn) msg;
        NodePortTuplePlusPkt key = new NodePortTuplePlusPkt(new NodePortTuple(sw.getId(),
                                                                        pktIn.getInPort()),
                                                                		eth);

		log.trace("Received a packet {}", eth);
		//log.trace("*** recvd pktin inport{}, inswitch {} hash {}",
			//new Object[]{pktIn.getInPort(), sw.getId(), key.hashCode()});
	
        if (map.containsKey(key)) {
            TopoLock lock = map.remove(key);
            if (lock != null) {
				log.trace("incrementing count");
                lock.incrVerifiedCnt();
                if (lock.checkValidationStatus()) {
		    		log.trace("Finished validating the topology ");
                    lock.taskComplete();
					synchronized (lock) {
                    	lock.notifyAll();
					}
                }
            }
            return Command.STOP;
        }
        return Command.CONTINUE;
    }

    // Internal utility methods
    private boolean validateLink (Link link,
                                    Rules ruleTransTable,
                                    TopoLock lock)
    {
        OFPacketOut po = null;
        IPacket pkt = null;

        if (log.isDebugEnabled()) {
            log.debug("Validating random flowspace on link {}--> {}",
                      link.getSrc(), link.getDst());
        }

        if (!isDiscoveryAllowed(link.getSrc(),
                                link.getSrcPort())) {
            if (log.isDebugEnabled())
                log.debug("source port of the link {}--> {} is blocked",
                            link.getSrc(), link.getDst());
            return (false);
        }

        if (!isOutgoingDiscoveryAllowed(link.getDst(), 
                                        link.getDstPort())) {
            if (log.isDebugEnabled())
                log.debug("destination port of the link {}--> {} is blocked",
                            link.getSrc(), link.getDst());
            return (false);
        }

        IOFSwitch srcSw = floodlightProvider.getSwitches().get(link.getSrc());
        IOFSwitch dstSw = floodlightProvider.getSwitches().get(link.getDst());
        IOFFlowspace flowspace = srcSw.getPort(link.getSrcPort()).getEgressFlowspace();

        if (flowspace != null)
            pkt = getPacket(srcSw, flowspace, true);
        else
            pkt = getDefaultPacket(srcSw);

        if (pkt == null)
            return false;

		log.trace("Generated packet {}", pkt);

        po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
                                            .getMessage(OFType.PACKET_OUT);
		byte[] packetData = pkt.serialize();
        po.setPacketData(packetData);

		byte[] clonePktData = Arrays.copyOf(packetData, packetData.length);

        if (!pushFlowMod(clonePktData, dstSw, ruleTransTable, link.getDstPort()))
            return (false);

		IPacket clonePkt = new Ethernet().deserialize(clonePktData, 0, clonePktData.length);
	
		log.trace("cloned packet {}", clonePkt);

		NodePortTuplePlusPkt key = new NodePortTuplePlusPkt(new NodePortTuple(link.getDst(), link.getDstPort()), clonePkt);
	
		log.trace("Pushing packet to Destination port: {} switch: {}", link.getDstPort(), link.getDst());

        map.put(key, lock);
        // May be we need to push a flowmod at the destination switch and with specific mac
        // or anyway we have to store the mapper packet for matching when we receive
        sendPacket (srcSw, link.getSrcPort(), po);
        return (true);
    }

    private boolean validatePath (List<NodePortTuple> path,
                                 Map<Link, Rules> ruleTransTables,
                                    TopoLock lock)
    {
        return (true);
    }

    private boolean validateTopology (List<Link> links,
                                     Map<Link, Rules> ruleTransTables,
                                        TopoLock lock)
    {
        for (Link link: links) {
            if(!validateLink(link, ruleTransTables.get(link), lock))
                return (false);
        }
        
        return (true);
    }

    // ITopoValidationService methods
    public TopoLock validateTopology (List<Link> links,
                                     Map<Link, Rules> ruleTransTables,
                                     boolean completeFlowspace)
    {
        TopoLock lock = new TopoLock();

        if (!completeFlowspace) {
			lock.updateTotalCnt(links.size());	
            if (!validateTopology (links, ruleTransTables, lock)) {
                // Aborting the validation part and reseting the lock
                synchronized (map) {
                    map.values().remove(lock);
                }
                return (null);
            }
        }
        return (lock);
    }

    public TopoLock validateLink (Link link,
                                 Rules ruleTransTable,
                                 boolean completeFlowspace)
    {   
         TopoLock lock = new TopoLock();

        if (!completeFlowspace) {
            if (!validateLink (link, ruleTransTable, lock)) {
                synchronized (map) {
                    map.values().remove(lock);
                }
                return (null);       
            }
        }
        return (lock);
    }

    public TopoLock validatePath (List<NodePortTuple> path,
                                 Map<Link, Rules> ruleTransTables,
                                 boolean completeFlowspace)
    {
        TopoLock lock = new TopoLock();

        if (!completeFlowspace) {
            if (!validatePath(path, ruleTransTables, lock)) {
                synchronized (map) {
                    map.values().remove(lock);
                }
                return (null); 
            }
        }
        return (lock);
    }

    // utility methods
    public IPv4 getICMPHeader (IPv4 ipHeader,
                              IOFFlowspace flowspace,
                              boolean isRandom)
    {
        ICMP icmpHeader = new ICMP();

        if (!isRandom) {

        } else {
            icmpHeader.setIcmpType((byte) 8)
                      .setIcmpCode((byte) 0)
                      .setParent(ipHeader);
            ipHeader.setPayload(icmpHeader);
            return (ipHeader);
        }
        return (null);
    }

    public IPv4 getUDPHeader (IPv4 ipHeader,
                              IOFFlowspace flowspace,
                              boolean isRandom)
    {
        Short dstPort = null, srcPort = null;
        UDP udpHeader = new UDP();

        if (!isRandom) {

        } else {
            dstPort = flowspace.getRandomTPDst();
            srcPort = flowspace.getRandomTPSrc();
            if ((dstPort == null) || (srcPort == null))
                return (null);

            udpHeader.setSourcePort(srcPort)
                     .setDestinationPort(dstPort)
                     .setParent(ipHeader);
            ipHeader.setPayload(udpHeader);
        }
        return (ipHeader);
    }

    public IPv4 getTCPHeader (IPv4 ipHeader,
                              IOFFlowspace flowspace,
                              boolean isRandom)
    {
        Short dstPort = null, srcPort = null;
        TCP tcpHeader = new TCP();

        if (!isRandom) {

        } else {
            dstPort = flowspace.getRandomTPDst();
            srcPort = flowspace.getRandomTPSrc();

            if (dstPort == null)
                dstPort = STANDARD_TP_DST;

            if (srcPort == null)
                srcPort = STANDARD_TP_SRC;

            tcpHeader.setSourcePort(srcPort)
                     .setDestinationPort(dstPort)
                     .setParent(ipHeader);
            ipHeader.setPayload(tcpHeader);
        }
        return (ipHeader);
    }

    public IPv4 getIPv4Payload (short networkProtocol,
                                IPv4 ipHeader,
                                IOFFlowspace flowspace,
                                boolean isRandom)
    {
        switch (networkProtocol) {
            case (0x6):
                ipHeader = getTCPHeader(ipHeader, flowspace, isRandom);
                break;
            case (0x11):
                ipHeader = getUDPHeader(ipHeader, flowspace, isRandom);
                break;
            case (0x01):
                ipHeader = getICMPHeader(ipHeader, flowspace, isRandom);
                break;
            default:
                log.debug("unkown network protocol");
        }
        return (ipHeader);
    }

    public Ethernet getIPv4PacketHeader (Ethernet ethernet,
                                        IOFFlowspace flowspace,
                                        boolean isRandom)
    {
        short networkProtocol = -1;
        IPv4Address srcIP = null, dstIP = null;
        int src, dst;

        if (!isRandom) {

        } else {
            networkProtocol = flowspace.getRandomNetworkProtocol();
            if (networkProtocol == -1)
                networkProtocol = IPv4.PROTOCOL_TCP;

            srcIP = flowspace.getRandomNetworkDestination();
            dstIP = flowspace.getRandomNetworkSource();

            if (dstIP == null)
                dst = STANDARD_IPV4_DST_ADDRESS;
            else
                dst = dstIP.getIP();

            if (srcIP == null)
                src = STANDARD_IPV4_SRC_ADDRESS;
            else
                src = srcIP.getIP();

            IPv4 ipHeader = new IPv4();
            ipHeader.setSourceAddress(src)
                    .setDestinationAddress(dst)
                    .setProtocol((byte) networkProtocol)
                    .setTtl((byte) 255);
            ipHeader = getIPv4Payload(networkProtocol, ipHeader,
                                    flowspace, isRandom);
            if (ipHeader == null)
                return (ethernet);
            ipHeader.setParent(ethernet);
            ethernet.setPayload(ipHeader);
        }
        return (ethernet);
    }

    public IPacket getIPv4Packet (IOFSwitch srcSw,
                                  IOFFlowspace flowspace,
                                  boolean isVlanTagged,
                                  boolean isRandom)
    {
        Ethernet ethernet = new Ethernet()
                                .setSourceMACAddress(MACAddress.long2ByteArray(
                                                            srcSw.getId()))
                                .setDestinationMACAddress(STANDARD_DST_MAC_STRING)
                                .setEtherType(Ethernet.TYPE_IPv4);

        Long dstMac = null, srcMac = null;

        if (!isRandom) {
            // need think about this
        } else {
            dstMac = flowspace.getRandomDataLayerDst();
            if (dstMac != null)
            {
                ethernet.setDestinationMACAddress(
                                MACAddress.long2ByteArray(dstMac));
            }

            srcMac = flowspace.getRandomDataLayerSrc();
            if (srcMac != null)
            {
                ethernet.setSourceMACAddress(MACAddress.long2ByteArray(srcMac));
            }

            if (isVlanTagged)
            {
                byte pcp = flowspace.getRandomVlanPCP();
                short vlan = flowspace.getRandomDataLayerVlan();
                ethernet.setPriorityCode(pcp);
                ethernet.setVlanID(vlan);
            }
            ethernet = getIPv4PacketHeader(ethernet, flowspace, true);
        }

        return ((IPacket)ethernet);
    }

    public IPacket getRandomPacket (IOFSwitch srcSw,
                                    IOFFlowspace flowspace)
    {
        IPacket pkt = null;
        Short DLType = flowspace.getRandomDataLayerType();
        if (DLType == null)
            DLType = Short.valueOf(Ethernet.TYPE_IPv4);

        switch (DLType.shortValue()) {
            case Ethernet.TYPE_IPv4:
                pkt = getIPv4Packet(srcSw, flowspace, false, true);
                break;
            case Ethernet.TYPE_VLANTAGGED:
                pkt = getIPv4Packet(srcSw, flowspace, true, true);
                break;
            default:
                log.debug("unknown datalayer type");
        }
        return (pkt);
    }

    public IPacket getDefinitePacket (IOFSwitch srcSw,
                                        IOFFlowspace flowspace)
    {
        return (null);
    }

    private IPacket getDefaultPacket (IOFSwitch srcSw)
    {
        Ethernet ethernet = new Ethernet().setSourceMACAddress(MACAddress.long2ByteArray(
                                                                    srcSw.getId()))
                                          .setDestinationMACAddress(STANDARD_DST_MAC_STRING)
                                          .setEtherType(Ethernet.TYPE_IPv4);
        IPv4 ipHeader = new IPv4();
        ipHeader.setSourceAddress(STANDARD_IPV4_SRC_ADDRESS)
            .setDestinationAddress(STANDARD_IPV4_DST_ADDRESS)
            .setProtocol((byte) 0x06)
            .setTtl((byte) 255);
        ipHeader.setParent(ethernet);
        ethernet.setPayload(ipHeader);

        TCP tcpHeader = new TCP();
        tcpHeader.setSourcePort(STANDARD_TP_SRC)
                 .setDestinationPort(STANDARD_TP_DST)
                 .setParent(ipHeader);
        ipHeader.setPayload(tcpHeader);

        return ((IPacket) ethernet);
    }

    public IPacket getPacket (IOFSwitch srcSw,
                              IOFFlowspace flowspace,
                              boolean random)
    {
        IPacket pkt = null;

        // need to ensure that the state gets stored in the flowspace
        // object and gets reset when the validation gets completed
        if (random)
            pkt = getRandomPacket(srcSw, flowspace);
        else
            pkt = getDefinitePacket(srcSw, flowspace);

        return (pkt);
    }

    //internal methods

    /**
     * Pushes a packet-out to a switch.
     * @param sw        switch from which packet-out is sent
     * @param outport   output port
     * @param packetout OFPacketOut
     */
    protected void sendPacket (IOFSwitch sw,
                               short outport,
                               OFPacketOut po)
    {
        if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} po={} ",
                            new Object[] {sw, po});
        }
        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(outport, (short) 0xffff));

        po.setActions(actions)
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
        short poLength =
            (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH
                     + po.getPacketData().length);

        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(OFPort.OFPP_NONE);
        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, po);
            messageDamper.write(sw, po, null, true);
        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }

    /**
     * Push flowmod
     */
    public boolean pushFlowMod (byte[] packetData,//OFPacketOut po,
                                IOFSwitch sw,
                                Rules ruleTransTable,
                                short inPort)
    {
        long cookie = AppCookie.makeCookie(VALIDATION_APP_ID, 0);

        OFFlowMod fm =
                (OFFlowMod) floodlightProvider.getOFMessageFactory()
                                              .getMessage(OFType.FLOW_MOD);

        OFMatch match = new OFMatch();
        OFActionOutput action = new OFActionOutput();
        action.setPort(OFPort.OFPP_CONTROLLER.getValue())
              .setMaxLength((short)0xffff);

        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(action);

		//byte[] packetData = po.getPacketData();

		if (ruleTransTable != null) {
			packetData = ruleTransTable.getPacketHeader(packetData);
		}

        match.loadFromPacket(packetData, inPort);
        fm.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
            .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
            .setCookie(cookie)
            .setCommand(OFFlowMod.OFPFC_ADD)
            .setMatch(match)
            .setActions(actions)
            .setLengthU(OFFlowMod.MINIMUM_LENGTH +
                        OFActionOutput.MINIMUM_LENGTH);

        Integer wildcard_hints = ((Integer) sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS))
                                                .intValue()
                                                & ~OFMatch.OFPFW_IN_PORT
                                                & ~OFMatch.OFPFW_DL_VLAN
                                                & ~OFMatch.OFPFW_DL_SRC
                                                & ~OFMatch.OFPFW_DL_DST
                                                & ~OFMatch.OFPFW_NW_SRC_MASK
                                                & ~OFMatch.OFPFW_NW_DST_MASK;
        wildcard_hints = wildcard_hints | OFMatch.OFPFW_DL_VLAN;
        fm.getMatch().setWildcards(wildcard_hints);
        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, fm);
            if (log.isTraceEnabled()) {
                log.trace("Pushing flowmod " +
                        "sw={} inPort={} outPort={}",
                        new Object[] {sw,
                        fm.getMatch().getInputPort(),
                        OFPort.OFPP_CONTROLLER});
            }
			
			log.trace("Generated Flowmod: {}", fm);
            messageDamper.write(sw, fm, null);
            sw.flush();
            counterStore.updateFlush();
        } catch (IOException e) {
            log.error("Failure writing flow mod", e);
            return (false);
        }
        return true;
    }

    /**
     * Check if outgoing discovery messages are enabled or not.
     * @param sw
     * @param port
     * @return
     */
    protected boolean isDiscoveryAllowed (long sw,
                                          short port)
    {
        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
            return false;
        }

        if (port == OFPort.OFPP_LOCAL.getValue())
            return false;

        OFPhysicalPort ofpPort = iofSwitch.getPort(port);
        if (ofpPort == null) {
            if (log.isTraceEnabled()) {
                log.trace("Null physical port. sw={}, port={}",
                        HexString.toHexString(sw), port);
            }
            return false;
        }

        return true;
    }


    /**
     * Check if outgoing discovery messages are enabled or not.
     * @param sw
     * @param port
     * @param isStandard
     * @param isReverse
     * @return
     */
    protected boolean isOutgoingDiscoveryAllowed(long sw, short port)
    {

        IOFSwitch iofSwitch = floodlightProvider.getSwitches().get(sw);
        if (iofSwitch == null) {
            log.trace("switch is null");
            return false;
        }

        if (port == OFPort.OFPP_LOCAL.getValue()) return false;

        OFPhysicalPort ofpPort = iofSwitch.getPort(port);
        if (ofpPort == null) {
            if (log.isTraceEnabled()) {
                log.trace("Null physical port. sw={}, port={}",
                          HexString.toHexString(sw), port);
            }
            return false;
        }

        return true;
    }

    private static final byte[] STANDARD_DST_MAC_STRING = HexString.fromHexString(
                                                                "01:80:c2:12:34:56");
    private static final int STANDARD_IPV4_DST_ADDRESS = IPv4.toIPv4Address("10.1.1.2");
    private static final int STANDARD_IPV4_SRC_ADDRESS = IPv4.toIPv4Address("10.1.1.1");
    private static final short STANDARD_TP_SRC = 8;
    private static final short STANDARD_TP_DST = 14;
    private static final int VALIDATION_APP_ID = 9;
    public static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
    public static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
    protected final int PRIMARY_CHECK_INTERVAL = 1; // in seconds.
    protected final int RETRY_INTERVAL = 200; // 100 ms.
    protected static int OFMESSAGE_DAMPER_CAPACITY = 10000;
    protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms
}
