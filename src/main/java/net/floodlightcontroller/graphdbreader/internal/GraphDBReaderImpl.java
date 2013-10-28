/**
 *
 * Author: Rajesh Gopidi
 *
 */

package net.floodlightcontroller.graphdbreader.internal;

import net.floodlightcontroller.graphdbreader.IGraphDBReaderService;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.IFloodlightService;
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
    private class WorkerThread implements Runnable
    {
        IGraphDBRequest topoSlice;
        TopoLock lock;
        ScheduledExecutorService ses = null;
        
        public WorkerThread (IGraphDBRequest topoSlice,
                             ScheduledExecutorService ses)
        {
            super();
            this.topoSlice = topoSlice;
            this.ses = ses;
            floodlightProvider.addFlowspace(topoSlice.getDomainFlowspace());
            floodlightProvider.addRuleTransTables(topoSlice.getRuleTransTables());
            floodlightProvider.addDomainMapper(topoSlice.getDomainMapper());
            lock = topoValidator.validateTopology(topoSlice.getLinks(),
                                                topoSlice.getRuleTransTables(), false);
        }

        public void run()
        {
            if (lock == null) {
                logger.debug("*******Exception, Unable to validate topology, lock is null*******");
                return;
            }
            try { 
                if (lock.checkValidationStatus()) {
                    logger.trace("*********starting topology update ***********");
                    Link[] topoLinks = new Link[topoSlice.getLinks().size()];
                    topoLinks = topoSlice.getLinks().toArray(topoLinks);
                    logger.trace("*********At the end of it ***********");
                    linkManager.addLinks(topoLinks);
                    logger.trace("******** Added links to the topology ***********");
                } else {
                    if (lock.getRetryCount() > 3) {
                        logger.debug("*******Exception, Unable to validate topology*******");
                    }  else {
                        lock.incrementRetryCount();
                        ses.schedule(this, TOPOLOGY_UPDATE_INTERVAL_MS,
                                    TimeUnit.MILLISECONDS);   
                    }  
                }
            } catch (Exception e) {
                logger.trace("got you!!!!!!!!! {}", e);
            }
        }
    }

    private class UpdatesThread implements Runnable
    {
        int indx = 0;
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
			logger.trace("*************** Executing the updates thread *************");	
            List<IGraphDBRequest> clone = null;
            try {
                if (!queue.isEmpty()) {
                    synchronized (queue) {
                        // No need of a deep as we are not going to modify any data
                        clone = new ArrayList(queue);
                    }
					logger.trace("*********** Checking if the switches are ready *******"); 
                    //TODO need replace it with a loop, 
                    IGraphDBRequest req = clone.get(indx);
                    if (floodlightProvider.allPresent(req.getSwitches())) {
			 			synchronized (queue) {
							queue.remove(indx);
						}
						logger.trace("*************** Switches are ready, starting a worker thread *************");	
                        WorkerThread worker = new WorkerThread(req, ses);
                        ses.execute(worker);
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
        queue = Collections.synchronizedList(new ArrayList<IGraphDBRequest>());
    }

    public void startUp(FloodlightModuleContext context)
    {
        startServer();
        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        updatesTask = new SingletonTask(ses, new UpdatesThread(ses));
	updatesTask.reschedule(0, TimeUnit.MILLISECONDS);
        logger.info("\n ****Starting the Graphdb reader module****\n");
    }

    // internal utility methods
    public void readGraph (byte[] xmlFile)
    {
        Set <Long> switches = new HashSet <Long> ();
        HashSet <Vertex> vertices = new HashSet <Vertex>();
        List<Edge> pruneList = new ArrayList<Edge>();
        Map <Long, String> domainMapper;
        Map <NodePortTuple, IOFFlowspace[]>  flowspace;
        Map <Link, Rules> ruleTransTables;
        List<Link> links;
        String localDomain = null;
        String fileName = "./db/topology.dex";
        ByteArrayInputStream stream = null; 
        //BufferedInputStream stream = null;
        File file = new File(fileName);

        if (file.exists())
            file.delete();
        stream = new ByteArrayInputStream(xmlFile);
        /*
           try {
        //stream = new BufferedInputStream(
        //                            new FileInputStream("graph/topology.xml"));
        } catch (FileNotFoundException fnf) {
        logger.trace("Caught a FileNotFoundException {} ", fnf);  
        }*/
        flowspace = new ConcurrentHashMap <NodePortTuple, IOFFlowspace[]>();
        ruleTransTables = new ConcurrentHashMap <Link, Rules>();
        links = new ArrayList<Link>();
        domainMapper = new HashMap<Long, String>();

        Graph graph = new DexGraph(fileName);
        try {
            GraphMLReader.inputGraph(graph, stream);
        } catch (IOException io) {
            logger.trace("*********caught an exception {}*********", io); 
        }
        for (Edge e: graph.getEdges()) {
            try {
                processEdge(e, vertices, switches, pruneList, 
                        links, flowspace, domainMapper,
                        ruleTransTables, localDomain);
            } catch (FlowspaceException t) {
                logger.trace("*********caught an exception {}*********", t); 
            }
        }

        if (!pruneList.isEmpty()) {
            Edge[] array = new Edge[pruneList.size()];
            array = pruneList.toArray(array);
            for (Edge e: array) {
                try {
                    processEdge(e, vertices, switches, pruneList,
                            links, flowspace, domainMapper,
                            ruleTransTables, localDomain);
                } catch (FlowspaceException t) {
                    logger.trace("*********caught an exception {}*********", t);
                }
            }
        }

        IGraphDBRequest node = new GraphDBRequest(domainMapper, flowspace,
                					ruleTransTables, links, switches);
        queue.add(node);
    }

    private void processEdge (Edge e, HashSet <Vertex> vertices,
            Set <Long> switches,
            List<Edge> pruneList,
            List<Link> links,
            Map <NodePortTuple, IOFFlowspace[]> flowspace,
            Map <Long, String> domainMapper,
            Map <Link, Rules> ruleTransTables,
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
                return;
            }
        } else {
            localDomain = inNode.getProperty("domain");
        }

        isPhysEdge = e.getLabel().equals(wantedLabel);

        //headDpid = Long.valueOf((String)inNode.getProperty("DPID"));
        headDpid = new BigInteger((String)inNode.getProperty("DPID"), 16).longValue();
        headSwitchPort = new NodePortTuple(headDpid,
                Short.valueOf((String)inNode.getProperty("Port")));

        //tailDpid = Long.valueOf((String)outNode.getProperty("DPID"));
        tailDpid = new BigInteger((String)outNode.getProperty("DPID"),16).longValue();
        tailSwitchPort = new NodePortTuple(tailDpid,
                Short.valueOf((String)outNode.getProperty("Port")));

        if (isPhysEdge) {
            System.out.println("link between {srcDpid = "+ tailDpid +", srcPort = "+ 
                    (String)outNode.getProperty("Port") + " ----> {dstDpid = "+ headDpid +
                    ", dstPort = "+  (String)inNode.getProperty("Port"));
            link = new Link (tailDpid, tailSwitchPort.getPortId(),
                    headDpid, headSwitchPort.getPortId());
            links.add(link);
            ruleTransTables.put(link, new Rules((String)e.getProperty("Rules")));

        } else {
            // for now ignoring the "can be connected links"
        }

        if (!vertices.contains(inNode) &&
                inNode.getProperty("domain").equals(localDomain)) {
            vertices.add(inNode);
            if (!switches.contains(headDpid)) {
                switches.add(headDpid);
                System.out.println("Vertex is " + inNode.getProperty("DPID") + ", " + inNode.getId());
                domainMapper.put(headDpid, (String)inNode.getProperty("domain"));
            }
            if (flowspace.get(headSwitchPort) == null)
                flowspace.put(headSwitchPort, new IOFFlowspace[2]);
            System.out.println("Domain: "+ inNode.getProperty("domain")+" Node: "+ 
                    inNode.getProperty("DPID") + " Port: " + 
                    inNode.getProperty("Port"));
            if (isPhysEdge) {
                System.out.println("[Ingress] Flowspace: " 
                        + inNode.getProperty("Flowspace"));
                flowspace.get(headSwitchPort)[INGRESS] = OFFlowspace.parseFlowspaceString(
                        (String) inNode.getProperty("Flowspace"));
            } else {
                System.out.println("[Egress] Flowspace: "
                        + inNode.getProperty("Flowspace"));
                flowspace.get(headSwitchPort)[EGRESS] = OFFlowspace.parseFlowspaceString(
                        (String)inNode.getProperty("Flowspace"));
            }
        }
        if (!vertices.contains(outNode)&&
                outNode.getProperty("domain").equals(localDomain)) {
            vertices.add(outNode);
            if (!switches.contains(tailDpid)) {
                switches.add(tailDpid);
                domainMapper.put(tailDpid, (String) outNode.getProperty("domain"));
                System.out.println("Vertex is " + outNode.getProperty("DPID") + ", " + outNode.getId());
            }
            if (flowspace.get(tailSwitchPort) == null)
                flowspace.put(tailSwitchPort, new IOFFlowspace[2]);

            System.out.println("Domain: " + outNode.getProperty("domain") + " Node: " +
                    outNode.getProperty("DPID") + " Port: "+ outNode.getProperty("Port"));
            if (isPhysEdge) {
                flowspace.get(tailSwitchPort)[EGRESS] = OFFlowspace.parseFlowspaceString(
                        (String) outNode.getProperty("Flowspace"));
                System.out.println("[Egress] Flowspace: " + outNode.getProperty("Flowspace"));
            } else {
                flowspace.get(tailSwitchPort)[INGRESS] = OFFlowspace.parseFlowspaceString(
                        (String) outNode.getProperty("Flowspace"));;
                System.out.println("[Ingress] Flowspace: " + outNode.getProperty("Flowspace"));
            }
        }
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
    protected static List<IGraphDBRequest> queue;
    protected SingletonTask updatesTask;
    protected static Logger logger;
    private static final int port = 6999;
    private WebServer webServer;
    private XmlRpcServer xmlRpcServer;
    private String wantedLabel = "Is connected to";
    private final int FLOWSPACE_SIZE = 2, INGRESS = 0, EGRESS = 1;
    private final int SWITCH_STATE_UPDATE_INTERVAL_MS = 400;
    private final int TOPOLOGY_UPDATE_INTERVAL_MS = 200;
}
