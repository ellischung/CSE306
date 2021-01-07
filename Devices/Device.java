package osp.Devices;
/**
   Ellis Chung
   ELLCHUNG
   111135169

   I pledge my honor that all parts of this project were done by me individually, without
   collaboration with anyone, and without consulting any external sources that provide
   full or partial solutions to a similar project.
   I understand that breaking this pledge will result in an F for the entire course.
*/
/**
    This class stores all pertinent information about a device in
    the device table.  This class should be sub-classed by all
    device classes, such as the Disk class.

    @OSPProject Devices
*/

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Memory.*;
import osp.FileSys.*;
import osp.Tasks.*;
import java.util.*;

public class Device extends IflDevice
{
    // declare variables (previous/last cylinder) here
    public static int prevCylinder;
    public static int head;

    /**
        This constructor initializes a device with the provided parameters.
	As a first statement it must have the following:

	    super(id,numberOfBlocks);

	@param numberOfBlocks -- number of blocks on device

        @OSPProject Devices
    */
    public Device(int id, int numberOfBlocks)
    {
        super(id, numberOfBlocks);
        iorbQueue = new GenericList();
    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Devices
    */
    public static void init()
    {
        prevCylinder = 0;
        head = 0;
    }

    /**
       Enqueues the IORB to the IORB queue for this device
       according to some kind of scheduling algorithm.
       
       This method must lock the page (which may trigger a page fault),
       check the device's state and call startIO() if the 
       device is idle, otherwise append the IORB to the IORB queue.

       @return SUCCESS or FAILURE.
       FAILURE is returned if the IORB wasn't enqueued 
       (for instance, locking the page fails or thread is killed).
       SUCCESS is returned if the IORB is fine and either the page was 
       valid and device started on the IORB immediately or the IORB
       was successfully enqueued (possibly after causing pagefault pagefault)
       
       @OSPProject Devices
    */
    public int do_enqueueIORB(IORB iorb)
    {
        // lock the i/o request block
        PageTableEntry requestPage = iorb.getPage();
        requestPage.lock(iorb);

        // if iorb thread is not on kill status, increment count
        if(iorb.getThread().getStatus() != ThreadCB.ThreadKill)
        {
            OpenFile requestFile = iorb.getOpenFile();
            requestFile.incrementIORBCount();
        }

        // calculations for getting the cylinder
        int sectorBytes = ((Disk)this).getSectorsPerTrack() * ((Disk)this).getBytesPerSector();
        int sizeBlock = (int)Math.pow(2, MMU.getVirtualAddressBits() - MMU.getPageAddressBits());
        int trackBlocks = sectorBytes / sizeBlock;
        int cylinderTracks = trackBlocks * ((Disk)this).getPlatters();
        int cylinder = iorb.getBlockNumber() / cylinderTracks;

        // set the cylinder to calculated cylinder
        iorb.setCylinder(cylinder);

        // if no kill status, return after insertion or start of request
        if(iorb.getThread().getStatus() != ThreadCB.ThreadKill) {
            if(this.isBusy()) {
                ((GenericList)iorbQueue).insert(iorb);
                return SUCCESS;
            } else {
                startIO(iorb);
                return SUCCESS;
            }
        } else {
            // if kill status, return
            return FAILURE;
        }
    }

    /**
       Selects an IORB (according to some scheduling strategy)
       and dequeues it from the IORB queue.

       @OSPProject Devices
    */
    public IORB do_dequeueIORB()
    {
        // do dequeue if iorb queue isn't empty
        if(!iorbQueue.isEmpty())
        {
        	// SSTF algorithm here
            head = iorbQueue.length() - 1;
            int i = iorbQueue.length() - 1;
            while(i >= 0) {
                IORB chosenRequest = (IORB)((GenericList)iorbQueue).getAt(head);
                IORB currentRequest = (IORB)((GenericList)iorbQueue).getAt(i);

                // if current request is shorter than the chosen one, set head to i
                int currentRequestVal = Math.abs(currentRequest.getCylinder() - prevCylinder);
                int chosenRequestVal = Math.abs(chosenRequest.getCylinder() - prevCylinder);
                if(currentRequestVal < chosenRequestVal) {
                    head = i;
                }
                // decrement i
                i = i - 1;
            }

            // remove the iorb from queue
            IORB iorbToRemove = (IORB)((GenericList)iorbQueue).getAt(head);
            ((GenericList)iorbQueue).remove(iorbToRemove);
            prevCylinder = iorbToRemove.getCylinder();

            // finally, return the iorb
            return iorbToRemove;
        } else {
            // on empty queue, return null
            return null;
        }
    }

    /**
        Remove all IORBs that belong to the given ThreadCB from 
	this device's IORB queue

        The method is called when the thread dies and the I/O 
        operations it requested are no longer necessary. The memory 
        page used by the IORB must be unlocked and the IORB count for 
	the IORB's file must be decremented.

	@param thread thread whose I/O is being canceled

        @OSPProject Devices
    */
    public void do_cancelPendingIO(ThreadCB thread)
    {
        // if iorb queue isn't empty, perform cancel pending request
        if(!iorbQueue.isEmpty())
        {
            int i = iorbQueue.length() - 1;
            while(i >= 0) {
                IORB iorbToCancel = (IORB)((GenericList)iorbQueue).getAt(i);
                // if request's thread = thread from param, continue cancel
                if(iorbToCancel.getThread().equals(thread)) {
                    // unlock page
                    PageTableEntry requestPage = iorbToCancel.getPage();
                    requestPage.unlock();
                    // decrement iorb count
                    OpenFile requestFile = iorbToCancel.getOpenFile();
                    requestFile.decrementIORBCount();
                    // get the iorb count afterwards to check
                    int iorbCount = requestFile.getIORBCount();
                    // if closePending boolean is true and iorb count is 0, close file
                    if(requestFile.closePending && iorbCount == 0) {
                        requestFile.close();
                    }
                    // remove iorb from queue after closing of file
                    ((GenericList)iorbQueue).remove(iorbToCancel);
                }
                // decrement i
                i = i - 1;
            }
        } else {
            // if empty, just return
            return;
        }
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atError()
    {

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atWarning()
    {

    }
}