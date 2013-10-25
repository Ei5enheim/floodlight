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
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.topovalidation.ITopoValidationService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.topology.IOFFlowspace;
import net.floodlightcontroller.topology.OFFlowspace;
import net.floodlightcontroller.topology.FlowspaceException;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.routing.Link;

import org.renci.doe.pharos.flow.Rules;
import org.renci.doe.pharos.flow.Rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.math.BigInteger;

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
    // IFloodlightModule routines
    public Collection<Class<? extends IFloodlightService>> getModuleServices()
    {
        return null;
    }

    public Map<Class<? extends IFloodlightService>,
               IFloodlightService> getServiceImpls()

    {
        return null;
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

    }

    public void startUp(FloodlightModuleContext context)
    {
        readGraph();
        logger.info("\n ****Starting the Graphdb reader module****\n");
    }

    // internal utility methods
    public void readGraph ()
    {
        HashSet <Long> switches = new HashSet <Long> ();
        HashSet <Vertex> vertices = new HashSet <Vertex>();
        String fileName = "./db/doe_pharos_domain_A.dex";
        File file = new File(fileName);
        List<Edge> pruneList = new ArrayList<Edge>();
        BufferedInputStream stream = null;

        try {
            stream = new BufferedInputStream(
                                        new FileInputStream("graph/doe_pharos_domain_A.xml"));
        } catch (FileNotFoundException fnf) {
    
        }

        String localDomain = null;
        flowspace = new ConcurrentHashMap <NodePortTuple, IOFFlowspace[]>();
        ruleTransTables = new ConcurrentHashMap <Link, Rules>();
        links = new ArrayList<Link>();
        domainMapper = new HashMap<Long, String>();

        if (file.exists())
            file.delete();

        Graph graph = new DexGraph(fileName);
        try {
            GraphMLReader.inputGraph(graph, stream);
        } catch (IOException io) {
           logger.trace("caught an exception"); 
        }
        for (Edge e: graph.getEdges()) {
            try {
                processEdge(e, vertices, switches, pruneList, 
                            links, flowspace, localDomain);
            } catch (FlowspaceException t) {
    
            }
        }

        if (!pruneList.isEmpty()) {
            Edge[] array = new Edge[pruneList.size()];
            array = pruneList.toArray(array);
            for (Edge e: array) {
                try {
                    processEdge(e, vertices, switches, pruneList,
                                links, flowspace, localDomain);
                } catch (FlowspaceException t) {
                
                }
            }
        }
        //linkManager.blockLinkDiscovery();
        floodlightProvider.addFlowspace(flowspace);
        floodlightProvider.addRuleTransTables(ruleTransTables);
        floodlightProvider.addDomainMapper(domainMapper);
        floodlightProvider.addSwitches(switches);
        //UNCOMMENT need to reconsider this
        try {
            synchronized(switches) {
                switches.wait();
            }
        } catch (InterruptedException ie) {
           logger.trace("***********caught an InterruptedException ***********"); 
        }
        topoValidator.validateTopology(links, ruleTransTables, false);
        //Invoke the topovalidator here using the topolock here.
        // Need to deal with the combination part and leaving out the
        // single node from a seperate domain and the lock part.
        Link[] topoLinks = new Link[links.size()];
        topoLinks = links.toArray(topoLinks);
        linkManager.addLinks(topoLinks);
        //linkManager.enableLinkDiscovery();

    }

    private void processEdge (Edge e, HashSet <Vertex> vertices,
                              HashSet <Long> switches,
                              List<Edge> pruneList,
                              List<Link> links,
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

    protected IFloodlightProviderService floodlightProvider;
    protected ILinkDiscoveryService linkManager;
    protected ITopoValidationService topoValidator;
    protected static Logger logger;
    protected Map <Long, String> domainMapper;
    protected Map <NodePortTuple, IOFFlowspace[]>  flowspace;
    protected Map <Link, Rules> ruleTransTables;
    protected List<Link> links;
    private String wantedLabel = "Is connected to";
    private int FLOWSPACE_SIZE = 2, INGRESS = 0, EGRESS = 1;
}
