/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.util;

import java.util.Arrays;
import net.floodlightcontroller.util.MACAddress;

/**
 * The class representing Delegated MAC address.
 *
 * @author Rajesh Gopidi
 */
public class DelegatedMAC {

    public static final int MAC_ADDRESS_LENGTH = 6;
	private long baseAddress;
	private int start;
	private int end;
    
	public DelegatedMAC ()
	{

	}

	public DelegatedMAC (long baseAddress)
	{
		this.baseAddress = baseAddress;
		this.start = 0;
		this.end = -1;
	}

	public DelegatedMAC (long baseAddress, int start)
	{
		this.baseAddress = baseAddress;
		this.start = start;
		this.end = start;
	}

    public DelegatedMAC (long baseAddress, int start, int end) 
	{
		this.baseAddress = baseAddress;
		this.start = start;
		this.end = end;
    }


	public long getBaseAddress()
	{
		return this.baseAddress;
	}

	public int getStart()
	{
		return this.start;
	}

	public int getEnd()
	{
		return this.end;
	}

	public void setBaseAddress(long baseAddress)
	{
		this.baseAddress = baseAddress;;
	}

	public void setStart(int start)
	{
		this.start = start;
	}

	public void setEnd(int end)
	{
		this.end = end;
	}

    
    public String toString()
	{
        StringBuilder builder = new StringBuilder();
        
		builder.append(baseAddress);
		builder.append(",");
		builder.append(start);
		builder.append(",");
		builder.append(end);
        
        return builder.toString();
    }

	private long getMask(int start, int end)
	{
		return (~((0x1L << end+1) - 0x1L) | ((0x1L << start) - 0x1L));
	}

	public boolean equals (Object o)
	{
		if (o == null)
			return false;

		DelegatedMAC obj = (DelegatedMAC) o;

		long mask = getMask(this.start, this.end);
		long addr2 = obj.getBaseAddress() & mask;
 		long addr1 = this.baseAddress & mask;

		if (addr1 == addr2)
			return true;
		
		return false;
	}
}
