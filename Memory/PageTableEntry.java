package osp.Memory;

import osp.Hardware.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Devices.*;
import osp.Utilities.*;
import osp.IFLModules.*;
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
   The PageTableEntry object contains information about a specific virtual
   page in memory, including the page frame in which it resides.
   
   @OSPProject Memory

*/
public class PageTableEntry extends IflPageTableEntry
{
    /**
       The constructor. Must call

       	   super(ownerPageTable,pageNumber);
	   
       as its first statement.

       @OSPProject Memory
    */
    public PageTableEntry(PageTable ownerPageTable, int pageNumber)
    {
        super(ownerPageTable, pageNumber);
    }

    /**
       This method increases the lock count on the page by one. 

	The method must FIRST increment lockCount, THEN  
	check if the page is valid, and if it is not and no 
	page validation event is present for the page, start page fault 
	by calling PageFaultHandler.handlePageFault().

	@return SUCCESS or FAILURE
	FAILURE happens when the pagefault due to locking fails or the 
	that created the IORB thread gets killed.

	@OSPProject Memory
     */
    public int do_lock(IORB iorb)
    {
      // get iorb thread
      ThreadCB iorb_Thread = iorb.getThread();
    
      // check if invalid
      if(!this.isValid()) {
        if(this.getValidatingThread() != null) {
          if(this.getValidatingThread() == iorb_Thread) {
            // increment lock count and return
            this.getFrame().incrementLockCount();
            return SUCCESS;
          } else {
            // else suspend the iorb thread and return
            iorb_Thread.suspend(this);
            if(!this.isValid()) {
              return FAILURE;
            }
          }
        } else {
          // if thread is null, handle page fault
          PageFaultHandler.handlePageFault(
            iorb_Thread,
            GlobalVariables.MemoryLock,
            this
          );
          // check status and return if threadkill
          if(iorb_Thread.getStatus() == ThreadKill) {
            return FAILURE;
          }
        }
      }

      // increment lock count and return
      this.getFrame().incrementLockCount();
      return SUCCESS;
    }

    /** This method decreases the lock count on the page by one. 

	This method must decrement lockCount, but not below zero.

	@OSPProject Memory
    */
    public void do_unlock()
    {
      // if there is a lock count, decrement it
      if(this.getFrame().getLockCount() > 0) {
        this.getFrame().decrementLockCount();
      } else {
        // otherwise return
        return;
      }
    }
}