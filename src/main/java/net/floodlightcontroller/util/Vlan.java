/*  
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.util;

import java.lang.StringBuilder;

public class Vlan implements Comparable
{
    public short vlan;
    public short range;

    public Vlan()
    {
        vlan = 0;
        range = 0;
    }
    
    public Vlan (short vlan)
    {
        this.vlan = vlan;
        this.range = 0;
    }

    public Vlan (short vlan, short range)
    {
        this.vlan = vlan;
        this.range = (short) (range-1);
    }

    public short getVlan()
    {
        return (vlan);
    }

    public short getRange()
    {
        return (range);
    }

    public boolean equals(Object o)
    {
        if (o == null)
            return (false);

        if (!(o instanceof Vlan))
            return (false);

        Vlan cousin = (Vlan) o;

        if ((this.vlan == cousin.getVlan()) && (this.range == cousin.getRange()))
            return (true);

        return (false);
    }

    public String toString()
    {
        StringBuilder str = new StringBuilder();

        str.append(vlan);
        if (range > 0) {
            str.append("-");
            str.append(vlan+range);
        }
        return (str.toString());
    }

    public int compareTo(Object b)
    {
        Vlan cousin = (Vlan) b;
        
        System.out.println("Comparing "+ toString() +" with "+ cousin.toString());
        if (((vlan <= cousin.vlan) && (cousin.vlan <= vlan + range)) ||
            ((vlan >= cousin.vlan) && (cousin.vlan + cousin.range >= vlan)))
            return (0);
        else if (cousin.vlan > vlan + range)
            return (-1);
        return (1);
    }
    
    public int hashCode()
    {
        return ((int) vlan);
    }
}
