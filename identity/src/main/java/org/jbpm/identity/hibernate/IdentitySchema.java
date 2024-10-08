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
package org.jbpm.identity.hibernate;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.jdbc.Work;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.hbm2ddl.SchemaExport.Action;
import org.hibernate.tool.schema.TargetType;
import org.jbpm.JbpmException;
import org.jbpm.db.hibernate.JbpmHibernateConfiguration;
import org.jbpm.logging.db.JDBCExceptionReporter;

@SuppressWarnings( "nls" )
public class IdentitySchema {

  private static final String IDENTITY_TABLE_PATTERN = "JBPM_ID_%";
  private static final String[] TABLE_TYPES = { "TABLE" };

  private final JbpmHibernateConfiguration jbpmHibernateConfiguration;
  private String delimiter;
  private final List<Exception> exceptions = new ArrayList<>();

  private MetadataSources metadataSources;
  private MetadataImplementor metadataImplementor;
  private SessionFactory sessionFactory;
  private Session session;
  private SessionImplementor sessionImplementor;

  public IdentitySchema(JbpmHibernateConfiguration jbpmHibernateConfiguration) {
      this.jbpmHibernateConfiguration = jbpmHibernateConfiguration;
      this.delimiter = ";";
  }

  private synchronized void configure() {
    if (sessionFactory == null) {
        this.sessionFactory = jbpmHibernateConfiguration.buildSessionFactory();
        this.metadataSources = jbpmHibernateConfiguration.getMetadataSources();
        this.metadataImplementor = jbpmHibernateConfiguration.getMetadataImplementor();
        this.session = sessionFactory.openSession();
        this.sessionImplementor = (SessionImplementor)session;
    }
  }

  Dialect getDialect() {
    return Dialect.getDialect(jbpmHibernateConfiguration.getConfigurationProxy().getProperties());
  }





  public void setDelimiter(String delimiter) {
    this.delimiter = delimiter;
  }

  public List<Exception> getExceptions() {
    return exceptions;
  }

  // runtime table detection //////////////////////////////////////////////////

  public boolean hasIdentityTables() {
    return !getIdentityTables().isEmpty();
  }

  public List<String> getIdentityTables() {

    configure();

    final List<String> existingTables = new ArrayList<>();

    session.doWork( new Work(){

      @Override
      public void execute( Connection connection ) {
        try {

          IdentitySchema.prepareConnection( connection );

          DatabaseMetaData metaData = connection.getMetaData();

          try ( ResultSet resultSet = metaData.getTables(
              null, null, IDENTITY_TABLE_PATTERN, TABLE_TYPES) ) {

            while (resultSet.next()) {
              String tableName = resultSet.getString("TABLE_NAME");
              if (tableName != null && tableName.length() > 5
                  && IDENTITY_TABLE_PATTERN.equalsIgnoreCase(tableName.substring(0, 5))) {
                  existingTables.add(tableName);
              }
            }
          }
        } catch (SQLException e) {
            throw new JbpmException("could not get identity tables", e);
        }
      }

    });

    return existingTables;
  }

  // script execution methods /////////////////////////////////////////////////

  public void dropSchema() {
      dropSchema(true, null);
  }

  public void dropSchema(boolean exportToDb, String exportToFile) {

    configure();

    SchemaExport schemaExport = new SchemaExport();
    schemaExport.setOutputFile(exportToFile);
    schemaExport.setDelimiter(delimiter);
    if (exportToDb) {
    	schemaExport.drop(EnumSet.of(TargetType.DATABASE), metadataImplementor);
    }
    else {
    	schemaExport.drop(EnumSet.of(TargetType.SCRIPT), metadataImplementor);
    }

    @SuppressWarnings( "unchecked" )
    List<Exception> schemaExceptions = schemaExport.getExceptions();

    if (schemaExceptions != null && schemaExceptions.size() > 0) {
      for ( Exception e : schemaExceptions ) {
          exceptions.add( e );
          JDBCExceptionReporter.logExceptions(e, "failed to drop schema");
      }
    }
  }

  public void createSchema() {
      createSchema( true, null );
  }

