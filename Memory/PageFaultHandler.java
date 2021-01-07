package osp.Memory;
import java.util.*;
import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
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
    The page fault handler is responsible for handling a page
    fault.  If a swap in or swap out operation is required, the page fault
    handler must request the operation.

    @OSPProject Memory
*/
public class PageFaultHandler extends IflPageFaultHandler
{
  // initialize daemon with the init method
  public static void init()
  {
    Daemon.create("M2HC Daemon", new M2HCAlgorithm(), 3000);
  }

  /**
      This method handles a page fault. 

      It must check and return if the page is valid, 

      It must check if the page is already being brought in by some other
  thread, i.e., if the page's has already pagefaulted
  (for instance, using getValidatingThread()).
      If that is the case, the thread must be suspended on that page.
      
      If none of the above is true, a new frame must be chosen 
      and reserved until the swap in of the requested 
      page into this frame is complete. 

  Note that you have to make sure that the validating thread of
  a page is set correctly. To this end, you must set the page's
  validating thread using setValidatingThread() when a pagefault
  happens and you must set it back to null when the pagefault is over.

      If a swap-out is necessary (because the chosen frame is
      dirty), the victim page must be dissasociated 
      from the frame and marked invalid. After the swap-in, the 
      frame must be marked clean. The swap-ins and swap-outs 
      must are preformed using regular calls read() and write().

      The student implementation should define additional methods, e.g, 
      a method to search for an available frame.

  Note: multiple threads might be waiting for completion of the
  page fault. The thread that initiated the pagefault would be
  waiting on the IORBs that are tasked to bring the page in (and
  to free the frame during the swapout). However, while
  pagefault is in progress, other threads might request the same
  page. Those threads won't cause another pagefault, of course,
  but they would enqueue themselves on the page (a page is also
  an Event!), waiting for the completion of the original
  pagefault. It is thus important to call notifyThreads() on the
  page at the end -- regardless of whether the pagefault
  succeeded in bringing the page in or not.

      @param thread the thread that requested a page fault
      @param referenceType whether it is memory read or write
      @param page the memory page 

  @return SUCCESS is everything is fine; FAILURE if the thread
  dies while waiting for swap in or swap out or if the page is
  already in memory and no page fault was necessary (well, this
  shouldn't happen, but...). In addition, if there is no frame
  that can be allocated to satisfy the page fault, then it
  should return NotEnoughMemory

      @OSPProject Memory
  */
  public static int do_handlePageFault(ThreadCB thread, 
                     int referenceType,
                     PageTableEntry page)
  {
    // check for valid page, return if not
    if(page.isValid()) {
      // resume waiting threads before returning
      page.notifyThreads();
      ThreadCB.dispatch();
      return FAILURE;
    }

    // check if memory is full
    int usable_Frames = MMU.getFrameTableSize();
    int i = 0;
    while(i < MMU.getFrameTableSize()) {
      FrameTableEntry new_Frame = MMU.getFrame(i);
      if(new_Frame.isReserved() || new_Frame.getLockCount() > 0) {
        usable_Frames--;
      }
      i++;
    }

    // if usable frames is 0, there isn't enough memory so update pages and return
    if(usable_Frames == 0) {
      page.notifyThreads();
      ThreadCB.dispatch();
      return NotEnoughMemory;
    }

    // set validating thread now that there is enough memory
    page.setValidatingThread(thread);

    // suspend the thread that is causing the page fault to occur
    SystemEvent pagefault_Event = new SystemEvent("Processing page fault...");
    thread.suspend(pagefault_Event);

    FrameTableEntry usable_Frame = null;
    int j = 0;
    while(j < MMU.getFrameTableSize()) {
      FrameTableEntry entry_Frame = MMU.getFrame(j);
      if(entry_Frame.getPage() == null &&
        !entry_Frame.isReserved() &&
        entry_Frame.getLockCount() == 0) {
        usable_Frame = entry_Frame;
        break;
      }
      j++;
    }

    // if there is a usable frame, update page and SWAP IN page
    if(usable_Frame != null) {
      usable_Frame.setReserved(thread.getTask());
      page.setFrame(usable_Frame);

      // swap in page here
      swapIn(thread, page);
    } else {
      // if frame is null, we choose a frame with the chooser algorithm
      usable_Frame = M2HCAlgorithm.chooserAlgorithm();

      // set reserved and then fetch the page from frame
      usable_Frame.setReserved(thread.getTask());
      PageTableEntry frame_Page = usable_Frame.getPage();

      // check to see if the usable frame is clean or dirty
      if(usable_Frame.isDirty()) {
        // if dirty, SWAP OUT page
        swapOut(thread, frame_Page);

        // check for thread kill
        if(thread.getStatus() == ThreadKill) {
          // unreserve the thread
          unreserveThread(thread, usable_Frame);

          // unreference the frame
          usable_Frame.setReferenced(false);

          // call clean up helper method
          swapCleanUp(page, pagefault_Event);

          // dispatch and return
          ThreadCB.dispatch();
          return FAILURE;
        }

        // now we can unset the frame from dirty because everything is unreferenced
        usable_Frame.setDirty(false);
      }

      // clean page, therefore frames need to be freed
      usable_Frame.setReferenced(false);
      usable_Frame.setPage(null);

      // update pages
      frame_Page.setValid(false);
      frame_Page.setFrame(null);


      // SWAP IN page (other edge case)
      page.setFrame(usable_Frame);
      swapIn(thread, page);
    }

    // if thread kill while swap in, set page to null
    if(thread.getStatus() == ThreadKill) {
      // unreserve the thread
      unreserveThread(thread, usable_Frame);

      // call clean up helper method
      swapCleanUp(page, pagefault_Event);

      // dispatch and return
      ThreadCB.dispatch();
      return FAILURE;
    }

    // frame should now be clean, safe to update valid page
    usable_Frame.setDirty(false);
    usable_Frame.setPage(page);
    page.setValid(true);

    // unreserve the thread
    if(usable_Frame.getReserved() == thread.getTask()) {
      usable_Frame.setUnreserved(thread.getTask());
    }

    // call clean up helper method
    swapCleanUp(page, pagefault_Event);

    // dispatch and return
    ThreadCB.dispatch();
    return SUCCESS;
  }

