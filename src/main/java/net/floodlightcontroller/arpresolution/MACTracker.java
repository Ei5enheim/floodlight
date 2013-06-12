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
// A utility class to convert string bytes to a hex string with ":" as seperator

import net.floodlightcontroller.counter.ICounterStoreService;
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

    // This interface is used as medium to talk to the controller.
    protected IFloodlightProviderService floodlightProvider;
    protected ICounterStoreService counterStore;
    protected static Logger logger;
    byte[] destIP, sourceIP, sourceMAC, destMAC;
    HashMap<String, String> arp_table; 
  
    // Inherited from Class package
    public String getName() {
        return "MACTracker";
    }

    public int getID()
    {
        return 0;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // TODO Auto-generated method stub
        return (type.equals(OFType.PACKET_IN) && 
                (name.equals("learningswitch") || 
                 name.equals("forwarding"))); 
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
        arp_table = new HashMap<String, String> ();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {

        String ipAddr, macAddr;
        BufferedReader br = null;
    
        try {

            br = new BufferedReader(new FileReader(
                                    new File("/home/rajesh/Documents/UNC/RENCI/Open Flow/floodlight/", "ARPtable"))); 
        } catch (Exception io) {
    
            System.out.println("caught an Exception while opening the file");
        }   

        String line = null;
        String[] tokens;

        // calling the controller to register for packet_in message 
        logger.info("\n ****Started the module****\n");
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        try { 
            while ((line = br.readLine()) != null) {
                tokens = line.split("\\s");
                System.out.println(line);
                arp_table.put(tokens[0], tokens[1]); 
            }
        } catch (Exception io) {
            System.out.println("caught an Exception while reading the file"); 
        }
    }

    /*  This is the method called by the controlller to tell the 
     *  recvrs about the packet_in message
     *  FloodlightContext has a storage field where the payload
     *  of the OF message is stored in the form of key-value pairs.
     */
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                                              IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        //String dstMACAddr = "00:bb:cc:dd:ee:ff";
        String dstMACAddr = null;
        String sourceMACAddr = "00:00:5E:19:21:68";
        
        Long sourceMACHash = Ethernet.toLong(eth.getSourceMACAddress());
        //logger.info("\n****got the packet*****");
        
        // check for the ARP request packet 
        if (eth.getEtherType() == Ethernet.TYPE_ARP) {
            //logger.info("\n****got the Ethernet payload*****");
            ARP arp_pkt = (ARP) eth.getPayload();
            if (arp_pkt.getProtocolType() == arp_pkt.PROTO_TYPE_IP) {
                if (arp_pkt.getHardwareType() == arp_pkt.HW_TYPE_ETHERNET) {
                    //logger.info("\n****got the ARP header*****");
                    sourceIP = arp_pkt.getSenderProtocolAddress();
                    destIP = arp_pkt.getTargetProtocolAddress();
                    sourceMAC = arp_pkt.getSenderHardwareAddress();
                    dstMACAddr = arp_table.get(IPv4.fromIPv4Address(IPv4.toIPv4Address(destIP))); 
                    destMAC = Ethernet.toMACAddress(dstMACAddr);
                    logger.info("\n ******** ARP request - from address: {} *********",
                            IPv4.fromIPv4Address(IPv4.toIPv4Address(sourceIP)));

                    logger.info("\n******** ARP request - HW address {}, address of {} *********",
                            HexString.toHexString(sourceMAC),
                            IPv4.fromIPv4Address(IPv4.toIPv4Address(destIP)));

                    //exchanging source and dest fields in the recvd request
                    arp_pkt = arp_pkt.setSenderHardwareAddress(destMAC); 
                    arp_pkt = arp_pkt.setTargetHardwareAddress(sourceMAC);
                    arp_pkt = arp_pkt.setSenderProtocolAddress(destIP);
                    arp_pkt = arp_pkt.setTargetProtocolAddress(sourceIP);
                    arp_pkt = arp_pkt.setOpCode(arp_pkt.OP_REPLY);

                    eth = eth.setDestinationMACAddress(eth.getSourceMACAddress());
                    eth = eth.setSourceMACAddress(sourceMACAddr); 
                    eth = (Ethernet) eth.setPayload((IPacket)arp_pkt);
                    //logger.info("\n****calling push packet*****");
                    pushPacket(sw, msg, cntx);
                    return Command.STOP;
                }
            }
        } 

        /*logger.info("\n ******** ARP request - from address: {} ********* ",
                    IPv4.fromIPv4Address(IPv4.toIPv4Address(sourceIP)));
        logger.info("******** ARP request - HW address {}, address of {} *********",
                HexString.toHexString(sourceMACHash),
                IPv4.fromIPv4Address(IPv4.toIPv4Address(destIP)));
        logger.info("******** ARP request - on switch {}  *********\n", sw.getId());*/
  
        return Command.CONTINUE;
    }

    protected void pushPacket (IOFSwitch sw, OFMessage msg, 
                               FloodlightContext cntx) {

        logger.info("\n****In push packet*****");
        OFPacketIn pi = (OFPacketIn) msg;
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        if (msg == null) {
            return;
        }

        /* The assumption here is (sw) is the switch that generated the 
        // packet-in. If the input port is the same as output port, then
        // the packet-out should be ignored.
        //$$ disabling the check for now. \\
        if (pi.getInPort() == outport) {
            if (log.isDebugEnabled()) {
                log.debug("Attempting to do packet-out to the same " + 
                          "interface as packet-in. Dropping packet. " + 
                          " SrcSwitch={}, pi={}", 
                          new Object[]{sw, pi});
                return;
            }
        }*/
        
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
            sw.write(po, null);
        } catch (IOException e) {
            logger.error("Failure writing packet out", e);
        }
    } 
}


