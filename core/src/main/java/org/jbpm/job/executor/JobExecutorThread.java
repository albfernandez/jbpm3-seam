package org.jbpm.job.executor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.db.JobSession;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.job.Job;
import org.jbpm.persistence.db.DbPersistenceService;
import org.jbpm.persistence.db.StaleObjectLogConfigurer;

public class JobExecutorThread extends Thread implements Deactivable {

  private final JobExecutor jobExecutor;
  private volatile boolean active = true;
  private Random random = new Random() ;

  public JobExecutorThread(String name, JobExecutor jobExecutor) {
    super(jobExecutor.getThreadGroup(), name);
    this.jobExecutor = jobExecutor;
  }

  /**
   * @deprecated use {@link #JobExecutorThread(String, JobExecutor)} instead
   */
  public JobExecutorThread(String name, JobExecutor jobExecutor,
    JbpmConfiguration jbpmConfiguration, int idleInterval, int maxIdleInterval,
    long maxLockTime, int maxHistory) {
    super(jobExecutor.getThreadGroup(), name);
    this.jobExecutor = jobExecutor;
  }

  public void run() {
    while (active) {
      // take on next job
      Job job = jobExecutor.getJob();
      // if an exception occurs, acquireJob() returns null
      if (job != null) {
        try {
          executeJob(job);
        }
        catch (Exception e) {
          // save exception stack trace
          // if another exception occurs, it is not rethrown
          saveJobException(job, e);
        }
        catch (Error e) {
          // unlock job so it can be dispatched again
          // if another exception occurs, it is not rethrown
          unlockJob(job);
          throw e;
        }
      }
    }
    log.info(getName() + " leaves cyberspace");
  }

  /** @deprecated responsibility moved to DispatcherThread **/
  protected Collection acquireJobs() {
    Collection jobs = Collections.EMPTY_LIST;
    boolean debug = log.isDebugEnabled();
    JbpmContext jbpmContext = jobExecutor.getJbpmConfiguration().createJbpmContext();
    try {
      // search for available job
      String lockOwner = getName();
      JobSession jobSession = jbpmContext.getJobSession();
      Job firstJob = jobSession.getFirstAcquirableJob(lockOwner);
      // is there a job?
      if (firstJob != null) {
        // is job exclusive?
        if (firstJob.isExclusive()) {
          // find other exclusive jobs
          ProcessInstance processInstance = firstJob.getProcessInstance();
          List exclusiveJobs = jobSession.findExclusiveJobs(lockOwner, processInstance);

          if (debug) log.debug("acquiring " + exclusiveJobs + " for " + processInstance);
          Date lockTime = new Date();
          for (Iterator i = exclusiveJobs.iterator(); i.hasNext();) {
            Job exclusiveJob = (Job) i.next();
            exclusiveJob.setLockOwner(lockOwner);
            exclusiveJob.setLockTime(lockTime);
          }

          // deliver result
          if (debug) log.debug("acquired " + exclusiveJobs);
          jobs = exclusiveJobs;
        }
        else {
          if (debug) log.debug("acquiring " + firstJob);
          firstJob.setLockOwner(lockOwner);
          firstJob.setLockTime(new Date());

          // deliver result
          if (debug) log.debug("acquired " + firstJob);
          jobs = Collections.singletonList(firstJob);
        }
      }
      else if (debug) log.debug("no acquirable job found");
    }
    catch (RuntimeException e) {
      jbpmContext.setRollbackOnly();
      if (debug) log.debug("failed to acquire jobs", e);
    }
    catch (Error e) {
      jbpmContext.setRollbackOnly();
      throw e;
    }
    finally {
      try {
        jbpmContext.close();
      }
      catch (RuntimeException e) {
        jobs = Collections.EMPTY_LIST;
        if (debug) log.debug("failed to acquire jobs", e);
      }
    }
    return jobs;
  }

  protected void executeJob(Job job) throws Exception {
    JbpmContext jbpmContext = jobExecutor.getJbpmConfiguration().createJbpmContext();
    try {
      // reattach job to persistence context
      JobSession jobSession = jbpmContext.getJobSession();
      jobSession.reattachJob(job);
      
      // register process instance for automatic save
      // https://jira.jboss.org/browse/JBPM-1015
      ProcessInstance processInstance = job.getProcessInstance();
      jbpmContext.addAutoSaveProcessInstance(processInstance);

      // if job is exclusive, lock process instance
      if (job.isExclusive()) {
        jbpmContext.getGraphSession().lockProcessInstance(processInstance);
      }

      if (log.isDebugEnabled()) log.debug("executing " + job);
      if (job.execute(jbpmContext)) jobSession.deleteJob(job);
    }
    catch (Exception e) {
      jbpmContext.setRollbackOnly();
      throw e;
    }
    catch (Error e) {
      jbpmContext.setRollbackOnly();
      throw e;
    }
    finally {
      jbpmContext.close();
    }
  }

