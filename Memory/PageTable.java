package osp.Memory;
/**
    The PageTable class represents the page table for a given task.
    A PageTable consists of an array of PageTableEntry objects.  This
    page table is of the non-inverted type.

    @OSPProject Memory
*/
import osp.Tasks.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import java.lang.Math;
/**
   Ellis Chung
   ELLCHUNG
   111135169

   I pledge my honor that all parts of this project were done by me individually, without
   collaboration with anyone, and without consulting any external sources that provide
   full or partial solutions to a similar project.
   I understand that breaking this pledge will result in an F for the entire course.
*/
public class PageTable extends IflPageTable
{
    /** 
	The page table constructor. Must call
	
	    super(ownerTask)

	as its first statement.

	@OSPProject Memory
    */
    public PageTable(TaskCB ownerTask)
    {
      super(ownerTask);

      // get all page table entries and make pages
      int num_Entries = (int)Math.pow(2, MMU.getPageAddressBits());
      pages = new PageTableEntry[num_Entries];

      // populate pages
      int i = 0;
      while(i < num_Entries) {
        pages[i] = new PageTableEntry(this, i);
        i++;
      }
    }

    /**
       Frees up main memory occupied by the task.
       Then unreserves the freed pages, if necessary.

       @OSPProject Memory
    */
    public void do_deallocateMemory()
    {
      // get task from page table
      TaskCB pt_Task = this.getTask();

      // set appropriate bits to deallocate
      int i = 0;
      while(i < MMU.getFrameTableSize()) {
        FrameTableEntry frame = MMU.getFrame(i);
        PageTableEntry page = frame.getPage();
        if(page != null) {
          if(page.getTask() == pt_Task) {
            frame.setReferenced(false);
            frame.setDirty(false);
            frame.setPage(null);
            if(frame.getReserved() == pt_Task) {
              frame.setUnreserved(pt_Task);
            }
          }
        }
        // continue while loop
        i++;
      }
    }
}