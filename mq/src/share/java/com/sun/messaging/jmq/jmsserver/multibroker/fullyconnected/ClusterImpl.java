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
 * @(#)ClusterImpl.java	1.106 07/08/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.util.*;
import java.io.*;
import java.net.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.core.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.multibroker.BrokerInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterCallback;
import com.sun.messaging.jmq.jmsserver.multibroker.Protocol;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.service.PortMapper;
import com.sun.messaging.jmq.jmsserver.license.LicenseBase;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.LoopbackAddressException;
import com.sun.messaging.jmq.jmsserver.util.VerifyAddressException;
import com.sun.messaging.jmq.net.MQServerSocketFactory;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.persist.LoadException;
import com.sun.messaging.jmq.jmsserver.persist.Store;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterBrokerInfoReply;
import com.sun.messaging.jmq.jmsserver.persist.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterInfoInfo;

import com.sun.messaging.jmq.jmsserver.cluster.*;
import com.sun.messaging.jmq.jmsserver.cluster.ha.*;
import com.sun.messaging.jmq.io.MQAddress;

import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import javax.net.ServerSocketFactory;

/**
 * This class implements the default fully connected topology.
 * Version 2.0 : Brokers are connected using single TCP connection.
 */
public class ClusterImpl implements Cluster, ClusterListener {

    static boolean DEBUG = false;

    ClusterCallback cb = null;
    private boolean supportClusters = false;
    private int connLimit = 0;
    private Properties matchProps = null;
    protected boolean useGPackets = false;

    private BrokerAddressImpl self;
    private Map connectList;
    private HashMap brokerList;
    private boolean readyForBroadcast = false;
    private int flowControlState = Packet.RESUME_FLOW;

    private String transport = null; // "tcp" or "ssl"
    private InetAddress listenHost = null;
    private int listenPort = 0;
    private ClusterServiceListener listener = null;

    private PingTimerTask pingTimer = null;
    private static final long DEFAULT_PING_INTERVAL = 60L; //sec
    private long pingInterval = Globals.getConfig().getLongProperty(
      Globals.IMQ + ".cluster.ping.interval", DEFAULT_PING_INTERVAL)*1000L;

    private int tcpInbufsz = Globals.getConfig().getIntProperty(Globals.IMQ + ".cluster.tcp.inbufsz", 2048);
    private int sslInbufsz = Globals.getConfig().getIntProperty(
                                     Globals.IMQ + ".cluster.ssl.inbufsz", 
                                     Globals.getConfig().getIntProperty(Globals.IMQ + ".cluster.tls.inbufsz", 2048));

    private int tcpOutbufsz = Globals.getConfig().getIntProperty(Globals.IMQ + ".cluster.tcp.outbufsz", 2048);
    private int sslOutbufsz = Globals.getConfig().getIntProperty(
                                     Globals.IMQ + ".cluster.ssl.outbufsz", 
                                     Globals.getConfig().getIntProperty(Globals.IMQ + ".cluster.tls.outbufsz", 2048));

    private boolean tcpNodelay = Globals.getConfig().getBooleanProperty(Globals.IMQ + ".cluster.tcp.nodelay", true);
    private boolean sslNodelay = Globals.getConfig().getBooleanProperty(
                                     Globals.IMQ + ".cluster.ssl.nodelay", 
                                     Globals.getConfig().getBooleanProperty(Globals.IMQ + ".cluster.tls.nodelay", true));

    private Object configServerLock =  new Object();
    private BrokerAddressImpl configServer = null;
    private boolean configServerResolved = false;

    public static final String SERVICE_NAME = "cluster";
    public static final String SERVICE_TYPE = "CLUSTER";

    private static final Logger logger = Globals.getLogger();
    private static final BrokerResources br = Globals.getBrokerResources();

    private TimerTask warningTask = null;
    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 30; //seconds
    private static final String SHUTDOWN_TIMEOUT_PROP = "imq.cluster.shutdownTimeout";

    private ClusterManager clsmgr = null;

    public ClusterImpl(int connLimit) throws BrokerException {
        clsmgr = Globals.getClusterManager();

        transport = clsmgr.getTransport();
        assert ( transport != null );
        if (transport.equalsIgnoreCase("tls")) {
            transport = "ssl";
        }

        boolean nocluster = (!Globals.getHAEnabled() && clsmgr.getConfigBrokerCount() == 0);

        listenPort = clsmgr.getClusterPort();
        String listenh = clsmgr.getClusterHost(); //can be null 
        try {
            listenHost = BrokerMQAddress.resolveBindAddress(listenh, true);
        } catch (LoopbackAddressException e) {
            int level = nocluster ? Logger.WARNING: Logger.ERROR;
            logger.log(level, br.E_CLUSTER_HOSTNAME, ClusterManager.HOST_PROPERTY);
            if (nocluster) throw e;
            Broker.getBroker().exit(1,  br.getKString(br.E_CLUSTER_HOSTNAME, ClusterManager.HOST_PROPERTY),
                                        BrokerEvent.Type.FATAL_ERROR);
            throw e;
        } catch (Exception e) {
            if (e instanceof BrokerException) {
            logger.log(Logger.ERROR, 
                   br.getKString(br.E_BADADDRESS_CLUSTER_SERVICE, 
                                 ClusterManager.HOST_PROPERTY, e.getMessage()));
            } else {
            logger.logStack(Logger.ERROR, 
                   br.getKString(br.E_BADADDRESS_CLUSTER_SERVICE, 
                                 ClusterManager.HOST_PROPERTY, e.getMessage()), e);
            }
            Broker.getBroker().exit(1, 
                   br.getString(br.E_BADADDRESS_CLUSTER_SERVICE, 
                                ClusterManager.HOST_PROPERTY, e.getMessage()),
                   BrokerEvent.Type.FATAL_ERROR);

            if (e instanceof BrokerException) throw (BrokerException)e;
            BrokerException be = new BrokerException(e.getMessage());
            be.initCause(e);
            throw be;
        } 

        try {
            BrokerMQAddress.checkLoopbackAddress(
                            Globals.getPortMapper().getBindAddress(),
                            Globals.getPortMapper().getHostname());
        } catch (LoopbackAddressException e) {
            int level = nocluster ? Logger.WARNING: Logger.ERROR;
            logger.log(level, br.E_BADADDRESS_PORTMAPPER_FOR_CLUSTER,
                              PortMapper.HOSTNAME_PROPERTY, e.getMessage());
            if (nocluster) throw e;
            Broker.getBroker().exit(1, 
                   br.getString(br.E_BADADDRESS_PORTMAPPER_FOR_CLUSTER,
                                PortMapper.HOSTNAME_PROPERTY, e.getMessage()),
                   BrokerEvent.Type.FATAL_ERROR);
            throw e;
        } catch (Exception e) {
            if (e instanceof BrokerException) {
            logger.log(logger.ERROR, br.E_BADADDRESS_PORTMAPPER_FOR_CLUSTER,
                       PortMapper.HOSTNAME_PROPERTY, e.getMessage());
            } else {
            logger.logStack(logger.ERROR, br.E_BADADDRESS_PORTMAPPER_FOR_CLUSTER,
                            PortMapper.HOSTNAME_PROPERTY, e.getMessage(), e);
            }
            Broker.getBroker().exit(1, 
                   br.getString(br.E_BADADDRESS_PORTMAPPER_FOR_CLUSTER,
                                PortMapper.HOSTNAME_PROPERTY, e.getMessage()),
                   BrokerEvent.Type.FATAL_ERROR);

            if (e instanceof BrokerException) throw (BrokerException)e;
            BrokerException be = new BrokerException(e.getMessage());
            be.initCause(e);
            throw be;
        }

        try {
            self = new BrokerAddressImpl();
        } catch (LoopbackAddressException e) {
            int level = nocluster ? Logger.WARNING: Logger.ERROR;
            logger.log(level, br.getKString(br.E_BADADDRESS_THIS_BROKER, e.getMessage()));

            if (nocluster) throw e;
            Broker.getBroker().exit(1,  
                   br.getString(br.E_BADADDRESS_THIS_BROKER, e.getMessage()),
                   BrokerEvent.Type.FATAL_ERROR);
            throw e;
        } catch (Exception e) {
            if (e instanceof BrokerException) {
            logger.log(Logger.ERROR, 
                   br.getKString(br.E_BADADDRESS_THIS_BROKER, e.getMessage()));
            } else {
            logger.logStack(Logger.ERROR, 
                   br.getKString(br.E_BADADDRESS_THIS_BROKER, e.getMessage()), e);
            }
            Broker.getBroker().exit(1, 
                   br.getString(br.E_BADADDRESS_THIS_BROKER, e.getMessage()),
                   BrokerEvent.Type.FATAL_ERROR);

            if (e instanceof BrokerException) throw (BrokerException)e;
            BrokerException be = new BrokerException(e.getMessage());
            be.initCause(e);
            throw be;
        } 

        LicenseBase lb = Globals.getCurrentLicense(null);
        this.supportClusters = lb.getBooleanProperty(lb.PROP_ENABLE_CLUSTER, false);

        this.connLimit = connLimit;
        readyForBroadcast = false;

        ClusteredBroker masterb = clsmgr.getMasterBroker();
        initConfigServer(masterb);

        initBrokerList();
    }

    private void initConfigServer(ClusteredBroker masterb) throws BrokerException {
        if (masterb == null) {
            configServer = null;
            configServerResolved = true;
            return;
        }

        if (supportClusters == false) {
            String emsg = br.getKString(br.E_FATAL_FEATURE_UNAVAILABLE,
                             Globals.getBrokerResources().getString(
                                     BrokerResources.M_BROKER_CLUSTERS));
            logger.log(Logger.ERROR, emsg);
            Broker.getBroker().exit(1, emsg, BrokerEvent.Type.FATAL_ERROR);
            throw new BrokerException(emsg);
        }

        if (Globals.getHAEnabled()) { //sync log message /w clsmgr
            String emsg = br.getKString(br.E_CLUSTER_HA_NOT_SUPPORT_MASTERBROKER);
            logger.log(Logger.ERROR, emsg);
            Broker.getBroker().exit(1, emsg, BrokerEvent.Type.FATAL_ERROR);
            throw new BrokerException(emsg);
        }

        checkStoredLastConfigServer();

        try { 
            configServer = new BrokerAddressImpl((BrokerMQAddress)masterb.getBrokerURL(), null, 
                                                 Globals.getHAEnabled(), masterb.getBrokerName());
        }
        catch (Exception e) {
            configServer = null;
            configServerResolved = false;
            throw new BrokerException(e.getMessage(), e);
        }

        BrokerMQAddress key = configServer.getMQAddress();
        if (key.equals(self.getMQAddress())) {
            configServer = self;
            configServerResolved = true;
            return;
        }

        configServerResolved = false;
    }

    /**
     * Check to see if stored last config server property loaded properly
     *  
     * System.exit if
     *   the last config server info is corrupted
     *   the last refresh timestamp info is corrupted and unable to reset
     *   potentially last config server info is corrupted:
     *       store LoadPropertyException occurred with key corruption 
     *	     and LastConfigServer property does not in store
     *
     * In future release, the System.exit behavior may be changed to
     * allow administratively repair - eg. through imqbrokerd option
     * to force set last config server info to the current master broker 
     *
     */
    private void checkStoredLastConfigServer() throws BrokerException {
        Store s =  Globals.getStore();

        boolean bad = false; 
        boolean potentiallyBad = false;
        LoadException le  =  s.getLoadPropertyException();
        LoadException savele = null;
        while (le != null) {
            Object o = le.getKey();
            if (o == null || ! (o instanceof String)) {
                potentiallyBad = true;
                savele = le;
                le = le.getNextException(); 
                continue;
            } 
            if (((String)o).equals(ClusterGlobals.STORE_PROPERTY_LASTCONFIGSERVER)) {
              logger.log(Logger.ERROR, BrokerResources.E_CLUSTER_LOAD_LASTCONFIGSERVER, le); 
              bad = true;            
              break;
            }
            if (((String)o).equals(ClusterGlobals.STORE_PROPERTY_LASTREFRESHTIME)) {
                logger.log(Logger.WARNING, BrokerResources.W_CLUSTER_LOAD_LASTREFRESHTIME, le); 
                try {
                s.updateProperty(ClusterGlobals.STORE_PROPERTY_LASTREFRESHTIME, new Long(-1), false);
                } catch (BrokerException e) {
                logger.log(Logger.ERROR, BrokerResources.E_CLUSTER_RESET_LASTREFRESHTIME, e); 
                bad = true;
                break;
                };
            }
            le = le.getNextException(); 
        }
        if (potentiallyBad && !bad) {
            try {
                if (s.getProperty(ClusterGlobals.STORE_PROPERTY_LASTCONFIGSERVER) == null) {
                   logger.log(Logger.ERROR, BrokerResources.E_CLUSTER_LOAD_LASTCONFIGSERVER, savele);
                   bad = true;
                }
            } catch (BrokerException e) {
                logger.log(Logger.ERROR, e.getMessage(), e);
                logger.log(Logger.ERROR, BrokerResources.E_CLUSTER_LOAD_LASTCONFIGSERVER, savele);
                bad = true;
            }
        }
        if (bad)  {
            logger.log(Logger.ERROR, BrokerResources.E_CLUSTER_LOAD_LASTCONFIGSERVER);
            Broker.getBroker().exit(1, Globals.getBrokerResources().getKString(
                BrokerResources.E_CLUSTER_LOAD_LASTCONFIGSERVER), BrokerEvent.Type.FATAL_ERROR);
        }
    }

    /**
     * Processes the configuration and command line info.
     *
     * Creates 'connectList' : A mapping of
     *      Remote BrokerAddress <--> BrokerLink
     * objects for all the 'autoConnect' links that are initiated by
     * this broker.
     */
    private void initBrokerList() throws BrokerException {
        connectList = Collections.synchronizedMap(new HashMap());

        BrokerMQAddress selfKey = self.getMQAddress();
        if (DEBUG) {
            logger.log(Logger.INFO,
                "ClusterImpl:initBrokerList. selfKey =" + selfKey);
        }

        ClusteredBroker cb = null;
        BrokerAddressImpl b = null;
        BrokerLink link = null;
        Iterator itr = clsmgr.getConfigBrokers(); 
        int i = 0;
        while (itr.hasNext()) {
            cb = (ClusteredBroker)itr.next();
            try {
                b = new BrokerAddressImpl((BrokerMQAddress)cb.getBrokerURL(), null, 
                                          Globals.getHAEnabled(), cb.getBrokerName());
            }
            catch (Exception e) {
                throw new BrokerException(e.getMessage(), e);
            }
            BrokerMQAddress key = b.getMQAddress();
            if (!key.equals(selfKey)) {
                link = new BrokerLink(self, b, this);
                link.setAutoConnect(true);
                connectList.put(key, link);
                i++;
                if (DEBUG) {
                    logger.log(Logger.INFO,
                    "ClusterImpl: Added to connectList: key="+key+", link="+link +" ("+i+")");
                }
            }
        }
        
        if (connectList.size() > 0 && supportClusters == false) {
            String emsg = br.getKString(br.E_FATAL_FEATURE_UNAVAILABLE,
                          Globals.getBrokerResources().getString(
                                  BrokerResources.M_BROKER_CLUSTERS));
            logger.log(Logger.ERROR, emsg);
            Broker.getBroker().exit(1, emsg, BrokerEvent.Type.FATAL_ERROR);
            throw new BrokerException(emsg);
        }

        /*
         * BugId : 4455044 - Check the license connection limits here
         * to include the "-cluster" arguments. Same check is performed
         * in setConnectList() to validate dynamic configuration of
         * the brokerlist..
         */
        if (connectList.size() > connLimit) {
            String emsg = br.getKString(br.E_MBUS_CONN_LIMIT, 
                                        Integer.toString(connLimit + 1));
            logger.log(Logger.ERROR, emsg);
            Broker.getBroker().exit(1, emsg, BrokerEvent.Type.FATAL_ERROR);
            throw new BrokerException(emsg);
        }

        brokerList = new HashMap();
    }


    private BrokerLink searchBrokerList(BrokerMQAddress key) {
        if (brokerList == null)
            return null;

        synchronized (brokerList) {
            Iterator itr = brokerList.keySet().iterator();

            while (itr.hasNext()) {
                BrokerAddressImpl b = (BrokerAddressImpl) itr.next();
                if (key.equals(b.getMQAddress()))
                    return (BrokerLink) brokerList.get(b);
            }

            return null;
        }
    }

    protected String getTransport() {
        return transport;
    }

    protected boolean getTCPNodelay() {
        return tcpNodelay;
    }

    protected boolean getSSLNodelay() {
        return sslNodelay;
    }

    protected int getTCPInputBufferSize() {
        return tcpInbufsz;
    }
    protected int getTCPOutputBufferSize() {
        return tcpOutbufsz;
    }

    protected int getSSLInputBufferSize() {
        return sslInbufsz;
    }
    protected int getSSLOutputBufferSize() {
        return sslOutbufsz;
    }

    private void setListenHost(String host) throws Exception {
        if (DEBUG) {
            logger.log(Logger.DEBUG,
                "ClusterImpl: Changing the listening hostname to {0}",
                host);
        }

        if (supportClusters == false)
            return;

        InetAddress saveListenHost = listenHost;
        try {
            listenHost = BrokerMQAddress.resolveBindAddress(host, true);
            ClusterServiceListener newListener = new ClusterServiceListener(this);

            // Success! Replace the global listener.
            if (listener != null)
                listener.shutdown();
            listener = newListener;
        }
        catch (Exception e) {
            listenHost = saveListenHost;
            throw e;
        }
    }

    protected InetAddress getListenHost() {
        return listenHost;
    }

    private void setListenPort(int port) throws IOException {
        String args[] = { SERVICE_NAME,
                          String.valueOf(port),
                          String.valueOf(1),
                          String.valueOf(1)};
        logger.log(Logger.INFO, BrokerResources.I_UPDATE_SERVICE_REQ, args);

        if (supportClusters == false)
            return;

        int saveListenPort = listenPort;

        try {
            listenPort = port;
            ClusterServiceListener newListener = new ClusterServiceListener(this);

            // Success! Replace the global listener.
            if (listener != null)
                listener.shutdown();
            listener = newListener;
        }
        catch (IOException e) {
            listenPort = saveListenPort;
            throw e;
        }
    }

    protected int getListenPort() {
        return listenPort;
    }

    protected boolean isConfigServerResolved() {
        return configServerResolved;
    }

    protected boolean waitForConfigSync() {
        if (cb == null) return true;
        return ((Protocol)cb).waitForConfigSync();
    }

    protected boolean checkConfigServer(BrokerAddressImpl b) {
        synchronized(configServerLock) {
            if (configServerResolved) {
                return true; // Alredy resolved...
            }

            if (configServer == null) {
                return false; // Illegal config, reject cluster connections.
            }

            BrokerMQAddress key = b.getMQAddress();
            if (key.equals(configServer.getMQAddress())) {
                configServer = b;
                configServerResolved = true;

                if (warningTask != null) {
                    warningTask.cancel();
                    warningTask = null;
                }
            }
            return configServerResolved;
        }
    }

    protected boolean addBroker(BrokerAddressImpl b, BrokerLink link) {
        if (DEBUG) {
            logger.log(Logger.DEBUGMED,
                "ClusterImpl: Activating link = {0}",
                link);
        }
        synchronized (brokerList) {
            BrokerLink rlink = (BrokerLink)brokerList.get(b);
            if (rlink != null) {
                BrokerAddressImpl remoteb = rlink.getRemote();
                if (!remoteb.getMQAddress().equals(b.getMQAddress())) { 
                    String args[] = { b.toShortString(), link.toString(), rlink.toString() };
                    logger.log(Logger.ERROR, br.getKString(br.E_MBUS_SAME_ADDRESS_PEERS, args));
                    Broker.getBroker().exit(1,
                       br.getString(br.E_MBUS_SAME_ADDRESS_PEERS, args),
                       BrokerEvent.Type.FATAL_ERROR, null, false, true, false);
                }
                return false;
            }

            brokerList.put(b, link);
            return true;
        }
    }

    protected void removeBroker(BrokerAddressImpl remote, BrokerLink link) {
        synchronized (brokerList) {
            BrokerLink reallink = (BrokerLink) brokerList.get(remote);
            if (reallink != null && reallink != link) {
                return;
            }
              
            if (brokerList.remove(remote) == null) 
                return;
            brokerList.notify();
           
        }
        cb.removeBrokerInfo(remote);

        if (DEBUG) {
            logger.log(Logger.DEBUGMED,
                "ClusterImpl: Removed link with = {0}",
                remote);
        }
    }

    protected void handleBrokerLinkShutdown(BrokerAddressImpl remote) {
        connectList.remove(remote.getMQAddress());
    }

    /**
     * Accept a connection from another broker.
     *
     * This method is invoked by the common 'ClusterServiceListener' object.
     * The first packet must be of type 'Packet.LINK_INIT' - so that
     * the identity of the remote broker can be determined.
     */
    protected void acceptConnection(Socket conn, boolean ssl) throws IOException {
        BrokerAddressImpl remote = null;
        try {
            remote = BrokerLink.consumeLinkInit(conn, null, this);
            if (remote == null) return;
        } catch (Exception e) {
            if (DEBUG) {
            logger.logStack(logger.DEBUG, e.getMessage(), e);
            }
            conn.close();
            return;
        }

        // If remote is in connectList, either
        //     Drop this connection.
        //     (i.e. This broker initiates the connections)
        // or
        //     Kill the brokerLink in connectList.
        //     (i.e. Let remote initiate the connections ...)
        //
        // This can happen if the brokers are started like this -
        //     broker -name A -cluster B ...
        //     broker -name B -cluster A ...
        //

        BrokerMQAddress key = remote.getMQAddress();
        BrokerLink oldlink = null;
        synchronized (connectList) {
            oldlink = (BrokerLink) connectList.get(key);
            if (oldlink != null) {
                if (connectionInitiator(remote)) {
                    connectList.remove(key);
                    oldlink.shutdown();
                    oldlink = null;
                }
            }
        }
        if (oldlink != null) {
            /** We already have a BrokerLink thread that's
             * trying to initiate the connection with the
             * remote broker. So just pass on this connection
             * to that thread..
             */
            oldlink.acceptConnection(remote, conn, ssl);
            return;
        }

        // Create the BrokerLink object
        BrokerLink link = new BrokerLink(self, remote, this);
        link.setAutoConnect(false);

        if (link.acceptConnection(remote, conn, ssl)) {
            link.start();
        }
    }

    /**
     * Construct a packet of type 'Packet.LINK_INIT' for this broker.
     *
     * Packet.LINK_INIT is always the first packet sent on any new
     * broker-to-broker connection.
     */
    protected Packet getLinkInitPkt() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeInt(self.getClusterVersion());

            dos.writeUTF(self.getHostName());
            dos.writeUTF(self.getInstanceName());
            dos.writeInt(self.getPort());

            BrokerAddressImpl cs = (BrokerAddressImpl)getConfiguredConfigServer();
            dos.writeBoolean(cs != null);

            if (cs != null) {
                dos.writeUTF(cs.getHostName());
                dos.writeUTF(cs.getInstanceName());
                dos.writeInt(cs.getPort());
            }

            dos.writeInt(matchProps.size());
            for (Enumeration e = matchProps.propertyNames();
                e.hasMoreElements(); ) {
                String prop = (String) e.nextElement();
                dos.writeUTF(prop);
                dos.writeUTF(matchProps.getProperty(prop));
            }

            dos.writeBoolean(Globals.getHAEnabled());
            dos.writeBoolean(self.getBrokerID() != null);
            if (self.getBrokerID() != null) {
                dos.writeUTF(self.getBrokerID());
            }
            dos.writeLong(self.getBrokerSessionUID().longValue());
            if (Globals.getHAEnabled()) {
                dos.writeLong(self.getStoreSessionUID().longValue());
            }

            dos.flush();
            bos.flush();
        }
        catch (Exception e) { /* Ignore */ }

        byte[] buf = bos.toByteArray();

        Packet p = new Packet();
        p.setPacketType(Packet.LINK_INIT);
        p.setPacketBody(buf);
        p.setDestId(0);

        return p;
    }

    /**
     * Process the Packet.LINK_INIT packet.
     */
    protected static LinkInfo processLinkInit(Packet p) throws Exception {
        String hostName = null;
        String instName = null;
        String brokerID = null;
        UID brokerSessionUID = null;
        UID storeSessionUID = null;
        boolean ha = false;
        int port = 0;

        BrokerAddressImpl remote = null;

        ByteArrayInputStream bis =
            new ByteArrayInputStream(p.getPacketBody());
        DataInputStream dis = new DataInputStream(bis);

        int clusterVersion = dis.readInt();

        hostName = dis.readUTF();
        instName = dis.readUTF();
        port = dis.readInt();

        BrokerAddressImpl configServer = null;
        boolean hasConfigServer = false;
        String cfgHostName = null;
        String cfgInstName = null;
        int cfgPort = 0;
        hasConfigServer = dis.readBoolean();
        if (hasConfigServer) {
            cfgHostName = dis.readUTF();
            cfgInstName = dis.readUTF();
            cfgPort = dis.readInt();
        }

        Properties props = new Properties();
        int nprops = dis.readInt();
        for (int i = 0; i < nprops; i++) {
            String prop = dis.readUTF();
            String value = dis.readUTF();
            props.setProperty(prop, value);
        }

        if (clusterVersion >= ProtocolGlobals.VERSION_400) {
            ha = dis.readBoolean();
            if (dis.readBoolean()) {
                brokerID = dis.readUTF();
            }
            brokerSessionUID = new UID(dis.readLong());
            if (ha) storeSessionUID = new UID(dis.readLong());
        }

        if (hasConfigServer) {
            if (ha) {
                throw new BrokerException(
                    br.getKString(br.E_CLUSTER_HA_NOT_SUPPORT_MASTERBROKER));
            }
            configServer = new BrokerAddressImpl(cfgHostName, cfgInstName, cfgPort, 
                                                 ha, null, null, null);
        }

        remote = new BrokerAddressImpl(hostName, instName, port, 
                                       ha, brokerID, brokerSessionUID, storeSessionUID);
        remote.setClusterVersion(clusterVersion);

        return new LinkInfo(remote, configServer, props);
    }

    /**
     * Detects duplicate connections between a pair of brokers.
     * @param remote BrokerAddress of the remote broker.
     * @return <code> true </code> if this broker is supposed to
     * initiate the connection, <code> false </code> if remote is
     * supposed to initiate the connection.
     */
    private boolean connectionInitiator(BrokerAddressImpl remote) {
        if (self.getMQAddress().hashCode() > remote.getMQAddress().hashCode())
            return true;

        return false;
    }

    /**
     * Construct the Packet.BROKER_INFO packet.
     */
    protected Packet getBrokerInfoPkt() {
        // Get the BrokerInfo object from MessageBus...
        BrokerInfo selfInfo = cb.getBrokerInfo();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(selfInfo);
            oos.flush();
            oos.close();
        }
        catch (Exception e) { /* Ignore */ }

        byte[] buf = bos.toByteArray();

        Packet p = new Packet();
        p.setPacketType(Packet.BROKER_INFO);
        p.setPacketBody(buf);
        p.setDestId(0);

        return p;
    }

    protected ClusterBrokerInfoReply getBrokerInfoReply(BrokerInfo remote) throws Exception {
        if (cb == null)  {
            throw new BrokerException(br.getKString(
                BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), Status.UNAVAILABLE);
        }
        return cb.getBrokerInfoReply(remote);
    }

    /**
     * Receive and process the BROKER_INFO packet.
     */
    private BrokerInfo receiveBrokerInfo(BrokerAddressImpl sender, byte[] pkt, String realRemote, BrokerLink l) {
        ByteArrayInputStream bis = new ByteArrayInputStream(pkt);
        BrokerInfo info = null;

        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            info = (BrokerInfo) ois.readObject();
            info.setRealRemoteString(realRemote);
        }
        catch (Exception e) {
            logger.log(Logger.WARNING, br.W_MBUS_SERIALIZATION, sender);

            if (l != null) l.shutdown();
            return null;
        }

        Integer v = info.getClusterProtocolVersion();
        if (v != null && v.intValue() >= ProtocolGlobals.VERSION_400) return info; 

        if (l != null) l.handshakeSent();

        int status = cb.addBrokerInfo(info);

        if (status == cb.ADD_BROKER_INFO_OK) return info;

        if (status == cb.ADD_BROKER_INFO_RETRY) {
            synchronized (brokerList) {
                BrokerLink link = (BrokerLink) brokerList.get(sender);
                if (link != null)
                    link.closeConn();
            }
        }
        else if (status == cb.ADD_BROKER_INFO_BAN) {
            synchronized (brokerList) {
                BrokerLink link = (BrokerLink) brokerList.get(sender);
                if (link != null)
                    link.shutdown();
            }
        }

        return null;

    }

    private void receiveBrokerInfoReply(BrokerAddressImpl sender, GPacket gp, String realRemote) {
        BrokerInfo info = null;
        try {
            ClusterBrokerInfoReply cbi = ClusterBrokerInfoReply.newInstance(gp);
            info = cbi.getBrokerInfo();
            info.setRealRemoteString(realRemote);
            if (DEBUG) {
            logger.log(Logger.DEBUG, "Received BROKER_INFO_REPLY from "+sender);
            }
            if (!info.getBrokerAddr().equals(sender)) {
                logger.log(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, 
                       "mismatched BROKER_INFO ("+info.getBrokerAddr()+") from "+sender);
                throw new BrokerException("mismatched BROKER_INFO");
            }
            if (Globals.getHAEnabled() && cbi.isTakingover()) {
                String msg = br.getKString(BrokerResources.E_CLUSTER_TAKINGOVER_NOTIFY_RESTART, sender);
                BrokerException ex = new BrokerException(msg);
                logger.log(logger.ERROR, msg);
                Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(), msg,
                                   BrokerEvent.Type.RESTART, null, true, true, true);
                throw ex;
            }
        } catch (Exception e) {
            if (DEBUG)  e.printStackTrace();
            logger.logStack(Logger.ERROR, e.getMessage(), e); 

            synchronized (brokerList) {
                BrokerLink link = (BrokerLink) brokerList.get(sender);
                if (link != null)
                    link.shutdown();
            }
            return;
        }

        int status = cb.addBrokerInfo(info);

        if (status == cb.ADD_BROKER_INFO_OK) return;

        if (status == cb.ADD_BROKER_INFO_RETRY) {
            synchronized (brokerList) {
                BrokerLink link = (BrokerLink) brokerList.get(sender);
                if (link != null)
                    link.closeConn();
            }
        }
        else if (status == cb.ADD_BROKER_INFO_BAN) {
            synchronized (brokerList) {
                BrokerLink link = (BrokerLink) brokerList.get(sender);
                if (link != null)
                    link.shutdown();
            }
        }
    }

    protected GPacket getFirstInfoPacket() {
        if (!Globals.useSharedConfigRecord()) {
            return null;
        }
        ChangeRecordInfo cri = cb.getLastStoredChangeRecord(); 
        if (cri == null) {
            return null;
        }
        ClusterInfoInfo cii = ClusterInfoInfo.newInstance();
        cii.setLastStoredChangeRecord(cri);
        cii.setIsFirstInfo(true);
        cii.setBroadcast(false);
        return cii.getGPacket();
    }

    protected void processFirstInfoPacket(GPacket gp, BrokerLink link) {
        try {
            if (!Globals.useSharedConfigRecord()) {//should never happen
                throw new BrokerException("Unexpected "+
                ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                " packet from "+link.getRemote());
            }
            ClusterInfoInfo cii = ClusterInfoInfo.newInstance(gp);
            ChangeRecordInfo cri = cii.getLastStoredChangeRecord();
            if (cri == null) {
                return;
            }
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVED_FIRST_INFO,
                ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                "[(sharecc)"+cri.toString()+"]", link.getRemote()));

            cb.syncChangeRecordOnJoin(link.getRemote(), cri);

        } catch (Exception e) {
            logger.logStack(logger.ERROR, e.getMessage(), e);
            if (e instanceof BrokerException) {
                if (((BrokerException)e).getStatusCode() == 
                    Status.PRECONDITION_FAILED) {
                    link.shutdown(); 
                    return;
                }
            }
            link.closeConn();
        }
    }

    private void setFlowControl(BrokerAddressImpl addr, boolean enabled) {
        BrokerLink link = null;
        synchronized (brokerList) {
            link = (BrokerLink) brokerList.get(addr);
        }
        if (link != null)
            link.setFlowControl(enabled);
    }

    /**
     * Receive packets from remote brokers.
     */
    protected Object receivePacket(BrokerAddressImpl from, Packet p, String realRemote, BrokerLink l) {
        Object ret = null;

        if (cb == null)
            return ret;

        switch (p.getPacketType()) {
        case Packet.UNICAST:
            cb.receiveUnicast(from, p.getDestId(), p.getPacketBody());
            break;

        case Packet.BROADCAST:
            cb.receiveBroadcast(from, p.getDestId(), p.getPacketBody());
            break;

        case Packet.BROKER_INFO:
            ret = receiveBrokerInfo(from, p.getPacketBody(), realRemote, l);
            break;

        case Packet.STOP_FLOW:
            setFlowControl(from, true);
            break;

        case Packet.RESUME_FLOW:
            setFlowControl(from, false);
            break;

        case Packet.PING:
            if (DEBUG) {
                logger.log(Logger.DEBUG,
                    "ClusterImpl: Received ping from : " + from);
            }
            break;
        default:
            logger.log(logger.WARNING,
                "ClusterImpl: INTERNAL ERROR -" +
                " Received Unknown packet from : " + from);
        }
        return ret;
    }

    protected void receivePacket(BrokerAddressImpl from, GPacket gp, String realRemote) {
        if (gp.getType() == ProtocolGlobals.G_BROKER_INFO_REPLY) {
            receiveBrokerInfoReply(from, gp, realRemote);
            return;
        }
        if (cb == null)
            return;

        if (gp.getBit(gp.B_BIT))
            cb.receiveBroadcast(from, gp);
        else
            cb.receiveUnicast(from, gp);
    }

    public void useGPackets(boolean useGPackets) {
        this.useGPackets = useGPackets;
    }

    public void setCallback(ClusterCallback cb) {
        this.cb = cb;
        self.setClusterVersion(cb.getHighestSupportedVersion());
    }

    public void setMatchProps(Properties matchProps) {
        this.matchProps = matchProps;
    }

    protected Properties getMatchProps() {
        return matchProps;
    }

    public BrokerAddress getSelfAddress() {
        return (BrokerAddress) self;
    }

    public BrokerAddress getConfigServer() throws BrokerException {
        synchronized(configServerLock) {
            if (! configServerResolved) {
                if (Globals.getConfig().getBooleanProperty(
                    Globals.IMQ + ".cluster.masterbroker.enforce", true)) {
                    throw new BrokerException(
                        Globals.getBrokerResources().getString(
                            BrokerResources.X_CLUSTER_UNREACHABLE, 
                            (configServer == null ? "null":configServer.toString())));
                } else {
                    return null;
                }
            }
            return configServer;
        }
    }

    public void changeMasterBroker(BrokerAddress newmaster,
                                   BrokerAddress oldmaster)
                                   throws BrokerException {
        synchronized(configServerLock) {

            logger.log(logger.INFO, br.getKString(
                br.I_CLUSTER_CHANGE_MASTER_BROKER, 
                (configServer == null ? "null":configServer.toString()),
                newmaster.toString()));

            if (configServer == null) {
                throw new BrokerException(
                    br.getKString(br.X_CLUSTER_NO_MASTER_BROKER_REJECT_CHANGE_MASTER),
                    Status.PRECONDITION_FAILED);
            }
            if (!oldmaster.equals(configServer)) {
                throw new BrokerException(br.getKString(
                    br.X_CLUSTER_CHANGE_MASTER_BROKER_MISMATCH, 
                        oldmaster.toString(), configServer.toString()),
                    Status.PRECONDITION_FAILED);
 
            }

            BrokerAddressImpl oldconfigServer = this.configServer;
            this.configServer = (BrokerAddressImpl)newmaster;

            String newmasterhp = newmaster.getMQAddress().getHostAddressNPort();
            String oldmasterhp = Globals.getConfig().getProperty(
                                      ClusterManagerImpl.CONFIG_SERVER);
            Properties prop = new Properties();
            prop.put(ClusterManagerImpl.CONFIG_SERVER, newmasterhp);
            try {
                Globals.getConfig().updateProperties(prop, true);
            } catch (Exception e) {
                String emsg = br.getKString(br.E_CHANGE_MASTER_BROKER_FAIL, e.getMessage());
                logger.logStack(logger.ERROR, emsg, e);
                try {
                    prop.put(ClusterManagerImpl.CONFIG_SERVER, oldmasterhp);
                } catch (Exception e1) {
                    logger.log(logger.ERROR, br.getKString(
                        br.E_CLUSTER_RESTORE_MASTER_BROKER_PROP_FAIL, e1.getMessage()));
                    throw new BrokerException(emsg, e, Status.ERROR);
                }
                this.configServer = oldconfigServer;
                throw new BrokerException(emsg, e, Status.PRECONDITION_FAILED);  
            }
        }
    }

    protected BrokerAddress getConfiguredConfigServer() {
        synchronized(configServerLock) {
            return configServer;
        }
    }

    public void marshalBrokerAddress(BrokerAddress addr, GPacket gp) {
        ((BrokerAddressImpl)addr).writeBrokerAddress(gp);
    }

    public BrokerAddress unmarshalBrokerAddress(GPacket gp) throws Exception {
        return BrokerAddressImpl.readBrokerAddress(gp);
    }

    /**
     * Start all the initial BrokerLink threads in 'connectList'.
     * This list contains all the links (connections) to be initiated
     * by this broker.
     */
    public void start() throws IOException {
        if (supportClusters == false)
            return;

        clsmgr.addEventListener(this);

        synchronized(connectList) {
            Collection values = connectList.values();
            Iterator itr = values.iterator();
            while (itr.hasNext()) {
                BrokerLink link = (BrokerLink) itr.next();
                link.start();
            }
        }

        listener = new ClusterServiceListener(this);
        Object[] args = {SERVICE_NAME,
                         getTransport() +" [ " + getServerSocketString() + " ]",
                          new Integer(1), new Integer(1)};
        logger.log(Logger.INFO, BrokerResources.I_SERVICE_START, args);

        if (Globals.getClusterID() != null) {
            logger.log(Logger.INFO, BrokerResources.I_CLUSTER_USING_CLUSTERID, 
                                    Globals.getClusterID());
        }

        if (! configServerResolved) {
            warningTask = new WarningTask(this);
            Globals.getTimer().schedule(warningTask, 60 * 1000, 180 * 1000);
        }

        if (pingInterval <= 0) pingInterval = DEFAULT_PING_INTERVAL*1000;
        long p = pingInterval/1000; 
        pingTimer = new PingTimerTask();
        logger.log(Logger.INFO, BrokerResources.I_CLUSTER_PING_INTERVAL, new Long(p));
        Globals.getTimer().schedule(pingTimer, pingInterval, pingInterval);
    }

    protected String getServerSocketString() {
        ClusterServiceListener l = listener;
        if (l != null) return l.getServerSocketString();
        return null;
    }

    /**
     * Shutdown the cluster.
     */
    public void shutdown() {
        if (supportClusters == false)
            return;

        if (listener != null)
            listener.shutdown(); // Stop accepting connections.

        if (brokerList == null)
            return;

        long timeout = Globals.getConfig().getIntProperty(
                        SHUTDOWN_TIMEOUT_PROP, DEFAULT_SHUTDOWN_TIMEOUT);
        long waittime = timeout * 1000;
        long endtime = System.currentTimeMillis() + waittime;
        
        int pv = -1;
        try {
		pv = Globals.getClusterBroadcast().getClusterVersion();
        } catch (Exception e) {
        logger.log(logger.DEBUG, 
        "Unable to get cluster protocol version on cluster shutdown: "+e.getMessage());
        }
        if (pv >= ClusterBroadcast.VERSION_400) {

        synchronized (brokerList) {
            while (brokerList.size() != 0) {
                logger.log(logger.INFO,  br.getKString(
                br.I_CLUSTER_SERVICE_SHUTDOWN_WAITING, new Integer(brokerList.size())));
                try {
                brokerList.wait(waittime);
                } catch (Exception e) {}

                long curtime = System.currentTimeMillis();
                if (curtime >= endtime)  {
                    if (brokerList.size() > 0) {
                    logger.log(logger.WARNING, br.getKString(
                           br.W_CLUSTER_SERVICE_SHUTDOWN_TIMEOUT));
                    }
                    break;
                }
                waittime = endtime - curtime;
            }
        }
        }

        BrokerAddress remote = null;
        BrokerLink l = null;
        synchronized(brokerList) {
            Iterator itr = brokerList.keySet().iterator();
            while (itr.hasNext()) {
                remote = (BrokerAddress)itr.next(); 
                l = (BrokerLink)brokerList.get(remote);
                logger.log(logger.WARNING, br.getKString(
                    br.W_CLUSTER_FORCE_CLOSE_LINK, remote, 
                        br.getString(br.M_LINK_SHUTDOWN)));
                l.shutdown();
            }
        }

        synchronized(connectList) {
            Collection values = connectList.values();
            Iterator itr = values.iterator();
            while (itr.hasNext()) {
                BrokerLink link = (BrokerLink) itr.next();
                link.shutdown();
            }
        }

        if (warningTask != null) {
            try {
                warningTask.cancel();
            } catch (Exception e) {}
            warningTask = null;
        }
    }

    public void closeLink(BrokerAddress remote, boolean force) {
        synchronized(brokerList) {
            BrokerLink l = (BrokerLink)brokerList.get(remote);
            if (l != null) l.closeConn(force);
            brokerList.notify();
        }
    }

    private void closeLink(String brokerID, UID storeSession) {
        if (!Globals.getHAEnabled()) return;
        BrokerAddress remote = null;
        BrokerLink l = null;
        synchronized(brokerList) {
            Iterator itr = brokerList.keySet().iterator();
            while (itr.hasNext()) {
                remote = (BrokerAddress)itr.next();
                l = (BrokerLink)brokerList.get(remote);
                if (remote.getBrokerID().equals(brokerID) && 
                    (storeSession == null || 
                     remote.getStoreSessionUID().equals(storeSession))) {
                    logger.log(logger.WARNING, br.getKString(
                           br.W_CLUSTER_CLOSE_DOWN_BROKER_LINK, remote));
                    l.closeConn(false);
                }
            }
        }
    }

    public boolean isReachable(BrokerAddress remote, int timeout) throws IOException {
        Class inetc = null;
        java.lang.reflect.Method m = null;
        try {
            inetc =  Class.forName("java.net.InetAddress");
            m = inetc.getMethod("isReachable", new Class[]{java.lang.Integer.TYPE});
            boolean b = ((Boolean)m.invoke(((BrokerAddressImpl)remote).getHost(),
                                    new Object[]{new Integer(timeout*1000)})).booleanValue();
            if (b) {
            logger.log(Logger.INFO, br.getKString(br.I_CLUSTER_REMOTE_IP_REACHABLE, 
                                                  remote, new Integer(timeout)));
            }else {
            logger.log(Logger.INFO, br.getKString(br.I_CLUSTER_REMOTE_IP_UNREACHABLE,
                                                  remote, new Integer(timeout)));
            }
            return b;

        } catch (ClassNotFoundException e) {
            logger.logStack(Logger.WARNING, e.getMessage(), e);
            return true;
        } catch (NoSuchMethodException e) {
            if (DEBUG) {
            logger.logStack(Logger.WARNING, e.getMessage(), e);
            }
            return true;
        } catch (Exception e) {
            logger.logStack(Logger.WARNING, e.getMessage(), e);
            return true;
        }
    }

    /**
     * Wait for the cluster connection initialization.
     */
    public void waitClusterInit() {
        BrokerLink link = null;
        Object[] values = connectList.values().toArray();
        for (int i = 0; i < values.length; i++) {
            link = (BrokerLink) values[i];
            link.waitLinkInit();
        }

        readyForBroadcast = true;
    }

    public void sendFlowControlUpdate(BrokerAddressImpl addr)
        throws IOException {
        if (flowControlState == Packet.STOP_FLOW)
            sendFlowControlUpdate(addr, flowControlState);
    }

    private void sendFlowControlUpdate(BrokerAddressImpl addr, int type)
        throws IOException {
        Packet p = new Packet();
        p.setPacketType(type);
        p.setPacketBody(null);
        p.setDestId(0);

        synchronized (brokerList) {
            if (addr != null) {
                BrokerLink link = (BrokerLink) brokerList.get(addr);
                link.sendPacket(p);
            }
            else {
                Collection values = brokerList.values();
                Iterator itr = values.iterator();

                while (itr.hasNext()) {
                    BrokerLink link = (BrokerLink) itr.next();
                    link.sendPacket(p);
                }
            }
        }
    }

    public void stopMessageFlow() throws IOException {
        synchronized (brokerList) {
            flowControlState = Packet.STOP_FLOW;
        }

        sendFlowControlUpdate(null, Packet.STOP_FLOW);
    }

    public void resumeMessageFlow() throws IOException {
        synchronized (brokerList) {
            flowControlState = Packet.RESUME_FLOW;
        }

        sendFlowControlUpdate(null, Packet.RESUME_FLOW);
    }

    private void sendPingGPacket() throws Exception {
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_PING);

        synchronized (brokerList) {
            Collection values = brokerList.values();
            Iterator itr = values.iterator();

            while (itr.hasNext()) {
                BrokerLink link = (BrokerLink) itr.next();
                if (!link.isIOActive()) {
                    try {
                    link.sendPacket(gp);
                    } catch (Exception e) {/* Ignore */} 
                }
                link.clearIOActiveFlag();
            }
        }
    }

    private void sendPingPacket() throws Exception {
        Packet p = new Packet();
        p.setPacketType(Packet.PING);
        p.setPacketBody(null);
        p.setDestId(0);

        synchronized (brokerList) {
            Collection values = brokerList.values();
            Iterator itr = values.iterator();

            while (itr.hasNext()) {
                BrokerLink link = (BrokerLink) itr.next();
                if (!link.isIOActive()) {
                    try {
                    link.sendPacket(p);
                    } catch (Exception e) {/* Ignore */}
                }
                link.clearIOActiveFlag();
            }
        }
    }

    private class PingTimerTask extends TimerTask 
    {
        public void run() {
            try {
                if (useGPackets)
                    sendPingGPacket();
                else
                    sendPingPacket();
            }
            catch (Exception e) { /* Ignore */ }
        }
    }

    public void unicast(BrokerAddress addr, GPacket gp)
        throws IOException {
        if (! useGPackets) {
            logger.log(logger.WARNING,
                "Protocol mismatch. GPacket unicast on old cluster");
            Thread.dumpStack();
        }

        unicast(addr, gp, false, false);
    }

    public void unicastAndClose(BrokerAddress addr, GPacket gp) throws IOException {
        unicast(addr, gp, false, true);
    }

    public void unicast(BrokerAddress addr, GPacket gp, boolean flowControl) throws IOException {
        unicast(addr, gp, flowControl, false);
    }

    private void unicast(BrokerAddress addr, GPacket gp, boolean flowControl, boolean close)
        throws IOException {
        if (! useGPackets) {
            logger.log(logger.WARNING,
                "Protocol mismatch. GPacket unicast on old cluster");
            Thread.dumpStack();
        }

        if (addr.equals(self)) {
            if (cb != null)
                cb.receiveUnicast(self, gp);
            return;
        }

        BrokerLink link = null;
        synchronized (brokerList) {
            link = (BrokerLink) brokerList.get(addr);
        }

        if (link == null) {
            throw new IOException(
            br.getString(BrokerResources.X_CLUSTER_UNICAST_UNREACHABLE, addr.toString()));
        }

        gp.setBit(gp.F_BIT, flowControl);
        link.sendPacket(gp, close);
    }

    public void broadcast(GPacket gp) throws IOException {
        if (! useGPackets) {
            logger.log(logger.WARNING,
                "Protocol mismatch. GPacket broadcast on old cluster");
            Thread.dumpStack();
        }

        if (! readyForBroadcast) {
            waitClusterInit();
        }

        gp.setBit(gp.B_BIT, true);

        synchronized (brokerList) {
            Collection values = brokerList.values();
            Iterator itr = values.iterator();

            while (itr.hasNext()) {
                BrokerLink link = (BrokerLink) itr.next();
                link.sendPacket(gp);
            }
        }
    }

    public void unicast(BrokerAddress addr, int destId, byte[] pkt)
        throws IOException {
        if (useGPackets) {
            logger.log(logger.WARNING,
                "Protocol mismatch. Old packet unicast on raptor cluster");
            Thread.dumpStack();
        }

        unicast(addr, destId, pkt, false);
    }

    public void unicast(BrokerAddress addr, int destId, byte[] pkt,
        boolean flowControl) throws IOException {
        if (useGPackets) {
            logger.log(logger.WARNING,
                "Protocol mismatch. Old packet unicast on raptor cluster");
            Thread.dumpStack();
        }

        if (addr.equals(self)) {
            if (cb != null)
                cb.receiveUnicast(self, destId, pkt);
            return;
        }

        BrokerLink link = null;
        synchronized (brokerList) {
            link = (BrokerLink) brokerList.get(addr);
        }

        if (link == null) {
            // exception is never displayed (does not need to be localized)
            throw new IOException(
                "Packet send failed. Unreachable BrokerAddress : " + addr);
        }

        Packet p = new Packet();
        p.setPacketType(Packet.UNICAST);
        p.setPacketBody(pkt);
        p.setDestId(destId);
        p.setFlag(Packet.USE_FLOW_CONTROL, flowControl);

        link.sendPacket(p);
    }

    public void broadcast(int destId, byte[] pkt)
        throws IOException {
        if (useGPackets) {
            logger.log(logger.WARNING,
                "Protocol mismatch. Old packet broadcast on raptor cluster");
            Thread.dumpStack();
        }

        if (! readyForBroadcast) {
            waitClusterInit();
        }

        Packet p = new Packet();
        p.setPacketType(Packet.BROADCAST);
        p.setPacketBody(pkt);
        p.setDestId(destId);

        synchronized (brokerList) {
            Collection values = brokerList.values();
            Iterator itr = values.iterator();

            while (itr.hasNext()) {
                BrokerLink link = (BrokerLink) itr.next();
                link.sendPacket(p);
            }
        }
    }

    public void reloadCluster() {
        try {
            clsmgr.reloadConfig();
        }
        catch (Exception e) { 
            logger.logStack(logger.WARNING, br.getKString(
                            br.W_CLUSTER_RELOAD_FAILED), e);
        }
    }


   /**********************************************************
    *    implements ClusterListener  *************************
    **********************************************************/

   /**
    * Called to notify ClusterListeners when the cluster service
    * configuration. Configuration changes include:
    * <UL><LI>cluster service port</LI>
    *     <LI>cluster service hostname</LI>
    *     <LI>cluster service transport</LI>
    * </UL><P>
    *
    * @param name the name of the changed property
    * @param value the new value of the changed property
    */
    public void clusterPropertyChanged(String name, String value) {
        if (name.equals(ClusterManager.PORT_PROPERTY)) {
            try {
            setListenPort(Integer.valueOf(value).intValue()); 
            } catch (IOException e) {
            logger.logStack(logger.ERROR, e.getMessage(), e);
            }
        } 
    }


   /**
    * Called when a new broker has been added.
    * @param broker the new broker added.
    */
    public void brokerAdded(ClusteredBroker broker, UID uid) {
        if (!broker.isConfigBroker()) {
            if (DEBUG) {
            logger.log(logger.INFO, "ClusterImpl:brokerAdded: Ignore dynamic broker "+broker);
            }
            return;
        }
        BrokerMQAddress key = (BrokerMQAddress)broker.getBrokerURL();
        if (!key.equals(self.getMQAddress()) && !connectList.containsKey(key)) {
            if (connectList.size() > connLimit) { //XXX limit check in clmgr  
                logger.log(Logger.ERROR, br.E_MBUS_CONN_LIMIT, Integer.toString(connLimit + 1));
                return;
            }

            boolean newLink = false;
            BrokerLink link = searchBrokerList(key); //XXX 1
            if (link == null) {
                BrokerAddressImpl b = null;
                try {
                    b = new BrokerAddressImpl(key, null, Globals.getHAEnabled(), broker.getBrokerName());
                    link = new BrokerLink(self, b, this);
                    newLink = true;
                } catch (Exception e) {
                    logger.logStack(logger.ERROR, br.getKString(
                           br.W_CLUSTER_AUTOCONNECT_ADD_FAILED, broker), e);
                    return;
                }
            }
            link.setAutoConnect(true); //XXX 2
            connectList.put(key, link);
            if (DEBUG) {
                logger.log(Logger.INFO, "ClusterImpl: Added link to connectList - "+link);
            }
            if (newLink) link.start();
            return;
        }
        if (DEBUG) {
        logger.log(logger.INFO, "Broker link for "+key+" ("+broker+") already exist");
        }
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable(); 
        ht.put("self", self.toString());
        BrokerAddressImpl cs = configServer;
        if (cs != null) {
            ht.put("configServer[masterbroker]", cs.toString());
            ht.put("configServerResolved", Boolean.valueOf(configServerResolved));
        }
        ArrayList clist = null;
        synchronized(connectList) {
            clist = new ArrayList(connectList.keySet());
        }
        ht.put("connectListCount", String.valueOf(clist.size()));
        Iterator itr = clist.iterator();
        while (itr.hasNext()) {
            BrokerMQAddress key  = (BrokerMQAddress)itr.next();
            BrokerLink link = (BrokerLink)connectList.get(key);
            if (link != null) {
                ht.put(key.toString(), link.toString());  
            }
        }
        return ht;
    }

   /**
    * Called when a broker has been removed.
    * @param broker the broker removed.
    */
    public void brokerRemoved(ClusteredBroker broker, UID uid) {
        if (!broker.isConfigBroker()) {
            if (DEBUG) {
            logger.log(logger.INFO, "ClusterImpl:brokerRemoved: Ignore dynamic broker "+broker);
            }
            return;
        }

        BrokerMQAddress key = (BrokerMQAddress)broker.getBrokerURL();
        BrokerLink link = (BrokerLink)connectList.remove(key);
        if (link != null) {
            link.shutdown();
            if (DEBUG) {
                logger.log(Logger.INFO, "ClusterImpl: Removed link from connectList - " + link);
            }
            return;
        }
        if (DEBUG) {
        logger.log(logger.INFO, "Broker link for "+key+" ("+broker+") not exist");
        }
    }

   /**
    * Called when the broker who is the master broker changes
    * (because of a reload properties).
    * @param oldMaster the previous master broker.
    * @param newMaster the new master broker.
    */
    public void masterBrokerChanged(ClusteredBroker oldMaster,
                    ClusteredBroker newMaster) {
    //do nothing
    }

   /**
    * Called when the status of a broker has changed. The
    * status may not be accurate if a previous listener updated
    * the status for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param oldStatus the previous status.
    * @param newStatus the new status.
    * @param userData data associated with the state change
    */
    public void brokerStatusChanged(String brokerid,
                  int oldStatus, int newStatus, UID brokerSession,
                  Object userData) {
        ClusteredBroker cb = clsmgr.getBroker(brokerid);
        if (!(cb instanceof HAClusteredBroker)) return;
        if (cb.isLocalBroker()) return;

        if (BrokerStatus.getBrokerIsDown(newStatus)) {
            closeLink(cb.getBrokerName(), (UID)userData);
        }

    }

   /**
    * Called when the state of a broker has changed. The
    * state may not be accurate if a previous listener updated
    * the state for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param oldState the previous state.
    * @param newState the new state.
    */
    public void brokerStateChanged(String brokerid,
                  BrokerState oldState, BrokerState newState) {
        /*
        if (newState == BrokerState.FAILOVER_PENDING) { //storeSession pass in ?
            cb.preTakeover(brokerid, 
               ((HAClusteredBroker)clsmgr.getBroker(brokerid)).getStoreSessionUID(),
               ((BrokerMQAddress)clsmgr.getBroker(brokerid).getBrokerURL()).getHost().getHostAddress(),
               ((HAClusteredBroker)clsmgr.getBroker(brokerid)).getBrokerSessionUID());
            return;
        }
        if (newState == BrokerState.FAILOVER_COMPLETE) { //storeSession pass in ?
            cb.postTakeover(brokerid, 
               ((HAClusteredBroker)clsmgr.getBroker(brokerid)).getStoreSessionUID(), false);
            return;
        }
        if (newState == BrokerState.FAILOVER_FAILED) { //storeSession pass in ?
            cb.postTakeover(brokerid, 
               ((HAClusteredBroker)clsmgr.getBroker(brokerid)).getStoreSessionUID(), true);
            return;
        }
        */
    }

   /**
    * Called when the version of a broker has changed. The
    * state may not be accurate if a previous listener updated
    * the version for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param oldVersion the previous version.
    * @param newVersion the new version.
    */
    public void brokerVersionChanged(String brokerid,
                  int oldVersion, int newVersion) {
    }

   /**
    * Called when the url address of a broker has changed. The
    * address may not be accurate if a previous listener updated
    * the address for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param newAddress the previous address.
    * @param oldAddress the new address.
    */
    public void brokerURLChanged(String brokerid,
                  MQAddress oldAddress, MQAddress newAddress) {
    }

}