  private void saveJobException(Job job, Exception exception) {
    // if this is a locking exception, keep it quiet
    if (DbPersistenceService.isLockingException(exception)) {
      StaleObjectLogConfigurer.getStaleObjectExceptionsLog()
        .error("failed to execute " + job, exception);
    }
    else {
      log.error("failed to execute " + job, exception);
    }

    JbpmContext jbpmContext = jobExecutor.getJbpmConfiguration().createJbpmContext();
    try {
      // do not reattach existing job as it contains undesired updates
      jbpmContext.getSession().refresh(job);

      // print and save exception
      StringWriter out = new StringWriter();
      exception.printStackTrace(new PrintWriter(out));
      job.setException(out.toString());

      // unlock job so it can be dispatched again
      job.setLockOwner(null);
      job.setLockTime(null);
      int waitPeriod = jobExecutor.getRetryInterval() / 2;
      waitPeriod += random.nextInt(waitPeriod) ;
      job.setDueDate(new Date(System.currentTimeMillis() + waitPeriod)) ;
    }
    catch (RuntimeException e) {
      jbpmContext.setRollbackOnly();
      log.warn("failed to save exception for " + job, e);
    }
    catch (Error e) {
      jbpmContext.setRollbackOnly();
      throw e;
    }
    finally {
      try {
        jbpmContext.close();
      }
      catch (RuntimeException e) {
        log.warn("failed to save exception for " + job, e);
      }
    }
    // notify job executor
    synchronized (jobExecutor) {
      jobExecutor.notify();
    }
  }

  private void unlockJob(Job job) {
    JbpmContext jbpmContext = jobExecutor.getJbpmConfiguration().createJbpmContext();
    try {
      // do not reattach existing job as it contains undesired updates
      jbpmContext.getSession().refresh(job);

      // unlock job
      job.setLockOwner(null);
      job.setLockTime(null);
      if (job.getException() != null)
      {
    	  job.setRetries(job.getRetries()+1) ;
      }
    }
    catch (RuntimeException e) {
      jbpmContext.setRollbackOnly();
      log.warn("failed to unlock " + job, e);
    }
    catch (Error e) {
      jbpmContext.setRollbackOnly();
      // do not rethrow as this method is already called in response to an Error
      log.warn("failed to unlock " + job, e);
    }
    finally {
      try {
        jbpmContext.close();
      }
      catch (RuntimeException e) {
        log.warn("failed to unlock " + job, e);
      }
    }
    // notify job executor
    synchronized (jobExecutor) {
      jobExecutor.notify();
    }
  }

  /** @deprecated responsibility moved to DispatcherThread */
  protected long getWaitPeriod(int currentIdleInterval) {
    Date nextDueDate = getNextDueDate();
    if (nextDueDate != null) {
      long waitPeriod = nextDueDate.getTime() - System.currentTimeMillis();
      if (waitPeriod < currentIdleInterval) return waitPeriod;
    }
    return currentIdleInterval;
  }

  /** @deprecated responsibility moved to DispatcherThread */
  protected Date getNextDueDate() {
    Date nextDueDate = null;
    JbpmContext jbpmContext = jobExecutor.getJbpmConfiguration().createJbpmContext();
    try {
      String lockOwner = getName();
      Job job = jbpmContext.getJobSession()
        .getFirstDueJob(lockOwner, jobExecutor.getMonitoredJobIds());
      if (job != null) {
        jobExecutor.addMonitoredJobId(lockOwner, job.getId());
        nextDueDate = job.getDueDate();
      }
      else if (log.isDebugEnabled()) log.debug("no due job found");
    }
    catch (RuntimeException e) {
      jbpmContext.setRollbackOnly();
      if (log.isDebugEnabled()) log.debug("failed to determine next due date", e);
    }
    catch (Error e) {
      jbpmContext.setRollbackOnly();
      throw e;
    }
    finally {
      try {
        jbpmContext.close();
      }
      catch (RuntimeException e) {
        nextDueDate = null;
        if (log.isDebugEnabled()) log.debug("failed to determine next due date", e);
      }
    }
    return nextDueDate;
  }

  /**
   * @deprecated As of jBPM 3.2.3, replaced by {@link #deactivate()}
   */
  public void setActive(boolean isActive) {
    if (isActive == false) deactivate();
  }

  public void deactivate() {
    if (active) {
      active = false;
      interrupt();
    }
  }

  private static final Log log = LogFactory.getLog(JobExecutorThread.class);
}
