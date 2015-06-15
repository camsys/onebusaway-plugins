package org.onebusaway.plugins.hsqldb;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 * 
 * Modifications Copyright 2010 Brian Ferris
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Original source taken from:
 * 
 * http://svn.codehaus.org/mojo/trunk/sandbox/maven-hsqldb-plugin
 */

import java.io.File;
import java.io.FileFilter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.hsqldb.server.Server;
import org.hsqldb.server.WebServer;

/**
 * Run an instance of HSQL.
 * 
 * @todo if isTransient is false, move the generated files in the default target directory,
 * @todo this requires a MavenProject var
 * @goal run
 * 
 * @author valerio.schiavoni@gmail.com
 */
public class RunMojo extends AbstractMojo
{

    /**
     * HSQL allows 3 modes: server, webserver, servlet. Only server and webserver are supported.
     * 
     * @parameter expression="${mode}" default-value="server"
     */
    private String mode;

    /**
     * The name of the database to connect to. Default is 'test'.
     * 
     * @parameter default-value="test"
     */
    private String dbName;

    /**
     * false => display all queries
     * 
     * @parameter default-value="true"
     */
    private boolean silent;

    /**
     * display JDBC trace messages
     * 
     * @parameter default-value="false"
     */
    private boolean trace;

    /**
     * TLS/SSL (secure) sockets
     * 
     * @paramenter default-value="false"
     */
    private boolean tls;

    /**
     * port at which server listens. Defauls is 9001/544
     * 
     * @parameter
     */
    private int port;

    /**
     * If true, a transient in-process connection is opened, and the url used to start the dbms will be [mem:dbName].
     * When false, the url is [file:dbName].
     * 
     * @parameter default-value="false"
     */
    private boolean isTransient;

    /**
     * @parameter default-value="false"
     */
    private boolean deleteOnEntry;

    /**
     * If true, and isTransient is false, all HSQLDB files are going to be removed. In particular, file considered to be
     * to be removed are: ${dbname}.{log|script|properties|data|backup}
     * 
     * @parameter default-value="false"
     */
    private boolean deleteOnExit;

    /**
     * Execute the mojo.
     * 
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException
    {

        if ( deleteOnEntry )
            deleteFilesOnEntry();

        final Server hsqlServer = createServer();

        hsqlServer.setSilent( silent );
        hsqlServer.setTrace( trace );
        hsqlServer.setTls( tls );

        if ( port != 0 )
            hsqlServer.setPort( port );

        /**
         * If the dbName is a path, just use the last portion of the path as the database name
         */
        String name = dbName;
        int index = name.lastIndexOf( '/' );
        if ( index != -1 )
            name = name.substring( index + 1 );

        hsqlServer.setDatabaseName( 0, name );
        hsqlServer.setDatabasePath( 0, persistenceDevice() + "" + dbName + ";sql.enforce_strict_size=true" );

        // hsqlServer.setLogWriter( null );
        // hsqlServer.setErrWriter( null );
        hsqlServer.start();
        getLog().info( "HSQL Launched" );
        getLog().info( hsqlServer.getStateDescriptor() );
        getLog().info( "Live name: " + hsqlServer.getDatabaseName( 0, false ) );

        deleteFilesOnExit();

    }

    /**
     * 
     */
    private void deleteFilesOnEntry()
    {
        if ( !deleteOnEntry )
            return;

        getLog().info( "Deleting HSQL Files on Entry" );

        File dir = new File( "." );
        File[] files = dir.listFiles( new FileFilter()
        {
            public boolean accept( File pathname )
            {
                return pathname.getName().startsWith( dbName );
            }
        } );

        if ( files == null )
            return;

        for ( int i = 0; i < files.length; i++ )
            deleteFile( files[i] );
    }

    private void deleteFile( File file )
    {
        if ( file.isDirectory() )
        {
            File[] files = file.listFiles();
            if ( files != null )
            {
                for ( int i = 0; i < files.length; i++ )
                    deleteFile( files[i] );
            }
        }

        file.delete();
    }

    /**
     * If deleteOnExit is true, hsql files are going to be removed
     */
    private void deleteFilesOnExit()
    {

        if ( isTransient == false )
        {
            if ( deleteOnExit == true )
            {

                deleteOnExitDbFileWithExtensionScript( ".log" );
                deleteOnExitDbFileWithExtensionScript( ".properties" );
                deleteOnExitDbFileWithExtensionScript( ".script" );
                deleteOnExitDbFileWithExtensionScript( ".data" );
                deleteOnExitDbFileWithExtensionScript( ".backup" );
            }
        }
    }

    /**
     * Open a file over hsql temporaries files and delete them when the JVM shut down.
     * 
     * @param ext
     *            the extension of the file to be deleted.
     * 
     */
    private void deleteOnExitDbFileWithExtensionScript( String ext )
    {
        File dbFile = new File( dbName + ext );
        if ( dbFile.exists() )
        {
            dbFile.deleteOnExit();
        }
    }

    /**
     * @return <span>mem:</span> if an using a transient database, <span>file:</span> otherwise
     */
    private String persistenceDevice()
    {
        String persistenceDevice = "";
        if ( isTransient )
        {
            persistenceDevice = "mem:";
        }
        else
        {
            persistenceDevice = "file:";
        }

        return persistenceDevice;
    }

    /**
     * Create an istance of the org.hsql.Server interface. If the property <span>mode</span> is 'webserver', also the
     * <span>port</span> is being used.
     * 
     * @return an istance of org.hsql.Server
     * @throws MojoExecutionException
     */
    private Server createServer() throws MojoExecutionException
    {

        Server hsqlServer = null;
        if ( mode.equalsIgnoreCase( "server" ) )
            hsqlServer = new Server();
        else if ( mode.equalsIgnoreCase( "webserver" ) )
            hsqlServer = new WebServer();
        else
            throw new MojoExecutionException( "This release doesn't support [" + mode + "].Try 'server' instead." );

        return hsqlServer;
    }

}
