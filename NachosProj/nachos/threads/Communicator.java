package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator
{
    /**
     * Allocate a new communicator.
     */
    public Communicator()
    {
    	lock = new Lock();
    	condition = new Condition2(lock);
    	message = 0;
    	flag = false;
    	listenerComplete = false;
    	speakerComplete = false;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word)
    {
		Lib.debug(debug, "Beginning of speak method");

		if (!flag)
		{
			if (!lock.isHeldByCurrentThread())
			{
				lock.acquire();	
			}
			flag = true;
			Lib.debug(debug, "Speak ready, waiting for listener, going to sleep");
			condition.sleep();

			Lib.debug(debug, "Listener ready, speaker is continuing and going to send the word");
			message = word;
		}
		else
		{
			Lib.debug(debug, "Listener ready, speaker is sending word");
			message = word;
		}

		Lib.debug(debug, "Word sent, speaker attempting to notify the listener");
		
		if (!lock.isHeldByCurrentThread())
		{
			lock.acquire();	
		}
		
		condition.wake();
		
		//if the listener is not complete, wait to exit until listener has received
		//the word the speaker sent
		if (!listenerComplete)
		{
			if (!lock.isHeldByCurrentThread())
			{
				lock.acquire();	
			}
			
			condition.sleep();
		}
		
		//Speaker will now finish and wake the listener so he also can finish
		if (!lock.isHeldByCurrentThread())
		{
			lock.acquire();
		}
		
		condition.wake();
		
		if (lock.isHeldByCurrentThread())
		{
			lock.release();
		}	

		Lib.debug(debug, "Speaker FINISHED");
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */
    public int listen()
    {
		Lib.debug(debug, "Beginning of listen method");

		if (flag)
		{
			Lib.debug(debug, "Listener ready, proceeding to try to listen to message from speak");
			
			if (!lock.isHeldByCurrentThread())
			{
				lock.acquire();
			}

			condition.wake();

			Lib.debug(debug, "listener has called to wake speaker, now going to sleep");
			if (!lock.isHeldByCurrentThread())
			{
				lock.acquire();
			}

			condition.sleep();

			Lib.debug(debug, "listener has waken up after speaker finished");
		}
		else
		{
			flag = true;
			Lib.debug(debug, "Listener ready, waiting for speaker");
			if (!lock.isHeldByCurrentThread())
			{
				lock.acquire();
			}

			condition.sleep();
		}

		flag = false;
		Lib.debug(debug, "Listener received word, waking speaker so he can exit");
		if (!lock.isHeldByCurrentThread())
		{
			lock.acquire();
		}

		condition.wake();
		
		//sleep till speaker has exited
		if (!lock.isHeldByCurrentThread())
		{
			lock.acquire();
		}

		condition.sleep();
		
		if (lock.isHeldByCurrentThread())
		{
			lock.release();
		}
		
		if (speakerComplete)
		{
			//reset variables and exit
		    flag = false;
		    listenerComplete = false;
		    speakerComplete = false;	
		}
		
		Lib.debug(debug, "Listener FINISHED");
		return message;
	}


    private static class CommunicatorTest implements Runnable {
	CommunicatorTest(Communicator communicator, int type) {
	    this.communicator = communicator;
	    this.type = type;
	}

	public void run()
	{
		int receivedNumber = 0;

		if (1 == type) //speaker
		{		
			communicator.speak(1);
			Lib.debug(debug, "1st speak call sent " + 1);
			
			communicator.speak(2);
			Lib.debug(debug, "2nd speak call sent " + 2);
			
			communicator.speak(3);
			Lib.debug(debug, "3rd speak call sent " + 3);
			
			communicator.speak(4);
			Lib.debug(debug, "4th speak call sent " + 4);
			
			communicator.speak(5);
			Lib.debug(debug, "5th speak call sent " + 5);
		}
		else //listener
		{
			receivedNumber = communicator.listen();
			Lib.debug(debug, "1st listen call returned " + receivedNumber);

			receivedNumber = communicator.listen();
			Lib.debug(debug, "2nd listen call returned " + receivedNumber);

			receivedNumber = communicator.listen();
			Lib.debug(debug, "3rd listen call returned " + receivedNumber);

			receivedNumber = communicator.listen();
			Lib.debug(debug, "4th listen call returned " + receivedNumber);

			receivedNumber = communicator.listen();
			Lib.debug(debug, "5th listen call returned " + receivedNumber);
		}
	}

	private Communicator communicator;
	private int type;
    }

    /**
     * Test if this module is working.
     */
    public static void selfTest() {
    	Communicator commObject = new Communicator();
    	Communicator commObject2 = new Communicator();

    	new KThread(new CommunicatorTest(commObject, 1)).setName("speaker").fork();
    	new KThread(new CommunicatorTest(commObject, 2)).setName("listener").fork();
    	
    	new KThread(new CommunicatorTest(commObject2, 2)).setName("listener").fork();     	
    	new KThread(new CommunicatorTest(commObject2, 1)).setName("speaker").fork();
 	
    }

    private Lock lock;
    private static final char debug = 't';
    int message;
    Condition2 condition;
    boolean flag;
    boolean listenerComplete;
    boolean speakerComplete;

}
