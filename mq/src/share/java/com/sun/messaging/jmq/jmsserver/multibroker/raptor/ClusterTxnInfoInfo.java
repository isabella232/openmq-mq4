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
 * @(#)ClusterTxnInfoInfo.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.*;
import java.util.*;
import java.nio.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

/**
 * An instance of this class is intended to be used one direction only
 */

public class ClusterTxnInfoInfo 
{
    protected Logger logger = Globals.getLogger();

    private Long transactionID = null;
    private int transactionState;
    private BrokerAddress[] brokers;
    private BrokerAddress[] waitfor;
    private BrokerAddress txnHome;
    private boolean owner = false;
    private Cluster c = null;
    private Long xid = null;

    private GPacket pkt = null;

    private ClusterTxnInfoInfo(Long txnID, int txnState,
                               BrokerAddress[] brokers, 
                               BrokerAddress[] waitfor, 
                               BrokerAddress txnHome, boolean owner, 
                               Cluster c, Long xid ) {
        this.transactionID = txnID;
        this.transactionState = txnState;
        this.brokers = brokers;
        this.waitfor = waitfor;
        this.txnHome = txnHome;
        this.owner = owner;
        this.c = c;
        this.xid = xid;
    }

    private ClusterTxnInfoInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    public static ClusterTxnInfoInfo newInstance(Long txnID, int txnState,
                                            BrokerAddress[] brokers,
                                            BrokerAddress[] waitfor,
                                            BrokerAddress txnHome, boolean owner,
                                            Cluster c, Long xid) {
        return new ClusterTxnInfoInfo(txnID, txnState, brokers, 
                                      waitfor, txnHome, owner, c, xid); 
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterTxnInfoInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterTxnInfoInfo(pkt, c);
    }

    public GPacket getGPacket() throws IOException { 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_TRANSACTION_INFO);
        gp.putProp("transactionID", transactionID);
        gp.putProp("transactionState", new Integer(transactionState));
        if (owner) {
        gp.putProp("owner", Boolean.valueOf(true));
        c.marshalBrokerAddress(c.getSelfAddress(), gp); 
        }
        if (brokers != null) {
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < brokers.length; i++) {
                if (i > 0) buf.append(",");
                buf.append(brokers[i].toProtocolString());
            }
            gp.putProp("brokers", buf.toString());
        }
        if (waitfor != null) {
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < waitfor.length; i++) {
                if (i > 0) buf.append(",");
                buf.append(waitfor[i].toProtocolString());
            }
            gp.putProp("waitfor", buf.toString());
        }
        if (txnHome != null) { 
            gp.putProp("transactionHome", txnHome.toProtocolString());
        }
        if (xid != null) gp.putProp("X", xid);
        gp.setBit(gp.A_BIT, false);

        return gp;
    }

    public boolean isOwner() {
        assert ( pkt != null );
        Boolean b = (Boolean)pkt.getProp("owner");
        if (b == null) return false;
        return b.booleanValue();
    }

    public BrokerAddress getOwnerBrokerAddress() throws Exception {
        assert ( pkt != null );
        if (!isOwner()) return null;
        return c.unmarshalBrokerAddress(pkt);
    }

    public int getTransactionState() {
        assert ( pkt != null );
        return ((Integer) pkt.getProp("transactionState")).intValue();
    }

    public Long getTransactionID() {
        assert ( pkt != null );
        return  (Long)pkt.getProp("transactionID");
    }

    public List getWaitfor() throws Exception {
        assert ( pkt != null );
        String w = (String)pkt.getProp("waitfor");
        if (w == null) return null;
        StringTokenizer tokens = new StringTokenizer(w, ",", false); 
        List bas = new ArrayList();
        String b = null;
        while (tokens.hasMoreElements()) {
            b=(String)tokens.nextElement();
            bas.add(Globals.getMyAddress().fromProtocolString(b));
        }
        return bas;
    }

    public boolean isWaitedfor(BrokerAddress me) throws Exception {
        List waitfor = getWaitfor();
        if (waitfor == null) return false;
        BrokerAddress b = null;
        TransactionBroker tb = null;
        Iterator itr = waitfor.iterator(); 
        while (itr.hasNext()) {
            tb = new TransactionBroker((BrokerAddress)itr.next());
            if (tb.getCurrentBrokerAddress().equals(me)) {
                return true;
            }
        }
        return false;
    }

    public BrokerAddress[] getBrokers() throws Exception {
        assert ( pkt != null );
        String p = (String)pkt.getProp("brokers");
        if (p == null) return null;
        StringTokenizer tokens = new StringTokenizer(p, ",", false); 
        List bas = new ArrayList();
        String b = null;
        while (tokens.hasMoreElements()) {
            b=(String)tokens.nextElement();
            bas.add(Globals.getMyAddress().fromProtocolString(b));
        }
        return (BrokerAddress[])bas.toArray(new BrokerAddress[0]);
    }

    public BrokerAddress getTransactionHome() {
        assert ( pkt != null );
        String b = (String)pkt.getProp("transactionHome");
        if (b == null) return null;
        try {
	        return Globals.getMyAddress().fromProtocolString(b);
	    } catch (Exception e) {
	        Globals.getLogger().log(Globals.getLogger().WARNING,
	        "Unable to get transaction home broker address for TID="+getTransactionID()+":"+e.getMessage());
	    }

        return null;
    }

    /**
     * To be called by sender
     */
    public String toString() {

        if (pkt == null) {
        StringBuffer buf = new StringBuffer();

        buf.append("\n\tTransactionID = ").append(transactionID);
        buf.append("\n\tTransactionState = ").append(TransactionState.toString(transactionState));

        if (txnHome != null) {
            buf.append("\n\tTransactionHome = ").append(txnHome);
        }
        if (brokers != null) {
            StringBuffer bf = new StringBuffer();
            for (int i = 0; i < brokers.length; i++) {
                if (i > 0) bf.append(",");
                bf.append(brokers[i].toProtocolString());
            }
            buf.append("\n\tBrokers = ").append(bf.toString());
        }
        if (waitfor != null) {
            StringBuffer bf = new StringBuffer();
            for (int i = 0; i < waitfor.length; i++) {
                if (i > 0) bf.append(",");
                bf.append(waitfor[i].toProtocolString());
            }
            buf.append("\n\tWaitfor = ").append(bf.toString());
        }
        if (xid != null) {
           buf.append("\n\tXID = ").append(xid);
        }

        buf.append("\n");
        return buf.toString();

        } //pkt == null

        StringBuffer buf = new StringBuffer();
        buf.append("\n\tTransactionID = ").append(getTransactionID());
        buf.append("\n\tTransactionState = ").append(TransactionState.toString(getTransactionState()));

        try {
        BrokerAddress b = getTransactionHome(); 
        if (b != null) buf.append("\n\tTransactionHome = ").append(b);
        } catch (Exception e) {
        buf.append("\n\tTransactionHome = ERROR:").append(e.toString());
        }

        BrokerAddress[] bas = null;
        try {
        bas = getBrokers(); 
        if (bas != null) {
            StringBuffer bf = new StringBuffer();
            for (int i = 0; i < bas.length; i++) {
                if (i > 0) bf.append(",");
                bf.append(bas[i].toProtocolString());
            }
            buf.append("\n\tBrokers = ").append(bf.toString());
        }
        } catch (Exception e) {
        buf.append("\n\tBrokers = ERROR:").append(e.toString());
        }

        try {
        List wbas = getWaitfor(); 
        if (wbas != null) {
            Iterator itr = wbas.iterator();
            StringBuffer bf = new StringBuffer();
            int i = 0;
            while (itr.hasNext()) {
                if (i > 0) bf.append(",");
                bf.append(((BrokerAddress)itr.next()).toProtocolString());
                i++;
            }
            buf.append("\n\tWaitfor = ").append(bf.toString());
        }
        } catch (Exception e) {
        buf.append("\n\tWaitfor = ERROR:").append(e.toString());
        }

        if (pkt.getProp("X") != null) {
           buf.append("\n\tXID = ").append(pkt.getProp("X"));
        }

        buf.append("\n");

        return buf.toString();
    }
}
