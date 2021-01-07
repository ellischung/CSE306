package osp.Devices;
import java.util.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import osp.Interrupts.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.FileSys.*;
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
    The disk interrupt handler.  When a disk I/O interrupt occurs,
    this class is called upon the handle the interrupt.

    @OSPProject Devices
*/
public class DiskInterruptHandler extends IflDiskInterruptHandler
{
    /** 
        Handles disk interrupts. 
        
        This method obtains the interrupt parameters from the 
        interrupt vector. The parameters are IORB that caused the 
        interrupt: (IORB)InterruptVector.getEvent(), 
        and thread that initiated the I/O operation: 
        InterruptVector.getThread().
        The IORB object contains references to the memory page 
        and open file object that participated in the I/O.
        
        The method must unlock the page, set its IORB field to null,
        and decrement the file's IORB count.
        
        The method must set the frame as dirty if it was memory write 
        (but not, if it was a swap-in, check whether the device was 
        SwapDevice)

        As the last thing, all threads that were waiting for this 
        event to finish, must be resumed.

        @OSPProject Devices 
    */
    public void do_handleInterrupt()
    {
        // get event from interrupt vector to access other props
        IORB requestEvent = (IORB)InterruptVector.getEvent();

        // decrement request count after getting the open file
        OpenFile eventFile = requestEvent.getOpenFile();
        eventFile.decrementIORBCount();

        // check for close pending and 0 request counts
        int iorbCount = eventFile.getIORBCount();
        if(eventFile.closePending && iorbCount == 0) {
            // close the file if true
            eventFile.close();
        }

        // unlock page from event
        PageTableEntry eventPage = requestEvent.getPage();
        eventPage.unlock();

        // grab the thread and then task from event
        ThreadCB eventThread = requestEvent.getThread();
        TaskCB eventTask = eventThread.getTask();

        // save frame for later
        FrameTableEntry eventFrame = eventPage.getFrame();

        // if not taskterm status, set dirty frame
        int taskStatus = eventTask.getStatus(); 
        if(taskStatus != TaskTerm) {
            // get status of thread
            int threadStatus = eventThread.getStatus();
            if(threadStatus != ThreadCB.ThreadKill
            && requestEvent.getDeviceID() != SwapDeviceID) {
                if(requestEvent.getIOType() == FileRead) {
                    eventFrame.setDirty(true);
                }
                eventFrame.setReferenced(true);
            } else {
                // just frame to clean else
                eventFrame.setDirty(false);
            }
        }

        // unreserve the task if it is still reserved
        int newTaskStatus = eventTask.getStatus();
        if(eventFrame.isReserved() && newTaskStatus == TaskTerm) {
            eventFrame.setUnreserved(eventTask);
        }

        // notify all threads
        requestEvent.notifyThreads();

        // dequeue iorb with device id
        int eventDeviceID = requestEvent.getDeviceID();
        Device.get(eventDeviceID).setBusy(false);
        IORB eventDevice = Device.get(eventDeviceID).dequeueIORB();
        if(eventDevice != null) {
            // if device is valid, start the iorb from the device
            Device.get(eventDeviceID).startIO(eventDevice);
        }

        // dispatch thread
        ThreadCB.dispatch();
    }
}