class ClusterServiceListener extends Thread {
    private static final BrokerResources br = Globals.getBrokerResources();
    private static final Logger logger = Globals.getLogger();
    ClusterImpl callback = null;
    boolean done = false;
    ServerSocket ss = null;
    private boolean nodelay;
    private boolean isSSL = false;

    private static ServerSocketFactory ssf = MQServerSocketFactory.getDefault();

    public ClusterServiceListener(ClusterImpl callback) throws IOException {
        this.callback = callback;
        setName("ClusterServiceListener");
        setDaemon(true);
        this.nodelay = callback.getTCPNodelay();

        if (callback.getTransport().equalsIgnoreCase("ssl")) { 
            nodelay = callback.getSSLNodelay();
            isSSL = true;
            initSSLListener();
        } else {
            initTCPListener();
        }

        start();
    }

    private void initSSLListener() throws IOException {
        if (ClusterImpl.DEBUG) {
        logger.log(logger.INFO, "ClusterImpl.initSSLListener[nodelay="+nodelay+
            ", inbufsz="+callback.getSSLInputBufferSize()+", outbufsz="+callback.getSSLOutputBufferSize()+"]");
        }

        ServerSocketFactory sslfactory = null;
        try {
           /* This can be called as a result of cluster property update.
            * Hence does not do System.exit here.
            * Cluster.start() Exception will cause System.exit, 
            */
           LicenseBase license = Globals.getCurrentLicense(null);
           if (!license.getBooleanProperty(license.PROP_ENABLE_SSL, false)) {
              logger.log(Logger.ERROR, br.E_FATAL_FEATURE_UNAVAILABLE, 
                                       br.getString(br.M_SSL_BROKER_CLUSTERS));
              throw new BrokerException(br.getKString(br.E_FATAL_FEATURE_UNAVAILABLE, 
                                               br.getString(br.M_SSL_BROKER_CLUSTERS)));

           }
 
            Class TLSProtocolClass = Class.forName(
                "com.sun.messaging.jmq.jmsserver.net.tls.TLSProtocol");

            if (ClusterImpl.DEBUG) {
                logger.log(logger.DEBUG, "ClusterImpl.initSSLListener. " +
                    "Initializing SSLServerSocketFactory");
            }

            /* SSLServerSocketFactory ssf = (SSLServerSocketFactory)
                TLSProtocol.getServerSocketFactory(); */
            java.lang.reflect.Method m = TLSProtocolClass.getMethod("getServerSocketFactory", null);
            sslfactory = (ServerSocketFactory)m.invoke(null, null);
        }
        catch (Exception e) {
            Throwable t = e;
            if (e instanceof java.lang.reflect.InvocationTargetException) {
               t = e.getCause(); 
               if (t == null) t = e;
               if (ClusterImpl.DEBUG && t != e) {
                 logger.logStack(Logger.ERROR, e.getMessage(), e);
               }
            }
            logger.logStack(Logger.ERROR, t.getMessage(), t);
            throw new IOException(t.getMessage());
        }

        InetAddress listenHost = callback.getListenHost();
        int listenPort = callback.getListenPort();
        HashMap h = null;

        if (ClusterImpl.DEBUG) {
            logger.log(logger.DEBUG, "ClusterImpl.initSSLListener. " +
                "Initializing ServerSocket");
        }

        if (listenHost == null) {
            ss = sslfactory.createServerSocket(listenPort);
        }
        else {
            ss = sslfactory.createServerSocket(listenPort, 50, listenHost);
            // Why backlog = 50? According the JDK 1.4 javadoc,
            // that's the default value for ServerSocket().
            // Also even if a connection gets refused, that broker
            // will try again after sometime anyway...

            h = new HashMap();
            h.put("hostname", listenHost.getHostName());
            h.put("hostaddr", listenHost.getHostAddress());
        }

        Globals.getPortMapper().addService(ClusterImpl.SERVICE_NAME,
            "ssl", ClusterImpl.SERVICE_TYPE, ss.getLocalPort(), h);

        if (ClusterImpl.DEBUG && ss != null) {
            logger.log(logger.DEBUG, "ClusterImpl.initSSLListener: " +
	        ss + " " + MQServerSocketFactory.serverSocketToString(ss));
        }
    }

