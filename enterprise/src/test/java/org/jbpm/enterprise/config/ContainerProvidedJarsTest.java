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
package org.jbpm.enterprise.config;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.jbpm.enterprise.IntegrationTestHelper;
import org.jbpm.enterprise.ObjectNameFactory;

import junit.framework.TestCase;

/**
 * Test that there are jars deployed which should in fact be provided by the container
 * 
 * @author thomas.diesler@jboss.com
 * @since 23-Sep-2008
 */
public class ContainerProvidedJarsTest extends TestCase {

  IntegrationTestHelper delegate = new IntegrationTestHelper();

  public void testDependencies() throws Exception {
    String targetContainer = delegate.getIntegrationTarget();
    File jbossJars = delegate.getResourceFile(targetContainer + "-dependencies.txt");
    assertTrue("JBoss jar fixture exists: " + jbossJars, jbossJars.exists());

    // Read the JBoss ServerHomeDir
    ObjectName oname = ObjectNameFactory.create("jboss.system:type=ServerConfig");
    File serverHomeDir = (File) delegate.getServer().getAttribute(oname, "ServerHomeDir");
    if (serverHomeDir == null) throw new IllegalStateException("Cannot obtain jboss home dir");

    File jbpmDir = new File(serverHomeDir + "/deploy/jbpm");
    assertTrue("jBPM dir exists: " + jbpmDir, jbpmDir.exists());

    List<String> deployedJars = getDeployedJars(jbpmDir);

    // Iterate over the known server provided jars
    List<String> matchingJars = new ArrayList<String>();
    BufferedReader br = Files.newBufferedReader(jbossJars.toPath(), StandardCharsets.UTF_8);
    String jbossJar = br.readLine();
    while (jbossJar != null) {
      if (jbossJar.length() == 0 || jbossJar.startsWith("#")) {
        jbossJar = br.readLine();
        continue;
      }

      for (String deployedJar: deployedJars) {
        if (deployedJar.startsWith(jbossJar)) {
        	matchingJars.add(deployedJar);
        }
      }
      jbossJar = br.readLine();
    }
    br.close();

    assertEquals("Invalid deployed jars: " + matchingJars, 0, matchingJars.size());
  }

  private List<String> getDeployedJars(File subdir) {
    List<String> deployedJars = new ArrayList<String>();
    File[] files = subdir.listFiles();
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      if (file.isDirectory()) {
        deployedJars.addAll(getDeployedJars(file));
        continue;
      }

      String fileName = file.getName();
      if (fileName.endsWith(".jar")
          && !fileName.startsWith("jbpm")) {
        deployedJars.add(fileName);
      }
    }
    return deployedJars;
  }
}
