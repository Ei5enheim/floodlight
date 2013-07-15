/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.circuitswitching;

import net.floodlightcontroller.core.module.IFloodlightService;
import java.util.Collection;
import java.util.LinkedList;

public interface ICircuitSwitching extends IFloodlightService
{
    public LinkedList<byte[]> getPathID (long srcID, long dstID, long cookie);
}

