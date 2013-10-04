/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topovalidation;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.topovalidation.IOFFlowspace;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.serializers.OFFlowspacePointJSONSerializer;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.util.U8;

public class OFFlowspacePoint implements Cloneable, IOFFlowspace
{
    protected int wildcards;
    protected Long dataLayerSource;
    protected Long dataLayerDestination;
    protected short dataLayerVlan;
    protected byte dataLayerVlanPCP;
    protected short dataLayerType;
    protected byte networkTOS;
    protected byte networkProtocol;
    protected int networkSource;
    protected int networkDestination;
    protected short transportSource;
    protected short transportDestination;

    /**
     * By default, create a OFFlowspacePoint that verifies everything (mostly because it's
     * the least amount of work to make a valid OFFlowspacePoint)
     */
    public OFFlowspacePoint()
    {
        this.wildcards = OFPFW_ALL;
        this.dataLayerVlan = Ethernet.VLAN_UNTAGGED;
        this.dataLayerVlanPCP = 0;
        this.dataLayerType = 0;
        this.networkProtocol = 0;
        this.networkTOS = 0;
        this.networkSource = 0;
        this.networkDestination = 0;
        this.transportDestination = 0;
        this.transportSource = 0;
    }

    // utility method 
    private long byteArray2Long(byte[] array)
    {
        long value = 0;
        int LENGTH = array.length;

        for (int i = 0; i < LENGTH; i++)                         
        {
            value = (value << 8) | (array[i] & 0x00FF);                  
        }
        return (value);
    }

    /**
     * Get dl_dst
     * 
     * @return an arrays of bytes
     */
    public Long getDataLayerDestination()
    {
        return this.dataLayerDestination;
    }

    /**
     * Set dl_dst
     * 
     * @param dataLayerDestination
     */
    public OFFlowspacePoint setDataLayerDestination(long dataLayerDestination)
    {
        this.dataLayerDestination = dataLayerDestination;
        return this;
    }

    /**
     * Set dl_dst, but first translate to byte[] using HexString
     * 
     * @param mac
     *            A colon separated string of 6 pairs of octets, e..g.,
     *            "00:17:42:EF:CD:8D"
     */
    public OFFlowspacePoint setDataLayerDestination(String mac) {
        byte bytes[] = HexString.fromHexString(mac);
        if (bytes.length != 6)
                              throw new IllegalArgumentException(
                                                                 "expected string with 6 octets, got '"
                                                                         + mac
                                                                         + "'");
        this.dataLayerDestination = byteArray2Long(bytes);
        return this;
    }

    /**
     * Get dl_src
     * 
     * @return an array of bytes
     */
    public long getDataLayerSource()
    {
        return this.dataLayerSource;
    }

    /**
     * Set dl_src
     * 
     * @param dataLayerSource
     */
    public OFFlowspacePoint setDataLayerSource(long dataLayerSource)
    {
        this.dataLayerSource = dataLayerSource;
        return this;
    }

    /**
     * Set dl_src, but first translate to byte[] using HexString
     * 
     * @param mac
     *            A colon separated string of 6 pairs of octets, e..g.,
     *            "00:17:42:EF:CD:8D"
     */
    public OFFlowspacePoint setDataLayerSource (String mac) {
        byte bytes[] = HexString.fromHexString(mac);
        if (bytes.length != 6)
                              throw new IllegalArgumentException(
                                                                 "expected string with 6 octets, got '"
                                                                         + mac
                                                                         + "'");
        this.dataLayerSource = byteArray2Long(bytes);
        return this;
    }

    /**
     * Get dl_type
     * 
     * @return ether_type
     */
    public short getDataLayerType() {
        return this.dataLayerType;
    }

    /**
     * Set dl_type
     * 
     * @param dataLayerType
     */
    public OFFlowspacePoint setDataLayerType(short dataLayerType) {
        this.dataLayerType = dataLayerType;
        return this;
    }

    /**
     * Get dl_vlan
     * 
     * @return vlan tag; VLAN_NONE == no tag
     */
    public short getDataLayerVlan() {
        return this.dataLayerVlan;
    }

    /**
     * Set dl_vlan
     * 
     * @param dataLayerVlan
     */
    public OFFlowspacePoint setDataLayerVlan(short dataLayerVlan) {
        this.dataLayerVlan = dataLayerVlan;
        return this;
    }

    /**
     * Get dl_vlan_pcp
     * 
     * @return
     */
    public byte getDataLayerVlanPCP() {
        return this.dataLayerVlanPCP;
    }

    /**
     * Set dl_vlan_pcp
     * 
     * @param pcp
     */
    public OFFlowspacePoint setDataLayerVlanPCP(byte pcp) {
        this.dataLayerVlanPCP = pcp;
        return this;
    }

    /**
     * Get in_port
     * 
     * @return
     */
    public short getInputPort() {
        return this.inputPort;
    }

    /**
     * Set in_port
     * 
     * @param inputPort
     */
    public OFFlowspacePoint setInputPort(short inputPort) {
        this.inputPort = inputPort;
        return this;
    }

    /**
     * Get nw_dst
     * 
     * @return
     */
    public int getNetworkDestination() {
        return this.networkDestination;
    }

    /**
     * Set nw_dst
     * 
     * @param networkDestination
     */
    public OFFlowspacePoint setNetworkDestination(int networkDestination) {
        this.networkDestination = networkDestination;
        return this;
    }

