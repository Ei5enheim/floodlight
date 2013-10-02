/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.arpresolution;

import java.util.*;
import java.io.*;
import java.util.concurrent.ConcurrentSkipListSet;
import net.floodlightcontroller.packet.*;
// Classes to read OF type and packets
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
// This is an data structure of the hash map where the different fields of the packet.
import net.floodlightcontroller.core.FloodlightContext;
// This interface is used to receive event notifications
import net.floodlightcontroller.core.IOFMessageListener;
// This interface is the encapsulation of the OF switch
import net.floodlightcontroller.core.IOFSwitch;
// This class registers all floodlight modules and serves as a registry. It maps service objects to service type
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
// A floodlight module needs to implement this inteface.
import net.floodlightcontroller.core.module.IFloodlightModule;
// A base interface for a module to provide a particular service.
import net.floodlightcontroller.core.module.IFloodlightService;
//interface allows you to interact with connected switches
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.util.OFMessageDamper;
import net.floodlightcontroller.util.TimedCache;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.keyvaluestore.IKeyValueStoreService;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MACTracker implements IOFMessageListener, IFloodlightModule
{
    // This interface is used as a medium to talk to the controller.
    protected IFloodlightProviderService floodlightProvider;
    protected ICounterStoreService counterStore;
    protected IKeyValueStoreService kvStoreService;
    protected OFMessageDamper messageDamper;
    protected static Logger logger;
    byte[] destIP, sourceIP, sourceMAC, dstMAC;
  
    // Inherited from Class package
    public String getName() {
        return "MACTracker";
    }

    /*
    public int getID()
    {
        return 0;
    }
    */
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO Auto-generated method stub
        return (name.equals("devicemanager") || name.equals("linkdiscovery"));
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // TODO Auto-generated method stub
        return (type.equals(OFType.PACKET_IN) && 
                (name.equals("learningswitch") || 
                 name.equals("forwarding")) ||
                 name.equals("circuitswitchingimpl")); 
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() 
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies()
    {
        Collection<Class <? extends IFloodlightService>> collection = 
                                new ArrayList<Class<? extends IFloodlightService>>();
        collection.add(IFloodlightProviderService.class);
        collection.add(IKeyValueStoreService.class);
        collection.add(ICounterStoreService.class);
	return (collection);
    }

    @Override
    public void init(FloodlightModuleContext context)
                    throws FloodlightModuleException
    {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        logger = LoggerFactory.getLogger(MACTracker.class);
        counterStore =
                context.getServiceImpl(ICounterStoreService.class);
        kvStoreService = context.getServiceImpl(IKeyValueStoreService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context)
    {
        //BufferedReader br = null;

        // calling the controller to register for packet_in message 
        logger.info("\n ****Starting the ARP module****\n");
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                                            EnumSet.of(OFType.PACKET_OUT),
                                            OFMESSAGE_DAMPER_TIMEOUT);
    }

    public byte[] getDstMAC(Integer dstIP)
    {
        if (dstIP == null)
            return (null);

        String storeName = kvStoreService.getIP2MACTable();
        String MACString = (String) kvStoreService.get(storeName, dstIP); 
        
        if (MACString != null) {
            if (logger.isTraceEnabled())
                logger.trace ("retrieved value {} for key {}",
                            MACString, IPv4.fromIPv4Address(dstIP));
            MACString = MACString.substring(0, MACString.indexOf(','));
            return (Ethernet.toMACAddress(MACString));
        }
        return (null);
    }

    public boolean isBroadcast(byte[] address) {
        for (byte b : address) {
            if (b != 0) // checks if equal to 0xff
                return false;
        }
        return true;
    }
    /*  This is the method called by the controlller to tell the 
     *  recvrs about the packet_in message
     *  FloodlightContext has a storage field where the payload
     *  of the OF message is stored in the form of key-value pairs.
     */
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
    {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                                              IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        // check for the ARP request packet 
        if (eth.getEtherType() == Ethernet.TYPE_ARP)
        {
            ARP arp_pkt = (ARP) eth.getPayload();
            if (arp_pkt.getProtocolType() == arp_pkt.PROTO_TYPE_IP) {
                if (arp_pkt.getHardwareType() == arp_pkt.HW_TYPE_ETHERNET) {
                    sourceIP = arp_pkt.getSenderProtocolAddress();
                    destIP = arp_pkt.getTargetProtocolAddress();
                    sourceMAC = arp_pkt.getSenderHardwareAddress();
                    dstMAC = arp_pkt.getTargetHardwareAddress();
                    if (isBroadcast(dstMAC)) {
                        dstMAC = getDstMAC(IPv4.toIPv4Address(destIP)); 
                        if (dstMAC == null)
                            return Command.CONTINUE;
                    }
                    //exchanging source and dest fields in the recvd request
                    arp_pkt = arp_pkt.setSenderHardwareAddress(dstMAC); 
                    arp_pkt = arp_pkt.setTargetHardwareAddress(sourceMAC);
                    arp_pkt = arp_pkt.setSenderProtocolAddress(destIP);
                    arp_pkt = arp_pkt.setTargetProtocolAddress(sourceIP);
                    arp_pkt = arp_pkt.setOpCode(arp_pkt.OP_REPLY);

                    eth = eth.setDestinationMACAddress(eth.getSourceMACAddress());
                    eth = eth.setSourceMACAddress(dstMAC); 
                    eth = (Ethernet) eth.setPayload((IPacket)arp_pkt);
                    pushPacket(sw, msg, cntx, IPv4.toIPv4Address(destIP),
                                IPv4.toIPv4Address(sourceIP));
                    return Command.STOP;
                }
            }
        } 
        return Command.CONTINUE;
    }

    protected void pushPacket (IOFSwitch sw, OFMessage msg, 
                               FloodlightContext cntx, Integer dstIP, Integer srcIP)
    {
        OFPacketIn pi = (OFPacketIn) msg;
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        if (msg == null) {
            return;
        }
        OFPacketOut po =
                (OFPacketOut) floodlightProvider.getOFMessageFactory()
                                                .getMessage(OFType.PACKET_OUT);
        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(pi.getInPort(), (short) 0xffff));
        po.setActions(actions)
          .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
        short poLength =
                (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        byte[] packetData = eth.serialize();
        poLength += packetData.length;
        po.setPacketData(packetData);
        po.setInPort(OFPort.OFPP_NONE);
        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, po);
            if (messageDamper.write(sw, po, cntx)) {
                sw.flush(); 
                counterStore.updateFlush();
                if (logger.isDebugEnabled()) {
                    logger.debug("sent out ARP reply for the host IP {} as host"+
                                 " source IP {}", IPv4.fromIPv4Address(dstIP),
                                 IPv4.fromIPv4Address(srcIP));
                }
            }
        } catch (IOException e) {
            logger.error("Failure writing packet out", e);
        }
    } 

    protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; 
    protected static int OFMESSAGE_DAMPER_TIMEOUT = 30000; // ms 
}
