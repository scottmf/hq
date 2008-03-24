/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004-2008], Hyperic, Inc.
 * This file is part of HQ.
 *
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.util.unittest.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import junit.framework.TestCase;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.hyperic.util.jdbc.DBUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 * The test case that all server-side unit tests should extend. Before starting 
 * the server, the user must set the <code>hq.unittest.jboss.home</code> system  
 * property to the path where the jboss server that will be used for unit testing 
 * resides. That jboss instance must contain a "unittest" configuration created 
 * with the <code>unittest-prepare-jboss</code> Ant target. The datasource file 
 * (<code>hq-ds.xml</code>) in the "unittest" configuration must point to the 
 * *preexisting* unit testing server database.
 * 
 * In addition, the <code>hq.unittest.hq.home</code> system property must be set 
 * to the local path where the HQ src resides (.ORG or EE versions depending 
 * on the type of unit test).
 */
public abstract class BaseServerTestCase extends TestCase {
    
    private static final String logCtx = BaseServerTestCase.class.getName();
    private static final String DUMP_FILE = "hqdb.dump.xml.gz";
    
    /**
     * Path designated for file in the current run
     * contents may be deleted while not running
     */
    private static final String WORKING_DIR = "hq.unittest.working.dir";
    
    /**
     * The system property specifying the path to the jboss deployment 
     * that will be used for unit testing.
     */
    public static final String JBOSS_HOME_DIR = "hq.unittest.jboss.home";
    
    /**
     * The system property specifying the path to the HQ home directory. 
     * The HQ server will be deployed from the HQ home "build" subdirectory.
     */
    public static final String HQ_HOME_DIR = "hq.unittest.hq.home";
    
    /**
     * The "unittest" configuration that the jboss deployment must have installed 
     * and prepared using the Ant "prepare-jboss" target.
     */
    public static final String JBOSS_UNIT_TEST_CONFIGURATION = "unittest";
    
    private static ServerLifecycle server;
            
    private static URL deployment;
    
    static 
    {
        // Set properties required to contact the jboss JNDI
        System.setProperty("java.naming.factory.initial",
                "org.jnp.interfaces.NamingContextFactory");
        
        System.setProperty("java.naming.factory.url.pkgs",
                "org.jboss.naming:org.jnp.interfaces");
        
        System.setProperty("java.naming.provider.url",
                "jnp://localhost:2099");
    }
    
    /**
     * Creates an instance.
     *
     * @param name The test case name.
     */
    public BaseServerTestCase(String name) {
        super(name);
    }
    
    /**
     * Delegates to the super class.
     * 
     * @see junit.framework.TestCase#setUp()
     */
    public void setUp() throws Exception {
        super.setUp();
    }
    
    /**
     * Delegates to the super class.
     * 
     * @see junit.framework.TestCase#tearDown()
     */
    public void tearDown() throws Exception {
        super.tearDown();
    }
    
    protected final Connection getConnection(boolean forRestore)
            throws UnitTestDBException {
        try {
            String deployDir = 
                getJBossHomeDir()+"/server/"+JBOSS_UNIT_TEST_CONFIGURATION+"/deploy/";
            
            File file = new File(deployDir, "hq-ds.xml");
            Document doc =  new SAXBuilder().build(file);
            Element element =
                doc.getRootElement().getChild("local-tx-datasource");
            String url = element.getChild("connection-url").getText();
            String user = element.getChild("user-name").getText();
            String passwd = element.getChild("password").getText();
            String driverClass = element.getChild("driver-class").getText();
            if (forRestore && driverClass.toLowerCase().contains("mysql")) {
                String buf = "?";
                if (driverClass.toLowerCase().contains("?")) {
                    buf = "&";
                }
                driverClass = driverClass +
                    buf + "sessionVariables=FOREIGN_KEY_CHECKS=0";
            }
            Driver driver = (Driver)Class.forName(driverClass).newInstance();
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", passwd);
            return driver.connect(url, props);
        } catch (JDOMException e) {
            throw new UnitTestDBException(e);
        } catch (IOException e) {
            throw new UnitTestDBException(e);
        } catch (InstantiationException e) {
            throw new UnitTestDBException(e);
        } catch (SQLException e) {
            throw new UnitTestDBException(e);
        } catch (IllegalAccessException e) {
            throw new UnitTestDBException(e);
        } catch (ClassNotFoundException e) {
            throw new UnitTestDBException(e);
        }
    }

