package nachos.vm;

import java.util.Enumeration;
import java.util.Hashtable;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.userprog.UserKernel.MemoryManager;
import nachos.userprog.UserKernel.Page;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
	super();
	tlbIndexCounter = 0;
	tlbInitialized = false;
	tempFrameNumber = 0;
    }

    /**
     * Initializes Memory as required for a VM Process
     */
    @Override
	protected void initializeMemory()
	{
   	
		//Each process will be allocated 15 pages, since 8 did not seem to be sufficient
		int numPhysPages = 15;
		MemoryManager memoryManager = UserKernel.memoryManager; 
		
		if (memoryManager == null)
		{
			Lib.debug(dbgProcess, "memory manager is null");
		}
			
		virtualMemory = memoryManager.getPages(numPhysPages);
		
		int ppn = 0;
		Page currentPage;
		for (int i=0; i<numPhysPages; i++)
		{
			currentPage = virtualMemory.get(i);
			ppn = currentPage.getValue();
//			Lib.debug(dbgVM, "PID: "+ processId + " VPN: " + i + " PPN: " + ppn);
			VMKernel.ipt.addToInvertedPageTable(processId, i,ppn);
		}
	}
	
    /**
     * Destroys the memory when exiting from a VM Process
     */
    @Override
	protected void destoryMemory()
	{
		//no memory destruction required for tlb
	}
	
    /**
     * Returns whether an address is valid  
     *
     * @return	the validity of the address.
     */
    @Override
	protected boolean isValidMemoryAddress(int possibleAddress)
	{
		 return true;
	}
    
    /**
     * Sets any required read flags  
     *
     * @param	the virtual page number.
     */
    @Override
	protected void setReadFlags(int vpn)
	{
    	Processor processor = Machine.processor();
	    for (int i=0; (i<processor.getTLBSize()); i++) {
	    	TranslationEntry translationEntry = processor.readTLBEntry(i);
	    	if (translationEntry.valid && translationEntry.vpn == vpn)
	    	{
	    		translationEntry.used = true;
	    	}
	    }
	}
	
    /**
     * Sets any required write flags  
     *
     * @param	the virtual page number.
     */
    @Override
	protected void setWriteFlags(int vpn)
	{
    	Processor processor = Machine.processor();
	    for (int i=0; (i<processor.getTLBSize()); i++) {
	    	TranslationEntry translationEntry = processor.readTLBEntry(i);
	    	if (translationEntry.valid && translationEntry.vpn == vpn)
	    	{
	    		translationEntry.used = true;
	    		translationEntry.dirty = true;
	    		
	    	}
	    }
	}
    
    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    	
    	Processor processor = Machine.processor();
    	for (int i=0; (i<processor.getTLBSize()); i++) {
	    	TranslationEntry translationEntry = processor.readTLBEntry(i);
    		if (translationEntry.valid)
    		{
    			VMKernel.ipt.addToInvertedPageTable(
    					processId, translationEntry);
    		}
	    }
    	
