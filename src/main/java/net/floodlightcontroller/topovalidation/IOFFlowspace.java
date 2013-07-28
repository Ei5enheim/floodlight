/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topovalidation;

import java.util.Map;
import java.util.SortedMap;

import net.floodlightcontroller.util.Vlan;
import net.floodlightcontroller.util.IPv4Address;

public interface IOFFlowspace
{
    final public static int VlanPCP_LEN = 8;
    final public static int VLAN_NONE = 0;
    final public static int OFPFW_ALL = ((1 << 22) - 1);
    final public static int OFPFW_IN_PORT = 1 << 0; /* Switch input port. */
    final public static int OFPFW_DL_VLAN = 1 << 1; /* VLAN id. */
    final public static int OFPFW_DL_SRC = 1 << 2; /* Ethernet source address. */
    final public static int OFPFW_DL_DST = 1 << 3; /*
                                                    * Ethernet destination
                                                    * address.
                                                    */
    final public static int OFPFW_DL_TYPE = 1 << 4; /* Ethernet frame type. */
    final public static int OFPFW_NW_PROTO = 1 << 5; /* IP protocol. */
    final public static int OFPFW_TP_SRC = 1 << 6; /* TCP/UDP source port. */
    final public static int OFPFW_TP_DST = 1 << 7; /* TCP/UDP destination port. */

    /*
     * IP source address wildcard bit count. 0 is exact match, 1 ignores the
     * LSB, 2 ignores the 2 least-significant bits, ..., 32 and higher wildcard
     * the entire field. This is the *opposite* of the usual convention where
     * e.g. /24 indicates that 8 bits (not 24 bits) are wildcarded.
     */
    final public static int OFPFW_NW_SRC_SHIFT = 8;
    final public static int OFPFW_NW_SRC_BITS = 6;
    final public static int OFPFW_NW_SRC_MASK = ((1 << OFPFW_NW_SRC_BITS) - 1) << OFPFW_NW_SRC_SHIFT;
    final public static int OFPFW_NW_SRC_ALL = 32 << OFPFW_NW_SRC_SHIFT;

    /* IP destination address wildcard bit count. Same format as source. */
    final public static int OFPFW_NW_DST_SHIFT = 14;
    final public static int OFPFW_NW_DST_BITS = 6;
    final public static int OFPFW_NW_DST_MASK = ((1 << OFPFW_NW_DST_BITS) - 1) << OFPFW_NW_DST_SHIFT;
    final public static int OFPFW_NW_DST_ALL = 32 << OFPFW_NW_DST_SHIFT;

    final public static int OFPFW_DL_VLAN_PCP = 1 << 20; /* VLAN priority. */
    final public static int OFPFW_NW_TOS = 1 << 21; /*
                                                     * IP ToS (DSCP field, 6
                                                     * bits).
                                                     */

    final public static int OFPFW_ALL_SANITIZED = (((1 << 22) - 1)
                                                   & ~OFPFW_NW_SRC_MASK & ~OFPFW_NW_DST_MASK)
                                                  | OFPFW_NW_SRC_ALL
                                                  | OFPFW_NW_DST_ALL;

    /* List of Strings for marshalling and unmarshalling to human readable forms */
    final public static String STR_IN_PORT = "in_port";
    final public static String STR_DL_DST = "dl_dst";
    final public static String STR_DL_SRC = "dl_src";
    final public static String STR_DL_TYPE = "dl_type";
    final public static String STR_DL_VLAN = "dl_vlan";
    final public static String STR_DL_VLAN_PCP = "dl_vlan_pcp";
    final public static String STR_NW_DST = "nw_dst";
    final public static String STR_NW_SRC = "nw_src";
    final public static String STR_NW_PROTO = "nw_proto";
    final public static String STR_NW_TOS = "nw_tos";
    final public static String STR_TP_DST = "tp_dst";
    final public static String STR_TP_SRC = "tp_src";

    public void setFlowSpaceType (boolean isSparse);
    public Long getRandomDataLayerDst ();
    public boolean getDataLayerDst (long mac);
    public OFFlowspace setDataLayerDst (Map<Long, Boolean> dataLayerDst);
    public OFFlowspace addDataLayerDst (long macAddress);
    public OFFlowspace addDataLayerDst (byte[] macAddress);
    public OFFlowspace blockDataLayerDst (long macAddress);
    public OFFlowspace blockDataLayerDst(byte[] macAddress);
    public OFFlowspace removeDataLayerDst (long macAddress);
    public OFFlowspace removeDataLayerDst (byte[] macAddress);
    public OFFlowspace addDataLayerDst(String mac);
    