    private void initTCPListener() throws IOException {
        if (ClusterImpl.DEBUG) {
        logger.log(logger.INFO, "ClusterImpl.initTCPListener[TcpNoDelay="+nodelay+
            ", inbufsz="+callback.getTCPInputBufferSize()+", outbufsz="+callback.getTCPOutputBufferSize()+"]");
        }

        InetAddress listenHost = callback.getListenHost();
        int listenPort = callback.getListenPort();
        HashMap h = null;

        if (listenHost == null)
            ss = ssf.createServerSocket(listenPort);
        else {
            ss = ssf.createServerSocket(listenPort, 50, listenHost);
            // Why backlog = 50? According the JDK 1.4 javadoc,
            // that's the default value for ServerSocket().
            // Also even if a connection gets refused, that broker
            // will try again after sometime anyway...

            h = new HashMap();
            h.put("hostname", listenHost.getHostName());
            h.put("hostaddr", listenHost.getHostAddress());
        }

        Globals.getPortMapper().addService(ClusterImpl.SERVICE_NAME,
            "tcp", ClusterImpl.SERVICE_TYPE, ss.getLocalPort(), h);

        if (ClusterImpl.DEBUG && ss != null) {
            logger.log(logger.DEBUG, "ClusterImpl.initTCPListener: " +
	        ss + " " + MQServerSocketFactory.serverSocketToString(ss));
        }
    }