//    	
//    	Hashtable<Integer, TranslationEntry> translationEntryMapping =
//    		VMKernel.ipt.getTranslationEntryMappingForProcessId(processId);
//    	
//    	Enumeration<Integer> e =translationEntryMapping.keys();
//    	
//    	while (e.hasMoreElements())
//    	{
//    		Integer integerValue = e.nextElement();
//    		int virtualPageNumber = integerValue.intValue();
//    		if (VMKernel.ipt.isDirty(processId, virtualPageNumber))
//    		{
//    			//TODO:writeToDisk(intValue, translationEntry)
//    		}
//    		VMKernel.ipt.setValid(processId, virtualPageNumber, false);
//    	}
    	
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
    	Processor processor = Machine.processor();
    	Hashtable<Integer, TranslationEntry> innerTable = 
    		VMKernel.ipt.getTranslationEntryMappingForProcessId(processId);
    	int counter = 0;
    	Enumeration<TranslationEntry> e = innerTable.elements();
    	
    	while (counter < processor.getTLBSize() && e.hasMoreElements())
    	{
    		TranslationEntry translationEntryToRemove = e.nextElement();
    		processor.writeTLBEntry(counter, translationEntryToRemove);
    		VMKernel.ipt.removeTranslationEntry(
    				processId, translationEntryToRemove.vpn);
    		counter++;
    	}
    	while (counter < processor.getTLBSize())
    	{
    		processor.writeTLBEntry(counter, 
    				new TranslationEntry(0, 0, false, false, false, false));
    	}
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return	<tt>true</tt> if successful.
     */
    protected boolean loadSections() {
 	if (numPages > Machine.processor().getNumPhysPages() ) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;

		int physicalAddress = VMKernel.ipt.getPhysicalPageNumber(processId, vpn);
		
		// map virtual addresses to physical addresses
		section.loadPage(i, physicalAddress);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {

    }    

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exception</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause)
    {
    	Processor processor = Machine.processor();
      	switch (cause) 
    	{
    		case Processor.exceptionTLBMiss:
    		{
    			handleTLBMissException();
    			break;
    		}
    		default:
    		{
    			super.handleException(cause);
    			break;
    		}
    	}
    }
    
    void handleTLBMissException()
    {
    	Processor processor = Machine.processor();
    	int vaddr = processor.readRegister(Machine.processor().regBadVAddr);
    	
    	int virtualPageNumber = vaddr / pageSize;
    	int realMemOffset = vaddr % pageSize;
    	
    	TranslationEntry iptTranslationEntry = 
    		VMKernel.ipt.getTranslationEntry(processId, virtualPageNumber);
    	
    	
    	if (!tlbInitialized)
    	{
    		processor.writeTLBEntry(tlbIndexCounter, iptTranslationEntry);
    		if (tlbIndexCounter == processor.getTLBSize() -1)
    		{
    			tlbInitialized = true;
    		}
    	}
    	else
    	{
    		TranslationEntry tlbTranslationEntry = 
    			processor.readTLBEntry(tlbIndexCounter);
    		if (tlbTranslationEntry.valid)
    		{
    			VMKernel.ipt.addToInvertedPageTable(
    					processId, tlbTranslationEntry);
    		}
    		processor.writeTLBEntry(tlbIndexCounter, iptTranslationEntry);
    	}
		tlbIndexCounter++;
		tlbIndexCounter = tlbIndexCounter % processor.getTLBSize();
		
		tempFrameNumber = iptTranslationEntry.ppn;
    }
    
    /**
     * returns the Frame Number using the implementation
     *  required for a VM Process
     *
     * @return	the frame number associated with that page
     *  number.
     */
    @Override
    protected int returnFrameNumber(int virtualPageNumber, int offset)//kludge center
    {
//    	int realPageNumber =  VMKernel.tlb.getPhysicalPageNumber(processId, virtualPageNumber);
    	Processor processor = Machine.processor();
    	
    	int frameNumber = -1;
	    for (int i=0; (i<processor.getTLBSize()) && (frameNumber == -1); i++) {
	    	TranslationEntry translationEntry = processor.readTLBEntry(i);
	    	if (translationEntry.valid && translationEntry.vpn == virtualPageNumber)
	    	{
	    		frameNumber = translationEntry.ppn;
	    	}
	    }
	    if (frameNumber == -1)
    	{
    		int vaddress = (virtualPageNumber * Processor.pageSize) + offset;
    		Lib.debug(dbgProcess, "\t\tTLB miss");
    		Machine.processor().writeRegister(Machine.processor().regBadVAddr, vaddress);
    		handleException(Processor.exceptionTLBMiss);
    		frameNumber = tempFrameNumber;
    	}
    	return frameNumber;	
    }

    private int tempFrameNumber;
    private int tlbIndexCounter;
    private boolean tlbInitialized;
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
}
