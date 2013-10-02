/*
 * Author: Rajesh Gopidi
 * Grad student at UNC Chapel Hill
 */

package net.floodlightcontroller.topovalidation.internal;

public class TopoLock
{
    int verifiedCnt;
    int verifyCnt;
    int retryCount;
    boolean inProgress;

    public TopoLock ()
    {

    }

    public void updateTotalCnt(int totalCount)
    {
        this.verifyCnt += totalCount;
    }

    public void incrVerifiedCnt()
    {
        synchronized (this) {
			if (inProgress)
            	verifiedCnt++;
        }
    }

    public void taskComplete()
    {
        inProgress = false;
    }

	public void taskInProgress()
	{
		inProgress = true;
	}

	public void getTaskStatus()
	{
		return (inProgress);
	}

	public void setTopoValidStatus()
	{
		isTopoValid = true;	
	}

    public boolean checkValidationStatus ()
    {
        return (verifyCnt == verifiedCnt);
    }

    public void incrementRetryCount ()
    {
		retryCount++;
    }

	public void reset ()
	{
		verifiedCnt = 0;
		verifyCnt = 0;
		inProgress = false;
		retryCount = 0;
	}

    public int getRetryCount ()
    {
		return (retryCount);
    }
}