    public String getServerSocketString() {
        ServerSocket ssocket = ss;
        if (ssocket != null) {
            return ssocket.getInetAddress()+":"+ ssocket.getLocalPort();
        }
        return null;
    }

    public synchronized void shutdown() {
        done = true;
        try {
            ss.close();
        }
        catch (Exception e) { /* Ignore */ 
            /* Ignore. This happens when ServerSocket is closed.. */
        }
    }

    public void run() {
        while (true) {
            synchronized (this) {
                if (done)
                    break;
            }
            try {
                Socket sock = ss.accept();
                try {
                    sock.setTcpNoDelay(nodelay);
                } catch (SocketException e) {
                    logger.log(Logger.WARNING, getClass().getSimpleName()+".run(): ["+
                               sock.toString()+"]setTcpNoDelay("+nodelay+"): "+ e.toString(), e);
                }
                callback.acceptConnection(sock, isSSL);
            }
            catch (Exception e) {
                /* Ignore. This happens when ServerSocket is closed.. */
            }
        }
    }
}

class WarningTask extends TimerTask {
    private static final Logger logger = Globals.getLogger();
    private static final BrokerResources br = Globals.getBrokerResources();
    private ClusterImpl parent = null;
    
    public WarningTask(ClusterImpl parent) {
        this.parent = parent;
    }

    public void run() {
        if (parent.isConfigServerResolved()) {
            cancel();
        } else {
            if (Globals.nowaitForMasterBroker()) {
            logger.log(Logger.WARNING, br.W_MBUS_STILL_TRYING_NOWAIT, parent.getConfiguredConfigServer());
            } else {
            logger.log(Logger.WARNING, br.W_MBUS_STILL_TRYING, parent.getConfiguredConfigServer());
            }
        }
    }
}
/*
 * EOF
 */
