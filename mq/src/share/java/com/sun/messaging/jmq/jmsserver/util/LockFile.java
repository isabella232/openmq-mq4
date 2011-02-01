/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2000-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/*
 * @(#)LockFile.java	1.11 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.util;

import java.io.*;
import java.net.*;
import java.util.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.*;

/**
 * This class encapsulates a broker lock file. The lock file makes sure
 * that no two brokers using the same instance name are running at the
 * same time (using different port numbers). The algorithm goes like this:
 *
 * Try to create a lock file in $JMQ_VARHOME/instances/<instancename>lock
 * If lock didn't exist previously and was created then we got the
 *    lock write <instancename>:hostname:port\n to lock file and return.
 * Else if lock file already exists read it.
 * If contents of lock file match the instancename, hostname and port
 *    of this broker then the lock file was left over from a previous
 *    run of this broker and we assume we got the lock and return.
 * Else try to connect to the broker on host:port to see if it is still up.
 * If we connect to broker then we failed to get lock. return.
 * Else assume the lock file is cruft. Remove it and try to acquire again.
 *     
 */

public class LockFile
{
    private static LockFile currentLockFile = null;

    private String hostname = null;
    private String instance = null;
    private String filePath = null;
    private int    port = 0;
    private boolean isMyLock = false;


    private LockFile() {
    }

    private LockFile(String instance, String hostname, int port) {
        this.hostname = hostname;
        this.instance = instance;
        this.port = port;
    }

    public static synchronized void clearLock()
    {
        currentLockFile = null;
    }