    /**
     * Parse this match's wildcard fields and return the number of significant
     * bits in the IP destination field. NOTE: this returns the number of bits
     * that are fixed, i.e., like CIDR, not the number of bits that are free
     * like OpenFlow encodes.
     * 
     * @return a number between 0 (matches all IPs) and 63 ( 32>= implies exact
     *         match)
     */
    public int getNetworkDestinationMaskLen() {
        return Math.max(32 - ((wildcards & OFPFW_NW_DST_MASK) >> OFPFW_NW_DST_SHIFT),
                        0);
    }

    /**
     * Parse this match's wildcard fields and return the number of significant
     * bits in the IP destination field. NOTE: this returns the number of bits
     * that are fixed, i.e., like CIDR, not the number of bits that are free
     * like OpenFlow encodes.
     * 
     * @return a number between 0 (matches all IPs) and 32 (exact match)
     */
    public int getNetworkSourceMaskLen() {
        return Math.max(32 - ((wildcards & OFPFW_NW_SRC_MASK) >> OFPFW_NW_SRC_SHIFT),
                        0);
    }

    /**
     * Get nw_proto
     * 
     * @return
     */
    public byte getNetworkProtocol() {
        return this.networkProtocol;
    }

    /**
     * Set nw_proto
     * 
     * @param networkProtocol
     */
    public OFFlowspacePoint setNetworkProtocol(byte networkProtocol) {
        this.networkProtocol = networkProtocol;
        return this;
    }

    /**
     * Get nw_src
     * 
     * @return
     */
    public int getNetworkSource() {
        return this.networkSource;
    }

    /**
     * Set nw_src
     * 
     * @param networkSource
     */
    public OFFlowspacePoint setNetworkSource(int networkSource) {
        this.networkSource = networkSource;
        return this;
    }

    /**
     * Get nw_tos OFFlowspacePoint stores the ToS bits as top 6-bits, so right shift by 2
     * bits before returning the value
     * 
     * @return : 6-bit DSCP value (0-63)
     */
    public byte getNetworkTOS() {
        return (byte) ((this.networkTOS >> 2) & 0x3f);
    }

    /**
     * Set nw_tos OFFlowspacePoint stores the ToS bits as top 6-bits, so left shift by 2
     * bits before storing the value
     * 
     * @param networkTOS
     *            : 6-bit DSCP value (0-63)
     */
    public OFFlowspacePoint setNetworkTOS(byte networkTOS) {
        this.networkTOS = (byte) (networkTOS << 2);
        return this;
    }

    /**
     * Get tp_dst
     * 
     * @return
     */
    public short getTPDst() {
        return this.transportDestination;
    }

    /**
     * Set tp_dst
     * 
     * @param transportDestination
     */
    public OFFlowspacePoint setTPDst(short transportDestination) {
        this.transportDestination = transportDestination;
        return this;
    }

    /**
     * Get tp_src
     * 
     * @return
     */
    public short getTPSrc() {
        return this.transportSource;
    }

    /**
     * Set tp_src
     * 
     * @param transportSource
     */
    public OFFlowspacePoint setTPSrc(short transportSource) {
        this.transportSource = transportSource;
        return this;
    }

    /**
     * Get wildcards
     * 
     * @return
     */
    public int getWildcards() {
        return this.wildcards;
    }

    /**
     * Get wildcards
     * 
     * @return
     */
    public Wildcards getWildcardObj() {
        return Wildcards.of(wildcards);
    }

    /**
     * Set wildcards
     * 
     * @param wildcards
     */
    public OFFlowspacePoint setWildcards(int wildcards) {
        this.wildcards = wildcards;
        return this;
    }

    /** set the wildcard using the Wildcards convenience object */
    public OFFlowspacePoint setWildcards(Wildcards wildcards) {
        this.wildcards = wildcards.getInt();
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 131;
        int result = 1;
        result = prime * result + dataLayerType;
        result = prime * result + dataLayerVlan;
        result = prime * result + dataLayerVlanPCP;
        result = prime * result + inputPort;
        result = prime * result + networkDestination;
        result = prime * result + networkProtocol;
        result = prime * result + networkSource;
        result = prime * result + networkTOS;
        result = prime * result + transportDestination;
        result = prime * result + transportSource;
        result = prime * result + wildcards;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof OFFlowspacePoint)) {
            return false;
        }
        OFFlowspacePoint other = (OFFlowspacePoint) obj;
        if (dataLayerDestination !=  other.dataLayerDestination) {
            return false;
        }
        if (dataLayerSource != other.dataLayerSource) {
            return false;
        }
        if (dataLayerType != other.dataLayerType) {
            return false;
        }
        if (dataLayerVlan != other.dataLayerVlan) {
            return false;
        }
        if (dataLayerVlanPCP != other.dataLayerVlanPCP) {
            return false;
        }
        if (inputPort != other.inputPort) {
            return false;
        }
        if (networkDestination != other.networkDestination) {
            return false;
        }
        if (networkProtocol != other.networkProtocol) {
            return false;
        }
        if (networkSource != other.networkSource) {
            return false;
        }
        if (networkTOS != other.networkTOS) {
            return false;
        }
        if (transportDestination != other.transportDestination) {
            return false;
        }
        if (transportSource != other.transportSource) {
            return false;
        }
        if ((wildcards & OFFlowspacePoint.OFPFW_ALL) != (other.wildcards & OFPFW_ALL)) { // only
            // consider
            // allocated
            // part
            // of
            // wildcards
            return false;
        }
        return true;
    }

    /**
     * Implement clonable interface
     */
    @Override
    public OFFlowspacePoint clone() {
        try {
            OFFlowspacePoint ret = (OFFlowspacePoint) super.clone();
            ret.dataLayerDestination = this.dataLayerDestination.clone();
            ret.dataLayerSource = this.dataLayerSource.clone();
            return ret;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

}
