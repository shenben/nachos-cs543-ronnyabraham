package nachos.threads;

import nachos.machine.*;

import java.util.TreeMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    // # Q5 Self test
    public static void selfTest()
    {
    	Semaphore s 	= new Semaphore(0);
    	KThread low 	= new KThread(new LowPriorityThread()).setName("Low1");
        KThread med 	= new KThread(new MediumPriorityThread(s)).setName("Med1");
        KThread high 	= new KThread(new HighPriorityThread(s)).setName("High1");
        
        high.fork();
        med.fork();
        low.fork();
    }
    private static final char dbgThread = 't';
    
    private static class HighPriorityThread implements Runnable
    {
    	HighPriorityThread(Semaphore sema4)
    	{
    		this.sema4 = sema4;
    	}
    	public void run()
    	{
    		for(int i = 0; i < 60; ++i)
    		{
    			Lib.debug(dbgThread, "$$$ HighPriorityThread running, i = " + i);
    		}
    		// wait for someone else to release the sema4
    		Lib.debug(dbgThread, "$$$ HighPriorityThread - before Semaphore.P()");
    		sema4.P();
    		Lib.debug(dbgThread, "$$$ HighPriorityThread - after Semaphore.P()");
    	}
    	private Semaphore sema4;
    }
    
    private static class MediumPriorityThread implements Runnable
    {
    	MediumPriorityThread(Semaphore sema4)
    	{
    		this.sema4 = sema4;
    	}
    	public void run()
    	{
    		// release the sema4
    		Lib.debug(dbgThread, "$$$ MediumPriorityThread before Semaphore.V()");
    		sema4.V();
    		Lib.debug(dbgThread, "$$$ MediumPriorityThread after Semaphore.V()");
    		for(int i = 0; i < 50; ++i)
    		{
    			Lib.debug(dbgThread, "$$$ MediumPriorityThread running, i = " + i);
    		}
    	}
    	private Semaphore sema4;
    }
    
    private static class LowPriorityThread implements Runnable
    {
    	LowPriorityThread()
    	{
    	}
    	public void run()
    	{
    		for(int i = 0; i < 20; ++i)
    		{
    			Lib.debug(dbgThread, "$$$ LowPriorityThread running, i = " + i);
    		}
    	}
    }
    
    
    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	//Q5 this method is different from the pickNextThread
	//since it will modify the state of the threads waiting
	//in the data structure PQueue. 
	public KThread nextThread() {
		//runningThread is the thread
		//that is hold the current lock 
		//since it has finished the nextThread will get the
		//lock and will start to run
		if(runningThread != null)
		{
			runningThread.listOfHPThreads.clear();
			
			if(runningThread.inheritedPriority)
			{
				
				runningThread.setPriority(runningThread.oldPriority);
				runningThread.inheritedPriority = false;
			}
			
			runningThread = null;
		}
	    Lib.assertTrue(Machine.interrupt().disabled());
	    
	    ThreadState nThread = pickNextThread();
	    if(nThread != null)
	    {
	    	Integer highP = pQueue.lastKey();
	    	LinkedList<ThreadState> threads = pQueue.get(highP);
	    	threads.remove(nThread);
	    	if(threads.size() == 0)
	    	{
	    		pQueue.remove(highP);
	    	}
	    	nThread.acquire(this);
	    	return nThread.thread;
	    }
	    
	    return null;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    if(!pQueue.isEmpty())	    	
	    {
	    	
	    	ThreadState retValue = null;
	    	Integer highP = pQueue.lastKey();
	    	LinkedList<ThreadState> threads = pQueue.get(highP);
	    	
	    	//there are multiple threads waiting for the given priority
	    	if(threads.size() > 1)
	    	{
	    		long prevWaitTime = 0; 
	    		for(int i = 0; i < threads.size() ; i++)
	    		{
	    			long currWaitTime = Machine.timer().getTime() - threads.get(i).insertTime;
	    			if(currWaitTime > prevWaitTime)
	    			{
	    				prevWaitTime = currWaitTime;
	    				retValue = threads.get(i);
	    			}
	    		}
	    	}
	    	else
	    	{
	    		//there is only one threads waiting for the given priority
	    		retValue = threads.get(0);
	    	}
	        
	        return retValue;
	    }
	    return null;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
	
	//Q5
	//This list maintains the threads in the queue that are
	//wait for the resource. 
	TreeMap<Integer,LinkedList<ThreadState>> pQueue = new TreeMap<Integer,LinkedList<ThreadState>>();
	public ThreadState runningThread; 
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    
	    setPriority(priorityDefault);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    //Q5 If there are no other higher priority threads
		// that are in the queue that are waiting then 
		//priority is same as the priority of the current
		//thread. If there are higher priority threads
	    //that are waiting the priority inversion takes place
		//the current low priority threads get the priority
		//of the highest priority among the waiting threads. 
		//Please note keys in TreeMap is already sorted
		if(listOfHPThreads.size() > 0)
		{
			//below call to lastKey provides the optimization we 
			//are looking for since the keys in treemap is already sorted. 
			Integer highP = listOfHPThreads.lastKey();						
			oldPriority = priority;
			priority = highP;
			inheritedPriority = true;
		}
	    return priority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    if(priority <= priorityMinimum)
	    {
	    	this.priority = priorityMinimum;
	    }
	    else if(priority >= priorityMaximum)
	    {
	    	this.priority = priorityMaximum;
	    }
	    else
	    {
	    	this.priority = priority;
	    }
	    
	    
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
		Integer currPriority = new Integer(this.getEffectivePriority());
		LinkedList<ThreadState> waitingTh = waitQueue.pQueue.get(currPriority);
		if(waitingTh == null)
		{
			waitingTh = new LinkedList<ThreadState>();
			waitQueue.pQueue.put(currPriority,waitingTh);
		}
		this.insertTime = Machine.timer().getTime();
	    waitingTh.add(this);
	    
	    //we also need to this thread to the list of higher 
	    //priority threads only if the current thread 
	    //priority is greater than the priority of the
	    //running thread. 
	    if(waitQueue.transferPriority)
	    {
	    	if( this.priority > waitQueue.runningThread.getPriority())
	    	{
	    		
	    		LinkedList<ThreadState> threads = waitQueue.runningThread.listOfHPThreads.get(currPriority);
	    		if( threads == null)
	    		{
	    			threads = new LinkedList<ThreadState>();	
	    			waitQueue.runningThread.listOfHPThreads.put(currPriority,threads);
	    		}	    		
	    		
	    		threads.add(this);
	    	}
	    }
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    if(waitQueue.transferPriority)
	    {
	    	waitQueue.runningThread = this;
	    }
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
	public int oldPriority;
	public boolean inheritedPriority = true;
	public long insertTime; 
	public TreeMap<Integer,LinkedList<ThreadState>> listOfHPThreads = new TreeMap<Integer,LinkedList<ThreadState>>();
    }
    
        	
}   