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
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.restserver.IRestApiService;
import org.openflow.protocol.OFType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Random;
import java.util.ArrayList;
import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import voldemort.versioning.ObsoleteVersionException;
import voldemort.client.protocol.RequestFormatType;
import voldemort.client.SocketStoreClientFactory;
import voldemort.client.StoreClientFactory;
import voldemort.client.ClientConfig;
import voldemort.client.DefaultStoreClient;
import voldemort.versioning.Versioned;
import voldemort.versioning.Version;

// use the update from Idevicemanager to find a device and use the hash table to get the domain ID.
// need to change the forwarding code
// need to see what happens when we have link down event and flow reconcillation happens

public class VoldemortClientImpl implements IKeyValueStoreService,
                                                    IFloodlightModule
                                                    
{
    protected IFloodlightProviderService floodlightProvider;
    protected IDeviceService  deviceService;
    protected IRestApiService restAPIService;

    protected static Logger logger;
    public final String configFile = "config/cluster.xml";
    private final int STORES = 3;
    private int SERVERS;
    private final int MAC_STORE = 0;
    private final int DOMAIN_STORE = 1;
    private final int PATHID_STORE  = 2;
    private final String[] storeNames = {"mac_store", "domainID_store", "pathID_store"};
    private HashMap<String, Integer> storeMap = new HashMap<String, Integer>();
    protected DefaultStoreClient<Object, Object> client[][];
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

    public boolean isCallbackOrderingPrereq(OFType type, String name)
    {
        return false;
    }

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
        collection.add(IRestApiService.class);
	return (collection);
    }

    /**
     *
     * internal routines
     */

    private void buildStoreMap()
    {
        for (int i = 0; i < STORES; i++)
        {
            storeMap.put(storeNames[i], i); 
        } 
    }

    private static String getValue(String tag, Element element)
    {
        NodeList nodes = element.getElementsByTagName(tag).item(0).getChildNodes();
        Node node = (Node) nodes.item(0);
        return node.getNodeValue();
    }

    protected void buildMap()
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
                    SERVERS++;
                    Element element = (Element) node;
                    IP = getValue("host", element);
                    port = getValue("socket-port", element);
                    map.put(nodeNumber++, "tcp://"+IP+":"+port); 
                    System.out.println("*********host="+IP+"********");
                }
            }
        } catch (Exception e) {
            System.err.println("Caught an Exception");
            e.printStackTrace();
        }
        System.out.println("*******was to read the map*******");
    }

    private int getRandomInt(int MAX)
    {
        Date date = new Date();
        Random rgen = new Random(date.getTime());
        return (rgen.nextInt(MAX));
    } 

    private String getURL(int index)
    {
        return (map.get(index));
    }

    void bootStrapClients()
    {
        String bootstrapUrl = null;
        ClientConfig config = null;
        StoreClientFactory factory = null;

        buildStoreMap();
        buildMap();

        client = new DefaultStoreClient[STORES][SERVERS];

        for (int j = 0; j < SERVERS; j++) {
            bootstrapUrl = getURL(j);
            config = new ClientConfig().setBootstrapUrls(bootstrapUrl)
                                       .setEnableLazy(false)
                                       .setRequestFormatType(RequestFormatType.VOLDEMORT_V3);
            for (int i = 0; i < STORES; i++) {
                factory = new SocketStoreClientFactory(config);
                // create a client that executes operations on a single store
                client[i][j] = (DefaultStoreClient<Object, Object>) factory.getStoreClient(storeNames[i]);
            }
        }
    }

    @Override
        public void init(FloodlightModuleContext context) throws FloodlightModuleException
        {
            floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
            restAPIService = context.getServiceImpl(IRestApiService.class);
            logger = LoggerFactory.getLogger(VoldemortClientImpl.class);

            bootStrapClients(); 
        }

    @Override
        public void startUp(FloodlightModuleContext context)
        {
            logger.info("\n ****Voldemort has started looking for Harry Potter!***\n");
        }

    /*******************************
      IKeyValueStoreImplementation
     ********************************/
    
    private int getStoreIndex (String storeName)
    {
        Integer value = null;
        value = storeMap.get(storeName);
        if (value == null)
            return (-1);
        return (value);
    }

    @Override
    public boolean addRUpdate (String storeName, Object key, Object value)
    {
        int index = getStoreIndex(storeName);
        Version v = null;

        if (index == -1)
            return (false);
        try {
            v = client[index][getRandomInt(SERVERS)].put(key, value);
            if (v == null)
                return (false);
            if (logger.isTraceEnabled())
                logger.trace("Current version is {}", v.toString());
        } catch (ObsoleteVersionException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("caught exception {}" + e.getMessage());
            }
            return (false);
        }
        return (true);
    }

    @Override
    public boolean delete (String storeName, Object key)
    {
        int index = getStoreIndex(storeName);

        if (index == -1)
            return (false);
        
        return (client[index][getRandomInt(SERVERS)].delete(key));
    }

    @Override
    public Object get (String storeName, Object key)
    {
        int index = getStoreIndex(storeName);
        Versioned value = null;

        if (index == -1)
            return (null);
       
        if ((value = client[index][getRandomInt(SERVERS)].get(key)) != null)
            return (value.getValue());

        return (null);
    } 

     public String getIP2MACTable() 
     {
	return (storeNames[MAC_STORE]);
     }  

     public String getPathIDStore() 
     {
	return (storeNames[PATHID_STORE]);
     }  

     public String getDomain(String IP)
     {
	return ("0");
     }  
}

