/**
 * Author: Rajesh Gopidi
 *
 */

package net.floodlightcontroller.topology;

import java.util.Hashtable;

public class Ruletable
{
    private Hashtable<Integer, Hashtable< ? extends Object,
                                         ? extends Object> table;

    public Ruletable()
    {
        table = new Hashtable<Integer, Hashtable< ? extends Object,
                                                    ? extends Object> ();
    }

    


    public static int IPDST = 5;
    public static int IPSRC = 4;
    public static int VLAN = 3;
}
