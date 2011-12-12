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

package com.sun.messaging.bridge.service.jms.tx.log;

import java.util.List;
import java.util.Vector;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.timer.WakeupableTimer;
import com.sun.messaging.jmq.util.timer.TimerEventHandler;
import com.sun.messaging.bridge.service.jms.tx.GlobalXid;
import com.sun.messaging.bridge.service.jms.tx.BranchXid;
import com.sun.messaging.bridge.service.BridgeException;

/**
 *
 * @author amyk
 */

public abstract class TxLog {

    public static final String FILETYPE = "file";
    public static final String JDBCTYPE = "jdbc";
    public static final String FILECLASS = "com.sun.messaging.bridge.service.jms.tx.log.FileTxLogImpl";
    public static final String JDBCCLASS = "com.sun.messaging.bridge.service.jms.tx.log.JDBCTxLogImpl";
    public static final int DEFAULT_REAPLIMIT = 50;
    public static final int DEFAULT_REAPINTERVAL = 60; //1min

    Logger _logger = null;

    private boolean _closed = false;
    private Object _closedLock = new Object();
    private int _inprogressCount = 0;
    private Object _inprogressLock = new Object();

    private TransactionReaper _reaper = null;
    private int _reapLimit    = DEFAULT_REAPLIMIT;
    private int _reapInterval = DEFAULT_REAPINTERVAL;

    protected String _tmname = null;
    protected String _jmsbridge = null;

    public void setLogger(Logger logger) {
        _logger = logger;
    }

    public Logger getLogger() {
        return _logger;
    }

    /**
     * @param props The properties is guaranteed to contain 
     *              "txlogDir", "txlogSuffix", "txlogMaxBranches"
     * @param reset true to reset the txlog
     */
    public void init(Properties props, boolean reset) throws Exception {
        _jmsbridge = props.getProperty("jmsbridge");
        if (_jmsbridge == null) {
            throw new IllegalArgumentException("Property 'jmsbridge' not set");
        }
        _tmname = props.getProperty("tmname");
        if (_tmname == null) {
            throw new IllegalStateException("Property 'tmname' not set");
        }
        String v = props.getProperty("txlogReapLimit");
        if (v != null) {
            _reapLimit = Integer.parseInt(v);
        }
        v = props.getProperty("txlogReapInterval");
        if (v != null) {
            _reapInterval = Integer.parseInt(v);
        }
        _reaper = new TransactionReaper(this, _reapLimit, _reapInterval);
    }

    public String getTMName() {
        return _tmname;
    }

    public abstract String getType();

    /**
     * @param lr the LogRecord to log
     */
    public abstract void logGlobalDecision(LogRecord lr) throws Exception;

    /**
     * @param lr the LogRecord to identify the record to update 
     * @param bxid the branch xid to update 
     */
    public abstract void logHeuristicBranch(BranchXid bxid, LogRecord lr) throws Exception;

    /**
     *
     * @param gxid the GlobalXid 
     * @return a copy of LogRecord corresponding gxid or null if not exist
     */
    public abstract LogRecord getLogRecord(GlobalXid gxid) throws Exception;

    /**
     * @return a list of all log records
     */
    public abstract List getAllLogRecords() throws Exception;
    
    /**
     * @param gxid the global xid record to remove 
     */
    public void remove(GlobalXid gxid) throws Exception {
        _reaper.addTransaction(gxid);
    }

    /**
     * @param gxid the global xid record to remove 
     */
    public abstract void reap(String gxid) throws Exception;

    public boolean isClosed() {
        return _closed;
    }

    /**
     * subclass override 
     */
    public void close() throws Exception {
        synchronized(_closedLock) {
            if (!isClosed()) _closed = true ; 
        }
        if (_reaper != null) _reaper.destroy();
    }

    //The following methods are similar as in Store.java
    protected void setClosedAndWait() {
        synchronized (_closedLock) {
            _closed = true;
        }
        synchronized (_inprogressLock) {
            while (_inprogressCount > 0) {
                try {
                    _inprogressLock.wait();
                } catch (Exception e) {}
            }
        }
    }

