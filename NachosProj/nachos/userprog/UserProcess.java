package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
	public UserProcess()
	{
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i=0; i<numPhysPages; i++)
		{
			pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
		}
		fileDescriptorManager = new FileDescriptorManager();
		openedFileMap = new HashMap<String, OpenFile>();
	}
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
	public static UserProcess newUserProcess()
	{
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
	}

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(memory, vaddr, data, offset, amount);

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(data, offset, memory, vaddr, amount);

	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
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

		// for now, just assume virtual addresses=physical addresses
		section.loadPage(i, vpn);
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
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }

    /**
     * Handle the creat() system call. 
     */
    private int handleCreate()
    {
    	boolean createFileIfDoesNotExist = true;
    	int returnValue = 0;

    	Processor processor = Machine.processor();
    	int vaddr = processor.readRegister(4);

    	byte[] data = new byte[256];
    	int fileSize = readVirtualMemory(vaddr,data);
    	String filename = readVirtualMemoryString(vaddr, fileSize);

    	FileSystem fileSystem = Machine.stubFileSystem();

    	//Lib.assertTrue(fileSystem == null, "UserProcess::handleCreate: No FileSystem");
    	if (fileSystem == null)
    	{
			String errorMsg = "UserProcess::handleCreate: No FileSystem, returning -1";
			Lib.debug(dbgProcess, errorMsg);
    		returnValue = -1;
    	}
    	else
    	{
    		OpenFile openedFile = 
    			fileSystem.open(filename, createFileIfDoesNotExist);
    		
    		if (openedFile == null)
    		{
    			String errorMsg = "UserProcess::handleCreate: File could not be opened, returning -1";
    			Lib.debug(dbgProcess, errorMsg);
        		returnValue = -1;
    		}
    		else
    		{
    			try
    			{
    				returnValue = fileDescriptorManager.insertFileDescriptor(filename);
        		
    				String outputMsg = "handleCreate created file: " + filename + " with file descriptor: " 
    				+ returnValue;
        			Lib.debug(dbgProcess, outputMsg);
        			
        			openedFileMap.put(filename, openedFile);
    			}
    			catch(EmptyStackException e)
    			{
    				String errorMsg = e.toString() + "(returning -1)";
    				Lib.debug(dbgProcess, errorMsg);
    				returnValue = -1;
    			}
    			finally
    			{
    			}
    		}
    	}
    	
    	return returnValue;
    }

    /**
     * Handle the open() system call. 
     */
    private int handleOpen()
    {
    	boolean createFileIfDoesNotExist = true;
    	int returnValue = 0;

    	Processor processor = Machine.processor();
    	int vaddr = processor.readRegister(4);
    	//System.out.printf("address read = %d\n", vaddr);

    	byte[] data = new byte[256];
    	int fileSize = readVirtualMemory(vaddr,data);
    	String filename = readVirtualMemoryString(vaddr, fileSize);

    	FileSystem fileSystem = Machine.stubFileSystem();

    	//Lib.assertTrue(fileSystem == null, "UserProcess::handleOpen: No FileSystem");
    	if (fileSystem == null)
    	{
    		String errorMsg = "UserProcess::handleOpen: No FileSystem, returning -1";
			Lib.debug(dbgProcess, errorMsg);
    		returnValue = -1;
    	}
    	else
    	{
    		OpenFile openedFile = 
    			fileSystem.open(filename, createFileIfDoesNotExist);
    		
    		if (openedFile == null)
    		{
    			String errorMsg = "UserProcess::handleOpen: File could not be opened, returning -1";
    			Lib.debug(dbgProcess, errorMsg);
        		returnValue = -1;
    		}
    		else
    		{
    			try
    			{
    				returnValue = fileDescriptorManager.insertFileDescriptor(filename);
        		
    				String outputMsg = "handleOpen opened file: " + filename + " with file descriptor: " 
    				+ returnValue;
        			Lib.debug(dbgProcess, outputMsg);
        			
        			openedFileMap.put(filename, openedFile);
    			}
    			catch(EmptyStackException e)
    			{
    				String errorMsg = e.toString() + "(returning -1)";
    				Lib.debug(dbgProcess, errorMsg);
    				returnValue = -1;
    			}
    			finally
    			{
    			}
    		}
    	}

    	return returnValue;
    }
    
    /**
     * Handle the read() system call. 
     */
    private int handleRead()
    {
    	Processor processor = Machine.processor();
    	int fileDescriptor = processor.readRegister(4);
    	int bufferValue = processor.readRegister(5);
    	int length= processor.readRegister(6);
    	//System.out.printf("address read = %d\n", vaddr);
    	int returnValue = 0;

    	byte[] buffer = new byte[length];
    	int transferedBytes = readVirtualMemory(bufferValue,buffer);
    	if (transferedBytes != length)
    	{
    		//I think there are cases when this is okay, and cases where it is an error, see description in syscall
    		//if this shouldn't happen, the try below should be in an else
    	}

    	try
    	{
    		String filename = fileDescriptorManager.getFilename(fileDescriptor);
    		OpenFile openedFile = openedFileMap.get(filename);

    		if (openedFile == null)
    		{
    			Lib.debug(dbgProcess, "UserProcess::handleRead: File not opened");
    			returnValue = -1;
    		}
    		else
    		{
    			int offset = 0;//temp fix, may need to look into
    			int numberOfBytesRead = openedFile.read(buffer, offset, length);
    			if (numberOfBytesRead == -1)
    			{
    				returnValue = -1;
    			}
    			else if (numberOfBytesRead != length)
    			{
    				//may have problem, maybe not
    				//returnValue = numberOfBytesRead
    				//or
    				//returnValue = -1;
    			}
    			else
    			{
    				String outputMsg = "Read " + numberOfBytesRead + " bytes from file: " + 
    				filename + "with file descriptor: " + fileDescriptor + ", in handleRead";
    				Lib.debug(dbgProcess, outputMsg);
    			}
    		}
    	}
    	catch(Exception e)
    	{
    		Lib.debug(dbgProcess, e.toString());
    		returnValue = -1;
    	}
    	finally
    	{
    	}
    	return returnValue;
    }
    
    /**
     * Handle the write() system call. 
     */
    private int handleWrite()
    {
    	//basically the same as write, except call openedFile.write(buffer, offset, length);
    	Lib.assertNotReached("Need to implement write system call");
    	return 0;
    } 

    /**
     * Handle the close() system call. 
     */
    private int handleClose()
    {
    	int returnValue = 0;
    	String filename;

    	Processor processor = Machine.processor();
    	int fileDescriptor = processor.readRegister(4);

    	//DONT SEEM TO NEED ALL THIS TO READ AN INT
    	//byte[] data = new byte[256];
    	//int fileSize = readVirtualMemory(vaddr,data);
    	//String fileDescriptorAsString = readVirtualMemoryString(vaddr, fileSize);
    	//int fileDescriptor = Integer.parseInt(fileDescriptorAsString.trim());


    	try
    	{
    		filename = fileDescriptorManager.getFilename(fileDescriptor);
    		
    		//BEGIN: added late on 10/15/2009
    		OpenFile openedFile = openedFileMap.get(filename);
    		if (openedFile == null)
    		{
    			Lib.debug(dbgProcess, "UserProcess::handleRead: File not opened");
    			returnValue = -1;
    		}
    		else
    		{
    			//look if this file is used somewhere else write being done?
    			
    			openedFile.close();
    			//this close releases any resources associated with the file
    			openedFileMap.remove(filename);

        		fileDescriptorManager.removeFileDescriptor(fileDescriptor);
    		//END:  added late on 10/15/2009

        		String outputMsg = "handleClose closed file: " + filename + " with file descriptor: " 
				+ fileDescriptor;
    			Lib.debug(dbgProcess, outputMsg);
    		}

    	}
    	catch(Exception e)
    	{
			Lib.debug(dbgProcess, e.toString());
    		returnValue = -1;
    	}
    	finally
    	{
    	}
    	
		String returnMsg = "handleClose returning (0=success, -1=error): " + returnValue;
		Lib.debug(dbgProcess, returnMsg);
    	return returnValue;
    } 
    
    /**
     * Handle the unlink() system call. 
     */
    private int handleUnlink()
    {
    	Lib.assertNotReached("Need to implement unlink system call");
    	return 0;
    } 
    
    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallCreate:
	    return handleCreate();
	case syscallOpen:
	    return handleOpen();
	case syscallRead:
	    return handleRead();
	case syscallWrite:
	    return handleWrite();
	case syscallClose:
	    return handleClose();
	case syscallUnlink:
	    return handleUnlink();


	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause)
    {
    	Processor processor = Machine.processor();

    	switch (cause)
    	{
    	case Processor.exceptionSyscall:
    		int result = handleSyscall(processor.readRegister(Processor.regV0),
    				processor.readRegister(Processor.regA0),
    				processor.readRegister(Processor.regA1),
    				processor.readRegister(Processor.regA2),
    				processor.readRegister(Processor.regA3)
    		);
    		processor.writeRegister(Processor.regV0, result);
    		processor.advancePC();
    		break;				       

    	default:
    		Lib.debug(dbgProcess, "Unexpected exception: " +
    				Processor.exceptionNames[cause]);
    		Lib.assertNotReached("Unexpected exception");
    	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
    
    private FileDescriptorManager fileDescriptorManager;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    
    private Map<String,OpenFile> openedFileMap;
    
    private static class FileDescriptorManager
    {
    	public FileDescriptorManager() 
    	{
    		fileDescriptorList = new String[listLength];
    		indexStack = new Stack<Integer>();
    		
    		//descriptors 0 and 1 are already used by standard input/output?
    		for (int i = listLength-1; i >=2; i--)
    		{
    			fileDescriptorList[i] = null;
    			indexStack.push(new Integer(i));
    		}
    	}

    	public String getFilename(int fileDescriptor)
    	{
    		String filename = fileDescriptorList[fileDescriptor];// TODO Auto-generated method stub
    		return filename;
    	}
    	
    	public int insertFileDescriptor(String filename) throws EmptyStackException
    	{
    		int index = indexStack.pop();
    		fileDescriptorList[index] = filename;
    		return index;
    	}
    	
    	public void removeFileDescriptor(int fileDescriptor) throws EmptyStackException, ArrayIndexOutOfBoundsException
    	{
    		fileDescriptorList[fileDescriptor] = null;
    		indexStack.push(fileDescriptor);
    	}

    	private String[] fileDescriptorList;
    	private Stack<Integer> indexStack;

    	private final int listLength = 16;
    }
    /*
    private static class FileDescriptorManager
    {
    	public FileDescriptorManager() 
    	{
    		fileDescriptorList = new String[listLength];
    		indexStack = new Stack<Integer>();
    		for (int i = listLength-1; i >=0; i--)
    		{
    			fileDescriptorList[i] = null;
    			indexStack.push(new Integer(i));
    		}
    	}

    	public String getFilename(int fileDescriptor)
    	{
    		String filename = fileDescriptorList[fileDescriptor];// TODO Auto-generated method stub
    		return filename;
    	}
    	
    	public int insertFileDescriptor(String filename) throws EmptyStackException
    	{
    		int index = indexStack.pop();
    		fileDescriptorList[index] = filename;
    		return index;
    	}
    	
    	public void removeFileDescriptor(int fileDescriptor) throws EmptyStackException, ArrayIndexOutOfBoundsException
    	{
    		fileDescriptorList[fileDescriptor] = null;
    		indexStack.push(fileDescriptor);
    	}

    	private String[] fileDescriptorList;
    	private Stack<Integer> indexStack;

    	private final int listLength = 16;
    }
    */
}
