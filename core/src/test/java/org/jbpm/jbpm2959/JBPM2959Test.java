/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jbpm.jbpm2959;

import org.jbpm.db.AbstractDbTestCase;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;

/**
 * Backport the dispatcher thread from jBPM 4 to avoid race conditions with multiple JobExecutor
 * threads.
 * 
 * @see <a href="https://jira.jboss.org/browse/JBPM-2959">JBPM-2959</a>
 * @author Alejandro Guizar
 */
public class JBPM2959Test extends AbstractDbTestCase {

  private static final int INSTANCE_COUNT = 10;

  protected void setUp() throws Exception {
    super.setUp();

    // [JBPM-2115] multiple threads not supported on DB2 < 9.7
    // multiple threads not be supported on HSQL
    String dialect = getHibernateDialect();
    if (dialect.indexOf("DB2") == -1 && dialect.indexOf("HSQL") == -1) {
      jbpmConfiguration.getJobExecutor().setNbrOfThreads(4);
    }

    ProcessDefinition processDefinition = ProcessDefinition.parseXmlResource("org/jbpm/jbpm2959/processdefinition.xml");
    deployProcessDefinition(processDefinition);
  }

  protected void tearDown() throws Exception {
    jbpmConfiguration.getJobExecutor().setNbrOfThreads(1);
    super.tearDown();
  }

  public void testDeadlockAtJobInsert() {
    long[] processInstanceIds = new long[INSTANCE_COUNT];
    for (int i = 0; i < processInstanceIds.length; i++) {
      ProcessInstance processInstance = jbpmContext.newProcessInstanceForUpdate("jbpm2959");
      processInstance.signal();
      processInstanceIds[i] = processInstance.getId();
    }

    processJobs();

    for (int i = 0; i < processInstanceIds.length; i++) {
      ProcessInstance processInstance = jbpmContext.loadProcessInstance(processInstanceIds[i]);
      assert processInstance.hasEnded() : "expected " + processInstance + " to have ended";
    }
  }
}
