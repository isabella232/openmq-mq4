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
 * @(#)HAMonitorService.java	1.77 07/18/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import java.net.*;

import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.PortMapperTable;
import com.sun.messaging.jmq.io.PortMapperEntry;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.StoreBeingTakenOverException;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.core.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.cluster.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.cluster.ha.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.data.handlers.TransactionHandler;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.AutoRollbackType;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.RollbackReason;
import com.sun.messaging.jmq.util.timer.MQTimer;
import com.sun.messaging.jmq.jmsserver.persist.TakeoverStoreInfo;
import com.sun.messaging.jmq.jmsserver.persist.TakeoverLockException;
import com.sun.messaging.jmq.jmsserver.persist.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.DBManager;


/**
 * This class handles general setup of an HA broker, monitoring the state of
 * an HA broker (for split brain) and HA Takeover.
 * <P>
 * The logic to determine when a failover should occur is not handled by
 * this class
 */
public class HAMonitorService implements ClusterListener
{
   private static boolean DEBUG_HA =
                  Globals.getConfig().getBooleanProperty(
                          Globals.IMQ + ".ha.debug");

   private static boolean DEBUG = DEBUG_HA;

   private Logger logger = Globals.getLogger();

   private static final String MONITOR_THRESHOLD_PROP = Globals.IMQ + ".cluster.monitor.threshold";
   private static final String MONITOR_INTERVAL_PROP = Globals.IMQ + ".cluster.monitor.interval";

   /**
    * Value to use for the monitoring threshold.
    */
   private int MAX_MONITOR_DEFAULT = 3;
   private int MAX_MONITOR = Globals.getConfig().getIntProperty(
       MONITOR_THRESHOLD_PROP, MAX_MONITOR_DEFAULT);

   /**
    * Value to use for the monitoring timeout.
    */
   public static final int MONITOR_TIMEOUT_DEFAULT = 30;
   private int MONITOR_TIMEOUT = Globals.getConfig().getIntProperty(
        MONITOR_INTERVAL_PROP, MONITOR_TIMEOUT_DEFAULT)* 1000;

   /**
    * Value to use for the heartbeat threshold.
    */
   private int MAX_HEARTBEAT = Globals.getConfig().getIntProperty(
        Globals.IMQ + ".cluster.heartbeat.threshold", 30);

   /**
    * Value to use for the reaper timeout.
    */
    private int reaperTimeout = Globals.getConfig().getIntProperty(
        Globals.IMQ + ".cluster.reaptime", 300)* 1000;

    /**
     * Configuration information
     */
    ClusterManager clusterconfig = null;

    /**
     * ID associated with the local broker
     */
    String mybrokerid = null;

    /**
     * Monitor task which periodically updated timestamps
     */
    private HAMonitorTask haMonitor = null;

    /**
     * Indoubt brokers which are being monitored
     */
    Map indoubtBrokers = Collections.synchronizedMap(new LinkedHashMap());

    volatile int heartbeatMissedCnt = 0;

    /**
     * private class to hold information associated with an
     * indoubt broker
     */
    class indoubtData
    {
        String brokerid;
        long lastts = 0;
        int monitorCnt = 0;
        UID brokerSession = null;
    }

    class downBroker
    {
        HAClusteredBroker cb = null;
        long lastts = 0;
        UID brokerSession = null;
        UID storeSession = null;
    }


    /**
     * timer task which is called periodically to monitor
     * any indoubt brokers and update heartbeat timestamps
     */
    class HAMonitorTask implements Runnable {

        boolean valid = true;

        public HAMonitorTask() {
        }

        public void cancel() {
            valid = false;
        }

        public void run() {

            if (!valid) return;

            monitor(); 
        }
    }    

    /**
     * use instead of timer thread so that we have control of
     * thread priority
     */
    class HATimerThread implements Runnable
    {

        long nexttime = 0;
        long repeatItr = 0;
        Thread thr = null;
        Runnable child = null;

        public HATimerThread(String name, Runnable runner, int initTO, int repeatItr) {
            nexttime = initTO + System.currentTimeMillis();
            this.repeatItr = repeatItr;
            this.child = runner;
            thr = new Thread(this, name);
            thr.setPriority(Thread.MAX_PRIORITY);
            thr.start();
        }


        public void run() {
            while (true) {
                long time = System.currentTimeMillis();
                synchronized(this) {
                    if (time < nexttime) {
                        try {
                            this.wait(nexttime  - time);
                        } catch (InterruptedException ex) {}
                        continue;
                     } else {
                         child.run();
                     }
                 }
                 if (repeatItr == 0) break;
                 time = System.currentTimeMillis();
                 long tmpnext = nexttime + repeatItr;
                 if (time >= tmpnext) { 
                     nexttime = time;
                 } else {
                     nexttime = tmpnext;
                 }
            }      
                       
        }
    }


    /**
     * timer task which is to reap old temp destinations
     * and transactions
     */
    class TakeoverReaper extends TimerTask {

        List txns = null;
        String id = null;
        long time = 0;
        public TakeoverReaper(String broker, List txns)
        {
            this.txns = txns;
            this.id = broker;

            logger.log(logger.DEBUG,"monitoring " + txns.size() +
                 " transactions");
        }

        public void run() {
            boolean done = processTxns();
            if (done)
                cancel();
        }

        public boolean processTxns() {
            try {
                // see if txn still exists
                
                // if it does and there are no msgs or acks, clean it up
                Iterator itr = txns.iterator();
                while (itr.hasNext()) {
                    TransactionUID tid = (TransactionUID) itr.next();

                    // ok, make sure it really exists
                    TransactionState state = null;
                    try {
                        state = Globals.getStore().getTransactionState(tid);
                        if (state != null && 
                            state.getState() == TransactionState.COMMITTED) {
                            Globals.getTransactionList().reapTakeoverCommittedTransaction(tid);
                            itr.remove();
                            continue;
                        }
                    } catch (BrokerException ex) { // nope, gone
                        Globals.getTransactionList().removeTransactionAck(tid);
                        Globals.getTransactionList().removeTransactionID(tid);
                        itr.remove();
                        continue;
                    }

                    int realstate = state.getState();
                    int msgs = 0;
                    int acks = 0;
                    try {
                        int[] retval = Globals.getStore().getTransactionUsageInfo(tid);
                        msgs = retval[0];
                        acks = retval[1];
                    } catch (Exception ex) {
                    }

                    // OK .. if not in one of the "valid" states
                    //  immediately rollback
                    // else  count the # of acks, messages
                    if ((realstate != TransactionState.ROLLEDBACK &&
                         realstate != TransactionState.PREPARED &&
                         realstate != TransactionState.COMMITTED) ||
                         msgs == 0 && acks == 0) {
                        logger.log((DEBUG ? Logger.INFO:Logger.DEBUG), 
                                    "Removing finished transaction " + tid);
                        TransactionHandler thandler = (TransactionHandler)
                            Globals.getPacketRouter(0).getHandler(PacketType.ROLLBACK_TRANSACTION);
                        thandler.doRollback(tid, null, null, state, null, null,
                                            RollbackReason.TAKEOVER_CLEANUP);
                        itr.remove();
                    } else if (realstate == TransactionState.ROLLEDBACK) {
                    }
                }
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING,
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "removing reaped destinations", ex);
            }

            if (txns.size() == 0 ) {
               return true;
            }
            return false;
        }
    }    

	FaultInjection fi = null;

    /**
     * Start the monitoring service.
     * @param clusterid the id of this cluster
     * @param brokerURL the address to use for the broker (which may be
     *            different than the one currently associated with
     *            this broker in the persistent store.
     *
     * @throws IllegalStateException if the clusterid is invalid
     * @throws IllegalAccessException if another broker is using the 
     *              same brokerid
     * @throws BrokerException if there are issues accessing the store.
     */

    public HAMonitorService(String clusterid, MQAddress brokerURL,
                            boolean resetTakeoverThenExit) throws Exception
    {
        DEBUG = (DEBUG || (logger.getLevel() <= Logger.DEBUG));
        fi = FaultInjection.getInjection();
        clusterconfig = Globals.getClusterManager();
        clusterconfig.addEventListener(this);
        mybrokerid = clusterconfig.getLocalBroker().getBrokerName();

        logger.log(logger.INFO, BrokerResources.I_MONITOR_INFO,
               mybrokerid, brokerURL);

        logger.log(logger.INFO, MONITOR_INTERVAL_PROP+"="+MONITOR_TIMEOUT/1000);
        logger.log(logger.INFO, MONITOR_THRESHOLD_PROP+"="+MAX_MONITOR);

        // validate id is valid
        if (!clusterid.equals(clusterconfig.getClusterId())) {
            logger.log(Logger.ERROR,
                  BrokerResources.E_ERROR_MONITOR_BAD_CID,
                      clusterid, clusterconfig.getClusterId());
            throw new IllegalStateException("Bad Cluster ID " + clusterid);
        }

        // retrieve broker state info
        HAClusteredBroker cb = (HAClusteredBroker)clusterconfig.getLocalBroker();

        // make sure we are valid

        MQAddress mqa = cb.getBrokerURL();
        if (!mqa.equals(brokerURL)) {

            logger.log(Logger.INFO,
                 BrokerResources.I_UPD_STORED_PORT,
                 mybrokerid, brokerURL);

                // we need to vaidate that there isnt a misconfiguration
                // somewhere
            try {
                String version = String.valueOf(PortMapperTable.PORTMAPPER_VERSION)
                         + "\n";
                PortMapperTable pt = new PortMapperTable();

                Socket s = new Socket(mqa.getHostName(), mqa.getPort());
                InputStream is = s.getInputStream();
                OutputStream os = s.getOutputStream();

                try {
                        os.write(version.getBytes());
                        os.flush();
                } catch (IOException e) {
                        // This can sometimes fail if the server already wrote
                        // the port table and closed the connection
                        // Ignore...
                }
                pt.read(is);

                is.close();
                os.close();
                s.close();
                PortMapperEntry pme = pt.get(PortMapper.SERVICE_NAME);

                String remotebrokerid = pme.getProperty("brokerid");

                if (mybrokerid.equals(remotebrokerid)) {
                        logger.log(Logger.ERROR, BrokerResources.E_BID_CONFLICT,
                           mybrokerid);
                        Broker.getBroker().exit(1,
                           Globals.getBrokerResources().getKString(
                               BrokerResources.E_BID_CONFLICT,
                               mybrokerid),
                            BrokerEvent.Type.FATAL_ERROR, null, false, false, true);
                }
            } catch (IOException ex ) {
                    logger.log(Logger.DEBUG,"Unable to reach old remote broker "
                           + "associated with " + mybrokerid);
            }

            // OK, if we made it here, we know that we dont have a previous
            // broker running on the same system with a different brokerid

            // XXX-HA replace with real core
            cb.setBrokerURL(brokerURL);
        }

        cb.updateHeartbeat(true);

        // make sure we werent taking over other stores
        Iterator itr = clusterconfig.getConfigBrokers();
        while (itr.hasNext()) {
            HAClusteredBroker nextb = (HAClusteredBroker)itr.next();
            String bkr = nextb.getTakeoverBroker();
            if (bkr != null && bkr.equals(mybrokerid) &&
                (nextb.getState() == BrokerState.FAILOVER_PENDING ||
                 nextb.getState() == BrokerState.FAILOVER_STARTED)) {
                logger.log(logger.INFO,
                    BrokerResources.I_TAKEOVER_RESET, nextb.getBrokerName());

                nextb.setState(BrokerState.FAILOVER_COMPLETE);
            }
        }
        if (resetTakeoverThenExit) {
            logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                                    BrokerResources.I_RESET_TAKEOVER_EXIT));
            return;
        }

        // OK now check the state
        BrokerState state = cb.getState();
        String tkovrbkr = cb.getTakeoverBroker();

        if (state == BrokerState.FAILOVER_STARTED ||
            state == BrokerState.FAILOVER_PENDING) {
            logger.log(Logger.WARNING,
                       BrokerResources.W_TAKEOVER_IN_PROGRESS, mybrokerid, tkovrbkr);

            long maxwaitsec = Globals.getConfig().getLongProperty(Globals.IMQ +
                               ".cluster.ha.takeoverWaitTimeout", 300);
            long maxwait = maxwaitsec*1000L;
            if (maxwait == 0) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                                          BrokerResources.W_WAIT_FOR_BEEN_TAKENOVER_TIMEOUT,
                                          String.valueOf(maxwaitsec)));
            }
            long waitinterval = 10*1000L; 
            long currtime = System.currentTimeMillis();
            long precurrtime = currtime;
            long totalwaited = 0L;
            long waittime = (maxwait < 0 || maxwait > waitinterval ? waitinterval : maxwait);

            do  {
                logger.log(Logger.INFO, BrokerResources.I_STARTUP_PAUSE);
                try {
                    Thread.currentThread().sleep(waittime);
                    precurrtime = currtime;
                    currtime = System.currentTimeMillis();
                    totalwaited += ((currtime - precurrtime) > waittime ?
                                    (currtime - precurrtime):waittime);
                } catch (InterruptedException ex) {
                    throw new BrokerException(
                    "Waiting for taking over of this broker to complete is interrupted: "+ex.getMessage());
                }

                cb.updateHeartbeat(true);
                state = cb.getState();

                waittime = (maxwait < 0 ? waitinterval :(maxwait - totalwaited));
                if (waittime > waitinterval) waittime = waitinterval;

            } while ((state == BrokerState.FAILOVER_STARTED ||
                      state == BrokerState.FAILOVER_PENDING) && waittime > 0); 

            if (waittime <= 0 && 
                (state == BrokerState.FAILOVER_STARTED ||
                 state == BrokerState.FAILOVER_PENDING)) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                                          BrokerResources.W_WAIT_FOR_BEEN_TAKENOVER_TIMEOUT,
                                          String.valueOf(maxwaitsec)));
            }
        }

        try {
            cb.resetTakeoverBrokerReadyOperating();
        } catch (Exception e) {
            state = cb.getState();
            if (state == BrokerState.FAILOVER_STARTED ||
                state == BrokerState.FAILOVER_PENDING) {
                String msg = Globals.getBrokerResources().getKString(
                                 BrokerResources.E_CLUSTER_TAKINGOVER_RESTART);
                logger.logStack(logger.ERROR, msg, e);
                throw new StoreBeingTakenOverException(msg, Status.CONFLICT);
            }
            throw e;
        }

        Globals.setStoreSession(cb.getStoreSessionUID());

        logger.log(Logger.INFO, BrokerResources.I_HA_INFO_STRING,
                   String.valueOf(cb.getStoreSessionUID().longValue()),
                   String.valueOf(cb.getBrokerSessionUID().longValue()));


        // Make sure total monitor time is greater than 90 secs
        // to workaround HADB bug 6499325
        if ((MONITOR_TIMEOUT * MAX_MONITOR) < 90000 &&
            DBManager.getDBManager().isHADB()) {
            MAX_MONITOR = MAX_MONITOR_DEFAULT;
            MONITOR_TIMEOUT = MONITOR_TIMEOUT_DEFAULT * 1000;

            logger.log(Logger.WARNING,
                "The HA Monitor Service takes over a failed broker when the" +
                " total monitor time (the product of imq.cluster.monitor.interval" +
                " and imq.cluster.monitor.threshhold) exceeds a set value." +
                " Due to HADB limitations, that value must be at least 90" +
                " seconds. Otherwise, the broker might not reliably take over" +
                " messages from a failed broker. The total current interval" +
                " is lower than 90 seconds; therefore, we are resetting the" +
                " default value of imq.cluster.monitor.interval to 30, and" +
                " the imq.cluster.monitor.threshhold value to 3.");
        }

        haMonitor = new HAMonitorTask();
        try {
            new HATimerThread("HAMonitor", haMonitor, MONITOR_TIMEOUT, MONITOR_TIMEOUT);
        } catch (Exception ex) {
            logger.log(Logger.WARNING,
                 BrokerResources.E_INTERNAL_BROKER_ERROR,
                 "Unable to start monitor Timeout", ex);
        }

    }


   /**
    * @return the monitor interval in seconds
    */
    public int getMonitorInterval() {
        return MONITOR_TIMEOUT/1000;
    }
    /**
     * Retrieves the current Session for this broker.
     * @return the session uid
     */
    public UID getStoreSession() {
        return ((HAClusteredBroker)clusterconfig.getLocalBroker())
                   .getStoreSessionUID();
    }

    /**
     * a string representation of the object
     */
    public String toString() {
        return "HAMonitorService[" + clusterconfig.getLocalBroker() +"]";
    }

    /**
     * Called when the timer calls the HAMonitorTask, this method is
     * used to update the local broker's heartbeat and check the
     * state of any indbout brokers.
     */
    public void monitor() {
        HAClusteredBroker cb = (HAClusteredBroker)
                 clusterconfig.getLocalBroker();

        if (DEBUG) {
        logger.log(Logger.INFO, "HAMonitor is updating heartbeat timestamp of ["+ mybrokerid + "]"+MONITOR_TIMEOUT);
        }
 
        try {
            if (fi.FAULT_INJECTION) {
                fi.checkFaultAndThrowBrokerException(FaultInjection.FAULT_HB_SHAREDB, null);
            }
            cb.updateHeartbeat();
            heartbeatMissedCnt = 0; // Reset count back to 0
        } catch (BrokerException ex) {
            heartbeatMissedCnt += 1; // Increment threshold count by 1
            if (heartbeatMissedCnt < MAX_HEARTBEAT) {
                logger.logStack(logger.WARNING, Globals.getBrokerResources().getKString(
                       BrokerResources.W_UPDATE_HEARTBEAT_TS_EXCEPTION, ex.getMessage()), ex);
            } else {
                // Trigger failover when failed count exceed threshold
                String errorMsg = Globals.getBrokerResources().getKString(
                    BrokerResources.E_UPDATE_HEARTBEAT_FAILED,
                    MAX_HEARTBEAT);
                logger.logStack(logger.ERROR, errorMsg, ex);
                Broker.getBroker().exit(
                    BrokerStateHandler.getRestartCode(), errorMsg,
                    BrokerEvent.Type.RESTART, ex, true, false, false);
            }
        }

        try {
            BrokerState state = cb.getState();
            if (state == BrokerState.QUIESCE_STARTED ||
                state == BrokerState.QUIESCE_COMPLETED ||
                state == BrokerState.FAILOVER_PENDING ||
                state == BrokerState.FAILOVER_FAILED ||
                state == BrokerState.SHUTDOWN_STARTED ||
                state == BrokerState.SHUTDOWN_FAILOVER ||
                state == BrokerState.SHUTDOWN_COMPLETE) {
                // shutting down, nothing to do
                return;
            }
        } catch (BrokerException ex) {
            logger.logStack(logger.WARNING,
                BrokerResources.X_INTERNAL_EXCEPTION, ex.getMessage(), ex);
        }

        if (indoubtBrokers.size() > 0) {
            logger.log(Logger.INFO,
               BrokerResources.I_INDOUBT_COUNT,
                 String.valueOf(indoubtBrokers.size()));
        }

        ArrayList takeover = null;

        // make a copy of the indoubt Brokers (so we dont have to worry about
        // changes)

        Set s = new HashSet(indoubtBrokers.keySet());
        synchronized(takeoverRunnableLock) {
            monitorBusy = true;
        }

        try {

        Iterator itr = s.iterator();
        while (itr.hasNext()) {
            Object key = itr.next();
            indoubtData wbd = (indoubtData)indoubtBrokers.get(key);
            try {
                HAClusteredBroker idbcb = (HAClusteredBroker)
                          clusterconfig.getBroker(wbd.brokerid);
                BrokerState idbstate = idbcb.getState();
                long ts = idbcb.getHeartbeat();
                if (idbstate == BrokerState.SHUTDOWN_COMPLETE)
                {
                    // dont takeover
                    logger.log(logger.INFO,
                         BrokerResources.I_NO_TAKEOVER_SHUTDOWN,
                         wbd.brokerid);
                    idbcb.setBrokerIsUp(false, wbd.brokerSession, null);
                    indoubtBrokers.remove(key);
                    itr.remove();
                } else if (idbstate == BrokerState.FAILOVER_STARTED
                   || idbstate == BrokerState.FAILOVER_COMPLETE)
                {
                    // dont takeover
                    logger.log(logger.INFO,
                        BrokerResources.I_OTHER_TAKEOVER,
                        wbd.brokerid+"["+idbstate+"]");
                    idbcb.setBrokerIsUp(false, wbd.brokerSession, null);
                    indoubtBrokers.remove(key);
                    itr.remove();
                } else if (ts > wbd.lastts &&
                           idbstate != BrokerState.FAILOVER_PENDING) {
                    logger.log(logger.INFO,
                         BrokerResources.I_BROKER_OK,
                            idbcb.getBrokerName());
                    // OK .. we are NOT indoubt or down
                    idbcb.setBrokerInDoubt(false, wbd.brokerSession);
                    indoubtBrokers.remove(key);
                    itr.remove();
                } else {
                   wbd.monitorCnt ++;
                   if (wbd.monitorCnt >= MAX_MONITOR
                         || idbstate == BrokerState.SHUTDOWN_FAILOVER) {
                       logger.log(logger.INFO,
                            BrokerResources.I_BROKER_NOT_OK,
                            idbcb.getBrokerName());
                       if (takeoverRunnable != null) {
                           logger.log(logger.INFO, BrokerResources.I_NO_TAKEOVER_BUSY,
                           idbcb.getBrokerName());
                           continue;
                       }
                        // we are dead -> takeover
                       if (takeover == null)
                           takeover = new ArrayList();
                       downBroker dbroker = new downBroker();
                       dbroker.cb = idbcb;
                       dbroker.lastts = wbd.lastts;
                       dbroker.brokerSession = wbd.brokerSession;
                       dbroker.storeSession = idbcb.getStoreSessionUID();
                       takeover.add(dbroker);
                       idbcb.setBrokerIsUp(false, wbd.brokerSession, null);
                       itr.remove();
                       indoubtBrokers.remove(key);
                   } else {
                       logger.log(logger.INFO,
                           BrokerResources.I_BROKER_INDOUBT_CONTINUE_MONITOR,
                           idbcb.getBrokerName());
                   }
                }
            } catch (Exception ex) {
                logger.logStack(Logger.INFO,
                   BrokerResources.E_INTERNAL_BROKER_ERROR,
                       "Unable to monitor broker " 
                        + wbd.brokerid, ex);
            }
        }

        if (takeover == null) return;
        if (takeoverRunnable != null) {
            logger.log(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
               Globals.getBrokerResources().getString(
               BrokerResources.I_NO_TAKEOVER_BUSY, takeover));
            return;
        }
        takeoverRunnable = new TakeoverThread(takeover, false, false);

        } finally {
            synchronized(takeoverRunnableLock) {
                monitorBusy = false;
                takeoverRunnableLock.notify();
            }
        }
        Thread thr = new Thread(takeoverRunnable, "Takeover");
        thr.start();  
    }

    /**
     * called when a broker goes down to process
     * any in-process transactions
     */
    public void brokerDown(BrokerAddress addr)
        throws BrokerException
    {
	/*
        logger.log(Logger.DEBUG,"Processing brokerDown for " + addr);
        List l = Globals.getTransactionList().getRemoteBrokers(addr);

        if (l == null) {
            logger.log(Logger.DEBUG,"XXX - No txns to process " + addr);
            return;
        }

        // ok, when we crash (brokers are down) everything needs
        // to be cleaned up
        //
        // those transactions are no longer viable

        // what we want to do here is:
        //    - see if any transactions need to finish rolling back
        //    - see if any transactions need to finish committing
        //

        // what we are handling is messages CONSUMED by the remote
        // broker which are handled here

        Iterator itr = l.iterator();
        while (itr.hasNext()) {
            TransactionUID tuid = (TransactionUID) itr.next();
            logger.log(Logger.DEBUG,"Processing tuid " + tuid + " from " + addr);

            // ok we need to retrieve the PERSISTENT state

            TransactionState ts = Globals.getStore().getTransactionState(tuid);
            
            // ok, look @ each transaction
            switch (ts.getState()) {
                case TransactionState.CREATED:
                case TransactionState.STARTED:
                case TransactionState.FAILED:
                case TransactionState.INCOMPLETE:
                case TransactionState.COMPLETE:
                case TransactionState.ROLLEDBACK:
                    // ok in all these cases, we rollback
                    // get CONSUMED messages
                    Globals.getTransactionList().rollbackRemoteTxn(tuid);
                    break;
                case TransactionState.PREPARED:
                    // ok in this case we do NOTHING
                    break;
                case TransactionState.COMMITTED:
                    // ok in this case we commit
                    Globals.getTransactionList().commitRemoteTxn(tuid);
                    break;
            }
           
        }



        // ok now we've processed all transactions ... get rid of it
      */
    }

    public boolean inTakeover() {
        synchronized(takeoverRunnableLock) {
            return takeoverRunnable != null;
        }
    }

    public void takeoverBroker(HAClusteredBroker cb, boolean force) 
        throws BrokerException 
    {
        downBroker dbroker = new downBroker();
        dbroker.cb = cb;
        dbroker.lastts = cb.getHeartbeat();
        dbroker.brokerSession = cb.getBrokerSessionUID();
        dbroker.storeSession = cb.getStoreSessionUID();
        ArrayList l = new ArrayList();
        l.add(dbroker);
        synchronized(takeoverRunnableLock) {
            while(monitorBusy || takeoverRunnable != null) {
                try {
                takeoverRunnableLock.wait();
                } catch (InterruptedException e) {}
            }
            takeoverRunnable = new TakeoverThread(l, force, true);
        }
        try {
            ((TakeoverThread)takeoverRunnable).doTakeover();
        } catch (Exception e) {
            if (e instanceof BrokerException) throw (BrokerException)e;
            throw new BrokerException(e.getMessage(), e, Status.ERROR);
        }
    }

    Runnable takeoverRunnable = null;
    Object takeoverRunnableLock = new Object();
    boolean monitorBusy = false;
    private Vector takingoverTargets =  new Vector();

    /**
     */
    public boolean checkTakingoverDestination(Destination d) {
        if (takingoverTargets.size() == 0) return false;
        TakingoverTracker target = null;
        Iterator itr = null;
        synchronized(takingoverTargets) {
            itr = takingoverTargets.iterator();
            while (itr.hasNext()) {
                target = (TakingoverTracker)itr.next();
                if (target.containDestination(d)) return true; 
            }
        }
        return false;
    }

    public boolean checkTakingoverMessage(Packet p) {
        TakingoverTracker target = null;
        Iterator itr = null;
        synchronized(takingoverTargets) {
            itr = takingoverTargets.iterator();
            while (itr.hasNext()) {
                target = (TakingoverTracker)itr.next();
                if (target.containMessage(p)) return true; 
            }
        }
        return false;
    }

    public boolean isTakingoverTarget(String brokerID, UID storeSession) {
        TakingoverTracker target = null;
        Iterator itr = null;
        synchronized(takingoverTargets) {
            itr = takingoverTargets.iterator();
            while (itr.hasNext()) {
                target = (TakingoverTracker)itr.next();
                UID ss = target.getStoreSessionUID();
                if (target.getBrokerID().equals(brokerID) &&
                    ((ss == null && 
                      target.getDownStoreSessionUID().equals(storeSession)) ||
                     (ss != null && 
                      (ss.equals(storeSession) ||
                       target.getDownStoreSessionUID().equals(storeSession))))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Class used to handle takeover of indoubt brokers
     * after the monitor thread has determined that the
     * broker should be considered dead.
     * The takeover runs as a seperate thread to prevent
     * this processing from slowing down normal heartbeat
     * monitoring.
     */
    class TakeoverThread implements Runnable
    {
        /**
         * list of down brokers.
         */
        ArrayList downBkrs = null;
        boolean force = false;
        boolean throwex = false; 

        /**
         * create an instance of TakeoverThread.
         * @param downBkrs the list of brokers to takeover
         */
        public TakeoverThread(ArrayList downBkrs, boolean force, boolean throwex)
        {
            this.downBkrs = downBkrs;
            this.force = force;
            this.throwex = throwex;
        }


        /**
         * Processes each of the brokers which need to be
         * taken over.
         */
        public void run() {
            try {
                doTakeover();
            } catch (Exception e) { //should never happen
                logger.logStack(logger.WARNING, "Unexpected exception "+e.getMessage(), e);
            }
        }

        public void doTakeover() throws Exception {
            try {

            ClusterBroadcast mbus = Globals.getClusterBroadcast();
            while (mbus == null) {
                logger.log(Logger.INFO, BrokerResources.I_CLUSTER_WAIT_PROTOCOLINIT);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {};
                mbus = Globals.getClusterBroadcast();
            }
            Iterator itr = downBkrs.iterator();
            while (itr.hasNext()) {
                downBroker dbroker = (downBroker)itr.next();
                HAClusteredBroker cb = dbroker.cb;
                logger.log(Logger.INFO, BrokerResources.I_START_TAKEOVER,
                                        cb.getBrokerName() );
                boolean takeoverComplete = false;
                TakingoverTracker tracker = new TakingoverTracker(
                                                cb.getBrokerName(),
                                                Thread.currentThread());
                tracker.setBrokerSessionUID(dbroker.brokerSession);
                tracker.setDownStoreSessionUID(dbroker.storeSession);
                tracker.setLastHeartbeat(dbroker.lastts);
                takingoverTargets.add(tracker);

                try {
                    mbus.preTakeover(cb.getBrokerName(), tracker.getDownStoreSessionUID(),
                         ((BrokerMQAddress)cb.getBrokerURL()).getHost().getHostAddress(),
                         tracker.getBrokerSessionUID());

                    TakeoverStoreInfo info = cb.takeover(force, tracker);
                    tracker.setStage_BEFORE_PROCESSING();
                    takeoverComplete = true;
                    logger.log(Logger.INFO, BrokerResources.I_TAKEOVER_OK,
                                            tracker.getBrokerID());

                    Map msgs = info.getMessageMap();
                    List txn = info.getTransactionList();
                    List remoteTxn = info.getRemoteTransactionList();
                    logger.log(Logger.INFO,
                          BrokerResources.I_TAKEOVER_TXNS,
                          tracker.getBrokerID(),
                          String.valueOf(txn == null ? 0 : txn.size()));
                    logger.log(Logger.INFO,
                          BrokerResources.I_TAKEOVER_REMOTE_TXNS,
                          tracker.getBrokerID(),
                          String.valueOf(remoteTxn == null ? 0 : remoteTxn.size()));

                    Destination.remoteCheckTakeoverMsgs(msgs, tracker.getBrokerID());

                    // handle takeover logic
                    // process rolling back transactions
                    List openTxns = new ArrayList();
                    Iterator titr = txn.iterator();
                    while (titr.hasNext()) {
                        boolean rollbackTxn = false;
                        TransactionUID tid = (TransactionUID) titr.next();
                        TransactionState ts = Globals.getStore().getTransactionState(tid);
                        if (ts == null) {
                            titr.remove();
                            continue;
                        }
                        AutoRollbackType type = ts.getType();
                        int state = ts.getState();

                        // OK .. first handle loading messages because
                        // that means we have all the refs in order

                        // ok first handle database changes for ANY
                        // committed or rolledback message
                        if (state == TransactionState.ROLLEDBACK) {

                            logger.log(Logger.INFO, "XXX - DEBUG Rolling back "
                                                     + " transaction " + tid);
                            // ok we may not be done with things yet
                            // add to opentxn list
                            openTxns.add(tid);
                        
                        } else if (state == TransactionState.COMMITTED) {
                            logger.log(Logger.INFO, "XXX - DEBUG Committing "
                                                     + " transaction " + tid);
                             // ok we may not be done with things yet
                             // add to opentxn list
                             openTxns.add(tid);

                        } else if (type == AutoRollbackType.ALL) {
                            // rollback
                            String args[] = {
                                 tracker.getBrokerID(),
                                 String.valueOf(tid.longValue()),
                                 ts.toString(ts.getState()) };
                            logger.log(Logger.INFO,
                                BrokerResources.I_TAKEOVER_TXN_A_ROLLBACK,
                                args);
                            ts.setState(TransactionState.ROLLEDBACK);
                            try {
                                Globals.getStore().updateTransactionState(tid, ts,
                                                   Destination.PERSIST_SYNC);
                            } catch (IOException e) {
                                throw new BrokerException(null, e);
                            }

                             // ok we may not be done with things yet
                             // add to opentxn list
                             openTxns.add(tid);
                        } else if (ts.getType() == AutoRollbackType.NOT_PREPARED 
                            &&  ts.getState() < TransactionState.PREPARED) {
                            String args[] = {
                                 tracker.getBrokerID(),
                                 String.valueOf(tid.longValue()),
                                 ts.toString(ts.getState()) };
                            logger.log(Logger.INFO,
                                BrokerResources.I_TAKEOVER_TXN_P_ROLLBACK,
                                args);
                            ts.setState(TransactionState.ROLLEDBACK);
                            try {
                                Globals.getStore().updateTransactionState(tid, ts,
                                                   Destination.PERSIST_SYNC);
                            } catch (IOException e) {
                                throw new BrokerException(null, e);
                            }
                             // ok we may not be done with things yet
                             // add to opentxn list
                             openTxns.add(tid);
                        } else {
                            String args[] = {
                                tracker.getBrokerID(),
                                String.valueOf(tid.longValue()),
                                 ts.toString(ts.getState()) };
                            logger.log(Logger.INFO,
                                BrokerResources.I_TAKEOVER_TXN, args);
                        }
                    } 

                    TransactionUID tid = null;
                    TransactionState ts = null, myts = null;
                    TransactionList translist = Globals.getTransactionList();
                    titr = remoteTxn.iterator();
                    while (titr.hasNext()) {
                        tid = (TransactionUID) titr.next();
                        try {
                            ts = Globals.getStore().getTransactionState(tid);
                        } catch (Exception e) {
                            logger.log(logger.WARNING, 
                            "Unable to get transaction state "+tid+ 
                            " for takenover broker "+tracker.getBrokerID()); 
                            continue;
                        }
                        if (ts == null) continue;
                        try {
                            if (ts.getState() < TransactionState.PREPARED &&
                                translist.retrieveState(tid) != null) { // auto-rollback type
                                if (!translist.isClusterTransactionBroker(tid,
                                    tracker.getStoreSessionUID())) {
                                    continue;
                                }
                                myts = translist.retrieveState(tid);
                                if (myts == null) continue;
                                myts = new TransactionState(myts);
                                translist.updateState(tid, 
                                  myts.nextState(PacketType.ROLLBACK_TRANSACTION, null),
                                  myts.getState(), true);
                                logger.log(logger.INFO, 
                                "Remote transaction "+tid+"("+
                                 TransactionState.toString(ts.getState())+
                                 ") from takenover broker "+tracker+" will be rolledback");
                            }
                        } catch (Exception e) {
                            logger.log(logger.WARNING, 
                            "Unable to set ROLLBACK state to transaction "+tid+":"+
                            e.getMessage()+ " for takenover broker "+tracker.getBrokerID());
                            continue;
                        }
                    }

                    TakeoverReaper reaper = new TakeoverReaper(
                        tracker.getBrokerID(), openTxns);

                    Map m = Globals.getTransactionList().loadTakeoverTxns(txn, remoteTxn, msgs);
                    logger.log(Logger.INFO,
                          BrokerResources.I_TAKEOVER_MSGS,
                          tracker.getBrokerID(),
                         String.valueOf(msgs == null ? 0 : msgs.size()));

                    Destination.loadTakeoverMsgs(msgs, txn, m);
                    tracker.setStage_AFTER_PROCESSING();
                    takingoverTargets.remove(tracker);

                    Globals.getTransactionList().unlockTakeoverTxns(txn);
                    // OK rollback Open txns - we want to do this
                    boolean done = reaper.processTxns();

                    MQTimer timer = Globals.getTimer();
                    try {
                        if (!done)
                            timer.schedule(reaper, reaperTimeout, reaperTimeout);

                    } catch (IllegalStateException ex) {
                        logger.logStack(Logger.WARNING,
                            BrokerResources.E_INTERNAL_BROKER_ERROR,
                            "Unable to start takeover-transaction reaper", ex);
                    }

                    logger.log(logger.INFO, 
                          BrokerResources.I_TAKEOVER_DATA_PROCESSED,
                          tracker.toString());

                    // we have done processing data, set state to 
                    // complete
                    cb.setBrokerIsUp(false, tracker.getBrokerSessionUID(),
                                            tracker.getStoreSessionUID());
                    cb.setStateFailoverProcessed(tracker.getStoreSessionUID());

                    logger.log(logger.INFO, 
                          BrokerResources.I_TAKEOVER_COMPLETE,
                          tracker.toString());

                    itr.remove();
                } catch (Exception ex) {
                    if ( ex instanceof TakeoverLockException ) {
                        BrokerState state = null;
                        String takeoverBy = null;
                        HABrokerInfo bkrInfo = ((TakeoverLockException)ex).getBrokerInfo();
                        if (bkrInfo == null) {
                            // This shouldn't happens but just in case
                            try {
                                state = cb.getState();
                                takeoverBy = cb.getTakeoverBroker();
                            } catch ( BrokerException e ) {}
                        } else {
                            state = BrokerState.getState(bkrInfo.getState());
                            takeoverBy = bkrInfo.getTakeoverBrokerID();
                        }

                        if ( state == BrokerState.FAILOVER_STARTED ||
                             state == BrokerState.FAILOVER_PENDING ||
                             state == BrokerState.FAILOVER_COMPLETE ) {
                            if (takeoverBy != null) {
                                logger.log(Logger.INFO,
                                    BrokerResources.E_UNABLE_TO_TAKEOVER_BKR,
                                    cb.getBrokerName(),
                                    "Broker is being taken over by " + takeoverBy);
                            } else {
                                logger.log(Logger.ERROR,
                                    BrokerResources.I_NOT_TAKEOVER_BKR);
                            }
                        } else {
                            logger.logStack(Logger.ERROR,
                                BrokerResources.E_UNABLE_TO_TAKEOVER_BKR,
                                cb.getBrokerName(),
                                "Takeover lock error (state=" + state +
                                ", takeoverBroker=" + takeoverBy + ")", ex);
                        }

                        try {
                            cb.setStateFailoverFailed(dbroker.brokerSession);
                        } catch (Exception ex2) {
                            logger.logStack(Logger.ERROR,
                                BrokerResources.E_INTERNAL_ERROR, ex2);
                        }

                    } else if (ex instanceof BrokerException && 
                               ((BrokerException)ex).getStatusCode() == Status.CONFLICT){
                        logger.log(Logger.INFO, ex.getMessage());
                    } else {
                        if (tracker != null && 
                            tracker.getStage() >= TakingoverTracker.AFTER_DB_SWITCH_OWNER) {
                            logger.logStack(Logger.ERROR, Globals.getBrokerResources().getKString(
                                BrokerResources.E_TAKEOVER_DATA_PROCESSING_FAIL, tracker.toString()), ex); 
                        } else {
                            logger.logStack(Logger.ERROR,
                                BrokerResources.E_UNABLE_TO_TAKEOVER_BKR,
                                cb.getBrokerName(), ex.getMessage(), ex);
                        }
                    }
                    cb.setBrokerIsUp(false, dbroker.brokerSession, null);
                    if (throwex) throw ex;
                } finally {
                    mbus.postTakeover(tracker.getBrokerID(),
                                      (takeoverComplete ? tracker.getStoreSessionUID():
                                                          tracker.getDownStoreSessionUID()),
                                      !takeoverComplete);
                    if (tracker.getStage() < TakingoverTracker.AFTER_DB_SWITCH_OWNER) {
                        takingoverTargets.remove(tracker);
                    }
                }
            }

            } finally {
                synchronized(takeoverRunnableLock) {
                    takeoverRunnable = null;
                    takeoverRunnableLock.notify();
                }
            }
        }
    }


    /**
     * Places a broker on the "indoubt" list.
     *
     * @param brokerid broker who is indoubt
     */
    private void watchBroker(String brokerid, UID brokerSession)
        throws BrokerException
    {
        synchronized (indoubtBrokers)
        {
            if (indoubtBrokers.get(brokerid) != null) {
                   return;
            }
            indoubtData wbd = new indoubtData();
            wbd.brokerid = brokerid;
            wbd.lastts = ((HAClusteredBroker)clusterconfig.getBroker(
                             brokerid)).getHeartbeat();
            wbd.monitorCnt = 0;
            wbd.brokerSession = brokerSession;
            indoubtBrokers.put(brokerid, wbd);
        }
    }

    /**
     * Removed broker from the "indoubt" list.
     *
     * @param brokerid broker who is indoubt
     */
    private void stopWatchingBroker(String brokerid)
    {
        synchronized (indoubtBrokers)
        {
            indoubtBrokers.remove(brokerid);
        }
    }


    //-------------------------------------------------------------
    //  CLUSTER LISTENER
    //-------------------------------------------------------------

    /**
     * Notification that the cluster configuration has changed.
     * The monitoring service ignores any configuration changes.
     * @see ClusterListener
     * @param name property changed
     * @param value new setting for the property 
     */
    public void clusterPropertyChanged(String name, String value)
    {
        // do nothing, we dont care
    }

   /**
    * Called when a new broker has been added.
    * @param broker the new broker added.
    */
    public void brokerAdded(ClusteredBroker broker, UID brokerSession)
    {
        // do nothing, we dont care
    }

   /**
    * Called when a broker has been removed.
    * @param broker the broker removed.
    */
    public void brokerRemoved(ClusteredBroker broker, UID brokerSession)
    {
        // do nothing, we dont care
    }


   /**
    * Called when the broker who is the master broker changes
    * (because of a reload properties).
    * @param oldMaster the previous master broker.
    * @param newMaster the new master broker.
    */
    public void masterBrokerChanged(ClusteredBroker oldMaster,
                    ClusteredBroker newMaster)
    {
        // do nothing, we dont care
    }


   /**
    * Called when the status of a broker has changed. The
    * status may not be accurate if a previous listener updated
    * the status for this specific broker.
    * @param brokerid the name of the broker updated.
    * @param userData optional userData
    * @param oldStatus the previous status.
    * @param newStatus the new status.
    */
    public void brokerStatusChanged(String brokerid,
                  int oldStatus, int newStatus, UID brokerSession,
                  Object userData)
    {
        logger.log(Logger.DEBUG,"brokerStatusChanged " + brokerid + ":" + "\n\t"
             +BrokerStatus.toString(oldStatus) + "\n\t"
             + BrokerStatus.toString(newStatus) + "\n\t" + userData );
        // do nothing, we dont care
        if (BrokerStatus.getBrokerInDoubt(newStatus) && 
                 BrokerStatus.getBrokerIsUp(newStatus)) {
            ClusteredBroker cb = clusterconfig.getBroker(brokerid); 
            if (cb.isLocalBroker()) return;
            try {
                BrokerState state = cb.getState();
                if (state == BrokerState.SHUTDOWN_COMPLETE
                   || state == BrokerState.FAILOVER_COMPLETE) {
                    logger.log(logger.INFO,
                         BrokerResources.I_BROKER_OK,
                            cb.getBrokerName());
                    cb.setBrokerIsUp(false, brokerSession, null);
                    return;
                }
                logger.log(Logger.INFO,
                         BrokerResources.I_BROKER_INDOUBT_START_MONITOR,
                         brokerid);
                watchBroker(brokerid, brokerSession);
            } catch (Exception ex) {
                    logger.logStack(Logger.INFO,
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                       "Unable to monitor broker " + brokerid, ex);
            }
        } else if (BrokerStatus.getBrokerInDoubt(oldStatus)
                   && BrokerStatus.getBrokerIsUp(newStatus)) {
            // XXX - stop watching
            stopWatchingBroker(brokerid);
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
                  BrokerState oldState, BrokerState newState)
    {
        // do nothing, we dont care
        ClusteredBroker cb = clusterconfig.getBroker(brokerid);

        if (cb.isLocalBroker() && 
           ( newState == BrokerState.SHUTDOWN_COMPLETE ||
            newState == BrokerState.SHUTDOWN_FAILOVER)) {
                haMonitor.cancel();
                clusterconfig.removeEventListener(this);

        }
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
                  int oldVersion, int newVersion)
    {
        // do nothing, we dont care
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
                  MQAddress oldAddress, MQAddress newAddress)
    {
        // do nothing, we dont care
    }



}