    protected final void restoreDatabase()
            throws UnitTestDBException {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getConnection(true);
            IDatabaseConnection idbConn = new DatabaseConnection(conn);
            conn.setAutoCommit(false);
            stmt = conn.createStatement();
            // this is done for MySQL via another method
            if (DBUtil.isPostgreSQL(conn)) {
                stmt.execute("set constraints all deferred");
            } else if (DBUtil.isOracle(conn)) {
                stmt.execute("alter session set constraints = deferred");
            }
            IDataSet dataset = new FlatXmlDataSet(new GZIPInputStream(
                new FileInputStream(getHQWorkingDir()+"/"+DUMP_FILE)));
            DatabaseOperation.CLEAN_INSERT.execute(idbConn, dataset);
            conn.commit();
        } catch (SQLException e) {
            throw new UnitTestDBException(e);
        } catch (DataSetException e) {
            throw new UnitTestDBException(e);
        } catch (FileNotFoundException e) {
            throw new UnitTestDBException(e);
        } catch (IOException e) {
            throw new UnitTestDBException(e);
        } catch (DatabaseUnitException e) {
            throw new UnitTestDBException(e);
        } finally {
            DBUtil.closeJDBCObjects(logCtx, conn, stmt, null);
        }
    }

    protected final void dumpDatabase(Connection conn)
            throws UnitTestDBException {
        try {
            IDatabaseConnection idbConn = new DatabaseConnection(conn);
            IDataSet fullDataSet;
            fullDataSet = idbConn.createDataSet();
            GZIPOutputStream gstream =
                new GZIPOutputStream(
                    new FileOutputStream(getHQWorkingDir()+"/"+DUMP_FILE));
            FlatXmlDataSet.write(fullDataSet, gstream);
            gstream.finish();
        } catch (SQLException e) {
            throw new UnitTestDBException(e);
        } catch (FileNotFoundException e) {
            throw new UnitTestDBException(e);
        } catch (IOException e) {
            throw new UnitTestDBException(e);
        } catch (DataSetException e) {
            throw new UnitTestDBException(e);
        }
    }

    /**
     * Used to insert new data, will either update existing data or insert fresh.
     * @param schema File to extract XML data from.
     * @throws UnitTestDBException 
     */
    protected final void insertSchemaData(File schema)
        throws UnitTestDBException {
        overlayDBData(schema, DatabaseOperation.REFRESH);
    }
    
    /**
     * Used to delete data that was specified by the schema.
     * @param schema File to extract XML data from.
     * @throws UnitTestDBException 
     */
    protected final void deleteSchemaData(File schema)
        throws UnitTestDBException {
        overlayDBData(schema, DatabaseOperation.DELETE);
    }

    private void overlayDBData(File schema, DatabaseOperation operation)
        throws UnitTestDBException {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getConnection(true);
            conn.setAutoCommit(false);
            IDatabaseConnection iconn = new DatabaseConnection(conn);
            // this is done for MySQL via another method
            if (DBUtil.isPostgreSQL(conn)) {
                stmt.execute("set constraints all deferred");
            } else if (DBUtil.isOracle(conn)) {
                stmt.execute("alter session set constraints = deferred");
            }
            InputStream stream;
            if (schema.getName().endsWith(".gz")) {
                stream = new GZIPInputStream(new FileInputStream(schema));
            } else {
                stream = new BufferedInputStream(new FileInputStream(schema));
            }
            IDataSet dataset = new FlatXmlDataSet(stream);
            operation.execute(iconn, dataset);
            conn.commit();
        } catch (UnitTestDBException e) {
            throw new UnitTestDBException(e);
        } catch (SQLException e) {
            throw new UnitTestDBException(e);
        } catch (DatabaseUnitException e) {
            throw new UnitTestDBException(e);
        } catch (FileNotFoundException e) {
            throw new UnitTestDBException(e);
        } catch (IOException e) {
            throw new UnitTestDBException(e);
        } finally {
            DBUtil.closeJDBCObjects(logCtx, conn, stmt, null);
        }
    }

    /**
     * Start the jboss server with the "unittest" configuration.
     * 
     * @throws Exception
     */
    protected final void startServer() throws Exception {        
        if (server == null || !server.isStarted()) {
            String jbossHomeDir = getJBossHomeDir();
            
            server = new ServerLifecycle(new File(jbossHomeDir), 
                                         JBOSS_UNIT_TEST_CONFIGURATION);
            server.startServer();
        }
        
        assertTrue(server.isStarted());
    }
    
    /**
     * Deploy the HQ application into the jboss server, starting the jboss server 
     * first if necessary.
     * 
     * @throws Exception
     */
    protected final void deployHQ() throws Exception {
        if (server == null || !server.isStarted()) {
            startServer();
        }
        
        deployment = getHQDeployment();
        
		//                restoreDatabase();
        
        server.deploy(deployment);
    }
    
    /**
     * Undeploy the HQ or HQEE application from the jboss server.
     * 
     * @throws Exception
     */
    protected final void undeployHQ() throws Exception {
        if (server != null && server.isStarted() && deployment != null) {
            server.undeploy(deployment);
            deployment = null;
        }
    }
    
    /**
     * Stop the jboss server. This facility is provided for completeness, but 
     * if back to back server restarts are performed then unit tests may fail 
     * because of server socket binding issues (the jboss server socket 
     * factories do not have SO_REUSEADDR set to true).
     * 
     * In general, it should be left up to the framework to stop the server 
     * once it has been started. If a unit test does not explicitly stop the 
     * server, then a shutdown hook stops the server automatically before the 
     * unit tests finish executing within their forked vm. 
     * 
     * If the user must stop the server, then any future server restarts within 
     * the same unit test vm should be delayed until the jboss server sockets 
     * can move from a TIME_WAIT to CLOSED state.
     */
    protected final void stopServer() {
        if (server != null) {
            server.stopServer();
            assertFalse(server.isStarted());
            server = null;
            deployment = null;
        }
    }
    
    private String getJBossHomeDir() {
        String jbossHomeDir = System.getProperty(JBOSS_HOME_DIR);
        
        if (jbossHomeDir == null) {
            throw new IllegalStateException("The "+JBOSS_HOME_DIR+
                            " system property was not set");
        }
        
        return jbossHomeDir;
    }
        
    private URL getHQDeployment() throws MalformedURLException {
        return new URL("file:"+getHQHomeDir()+"/build/hq.ear/");
    }
    
    private String getHQHomeDir() {
        String hqHomeDir = System.getProperty(HQ_HOME_DIR);
        
        if (hqHomeDir == null) {
            throw new IllegalStateException("The "+HQ_HOME_DIR+
                                    " system property was not set");
        }
        
        return hqHomeDir;
    }
    
    private String getHQWorkingDir() {
        String hqWorkingDir = System.getProperty(WORKING_DIR);

        if (hqWorkingDir == null) {
            throw new IllegalStateException("The "+WORKING_DIR+
                                    " system property was not set");
        }

        return hqWorkingDir;
    }
    
}