  public void createSchema(boolean exportToDb, String exportToFile) {

      configure();

      SchemaExport schemaExport = new SchemaExport();
      schemaExport.setOutputFile(exportToFile);
      schemaExport.setDelimiter(delimiter);
      
      if (exportToDb) {
      	schemaExport.create(EnumSet.of(TargetType.DATABASE), metadataImplementor);
      }
      else {
      	schemaExport.create(EnumSet.of(TargetType.SCRIPT), metadataImplementor);
      }

      @SuppressWarnings( "unchecked" )
      List<Exception> schemaExceptions = schemaExport.getExceptions();

      if (schemaExceptions != null && schemaExceptions.size() > 0) {
        for ( Exception e : schemaExceptions ) {
            exceptions.add( e );
            JDBCExceptionReporter.logExceptions(e, "failed to create schema");
        }
      }
  }

  public void cleanSchema() {
      cleanSchema( true, null );
  }

  public void cleanSchema(boolean exportToDb, String exportToFile) {

    configure();

    SchemaExport schemaExportForDrop = new SchemaExport();
    schemaExportForDrop.setOutputFile(exportToFile);
    schemaExportForDrop.setDelimiter(delimiter);
    
    if (exportToDb) {
    	schemaExportForDrop.execute(EnumSet.of(TargetType.DATABASE), Action.BOTH,metadataImplementor);
    }
    else {
    	schemaExportForDrop.execute(EnumSet.of(TargetType.SCRIPT), Action.BOTH,metadataImplementor);
    }

    @SuppressWarnings( "unchecked" )
    List<Exception> schemaExceptions = schemaExportForDrop.getExceptions();

    if (schemaExceptions != null && schemaExceptions.size() > 0) {
      for ( Exception e : schemaExceptions ) {
        exceptions.add( e );
        JDBCExceptionReporter.logExceptions(e, "failed to clean schema");
      }
    }
  }

  public void saveSqlScripts(String dir, String prefix) {
    File path = new File(dir);
    if (!path.isDirectory()) {
      throw new JbpmException(path + " is not a directory");
    }

    dropSchema(true, new File(path, prefix + ".drop.sql").getAbsolutePath());
    createSchema(true, new File(path, prefix + ".create.sql").getAbsolutePath());

    for ( Exception e : exceptions ) {
        JDBCExceptionReporter.logExceptions(e, "failed to generate scripts");
    }
  }

  private static void prepareConnection( Connection connection ) throws SQLException {
    if ( connection.getAutoCommit() == false ) {
      connection.commit();
      connection.setAutoCommit( true );
    }
  }

  // main /////////////////////////////////////////////////////////////////////

  public static void main(String[] args) {
    if (args == null || args.length == 0) {
      syntax();
    }
    else if ("create".equalsIgnoreCase(args[0])) {
      new IdentitySchema(IdentitySessionFactory.createConfiguration()).createSchema();
    }
    else if ("drop".equalsIgnoreCase(args[0])) {
      new IdentitySchema(IdentitySessionFactory.createConfiguration()).dropSchema();
    }
    else if ("clean".equalsIgnoreCase(args[0])) {
      new IdentitySchema(IdentitySessionFactory.createConfiguration()).cleanSchema();
    }
    else if ("scripts".equalsIgnoreCase(args[0]) && args.length == 3) {
      new IdentitySchema(IdentitySessionFactory.createConfiguration()).saveSqlScripts(args[1], args[2]);
    }
    else {
      syntax();
    }
  }

  private static void syntax() {
    System.err.println("syntax:");
    System.err.println("IdentitySchema create");
    System.err.println("IdentitySchema drop");
    System.err.println("IdentitySchema clean");
    System.err.println("IdentitySchema scripts <dir> <prefix>");
  }



  // getters and setters

  public JbpmHibernateConfiguration getJbpmHibernateConfiguration() {
      return jbpmHibernateConfiguration;
    }


    public String getDelimiter() {
      return delimiter;
    }


    public MetadataSources getMetadataSources() {
      configure();
      return metadataSources;
    }


    public MetadataImplementor getMetadataImplementor() {
      configure();
      return metadataImplementor;
    }


    public SessionFactory getSessionFactory() {
      configure();
      return sessionFactory;
    }

    public SessionImplementor getSessionImplementor() {
      configure();
      return sessionImplementor;
    }

}
