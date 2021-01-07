package osp.Memory;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;
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
    The MMU class contains the student code that performs the work of
    handling a memory reference.  It is responsible for calling the
    interrupt handler if a page fault is required.

    @OSPProject Memory
*/
public class MMU extends IflMMU
{
    /** 
        This method is called once before the simulation starts. 
	  Can be used to initialize the frame table and other static variables.

        @OSPProject Memory
    */
    public static void init()
    {
      // set frames
      int i = 0;
      while(i < MMU.getFrameTableSize()) {
        setFrame(i, new FrameTableEntry(i));
        i++;
      }
    }

    /**
       This method handlies memory references. The method must 
       calculate, which memory page contains the memoryAddress,
       determine, whether the page is valid, start page fault 
       by making an interrupt if the page is invalid, finally, 
       if the page is still valid, i.e., not swapped out by another 
       thread while this thread was suspended, set its frame
       as referenced and then set it as dirty if necessary.
       (After pagefault, the thread will be placed on the ready queue, 
       and it is possible that some other thread will take away the frame.)
       
       @param memoryAddress A virtual memory address
       @param referenceType The type of memory reference to perform 
       @param thread that does the memory access
       (e.g., MemoryRead or MemoryWrite).
       @return The referenced page.

       @OSPProject Memory
    */
    static public PageTableEntry do_refer(int memoryAddress,
					  int referenceType, ThreadCB thread)
    {
      // calculate page of memoryAddress
      int offset = getVirtualAddressBits() - getPageAddressBits();
      int page_Size = (int)Math.pow(2.0, offset);
      int page_Number = memoryAddress / page_Size;
      
      // get the PTE
      PageTableEntry PTE = getPTBR().pages[page_Number];
    
      // check for valid entry
      if(PTE.isValid()) {
        // set frame to dirty
        if(referenceType == GlobalVariables.MemoryWrite) {
          PTE.getFrame().setDirty(true);
        }

        // increment use count for each reference
        if(PTE.getFrame().use_Counts < 4) {
          PTE.getFrame().setReferenced(true);
          PTE.getFrame().use_Counts++;
        } else {
          // should still reference frame
          PTE.getFrame().setReferenced(true);
        }

        // return PTE
        return PTE;
      } else {
        // get validating thread
        if(PTE.getValidatingThread() != null) {
          thread.suspend(PTE);
        } else {
          // set props for interrupt vector
          InterruptVector.setPage(PTE);
          InterruptVector.setInterruptType(referenceType);
          InterruptVector.setThread(thread);

          // call interrupt from CPU
          CPU.interrupt(PageFault);
        }

        // check status and return if threadkill
        if(thread.getStatus() == GlobalVariables.ThreadKill) {
          return PTE;
        }
      }

      // set frame to dirty
      if(referenceType == GlobalVariables.MemoryWrite) {
          PTE.getFrame().setDirty(true);
      }

      // increment use count for each reference
      if(PTE.getFrame().use_Counts < 4) {
        PTE.getFrame().setReferenced(true);
        PTE.getFrame().use_Counts++;
      } else {
        // should still reference frame
        PTE.getFrame().setReferenced(true);
      }

      // return PTE
      return PTE;
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
     
	@OSPProject Memory
     */
    public static void atError()
    {
      return;
    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
     
      @OSPProject Memory
     */
    public static void atWarning()
    {
      return;
    }
}