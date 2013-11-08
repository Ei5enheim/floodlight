/**
 *
 * Author: Rajesh Gopidi
 *
 */

package net.floodlightcontroller.graphdbreader.internal;

import net.floodlightcontroller.graphdbreader.IGraphDBReaderService;
import net.floodlightcontroller.graphdbreader.GraphDBException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.circuitswitching.ICircuitSwitching;
import net.floodlightcontroller.topovalidation.ITopoValidationService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.topology.IOFFlowspace;
import net.floodlightcontroller.topology.OFFlowspace;
import net.floodlightcontroller.topovalidation.internal.TopoLock;
import net.floodlightcontroller.topology.FlowspaceException;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.graphdbreader.IGraphDBRequest;
import net.floodlightcontroller.graphdbreader.internal.GraphDBRequest;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.util.DelegatedMAC;

import org.renci.doe.pharos.flow.Rules;
import org.renci.doe.pharos.flow.Rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;

import org.apache.xmlrpc.common.TypeConverterFactoryImpl;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.apache.xmlrpc.webserver.WebServer;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.impls.dex.DexGraph;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLReader;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLWriter;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.pipes.PipeFunction;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.pipes.filter.*;
import com.tinkerpop.blueprints.Contains;
import com.tinkerpop.blueprints.Direction;

