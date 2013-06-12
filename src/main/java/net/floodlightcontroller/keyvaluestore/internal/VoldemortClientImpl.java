/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.keyvaluestore.internal;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.keyvaluestore.IKeyValueStoreService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.devicemanager.IDevice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import voldemort.client.DefaultStoreClient;

// use the update from Idevicemanager to find a device and use the hash table to get the domain ID.
// need to change the forwarding code
// need to see what happens when we have link down event and flow reconcillation happens

public class VoldemortClientImpl <K, V> implements IKeyValueStoreService <K, V>,
                                                    IFloodlightModule,
                                                    IDeviceListener
{
    protected IFloodlightProviderService floodlightProvider;
    protected IDeviceService  deviceServive;
    protected IRestApiService restAPIService;

    protected static Logger logger;
    public final String configFile = "config/cluster.xml";
    private final int STORES = 3;
    private final int MAC_STORE = 0;
    private final int DOMAIN_STORE = 1;
    private final int PATHID_STORE  = 2;
    private final String[] stores = {"mac_store", "domainID_store", "pathID_store"}
    protected DefaultStoreClient<Object, Object> client[STORES];
    protected HashMap<Integer, String>  map;

    /**************************
        IFloodlightModule 
     *************************/
    public String getName()
    {
        return "VoldemortClientImpl";
    }

    public int getID()
    {
        return 0;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name)
    {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name)
    {
        return (false);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices()
    {
        Collection <Class<? extends IFloodlightService>> list = 
                            new ArrayList<Class<? extends IFloodlightService>>(); 

        list.add(IKeyValueStoreService.class);
        return (list);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls()
    {
        Map <Class<? extends IFloodlightService>, IFloodlightService> map =
                            new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();

        map.put(IKeyValueStoreService.class, this);
        return (map);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies()
    {
        Collection<Class <? extends IFloodlightService>> collection = 
                                new ArrayList<Class<? extends IFloodlightService>>();
        collection.add(IFloodlightProviderService.class);
        collection.add(IDeviceService.class);
        collection.add(IRestApiService.class);
	return (collection);
    }

    protected int buildMap()
    {
        map = new HashMap <Integer, String>();
        String IP = null, port = null; 
        int nodeNumber = 0;

        try { 
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(configFile);
            doc.getDocumentElement().normalize();
            NodeList nodes = doc.getElementsByTagName("server");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    IP = getValue("host", element);
                    port = getValue("socket-port", element);
                    map.put(nodeNumber++, "tcp://"+IP+":"+port); 
                }
            }
        } catch (Exception e) {
            Sytem.err.println("Caught an Exception");
            e.printStackTrace();
        } 
    }

    private static String getURL()
    {
        Date date = new Date();
        Random rgen = new Random(date.getTime());
        int pick = rgen.nextInt(4);
        return (map.get(pick));
    }

    void bootStrapClients()
    {
        String bootstrapUrl = null;
        ClientConfig config = null;
        StoreClientFactory factory = null;
 
        for (int i = 0; i < STORES; i++) {
            bootstrapUrl =  = getURL();
            config = new ClientConfig().setBootstrapUrls(bootstrapUrl)
                                       .setEnableLazy(false)
                                       .setRequestFormatType(RequestFormatType.VOLDEMORT_V3);
            factory = new SocketStoreClientFactory(config);
            // create a client that executes operations on a single store
            client[i] = (DefaultStoreClient<Object, Object>) factory.getStoreClient(stores[i]);            
        }
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException
    {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        deviceService = context.getServiceImpl(IDeviceService.class);
        restAPIService = context.getServiceImpl(IRestApiService.class);
        logger = LoggerFactory.getLogger(VoldemortClientImpl.class);
        
        bootStrapClients(); 
    }

    @Override
    public void startUp(FloodlightModuleContext context)
    {
        logger.info("\n ****Voldemort has started looking for Harry Potter!***\n");
        deviceService.addListener(this); 
         
    }

    /*********************
        IDeviceListener
     ********************/

    public void deviceAdded (IDevice device)
    {


    }


    public void deviceRemoved (IDevice device)
    {

    }

    public void deviceMoved (IDevice device)
    {


    }

    public vod deviceIPV4AddrChanged (IDevice device)
    {


    }

    public void deviceIPV4AddrChanged (IDevice device)
    {


    } 

}

