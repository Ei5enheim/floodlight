/*  
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.util;

import java.lang.StringBuilder;

public class IPv4Address 
{
    protected int ip;
    protected byte mask = (byte) 32;

    public IPv4Address()
    {
        this.ip = 0xFFFFFFFF;
        mask = 0;
    }
    
    public IPv4Address(int IP)
    {
        this.ip = IP;
        this.mask = 0;

    }

    public IPv4Address (int IP, byte mask)
    {
        this.ip = IP;
	if (mask <= 32)
            this.mask = mask;
    }

    public int getIP()
    {
        return (ip);
    }

    public byte getMask()
    {
        return (mask);
    }

    public void setMask(byte mask)
    {
	if (mask <= 32)
	   this.mask = mask;
	else
	   this.mask = (byte) 32;
    }

    public void setIP (int ip)
    {
       this.ip = ip;
    }

    public int getCidrMask()
    {
	int cidrMask = 0xFFFFFFFF;

	if (mask == 32)
		return (0);

	return (cidrMask << mask);
    }

    public static int getCidrMask(byte mask)
    {
	int cidrMask = 0xFFFFFFFF;

	if (mask == 32)
		return (0);

	return (cidrMask << mask);
    }

    public boolean equals(Object o)
    {
	if (o == null)
           return (false);

        if (!(o instanceof IPv4Address))
           return (false);

        IPv4Address cousin = (IPv4Address) o;

        if ((this.ip & getCidrMask()) != (cousin.ip & cousin.getCidrMask()))
            return (false);

        return (true);
    }

    public static int getIP (byte[] array)
    {
	int ip = 0;
	for (byte b: array)
	    ip = (ip << 8) + (b & 0x00FF);
	return (ip);
    }

    public int hashCode ()
    {
	return (ip & getCidrMask());

    }
    public String toString()
    {
        int MASK = 0xFF000000;
        int SIGN = 0x000000FF;

        StringBuilder str = new StringBuilder();

	str.append(((ip & 0xFF000000) >> 24) & SIGN);
	str.append(".");
	str.append(((ip & 0x00FF0000) >> 16) & SIGN);
	str.append(".");
	str.append(((ip & 0x0000FF00) >> 8) & SIGN);
	str.append(".");
	str.append((ip & 0x000000FF) & SIGN);
	/*
        for (int i = 3; i >= 0; i--)
        {
            str.append(((ip & MASK) >> (i*8)) & SIGN);
            MASK = MASK >> 8;
            str.append(".");
        }*/
    
        str.append('/');
        str.append(Math.max(32 - mask, 0));
        return (str.toString());
    }

}