public class GraphDBReaderImpl implements IGraphDBReaderService,
                                            IFloodlightModule
{
    private class UpdatesThread implements Runnable
    {
        ScheduledExecutorService ses = null;

        public UpdatesThread()
        {
            super();
        }

        public UpdatesThread(ScheduledExecutorService ses)
        {
            super();
            this.ses = ses;
        }
 
        public void run()
        {
            int indx = 0;
            List<IGraphDBRequest> clone = null;
            Set<Long> switches = null;
            
            try {
                if (!queue.isEmpty()) {
                    synchronized (queue) {
                        // No need of a deep copy as we are not going to modify any data
                        clone = new ArrayList(queue);
                    }
					logger.trace("*********** running through the queue *******"); 
                    //TODO need to replace it with a loop, 
                    for (indx = 0; indx < clone.size(); indx++) {
                        IGraphDBRequest req = clone.get(indx);
                        synchronized (queue) {
                            queue.remove(indx);
                        }
                        clone.remove(indx);
                        if (cSwitchingMod != null) {
                            cSwitchingMod.addBlockedSrcMACList(req.getBlockedSrcMACList());
                        }
                        logger.trace("Setting the blocked List in circuitSwitching module");
                    }
                }
            } catch (Exception e) {
                logger.trace("caugth an Exception in Updates Task, GraphDBReader: {}", e); 
            } finally {
                updatesTask.reschedule(SWITCH_STATE_UPDATE_INTERVAL_MS,
                                        TimeUnit.MILLISECONDS);
            }
        }
    }

    // IFloodlightModule routines
    public Collection<Class<? extends IFloodlightService>> getModuleServices()
    {
        Collection <Class<? extends IFloodlightService>> list =
            new ArrayList<Class<? extends IFloodlightService>>();
        list.add(IGraphDBReaderService.class);
        return (list);
    }

    public Map<Class<? extends IFloodlightService>,
           IFloodlightService> getServiceImpls()
    {
		Map <Class<? extends IFloodlightService>, IFloodlightService> map =
        			new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();

		map.put(IGraphDBReaderService.class, this);
		return (map);
    }

    public Collection<Class<? extends IFloodlightService>> getModuleDependencies()
    {
        Collection<Class <? extends IFloodlightService>> collection =
            new ArrayList<Class<? extends IFloodlightService>>();
        collection.add(IFloodlightProviderService.class);
        collection.add(ITopoValidationService.class);
        collection.add(ILinkDiscoveryService.class);

        return (collection);
    }

    public void init(FloodlightModuleContext context) throws FloodlightModuleException
    {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        logger = LoggerFactory.getLogger(GraphDBReaderImpl.class);
        linkManager = context.getServiceImpl(ILinkDiscoveryService.class);
        topoValidator = context.getServiceImpl(ITopoValidationService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        cSwitchingMod = context.getServiceImpl(ICircuitSwitching.class);
        queue = Collections.synchronizedList(new ArrayList<IGraphDBRequest>());
    }

    public void startUp(FloodlightModuleContext context)
    {
        startServer();
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        updatesTask = new SingletonTask(ses, new UpdatesThread(ses));
		updatesTask.reschedule(0, TimeUnit.MILLISECONDS);
        logger.info("\n **** Starting the Graphdb reader module ****\n");
    }

	public String getFileName()
	{
		String fileName = "./db/";
		synchronized (this) {
			fileName = fileName + String.valueOf(count++)+".xml";
		}
		return (fileName);
	}

    private List<DelegatedMAC> getBlockedSrcMACList (Collection<IOFFlowspace[]> flowspace)
    {
        List<DelegatedMAC> blockedList = new ArrayList<DelegatedMAC>();
        Object[] array = flowspace.toArray();
        for (Object o: array) {
            IOFFlowspace[] portFlowspace = (IOFFlowspace[])o;
            if (!blockedList.containsAll(portFlowspace[EGRESS].getBlockedDataLayerSrc()))
                blockedList.addAll(portFlowspace[EGRESS].getBlockedDataLayerSrc());
        }
        return (blockedList);
    }

    // internal utility methods
    public void readGraph (byte[] xmlFile)
    {
        Set <Long> switches = new HashSet<Long> ();
        HashSet <Vertex> vertices = new HashSet <Vertex>();
        List<Edge> pruneList = new ArrayList<Edge>();
        Map <NodePortTuple, IOFFlowspace[]>  flowspace;
        String localDomain = null;
        String fileName = getFileName();
        ByteArrayInputStream stream = null; 
        //BufferedInputStream stream = null;
        File file = new File(fileName);

        if (file.exists())
            file.delete();

        stream = new ByteArrayInputStream(xmlFile);
        flowspace = new ConcurrentHashMap <NodePortTuple, IOFFlowspace[]>();

        Graph graph = new DexGraph(fileName);
        try {
            GraphMLReader.inputGraph(graph, stream);
        } catch (IOException io) {
            logger.trace("*********caught an exception {}*********", io); 
        }
        for (Edge e: graph.getEdges()) {
            try {
                localDomain = processEdge(e, vertices, switches, pruneList, 
                                            flowspace, localDomain);
            } catch (FlowspaceException fe) {
                logger.trace("*********caught an exception {}*********", fe); 
            }
        }

        if (!pruneList.isEmpty()) {
            Edge[] array = new Edge[pruneList.size()];
            array = pruneList.toArray(array);
            for (Edge e: array) {
                try {
                    localDomain = processEdge(e, vertices, switches, pruneList,
                                                flowspace, localDomain);
                } catch (FlowspaceException fs) {
                    logger.trace("*********caught an exception {}*********", fs);
                }
            }
        }

        IGraphDBRequest node = new GraphDBRequest(getBlockedSrcMACList(flowspace.values()));

        //getBlockedSrcMACList(flowspace.values());
        queue.add(node);
    }

    private String processEdge (Edge e, HashSet <Vertex> vertices,
            Set <Long> switches,
            List<Edge> pruneList,
            Map <NodePortTuple, IOFFlowspace[]> flowspace,
            String localDomain) throws FlowspaceException
    {
        Long headDpid = null, tailDpid= null;
        short headPort = 0, tailPort = 0;
        Vertex inNode = null, outNode = null;
        boolean isPhysEdge = false;
        NodePortTuple headSwitchPort = null, tailSwitchPort = null;
        Link link = null;

        inNode = e.getVertex(Direction.IN);
        outNode = e.getVertex(Direction.OUT);

        if (!inNode.getProperty("domain").equals(outNode.getProperty("domain"))){
            if (localDomain == null) {
                pruneList.add(e);
                return null;
            }
        } else {
            localDomain = inNode.getProperty("domain");
        }

        isPhysEdge = e.getLabel().equals(wantedLabel);

        headDpid = new BigInteger((String)inNode.getProperty("DPID"), 16).longValue();
        headSwitchPort = new NodePortTuple(headDpid,
                Short.valueOf((String)inNode.getProperty("Port")));

        tailDpid = new BigInteger((String)outNode.getProperty("DPID"),16).longValue();
        tailSwitchPort = new NodePortTuple(tailDpid,
                Short.valueOf((String)outNode.getProperty("Port")));

        if (!vertices.contains(inNode) &&
                inNode.getProperty("domain").equals(localDomain)) {
            	vertices.add(inNode);
                switches.add(headDpid);
            if (flowspace.get(headSwitchPort) == null)
                flowspace.put(headSwitchPort, new IOFFlowspace[2]);
            	/*System.out.println("Domain: "+ inNode.getProperty("domain")+" Node: "+ 
                    inNode.getProperty("DPID") + " Port: " + 
                    inNode.getProperty("Port"));*/
            if (isPhysEdge) {
                //System.out.println("[Ingress] Flowspace: " 
                //        + inNode.getProperty("Flowspace"));
                flowspace.get(headSwitchPort)[INGRESS] = OFFlowspace.parseFlowspaceString(
                        (String) inNode.getProperty("Delegated"), false);
            } else {
                //System.out.println("[Egress] Flowspace: "
                //        + inNode.getProperty("Flowspace"));
                flowspace.get(headSwitchPort)[EGRESS] = OFFlowspace.parseFlowspaceString(
                        (String)inNode.getProperty("Delegated"), false);
            }
        }
        if (!vertices.contains(outNode)&&
                outNode.getProperty("domain").equals(localDomain)) {
            vertices.add(outNode);
            if (!switches.contains(tailDpid)) {
                switches.add(tailDpid);
            }
            if (flowspace.get(tailSwitchPort) == null)
                flowspace.put(tailSwitchPort, new IOFFlowspace[2]);

            /*System.out.println("Domain: " + outNode.getProperty("domain") + " Node: " +
                    outNode.getProperty("DPID") + " Port: "+ outNode.getProperty("Port"));*/
            if (isPhysEdge) {
                flowspace.get(tailSwitchPort)[EGRESS] = OFFlowspace.parseFlowspaceString(
                        (String) outNode.getProperty("Delegated"), true);
                //System.out.println("[Egress] Flowspace: " + outNode.getProperty("Flowspace"));
            } else {
                flowspace.get(tailSwitchPort)[INGRESS] = OFFlowspace.parseFlowspaceString(
                        (String) outNode.getProperty("Delegated"), true);;
                //System.out.println("[Ingress] Flowspace: " + outNode.getProperty("Flowspace"));
            }
        }
        return localDomain;
    }

    //internal utility method

    private void startServer()
    {

        try {
            webServer = new WebServer(port);
            xmlRpcServer = webServer.getXmlRpcServer();
            PropertyHandlerMapping phm = new PropertyHandlerMapping();
            phm.addHandler("GraphDBReader",
                    net.floodlightcontroller.graphdbreader.internal.GraphDBReaderImpl.class);
            xmlRpcServer.setHandlerMapping(phm);
            XmlRpcServerConfigImpl serverConfig =
                (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();
            serverConfig.setEnabledForExtensions(true);
            serverConfig.setContentLengthOptional(false);
            webServer.start();
        } catch (Exception e) {
            logger.trace("************caught an Exception {}*************", e);
        }
    }

    // IOFGraphDBReader

    public Boolean parse(byte[] file)
    {
		try {
        	readGraph(file);
		} catch (Exception e) {
			logger.trace("****** caught an exception {} ******", e);
			e.printStackTrace();
		}
		logger.trace("******* Finished executing client call ******");
        return (true);
    }

    protected IFloodlightProviderService floodlightProvider;
    protected ILinkDiscoveryService linkManager;
    protected ITopoValidationService topoValidator;
    protected IThreadPoolService threadPool;
    protected ICircuitSwitching cSwitchingMod;
    protected static List<IGraphDBRequest> queue;
    protected static IGraphDBRequest interDomainLinks;
    protected static Object interDomainLock;
    protected SingletonTask updatesTask;
    protected static Logger logger;
    private static final int port = 6999;
    private WebServer webServer;
    private XmlRpcServer xmlRpcServer;
    private String wantedLabel = "Is connected to";
    private final int FLOWSPACE_SIZE = 2, INGRESS = 0, EGRESS = 1;
    private final int SWITCH_STATE_UPDATE_INTERVAL_MS = 400;
    private final int TOPOLOGY_UPDATE_INTERVAL_MS = 200;
	private static int count = 0;
}
