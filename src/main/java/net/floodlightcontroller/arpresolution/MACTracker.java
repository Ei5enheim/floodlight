package net.floodlightcontroller.arpresolution;

import java.util.*;
import java.io.*;
import java.util.concurrent.ConcurrentSkipListSet;
// Classes to read OF type and packets
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
// class to register for events and get a call back.
import net.floodlightcontroller.core.FloodlightContext;
// This interface is used to receive event notifications
import net.floodlightcontroller.core.IOFMessageListener;
// This interface is the encapsulation of the OF switch
import net.floodlightcontroller.core.IOFSwitch;
// This class registers all floodlight modules and serves as a registry
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
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MACTracker implements IOFMessageListener, IFloodlightModule
{

    protected IFloodlightProviderService floodlightProvider;
    protected Set macAddresses;
    protected static Logger logger;
    
    
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
        return false;
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
    
	return (collection);
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        macAddresses = new ConcurrentSkipListSet<Long>();
        logger = LoggerFactory.getLogger(MACTracker.class);

    }

    @Override
    public void startUp(FloodlightModuleContext context) {
    
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

    }

    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                                                              IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        Long sourceMACHash = Ethernet.toLong(eth.getSourceMACAddress());
        if (!macAddresses.contains(sourceMACHash)) {
            macAddresses.add(sourceMACHash);
            logger.info("\n ***************** MAC address: {} seen by rajesh on switch {}************************* \n",
            HexString.toHexString(sourceMACHash),
            sw.getId());
        }
        return Command.CONTINUE;
    }
}


