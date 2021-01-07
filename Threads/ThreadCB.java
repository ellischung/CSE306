package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;
import java.util.ArrayList;

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
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.
   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{

    // starting variables
    static ArrayList<ThreadCB> ready_Queue_1;
    static ArrayList<ThreadCB> ready_Queue_2;
    static long dispatch_Count;
    static long invoke_Number;

    /**
       The thread constructor. Must call 
       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
        super();
    }
    
    //static ArrayList<ThreadCB> rdQueue;

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
        ready_Queue_1 = new ArrayList<ThreadCB>();
        ready_Queue_2 = new ArrayList<ThreadCB>();
        dispatch_Count = 0;
        invoke_Number = 0;
    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.

	@return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
        // null cases
        if (task == null || task.getThreadCount() >= MaxThreadsPerTask) {
            dispatch();
			return null;
        }
        
        // create new threadCB and set vars
        ThreadCB thread_CB = new ThreadCB();
        thread_CB.setTask(task);
        thread_CB.setStatus(ThreadReady);
        
        // check for error in addThread
        int add_Check = task.addThread(thread_CB);
        if (add_Check != SUCCESS) {
            dispatch();
            return null;
        }
        
        // add thread into Q1
        ready_Queue_1.add(thread_CB);
        
        // dispatch and return created thread
        dispatch();
        return thread_CB;
    }

    /** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
        // create task to get info
        TaskCB current_Task = this.getTask();
        
        // check current state
        int current_State = this.getStatus();

        // cases
        if (current_State == ThreadRunning) {
            current_Task.setCurrentThread(null);
            MMU.setPTBR(null);
        } else if (current_State >= ThreadWaiting) {
            for (int i = 0; i < Device.getTableSize(); i++) {
                Device.get(i).cancelPendingIO(this);
            }
        } else if (current_State == ThreadReady) {
            // remove from queue depending on location
            if (ready_Queue_1.contains(this)) {
                ready_Queue_1.remove(this);
            } else if (ready_Queue_2.contains(this)) {
                ready_Queue_2.remove(this);
            }   
        }
        
        // set kill state and remove thread
        this.setStatus(ThreadKill);
        ResourceCB.giveupResources(this);
        current_Task.removeThread(this);

        // kill the task if it has no more threads
        int thread_Count = current_Task.getThreadCount();
        if (thread_Count == 0) {
            current_Task.kill();
        }
        
        // dispatch
        dispatch();
    }

    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.
	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
        // get the current state
        int current_State = this.getStatus();

        // cases
        if (current_State >= ThreadWaiting) {
            this.setStatus(current_State + 1);
        } else if (current_State == ThreadRunning) {
            this.getTask().setCurrentThread(null);
            MMU.setPTBR(null);
            this.setStatus(ThreadWaiting);
        }

        // if thread not in event, add it
        if (!event.contains(this)) {
            event.addThread(this);
        }
        
        // dispatch
        dispatch();
    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public void do_resume()
    {
        // check cases for resume
        if (this.getStatus() == ThreadRunning) {
            return;
        } else if (this.getStatus() == ThreadWaiting) {
            this.setStatus(ThreadReady);
        } else if (this.getStatus() > ThreadWaiting) {
            this.setStatus(this.getStatus() - 1);
        }

        // add to Q1 or Q2 if ready
        if (this.getStatus() == ThreadReady) {
            // check for dispatches, demotion to Q2 happens after 7th dispatch
            if (this.dispatch_Count < 7) {
                ready_Queue_1.add(this);
            } else {
                ready_Queue_2.add(this);
            }
        }
        
        // dispatch
        dispatch();
    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
        // threads for dispatching
        ThreadCB current_Thread_CB;
        ThreadCB new_Thread_CB;
        
        try {
            // retrieve thread from MMU
            current_Thread_CB = MMU.getPTBR().getTask().getCurrentThread();
            current_Thread_CB.getTask().setCurrentThread(null);
            MMU.setPTBR(null);
            current_Thread_CB.setStatus(ThreadReady);
            ready_Queue_1.add(current_Thread_CB);

        } catch (NullPointerException e) {
            // null in MMU caught
            MMU.setPTBR(null);
		}

        /** queue scheduler algorithm (3 invocations = 1 cycle)
                  0    1    2
                | Q1 | Q1 | Q2 |
        */
        if (invoke_Number == 0 | invoke_Number == 1) {                // first 2 invocations
            // Q1 gets scheduled
            if (ready_Queue_1.isEmpty() && ready_Queue_2.isEmpty()) {
                // increment invoke number and return
                invoke_Number = invoke_Number + 1;
                return FAILURE;
            } else if (ready_Queue_1.isEmpty()) {
                // switch over to Q2 if Q1 is empty
                new_Thread_CB = ready_Queue_2.remove(0);
                MMU.setPTBR(new_Thread_CB.getTask().getPageTable());
                new_Thread_CB.getTask().setCurrentThread(new_Thread_CB);
                new_Thread_CB.setStatus(ThreadRunning);
                
                // clock tick for 60 for second queue
                HTimer.set(60);

                // if first invocation, increment 2 to skip remaining turns in cycle
                if (invoke_Number == 0) {
                    invoke_Number = invoke_Number + 2;
                    return SUCCESS;
                } else {
                    // if second invocation, increment once to skip remaining turns in cycle
                    invoke_Number = invoke_Number + 1;
                    return SUCCESS;
                }

            } else {
                // original Q1 scheduling
                new_Thread_CB = ready_Queue_1.remove(0);
                MMU.setPTBR(new_Thread_CB.getTask().getPageTable());
                new_Thread_CB.getTask().setCurrentThread(new_Thread_CB);
                new_Thread_CB.setStatus(ThreadRunning);

                // increase dispatch # for this thread
                new_Thread_CB.dispatch_Count = new_Thread_CB.dispatch_Count + 1;

                // clock tick of 40 for first queue
                HTimer.set(40);
        
                // increment invoke number and return
                invoke_Number = invoke_Number + 1;
                return SUCCESS;
            }
        } else if (invoke_Number == 2) {                              // third invocation
            // Q2 gets scheduled
            if (ready_Queue_1.isEmpty() && ready_Queue_2.isEmpty()) {
                // reset the cycle and return
                invoke_Number = 0;
                return FAILURE;
            } else if (ready_Queue_2.isEmpty()) {
                // switch over to Q1 if Q2 is empty
                new_Thread_CB = ready_Queue_1.remove(0);
                MMU.setPTBR(new_Thread_CB.getTask().getPageTable());
                new_Thread_CB.getTask().setCurrentThread(new_Thread_CB);
                new_Thread_CB.setStatus(ThreadRunning);

                // increase dispatch # for this thread
                new_Thread_CB.dispatch_Count = new_Thread_CB.dispatch_Count + 1;

                // clock tick for 40 for first queue
                HTimer.set(40);

                // reset the cycle and return (note: remaining turns will be skipped by default)
                invoke_Number = 0;
                return SUCCESS;
            } else {
                // original Q2 scheduling
                new_Thread_CB = ready_Queue_2.remove(0);
                MMU.setPTBR(new_Thread_CB.getTask().getPageTable());
                new_Thread_CB.getTask().setCurrentThread(new_Thread_CB);
                new_Thread_CB.setStatus(ThreadRunning);

                // clock tick of 60 for second queue
                HTimer.set(60);
        
                // reset the cycle and return
                invoke_Number = 0;
                return SUCCESS;
            }
        }

        // return value
        return SUCCESS;
    }
    
    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }
}