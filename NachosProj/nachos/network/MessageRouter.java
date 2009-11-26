package nachos.network;

import java.util.Random;
import java.util.Vector;

import nachos.machine.Machine;
import nachos.machine.OpenFile;

public class MessageRouter {
	
	public final static int maxPorts = 128;
	
	public MessageRouter()
	{
		currentLink = Machine.networkLink().getLinkAddress();
	}
	
	public OpenFile handleConnect(int id, int port)
	{
		int portAvail =-1;
		int i=0;
		for ( ; i < maxPorts+30; ++i)
		{
			portAvail = (new Random()).nextInt( maxPorts); 
		
			if (! activeSockets.contains ( currentLink + "." + portAvail + "." + id + "." + port ) )
				break;
			
			if ( i == maxPorts - 1 )
			{
				portAvail = -1;
				break;
			}
		}
		
		if( portAvail == -1)
		{
			return -1;
		}
		
		Channel soc = new Channel(currentLink,portAvail,id,port);
		
			
     }
	
	public OpenFile handleAccept(int port)
	{
		//todo need to finish implementation
		return new OpenFile();
	}
	private int currentLink;
	private Vector<String> activeSockets = new Vector<String> ();
}