    public Long getRandomDataLayerSrc();
    public boolean getDataLayerSrc (long mac);
    public OFFlowspace setDataLayerSrc (Map<Long, Boolean> dataLayerSource);
    public OFFlowspace addDataLayerSrc (long macAddress);
    public OFFlowspace addDataLayerSrc (byte[] macAddress);
    public OFFlowspace blockDataLayerSrc (long macAddress);
    public OFFlowspace blockDataLayerSrc (byte[] macAddress);
    public OFFlowspace removeDataLayerSrc (long macAddress);
    public OFFlowspace removeDataLayerSrc (byte[] macAddress);
    public OFFlowspace addDataLayerSrc (String mac);

    public boolean getDataLayerType (short etherType);
    public Short getRandomDataLayerType ();
    public OFFlowspace setDataLayerType (Map<Short, Boolean> dataLayerType);
    public OFFlowspace addDataLayerType (short dataLayerType);
    public OFFlowspace blockDataLayerType (short dataLayerType);
    public OFFlowspace removeDataLayerType (short dataLayerType);

    public Short getRandomDataLayerVlan ();
    public boolean getDataLayerVlan (short vlan);
    public OFFlowspace setDataLayerVlan (SortedMap <Vlan,
                                    Boolean> dataLayerVlan);
    public OFFlowspace addDataLayerVlan (short dataLayerVlan);
    public OFFlowspace addDataLayerVlan (short dataLayerVlan,
                                            short range);
    public OFFlowspace blockDataLayerVlan (short dataLayerVlan,
                                            short range);
    public OFFlowspace blockDataLayerVlan (short dataLayerVlan);
    public OFFlowspace removeDataLayerVlan (short dataLayerVlan,
                                            short range);

    public byte getRandomVlanPCP();
    public boolean getVlanPCP (byte pcp);
    public OFFlowspace setVlanPCP(byte pcp);
    public OFFlowspace addVlanPCP(byte pcp);
    public OFFlowspace removeVlanPCP(byte pcp);

    public IPv4Address getRandomNetworkDestination();
    public boolean getNetworkDestination (IPv4Address ip);
    public boolean getNetworkDestination (int ip);
    public OFFlowspace setNetworkDestination (Map<IPv4Address,
                                    Boolean> networkDestination);
    public OFFlowspace addNetworkDestination (IPv4Address ip,
                                              boolean bool);
    public OFFlowspace addNetworkDestination (int networkAddress,
                                                byte mask);
    public OFFlowspace addNetworkDestination (IPv4Address ip);
    public OFFlowspace blockNetworkDestination (int networkAddress,
                                                byte mask);
    public OFFlowspace blockNetworkDestination (IPv4Address ip);
    public OFFlowspace removeNetworkDestination (IPv4Address ip);
    public OFFlowspace removeNetworkDestination (int ip, byte mask);

    public short getRandomNetworkProtocol();
    public boolean getNetworkProtocol (short protocol);
    public OFFlowspace setNetworkProtocol (Map <Short, Boolean> networkProtocol);
    public OFFlowspace addNetworkProtocol (short prtcl);
    public OFFlowspace blockNetworkProtocol (short prtcl);
    public OFFlowspace removeNetworkProtocol (short prtcl);

    public IPv4Address getRandomNetworkSource();
    public boolean getNetworkSource (IPv4Address ip);
    public boolean getNetworkSource (int ip);
    public OFFlowspace setNetworkSource (Map<IPv4Address,
                                    Boolean> networkSource);
    public OFFlowspace addNetworkSource (IPv4Address ip,
                                            boolean bool);
    public OFFlowspace addNetworkSource (int networkAddress,
                                            byte mask);
    public OFFlowspace addNetworkSource (IPv4Address ip);
    public OFFlowspace blockNetworkSource (int networkAddress,
                                            byte mask);
    public OFFlowspace blockNetworkSource (IPv4Address ip);
    public OFFlowspace removeNetworkSource (IPv4Address ip);
    public OFFlowspace removeNetworkSource (int ip, byte range);

    public byte getRandomNetworkTOS ();
    public boolean getNetworkTOS (byte tos);
    public OFFlowspace setNetworkTypeOfService(long networkTOS);
    public OFFlowspace addNetworkTOS (byte tos);
    public OFFlowspace removeNetworkTOS (byte tos);

    public boolean getTPDst (short dst);
    public Short getRandomTPDst ();
    public OFFlowspace setTPDst (Map<Short, Boolean> transportDestination);
    public OFFlowspace addTPDst (short dst);
    public OFFlowspace blockTPDst (short dst);
    public OFFlowspace removeTPDst (short dst);

    public boolean getTPSrc (short src);
    public Short getRandomTPSrc ();
    public OFFlowspace setTPSrc (Map<Short, Boolean> transportSource);
    public OFFlowspace addTPSrc (short src);
    public OFFlowspace blockTPSrc (short src);
    public OFFlowspace removeTPSrc (short src);
}