    /**
     * Get the lock file for the specified instance of the broker.
     * If no lock file exists one is created using the parameters
     * provided. If one does exist it is loaded.
     * The caller should use the isMyLock() method on the returned
     * LockFile to determine if it acquired the lock, or if somebody
     * else has it.
     *
     */
    public synchronized static LockFile getLock(String varhome,
				       String instance,
				       String hostname,
				       int port)
        throws IOException
    {

        LockFile lf = null;
        File file = new File(getLockFilePath(varhome, instance));

        // Grab lock by creating lock file. 
        if (file.createNewFile()) {
            // Got the lock! Lock file didn't exist and was created.
            // Write info to it and register for it to be removed on VM exit
            lf = new LockFile(instance, hostname, port);
            lf.filePath = file.getCanonicalPath();
            lf.isMyLock = true;
            lf.writeLockFile(file);
            file.deleteOnExit();
	    currentLockFile = lf;
            return lf;
        }

        // Lock file already exists. Read in contents
        lf = loadLockFile(file);
        lf.filePath = file.getCanonicalPath();

        // Check if it is ours (maybe left over if we previously crashed).
        if (port == lf.getPort() &&
            equivalentHostNames(hostname, lf.getHost(), false) &&
            instance.equals(lf.getInstance())) {

            // It's ours! No need to read-write it.
            file.deleteOnExit();
            lf.isMyLock = true;
	        currentLockFile = lf;
            return lf;
        } else if ( port == lf.getPort() &&
        		    isSameIP (hostname, lf.getHost()) &&
        		    instance.equals(lf.getInstance())
        		) {
        	
        	//update hostname if same ip with diff host name
        	lf.updateHostname(hostname);
            file.deleteOnExit();
            lf.isMyLock = true;
	        currentLockFile = lf;
            return lf;
        }

        // Not ours. See if owner is still running
        Socket s = null;
        try {
            // Try opening socket to other broker's portmapper. If we
            // can open a socket then the lock file is in use.
            s = new Socket(InetAddress.getByName(lf.hostname), lf.port);
            lf.isMyLock = false;
        } catch (IOException e) {
            // Looks like owner is not running. Take lock
            if (!file.delete()) {
                throw new IOException(Globals.getBrokerResources()
                    .getString(BrokerResources.X_LOCKFILE_BADDEL));
            }
            // Lock file should be gone, resursive call should acquire it
            return getLock(varhome, instance, hostname, port);
        }

        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
            }
        }

	currentLockFile = lf;
        return lf;
    }
    
    /**
     * check if host1 and host 2 have the same IP address.
     * @param host1
     * @param host2
     * @return true if we can obtain IPs from host1 and host2 and they are the equal.
     */
    public static boolean isSameIP (String host1, String host2) {
    	
    	boolean sflag = false;
    	
    	try {
    		
    		String addr1 = InetAddress.getByName(host1).getHostAddress();
    		
    		String addr2 = InetAddress.getByName(host2).getHostAddress();
    		
    		if ( addr1.equals(addr2) ) {
    			sflag = true;
    		}
    		
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return sflag;
    }

    /**
     * Return the path to the lock file
     */
    public static String getLockFilePath(String varhome, String instance) {
        return varhome + File.separator + Globals.INSTANCES_HOME_DIRECTORY +
		File.separator + instance + File.separator + "lock";
    }

    /**
     * Returns true if this process acquired the lock. Returns false
     * if another process has the lock
     */
    public boolean isMyLock() {
        return isMyLock;
    }

    public String getHost() {
        return hostname;
    }

    public String getInstance() {
        return instance;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getPort() {
        return port;
    }

    public String toString() {
        return instance + " " + hostname + ":" + port + " (" + isMyLock + ")";
    }

    /**
     * Update the port number in the lock file. Needed because the
     * broker's port number may change via admin while it is running
     */
    public void updatePort(int port) throws IOException {

        File file = new File(filePath);
	int oldPort = this.port;

	this.port = port;

	try {
            writeLockFile(file);
	} catch (IOException e) {
	    this.port = oldPort;
	    throw e;
        }
    }

    /**
     * Update the hostname in the lock file. Needed because the
     * broker's hostname may change via admin while it is running
     */
    public void updateHostname(String hostname) throws IOException {

        File file = new File(filePath);
	String oldHostname = this.hostname;

	this.hostname = hostname;

	try {
            writeLockFile(file);
	} catch (IOException e) {
	    this.hostname = oldHostname;
	    throw e;
        }
    }

    /**
     * Load the lock file. Does not attempt to acquire it.
     */
    private static synchronized LockFile loadLockFile(File file)
        throws IOException {

        byte[] data = new byte[128];
        LockFile lf = new LockFile();

        FileInputStream fis = new FileInputStream(file);

        fis.read(data);

        String s = new String(data, "UTF8");

        StringTokenizer st = null;
        int i1 = s.indexOf(':');
        if (i1 == -1) {
            throw new IOException(Globals.getBrokerResources().getKString(
                BrokerResources.X_LOCKFILE_CONTENT_FORMAT, file.toString(), s));
        }
        st = new StringTokenizer(s.substring(0, i1), " \t\n\r\f");
        lf.instance = st.nextToken();
        int i2 = s.lastIndexOf(':');
        if (i2 == -1 || i1 == i2) {
            throw new IOException(Globals.getBrokerResources().getKString(
                BrokerResources.X_LOCKFILE_CONTENT_FORMAT, file.toString(), s));
        }
        st = new StringTokenizer(s.substring(i2+1), " \t\n\r\f");
        lf.port = Integer.parseInt(st.nextToken());
        st = new StringTokenizer(s.substring(i1+1, i2), " \t\n\r\f");
        lf.hostname = st.nextToken();

	    fis.close();

        return lf;
    }

    /**
     * Write the lock file. Assumes the file already exists.
     */
    public synchronized void writeLockFile(File file) throws IOException {

        String data = instance + ":" + hostname + ":" + port + "\n";

        FileOutputStream os = new FileOutputStream(file);
        os.write(data.getBytes("UTF8"));
        os.close();
        return;
    }

    /**
     * Get the current lock file
     */
    public static LockFile getCurrentLockFile() {
	return currentLockFile;
    }

    /**
     * Check if two hostname strings are equivalent. Note this is just
     * a simple string comparison.
     *
     * If "exact" is true then the two strings must match exactly.
     * Otherwise one string can be an unqualified version of the other.
     * For example if "exact" is false then the following are considered
     * equivalent: foo.central, foo.central.sun.com, foo. But foo.east
     * would not be equivalent.
     *
     * @param   h1  First hostname
     * @param   h2  Second hostname
     * @param   exact   True to perform an exact match. False to perform
     *                  a unqualified match.
     *
     */
    public static boolean equivalentHostNames(
                        String h1, String h2, boolean exact) {

        if (exact) {
            // Check for exact match
            return h1.equals(h2);
        }

        // Split hostnames by dots and make sure each component matches
        StringTokenizer st1 = new StringTokenizer(h1, ".");
        StringTokenizer st2 = new StringTokenizer(h2, ".");
        while (st1.hasMoreTokens() && st2.hasMoreTokens()) {
            if (!st1.nextToken().equals(st2.nextToken())) {
                return false;
            }
        }

        return true;
    }
}

