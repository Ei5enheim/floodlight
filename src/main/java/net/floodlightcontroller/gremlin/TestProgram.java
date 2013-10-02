import java.io.*;
import java.util.Collection;
import java.net.*;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.impls.dex.DexGraph;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLReader;
import com.tinkerpop.blueprints.util.io.graphml.GraphMLWriter;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.pipes.PipeFunction;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.pipes.filter.*;
import com.tinkerpop.blueprints.Predicate;
import com.tinkerpop.pipes.filter.CollectionFilterPipe;
import com.tinkerpop.blueprints.Contains;
import com.tinkerpop.blueprints.Direction;

public class TestProgram
{
    final static String wantedLabel = "Is connected to";
    static String localDomain = null;

    public static void main (String[] args) throws Exception
    {
        HashSet <String> nodes = new HashSet <String>();
        HashSet <Vertex> vertices = new HashSet <Vertex>();
        List<Edge> pruneList = new ArrayList<Edge>();
        String fileName = "./db/doe_pharos_domain_A.dex";
        TinkerGraph graph = new TinkerGraph();

        File file = new File(fileName);
        if (file.exists())
            file.delete();
        //Graph graph = new DexGraph(fileName);
        BufferedInputStream stream = new BufferedInputStream(new FileInputStream("doe_pharos_domain_A.xml"));
        GraphMLReader.inputGraph(graph, stream);
        //GraphMLWriter.outputGraph(graph, new FileOutputStream("B_1.xml"));
        // First lets get all the nodes irrespective of the ingress and egress flowspaces
        GremlinPipeline <Edge, Edge> pipe = new GremlinPipeline <Edge, Edge> (graph.getEdges());
        //filerEdges.
        //GremlinPipeline <Edge, Vertex> pipe = new GremlinPipeline <Edge, Vertex>(graph.getEdges());
        pipe.add(new LabelFilterPipe (new Predicate() {
                                            public boolean evaluate (final Object first, final Object second) {
                                                        //System.out.println("First: "+ first + " Second: "+ second);
                                                        return (first.equals(second));
                                            }
                                      }, wantedLabel));
        /*pipe.filter(new PipeFunction <Edge, Boolean> () {
                                                    private String wantedLabel = "is connected to";
                                                    public Boolean compute (Edge edge) {
                                                            return (edge.getLabel().equals(wantedLabel));
                                                    }
                                            }).inV();

        pipe.outV().add(new CollectionFilterPipe <Vertex> ((Collection <Vertex>) nodes, Contains.NOT_IN) {
                            private boolean checkCollection(final Vertex rightObject)
                            {
                                String dpid = rightObject.getProperty("DPID");
                                if (!this.storedCollection.contains(dpid)) {
                                    this.storedCollection.add(dpid);
                                    return (false);
                                }
                                return (true);
                            }
                        });
        pipe.outV().add(new FilterFunctionPipe <Vertex> (new PipeFunction <Vertex, Boolean> () {
                                                            HashSet <String> nodes = new HashSet <String>();
                                                            public Boolean compute (Vertex node) {
                                                                String dpid = node.getProperty("DPID");
                                                                if (!nodes.contains(dpid)) {
                                                                    nodes.add(dpid);
                                                                    return (true);
                                                                }
                                                                return (false);
                                                            }
                                                        }));
        */
        for (Edge e: graph.getEdges()) {
            processEdge(e, vertices, nodes, pruneList);
        }

        if (!pruneList.isEmpty()) {
            Edge[] array = new Edge[pruneList.size()];
            for (Edge e: array)
                processEdge(e, vertices, nodes, pruneList);
        }
    }

    public static void processEdge (Edge e, HashSet <Vertex> vertices,
                              HashSet <String> nodes,
                              List<Edge> pruneList)
    {
        Vertex node = null, outNode = null;

        node = e.getVertex(Direction.IN);
        outNode = e.getVertex(Direction.OUT);

        if (!node.getProperty("domain").equals(outNode.getProperty("domain"))){
            if (localDomain == null) {
                pruneList.add(e);
                return;
            }
        } else {
            localDomain = node.getProperty("domain");
        }

        if (!vertices.contains(node)  &&
            node.getProperty("domain").equals(localDomain)) {
            String dpid = node.getProperty("DPID");
            vertices.add(node);
            if (!nodes.contains(dpid)) {
                nodes.add(dpid);
                System.out.println("In Vertex is " + node.getProperty("DPID") + ", " + node.getId());
            }
            if (e.getLabel().equals(wantedLabel)) {
                System.out.println("Domain: "+node.getProperty("domain")+" Node: "+ node.getProperty("DPID")+" Port: "+ node.getProperty("Port")+"[Ingress] Flowspace: "+node.getProperty("Flowspace"));
            } else {
                System.out.println("Domain: "+node.getProperty("domain")+" Node: "+ node.getProperty("DPID")+" Port: "+ node.getProperty("Port")+"[Egress] Flowspace: "+node.getProperty("Flowspace"));
            }
        }
        node = outNode;
        if (!vertices.contains(node) &&
            node.getProperty("domain").equals(localDomain)) {
            String dpid = node.getProperty("DPID");
            vertices.add(node);
            if (!nodes.contains(dpid)) {
                nodes.add(dpid);
                System.out.println("Vertex is " + node.getProperty("DPID") + ", " + node.getId());
            }
            if (e.getLabel().equals(wantedLabel)) {
                System.out.println("Domain: "+node.getProperty("domain")+" Node: "+ node.getProperty("DPID")+" Port: "+ node.getProperty("Port")+"[Egress] Flowspace: "+node.getProperty("Flowspace"));
            } else {
                System.out.println("Domain: "+node.getProperty("domain")+" Node: "+ node.getProperty("DPID")+" Port: "+ node.getProperty("Port")+"[Ingress] Flowspace: "+node.getProperty("Flowspace"));
            }
        }
    }

}
