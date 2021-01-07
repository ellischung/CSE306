package osp.Devices;
import osp.IFLModules.*;
import osp.FileSys.OpenFile;
import osp.Threads.ThreadCB;
import osp.Memory.PageTableEntry;
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
   This class contains all the information necessary to carry out
   an I/O request.

    @OSPProject Devices
*/
public class IORB extends IflIORB
{
    /**
       The IORB constructor.
       Must have
       
	   super(thread,page,blockNumber,deviceID,ioType,openFile);

       as its first statement.

       @OSPProject Devices
    */
    public IORB(ThreadCB thread, PageTableEntry page, 
		int blockNumber, int deviceID, 
		int ioType, OpenFile openFile) {
      super(
        thread, 
        page, 
        blockNumber, 
        deviceID, 
        ioType,
        openFile
      );
    }
}