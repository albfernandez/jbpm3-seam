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
package org.jbpm.context.exe;

import org.jbpm.context.def.ContextDefinition;
import org.jbpm.db.AbstractDbTestCase;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;

public class LongIdVariableDbTest extends AbstractDbTestCase {

	public LongIdVariableDbTest() {
		super();
	}

	@Override
	protected String getJbpmTestConfig() {
		return "org/jbpm/context/exe/jbpm.cfg.xml";
	}


	public void testCustomVariableClassWithLongId() {
		try {
			// create and save process definition
			ProcessDefinition processDefinition = new ProcessDefinition(getName());
			processDefinition.addDefinition(new ContextDefinition());
			deployProcessDefinition(processDefinition);

			// create process instance
			ProcessInstance processInstance = new ProcessInstance(processDefinition);
			// create custom object
			CustomLongClass customLongObject = new CustomLongClass("my stuff");
			processInstance.getContextInstance().setVariable("custom", customLongObject);

			// save process instance
			processInstance = saveAndReload(processInstance);
			// get custom object from variables
			customLongObject = (CustomLongClass) processInstance.getContextInstance().getVariable("custom");
			assertNotNull(customLongObject);
			assertEquals("my stuff", customLongObject.getName());

			// delete custom object
			processInstance.getContextInstance().deleteVariable("custom");
			session.delete(customLongObject);
		} catch (Exception e) {
			throw e;
		}
	}
}