  // helper method to perform swap in operations
  public static void swapIn(ThreadCB currentThread, PageTableEntry swapPage) {
    // get page's task first
    TaskCB current_Task = swapPage.getTask();
    // get the swap file directly from the task
    OpenFile swap_Device = current_Task.getSwapFile();
    // perform read with the file
    swap_Device.read(swapPage.getID(), swapPage, currentThread);
  }

  // helper method to perform swap out operations
  public static void swapOut(ThreadCB currentThread, PageTableEntry swapPage) {
    // get page's task first
    TaskCB current_Task = swapPage.getTask();
    // get the swap file directly from the task
    OpenFile swap_Device = current_Task.getSwapFile();
    // perform write with the file
    swap_Device.write(swapPage.getID(), swapPage, currentThread);
  }

  // helper method to clean up swap operations
  public static void swapCleanUp(PageTableEntry swapPage, Event faultEvent) {
    // resume all waiting threads and update pages
    swapPage.notifyThreads();
    swapPage.setValidatingThread(null);
    faultEvent.notifyThreads();
  }

  // helper method for checking page to set to null
  public static void unreserveThread(ThreadCB currentThread, FrameTableEntry currentFrame) {
    // if current page isn't null and thread's task is the same as frame's task
    if(currentFrame.getPage() != null) {
      if(currentFrame.getPage().getTask() == currentThread.getTask()) {
        // set the frame page to null
        currentFrame.setPage(null);
      }
    }

    // now we can unreserve the thread here
    if(currentFrame.getReserved() == currentThread.getTask()) {
      currentFrame.setUnreserved(currentThread.getTask());
    }
  }
}

/** 
  This is the daemon class that will serve as the cleaner process (first-hand)
  init is called when OSP2 starts executing which then calls unleash, which is
  my implementation of m2hc
*/
class M2HCAlgorithm implements DaemonInterface {
  // thread kill is set to 0 (use for this class only)
  private static final int ThreadKill = 0;
  
  // mandatory method to be implemented, this is the cleaner process
  public void unleash(ThreadCB thread) {
    // loop through frame table to perform swap out
    int i = 0;
    while(i < MMU.getFrameTableSize()) {
      FrameTableEntry current_Frame = MMU.getFrame(i);

      // if not reserved, check if dirty and re-calculate use count
      if(!current_Frame.isReserved() && current_Frame.getLockCount() == 0) {
        // decrement use counts for current frame if > 0
        if(current_Frame.use_Counts > 0) {
          current_Frame.use_Counts--;
        }

        // check if use counts is 0 for the current frame
        if(current_Frame.use_Counts == 0) {
          // if it is, we grab page's task to perform swap out
          PageTableEntry current_Page = current_Frame.getPage();
          TaskCB current_Task = current_Page.getTask();
          // grab swap file directly from task
          OpenFile swap_Device = current_Task.getSwapFile();
          // perform write with the file
          swap_Device.write(current_Page.getID(), current_Page, thread);

          // if status isn't thread kill, it is clean
          if(thread.getStatus() != ThreadKill) {
            current_Frame.setDirty(false);
          }

          // de-reference frame
          current_Frame.setReferenced(false);
        }
      }
      // increment while loop
      i++;
    }
  }

  // this is the chooser method for my M2HC algorithm
  public static FrameTableEntry chooserAlgorithm() {
    // this is what we'll be returning in the end
    FrameTableEntry current_Frame = null;
    FrameTableEntry dirty_Frame = null;
    FrameTableEntry valid_Frame = null;

    // loop through table for frames, keeping track of indices
    int i = 0;
    int min_Index = 0;
    int dirty_Index = 0;
    while(i < MMU.getFrameTableSize()) {
      FrameTableEntry entry_Frame = MMU.getFrame(i);
      // check reserved and lock count for valid frame
      if(!entry_Frame.isReserved() && entry_Frame.getLockCount() == 0) {
        // check if frame is clean/dirty and if use count is lower than prior
        if(!entry_Frame.isDirty() && entry_Frame.use_Counts < MMU.getFrame(min_Index).use_Counts) {
          // if clean and lower use count, update values
          min_Index = i;
          current_Frame = MMU.getFrame(min_Index);
        } else if(entry_Frame.use_Counts < MMU.getFrame(dirty_Index).use_Counts) {
          // if dirty and lower use count, update values
          dirty_Index = i;
          dirty_Frame = MMU.getFrame(dirty_Index);
        } else {
          // if all cases don't apply, we must save any valid frame
          valid_Frame = entry_Frame;
        }
      }
      // increment while loop
      i++;
    }

    // if no clean frame, we move to dirty frame
    if(current_Frame == null) {
       current_Frame = dirty_Frame;
    }

    // if no frame from above, we return any valid frame
    if(current_Frame == null) {
      current_Frame = valid_Frame;
    }

    // return the chosen frame
    return current_Frame;
  }
}