    protected void checkClosedAndSetInProgress() throws Exception {
        synchronized (_closedLock) {
            if (_closed) {
                String emsg = "Accessing TM txLog after store closed"; 
                throw new BridgeException(emsg, Status.UNAVAILABLE);
            } else {
                setInProgress(true);
            }
        }
    }

    protected void setInProgress(boolean flag) {
        synchronized (_inprogressLock) {
            if (flag) {
                _inprogressCount++;
            } else {
                _inprogressCount--;
            }

            if (_inprogressCount == 0) {
                _inprogressLock.notify();
            }
        }
    }
}

class TransactionReaper implements Runnable, TimerEventHandler
{
    private TxLog _txlog = null;
	private Logger _logger = null;

    private Vector removes = new Vector(); 
    private WakeupableTimer reapTimer = null;
    private int _reapLimit = 1;
    private long _reapInterval = 1000; //in millisecs
    
    /*
     * @param reapInterval in secs 
     */
    public  TransactionReaper(TxLog txlog, int reapLimit, int reapInterval) { 
        _txlog = txlog;
        _logger = _txlog.getLogger();
        _reapLimit = reapLimit;
        if (_reapInterval <= 0) {
            _reapInterval = TxLog.DEFAULT_REAPINTERVAL;
        }
        _reapInterval = reapInterval * 1000L;
    }

    public void addTransaction(GlobalXid gxid) {
        if (_reapLimit == 0 ||
            removes.size() > (2*_reapLimit)) {
            try {
                 _txlog.reap(gxid.toString());
                 return;
            } catch (Exception e) {
                 _logger.log(Level.WARNING,
                 "Failed to cleanup global transaction "+gxid+":"+e+", will retry later", e);
            }
        }
        removes.add(gxid);
        createTimer();
        if (removes.size() > _reapLimit) reapTimer.wakeup();
    }

    private synchronized void createTimer() {
        if (reapTimer == null) { 
            try {
            String startString = "Transaction reaper thread has started for TM "+_txlog.getTMName();
            String exitString = "Transaction reaper thread for TM "+_txlog.getTMName()+" is exiting"; 

            reapTimer = new WakeupableTimer("JMSBridgeTMTransactionReaper-"+_txlog.getTMName(),
                                            this,
                                            _reapInterval, _reapInterval,
                                            startString, exitString, this);
            } catch (Throwable ex) {
            _logger.log(Level.WARNING, 
            "Unable to start transaction reaper thread for TM "+_txlog.getTMName(), ex);
            try {
            _txlog.close();
            } catch (Exception e) {}
            }
        }
    }

   

   /***************************************************
    * Methods for TimerEventHandler for WakeupableTimer
    ***************************************************/
    public void handleOOMError(Throwable e) {
    }

    public void handleLogInfo(String msg) {
        if (_logger == null) return;
        _logger.log(Level.INFO, msg);
    }
    public void handleLogWarn(String msg, Throwable e) {
        if (_logger == null) return;
        _logger.log(Level.WARNING, msg, e);
    }
    public void handleLogError(String msg, Throwable e) {
        if (_logger == null) return;
        _logger.log(Level.SEVERE, msg, e);
    }

    public void handleTimerExit(Throwable e) {
        if (reapTimer == null || _txlog.isClosed()) return;
        _logger.log(Level.SEVERE, 
            "["+_txlog.getTMName()+"]Unexpected transaction log reaper thread exit", e);
        try {
        _txlog.close();
        } catch (Exception ex) {}
    }


    public synchronized void destroy() {
        if (reapTimer != null)  {
            reapTimer.cancel();
            reapTimer = null;
        }
        removes.clear();
    }

    public void run() {

        GlobalXid[] gxids = (GlobalXid[])removes.toArray(new GlobalXid[0]);
        int cnt =  gxids.length - _reapLimit;
        for (int i = 0; i < cnt; i++) {
            String key = gxids[i].toString();
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, "Cleaning up global transaction "+key);
            }
            try {
                 _txlog.reap(key);
                 removes.remove(gxids[i]);
            } catch (Exception e) {
                 _logger.log(Level.WARNING,
                         "Failed to cleanup global transaction "+key, e);
            }
        }
    }
}
