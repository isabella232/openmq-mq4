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
 * @(#)MultibrokerRouter.java   1.51 7/30/07
 */ 

package com.sun.messaging.jmq.jmsserver.core.cluster;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.DMQ;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.core.ClusterRouter;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.multibroker.Protocol;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.util.AckEntryNotFoundException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.ConsumerAlreadyAddedException;
import com.sun.messaging.jmq.jmsserver.util.MQThread;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.lists.EventType;
import com.sun.messaging.jmq.util.lists.Prioritized;
import com.sun.messaging.jmq.util.lists.Reason;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.util.txnlog.TransactionLogType;

/**
 * This class handles the processing of messages from other brokers
 * in the cluster.
 */
public class MultibrokerRouter implements ClusterRouter
{
    private static boolean DEBUG = false;
    private static Logger logger = Globals.getLogger();

    private static final String ENFORCE_REMOTE_DEST_LIMIT_PROP = 
            Globals.IMQ + ".cluster.enforceRemoteDestinationLimit";
    private static boolean ENFORCE_REMOTE_DEST_LIMIT =
        Globals.getConfig().getBooleanProperty( ENFORCE_REMOTE_DEST_LIMIT_PROP, false);

    //obsolete private property
    private static String ROUTE_REJECTED_REMOTE_MSG =
        Globals.IMQ+".cluster.routeRejectedRemoteMsg"; //4.5

    private ArrayList loggedFullDestsOnHandleJMSMsg = new ArrayList();

    ClusterBroadcast cb = null;
    Protocol p = null;

    BrokerConsumers bc = null;

    public MultibrokerRouter(ClusterBroadcast cb)
    {
        this.cb = cb;
        this.p = cb.getProtocol();
        bc = new BrokerConsumers(p);
    }

    public static String msgToString(int id)
    {
        switch(id) {
            case ClusterBroadcast.MSG_DELIVERED:
                return "MSG_DELIVERED";
            case ClusterBroadcast.MSG_ACKNOWLEDGED:
                return "MSG_ACKNOWLEDGED";
            case ClusterBroadcast.MSG_PREPARE:
                return "MSG_PREPARE";
            case ClusterBroadcast.MSG_ROLLEDBACK:
                return "MSG_ROLLEDBACK";
            case ClusterBroadcast.MSG_IGNORED:
                return "MSG_IGNORED";
            case ClusterBroadcast.MSG_UNDELIVERABLE:
                return "MSG_UNDELIVERABLE";
            case ClusterBroadcast.MSG_DEAD:
                return "MSG_DEAD";
        }
        return "UNKNOWN";
    }

    public void addConsumer(Consumer c) 
       throws BrokerException, IOException, SelectorFormatException
    {
        bc.addConsumer(c);
    }

    public void removeConsumer(com.sun.messaging.jmq.jmsserver.core.ConsumerUID c,
                               Set pendingMsgs, boolean cleanup)
       throws BrokerException, IOException
    {
        bc.removeConsumer(c, pendingMsgs, cleanup);
    }


    public void removeConsumers(ConnectionUID uid)
       throws BrokerException, IOException
    {
        bc.removeConsumers(uid);
    }


    public void brokerDown(com.sun.messaging.jmq.jmsserver.core.BrokerAddress ba)
       throws BrokerException, IOException
    {
        bc.brokerDown(ba);
    }

    public void forwardMessage(PacketReference ref, Collection consumers) {
        bc.forwardMessageToRemote(ref, consumers);
    }


    public void shutdown() {
        bc.destroy();
    }

    public void handleJMSMsg(Packet p, List consumers, BrokerAddress sender,
              boolean sendMsgDeliveredAck)
        throws BrokerException
    {
        boolean hasflowcontrol = true;
        ArrayList targetVector = new ArrayList();
        ArrayList ignoreVector = new ArrayList();

        Iterator itr = consumers.iterator();
        while (itr.hasNext()) {
            ConsumerUID uid = (ConsumerUID)itr.next();
            Consumer interest = Consumer.getConsumer(uid);

            if (interest != null && interest.isValid()) {
                // we need the interest for updating the ref
                targetVector.add(interest);
                ConsumerUID suid = interest.getStoredConsumerUID();
                if ((suid == null || suid.equals(uid)) &&
                    interest.getSubscription() == null) {
                    hasflowcontrol = false;
                }
            } else {
                ignoreVector.add(uid);
            }
        }

        boolean exists = false;
        PacketReference ref = Destination.get(p.getSysMessageID());
        if (ref != null) {
            BrokerAddress addr = ref.getAddress();
            if (addr == null || !addr.equals(sender)) {
                if (DEBUG) {
                logger.log(Logger.INFO, 
                "Remote message "+ref.getSysMessageID()+ " home broker "+addr+" changed to "+sender);
                }
                Destination.remoteCheckMessageHomeChange(ref, sender);
            }
        }
        ref = Destination.get(p.getSysMessageID());
        if (ref != null) {
            exists = true;
            ref.setBrokerAddress(sender);
            if (p.getRedelivered()) ref.overrideRedeliver();
        } else {
            ref = PacketReference.createReference(p, null);
            ref.setBrokerAddress(sender);
        }
        // XXX - note, we send a reply for all message delivered
        //       acks, that is incorrect
        //       really, we should have a sendMsgDeliveredAck per
        //       consumer
        if (sendMsgDeliveredAck) {
            for (int i=0; i < targetVector.size(); i ++) {
                Consumer c = (Consumer)targetVector.get(i);
                //ref.addMessageDeliveredAck(c.getStoredConsumerUID());
                ref.addMessageDeliveredAck(c.getConsumerUID());
            }
        }
        List dsts = null;
        try {
            if (ref == null)  {
                return;
            }
            if (ref.getDestinationUID().isWildcard()) {
                dsts = Destination.findMatchingIDs(ref.getDestinationUID());
            } else {
                // ok, autocreate the destination if necessary
                Destination d = Destination.getDestination(
                    ref.getDestinationUID().getName(), 
                    ref.getDestinationUID().isQueue() ? DestType.DEST_TYPE_QUEUE
                       : DestType.DEST_TYPE_TOPIC, true, true);
                if (d != null) {
                    dsts = new ArrayList();
                    dsts.add(d.getDestinationUID());
                }
            }
            if (dsts == null || dsts.isEmpty()) {
               ignoreVector.addAll(targetVector); 
               targetVector.clear();
            } else {
                if (!exists && !targetVector.isEmpty()) {
                    ref.setNeverStore(true);
                    // OK .. we dont need to route .. its already happened
                    ref.store(targetVector);
                    itr = dsts.iterator();
                    while (itr.hasNext()) {
                        DestinationUID did = (DestinationUID)itr.next();
                        Destination d = Destination.getDestination(did);
                        if (DEBUG) {
                            logger.log(logger.INFO, "Route remote message "+ref+
                                " sent from "+sender+" to destination(s) "+did+
                                " for consumer(s) "+targetVector+" hasflowcontrol="+
                                 hasflowcontrol+", enforcelimit="+ENFORCE_REMOTE_DEST_LIMIT); 
                        }
                        //add to message count
                        d.queueMessage(ref, false, ENFORCE_REMOTE_DEST_LIMIT);
                    }
                } else if (exists) {
                    ref.add(targetVector);
                }
            }
        } catch (Exception ex) {
            Object[] args = { (ref == null ? "null":ref), sender, targetVector };
            String emsg = Globals.getBrokerResources().getKString(
                BrokerResources.W_EXCEPTION_PROCESS_REMOTE_MSG, args);
            if (!(ex instanceof BrokerException)) {
                logger.logStack(logger.WARNING, emsg, ex);
            } else {
                BrokerException be = (BrokerException)ex;
                int status = be.getStatusCode();
                if (status != Status.RESOURCE_FULL &&
                    status != Status.ENTITY_TOO_LARGE) {
                    logger.logStack(logger.WARNING, emsg, ex);
                } else {
                    Object[] args1 = { sender, targetVector };
                    emsg = Globals.getBrokerResources().getKString(
                        BrokerResources.W_PROCESS_REMOTE_MSG_DST_LIMIT, args1);
                    int level = Logger.DEBUG;
                    if (ref == null ||
                        !loggedFullDestsOnHandleJMSMsg.contains(ref.getDestinationUID())) {
                        level = Logger.WARNING;
                        loggedFullDestsOnHandleJMSMsg.add(ref.getDestinationUID());
                    }
                    logger.log(level, emsg+(level == Logger.DEBUG ? ": "+ex.getMessage():""));
                }
            }
        }

        // Now deliver the message...
        String debugString = "\n";

        int i;
        for (i = 0; i < targetVector.size(); i++) {
            Consumer interest = (Consumer)targetVector.get(i);

            if (!interest.routeMessage(ref, false)) {
                // it disappeard on us, take care of it
               try {
                    if (ref.acknowledged(interest.getConsumerUID(),
                          interest.getStoredConsumerUID(), true, false)) {
                        if (dsts == null) continue;
                        itr = dsts.iterator();
                        while (itr.hasNext()) {
                            DestinationUID did = (DestinationUID)itr.next();
                            Destination d=Destination.getDestination(did);
                            d.removeRemoteMessage(ref.getSysMessageID(),
                                          RemoveReason.ACKNOWLEDGED, ref);
                        }
                    }
                } catch (Exception ex) {
                    logger.log(logger.INFO,"Internal error processing ack",
                           ex);
                }
            } else {
                ref.addRemoteConsumerUID(interest.getConsumerUID(), 
                        interest.getConsumerUID().getConnectionUID());
            }



            if (DEBUG) {
                debugString = debugString +
                    "\t" + interest.getConsumerUID() + "\n";
            }
        }

        if (DEBUG) {
            logger.log(logger.DEBUGHIGH,
                "MessageBus: Delivering message to : {0}",
                debugString);
        }

        debugString = "\n";
        // Finally, send  ClusterGlobals.MB_MSG_IGNORED acks if any...
        Object o = null;
        ConsumerUID cuid = null;
        Consumer interest = null;
        for (i = 0; i < ignoreVector.size(); i++) {
            try {
                o = ignoreVector.get(i);
                if (o instanceof Consumer) { 
                    cuid = ((Consumer)o).getConsumerUID(); 
                } else {
                    cuid = (ConsumerUID)o;
                }
                cb.acknowledgeMessage(sender, ref.getSysMessageID(), cuid,
                         ClusterBroadcast.MSG_IGNORED, null, false);
            } catch (Exception e) {
                logger.logStack(logger.WARNING, "sendMessageAck IGNORE failed to "+sender, e);
            } 
            if (DEBUG) {
                debugString = debugString + "\t" + ignoreVector.get(i) + "\n";
            }
        }

        if (DEBUG) {
            if (ignoreVector.size() > 0)
                logger.log(logger.DEBUGHIGH,
                    "MessageBus: Invalid targets : {0}", debugString);
        }

    }

    public void handleAck(int type, SysMessageID sysid, ConsumerUID cuid,
                          Map optionalProps) throws BrokerException 
    {
       bc.acknowledgeMessageFromRemote(type, sysid, cuid, optionalProps);
    }

    public void handleAck2P(int type, SysMessageID[] sysids, ConsumerUID[] cuids,
                          Map optionalProps, Long txnID, 
                          com.sun.messaging.jmq.jmsserver.core.BrokerAddress txnHomeBroker) 
                          throws BrokerException 
    {
       bc.acknowledgeMessageFromRemote2P(type, sysids, cuids, optionalProps, txnID, txnHomeBroker);
    }


    public void handleCtrlMsg(int type, HashMap props)
        throws BrokerException
    {
        // not implemented
    }

    public Hashtable getDebugState() {
        return bc.getDebugState();
    }
}


/**
 * This class represents the remote Consumers associated with
 * the brokers in this cluster.
 */
class BrokerConsumers implements Runnable, com.sun.messaging.jmq.util.lists.EventListener
{
    private static boolean DEBUG = false;

    private static boolean DEBUG_CLUSTER_TXN =
                Globals.getConfig().getBooleanProperty(
                        Globals.IMQ + ".cluster.debug.txn");
    private static boolean DEBUG_CLUSTER_MSG =
               Globals.getConfig().getBooleanProperty(
               Globals.IMQ + ".cluster.debug.msg");

    //obsolete private property
    private static String REDELIVER_REMOTE_REJECTED = 
        Globals.IMQ+".cluster.disableRedeliverRemoteRejectedMsg"; //4.5

    static {
        if (!DEBUG) DEBUG = DEBUG_CLUSTER_TXN || DEBUG_CLUSTER_MSG;
    }

    Thread thr = null;

    Logger logger = Globals.getLogger();
    Protocol protocol = null;
    boolean valid = true;
    Set activeConsumers= Collections.synchronizedSet(new HashSet());
    Map consumers= Collections.synchronizedMap(new HashMap());
    Map listeners = Collections.synchronizedMap(new HashMap());

    private FaultInjection fi = null;


    public static int BTOBFLOW = Globals.getConfig().getIntProperty(
               Globals.IMQ + ".cluster.consumerFlowLimit",1000); 

    Map deliveredMessages = new LinkedHashMap();
    Map pendingConsumerUIDs = Collections.synchronizedMap(new LinkedHashMap());
    Map cleanupList = new HashMap();

    public BrokerConsumers(Protocol p)
    {
        this.protocol = p;
        this.fi = FaultInjection.getInjection();
        Thread thr =new MQThread(this,"Broker Monitor");
        thr.setDaemon(true);
        thr.start();
    }

    class ackEntry
    {
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = null;
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID storedcid = null;
        WeakReference pref = null;
        SysMessageID id = null;
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress address = null;
        TransactionUID tuid = null;

        public ackEntry(SysMessageID id,
              com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid,
              com.sun.messaging.jmq.jmsserver.core.BrokerAddress address) 
        { 
             assert id != null;
             assert uid != null;
             this.id = id;
             this.uid = uid;
             this.address = address;
             pref = null;
        }

        public String toString() {
            return ""+id+"["+uid+", "+storedcid+"]TUID="+tuid+", address="+address;
        }

        public void setTUID(TransactionUID uid) {
            this.tuid = uid;
        }

        public TransactionUID getTUID() {
            return tuid;
        }

        public com.sun.messaging.jmq.jmsserver.core.BrokerAddress 
               getBrokerAddress() {
             return address;
        }

        public com.sun.messaging.jmq.jmsserver.core.ConsumerUID 
               getConsumerUID() {
            return uid;
        }

        public com.sun.messaging.jmq.jmsserver.core.ConsumerUID
               getStoredConsumerUID() {
            return storedcid;
        }

        public SysMessageID getSysMessageID() {
            return id;
        }
        public PacketReference getReference() {
            return (PacketReference)pref.get();
        }

        public ackEntry(PacketReference ref, 
               com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid, 
               com.sun.messaging.jmq.jmsserver.core.ConsumerUID storedUID) 
        {
            pref = new WeakReference(ref);
            id = ref.getSysMessageID();
            storedcid = storedUID;
            this.uid = uid;
        }

        public boolean acknowledged(boolean notify) 
        {

            assert pref != null;

            PacketReference ref = (PacketReference)pref.get();

            boolean done = true;
            try {
                if (ref == null) {
                    ref = Destination.get(id);
                }
                if (ref == null) { // nothing we can do ?
                    return true;
                }
                if (ref.acknowledged(uid, storedcid, !uid.isDupsOK(), notify, tuid, null, true)) {
                    if (tuid != null && fi.FAULT_INJECTION) {
                        fi.checkFaultAndExit(
                           FaultInjection.FAULT_MSG_REMOTE_ACK_HOME_C_TXNCOMMIT_1_7,
                           null, 2, false);
                    }
                    Destination d=Destination.getDestination(ref.getDestinationUID());
                    d.removeMessage(ref.getSysMessageID(),
                      RemoveReason.ACKNOWLEDGED);
                }
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING, 
                       "Unable to process acknowledgement:["+id+","+uid+"]", ex);
                done = false;
            }
           return done;
        }

        public boolean equals(Object o) {
            if (! (o instanceof ackEntry)) {
                return false;
            }
            ackEntry ak = (ackEntry)o;
            return uid.equals(ak.uid) &&
                   id.equals(ak.id);
        }
        public int hashCode() {
            // uid is 4 bytes
            return id.hashCode()*15 + uid.hashCode();
        }
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ArrayList l = null; 
        synchronized(deliveredMessages) {
            l = new ArrayList(deliveredMessages.values());
        }
        ht.put("CLUSTER_ROUTER:deliveredMessagesCount", l.size());
        Iterator itr = l.iterator();
        while (itr.hasNext()) {
            ackEntry e = (ackEntry)itr.next();
            SysMessageID id  = e.getSysMessageID();
            ht.put("[deliveredMessages]"+id.toString(), e.toString());
        }

        synchronized(consumers) {
            l = new ArrayList(consumers.keySet());
        }
        ht.put("consumersCount", l.size());
        itr = l.iterator();
        while (itr.hasNext()) {
            ConsumerUID cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            Consumer c = (Consumer)consumers.get(cuid);
            if (c instanceof Subscription) {
                ht.put("[consumers]"+cuid.toString(), "Subscription: "+c);
            } else {
                ht.put("[consumers]"+cuid.toString(), c.toString());
            }
        }

        synchronized(activeConsumers) {
            l = new ArrayList(activeConsumers);
        }
        ht.put("activeConsumersCount", l.size());
        Vector v = new Vector();
        itr = l.iterator();
        while (itr.hasNext()) {
            Consumer c = (Consumer)itr.next();
            if (c instanceof Subscription) {
                v.add("Subscription: "+c);
            } else {
                v.add(c.toString());
            }
        }
        ht.put("activeConsumers", v);

        synchronized(pendingConsumerUIDs) {
            l = new ArrayList(pendingConsumerUIDs.keySet());
        }
        ht.put("pendingConsumerUIDsCount", l.size());
        itr = l.iterator();
        while (itr.hasNext()) {
            ConsumerUID cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            synchronized(deliveredMessages) {

            Set pending = (Set)pendingConsumerUIDs.get(cuid);
            if (pending == null) {
                ht.put("[pendingConsumerUIDs]"+cuid.toString(), "null");
            } else {
                v = new Vector(pending);
                if (v.size() == 0) {
                    ht.put("[pendingConsumerUIDs]"+cuid.toString(), "none");
                } else {
                    ht.put("[pendingConsumerUIDs]"+cuid.toString(), v);
                }
            }

            }
        }

        synchronized(cleanupList) {
            l = new ArrayList(cleanupList.keySet());
        }
        ht.put("cleanupListCount", l.size());
        v = new Vector();
        itr = l.iterator();
        while (itr.hasNext()) {
            ConsumerUID cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            v.add(cuid.toString());
        }
        ht.put("cleanupList", v);

        synchronized(listeners) {
            l = new ArrayList(listeners.keySet());
        }
        ht.put("listenersCount", l.size());
        v = new Vector();
        itr = l.iterator();
        while (itr.hasNext()) {
            ConsumerUID cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            v.add(cuid.toString());
        }
        ht.put("listeners", v);

        return ht;
    }


    public void destroy() {
        valid = false;
        synchronized(activeConsumers) {
            activeConsumers.notify();
        }
    }


    public void eventOccured(EventType type,  Reason r,
            Object target, Object oldval, Object newval, 
            Object userdata) 
    {
        if (type != EventType.BUSY_STATE_CHANGED) {
            assert false; // bad type
        }
        // OK .. add to busy list
        Consumer c = (Consumer)target;

        synchronized(activeConsumers) {
            if (c.isBusy() ) {
                activeConsumers.add(c);
            }
            activeConsumers.notify();
        }
    }

    public void brokerDown
         (com.sun.messaging.jmq.jmsserver.core.BrokerAddress address) 
        throws BrokerException
    {
        if (DEBUG) {
        logger.log(logger.INFO, "BrokerConsumers.brokerDown:"+address);
        }

        Set removedConsumers = new HashSet();
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = null;
        synchronized(consumers) {
            Iterator itr = consumers.keySet().iterator();
            while (itr.hasNext()) {
                cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
                if (DEBUG) {
                logger.log(logger.INFO, "Check remote consumer "+cuid+" from "+cuid.getBrokerAddress());
                }
                if (address.equals(cuid.getBrokerAddress())) {
                    if (address.getBrokerSessionUID() == null ||
                        address.getBrokerSessionUID().equals(
                            cuid.getBrokerAddress().getBrokerSessionUID())) {
                        removedConsumers.add(cuid);
                    }
                }
            }
        }
        synchronized(pendingConsumerUIDs) {
            Iterator itr = pendingConsumerUIDs.keySet().iterator();
            while (itr.hasNext()) {
                cuid  = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
                if (DEBUG) {
                logger.log(logger.INFO, "Check closed remote consumer "+cuid+" from "+cuid.getBrokerAddress());
                }
                if (address.equals(cuid.getBrokerAddress())) {
                    if (address.getBrokerSessionUID() == null ||
                        address.getBrokerSessionUID().equals(
                            cuid.getBrokerAddress().getBrokerSessionUID())) {
                        removedConsumers.add(cuid);
                    }
                }
            }
        }
        // destroy consumers
        Iterator itr = removedConsumers.iterator();
        while (itr.hasNext()) {
            cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            removeConsumer(cuid, true);
        }
    }

    // topic send
    public void forwardMessageToRemote(PacketReference ref, Collection cons)
    {
         Iterator itr = cons.iterator();
         while (itr.hasNext()) {
             // hey create an ack entry since we are bypassing consumer
            Consumer consumer = (Consumer)itr.next();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID sid = consumer.getStoredConsumerUID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = consumer.getConsumerUID();

            // if we dont have an ack, go on
            if (uid.isNoAck()) continue;

            synchronized(removeConsumerLock) {
                if (consumers.get(uid) == null) {
                    if (DEBUG || DEBUG_CLUSTER_TXN || DEBUG_CLUSTER_MSG) {
                    Globals.getLogger().log(Logger.INFO,
                    "BrokerConsumers.forwardMessageToRemote(): "+ref+", ignore removed consumer: "+consumer);
                    }
                    try {
                        if (ref.acknowledged(uid, sid, !uid.isDupsOK(), false)) {
                            Destination d=Destination.getDestination(ref.getDestinationUID());
                            d.removeMessage(ref.getSysMessageID(), RemoveReason.ACKNOWLEDGED);
                        }
                    } catch (Exception e) {
                        logger.logStack(Logger.WARNING,
                        "Unable to cleanup message "+ref.getSysMessageID()+" for closed consumer "+uid, e);
                    }
                    continue;
                }

                ackEntry entry = new ackEntry(ref, uid, sid);
                synchronized(deliveredMessages) {
                    deliveredMessages.put(entry, entry);
                }
            }
         }
         protocol.sendMessage(ref, cons, false);
    }

    public void removeConsumers(ConnectionUID uid) 
        throws BrokerException
    {
        if (DEBUG) {
        logger.log(logger.INFO, "BrokerConsumers.removeConsumers for remote connection: "+uid);
        }
        Set removedConsumers = new HashSet();
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = null;
        synchronized(consumers) {
            Iterator itr = consumers.keySet().iterator();
            while (itr.hasNext()) {
                cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
                if (uid.equals(cuid.getConnectionUID())) {
                    // found one
                    removedConsumers.add(cuid);
                }
            }
        }

        synchronized(pendingConsumerUIDs) {
            Iterator itr = pendingConsumerUIDs.keySet().iterator();
            while (itr.hasNext()) {
                cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
                if (uid.equals(cuid.getConnectionUID())) {
                    // found one
                    removedConsumers.add(cuid);
                }
            }
        }
        // destroy consumers
        Iterator itr = removedConsumers.iterator();
        while (itr.hasNext()) {
            cuid = (com.sun.messaging.jmq.jmsserver.core.ConsumerUID)itr.next();
            removeConsumer(cuid, true);
        }
    }

    private Object removeConsumerLock = new Object();

    public void removeConsumer(
       com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid, boolean cleanup) 
        throws BrokerException {
        removeConsumer(uid, null, cleanup);
    }

    public void removeConsumer(
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid, 
        Set pendingMsgs, boolean cleanup) 
        throws BrokerException {
          if (DEBUG) {
              logger.log(logger.INFO, "BrokerConsumers.removeConsumer:"+uid+
                         ", pending="+(pendingMsgs == null ? "null":pendingMsgs.size())+
                         ", cleanup="+cleanup);
          }
          Consumer c = null;
          synchronized(removeConsumerLock) {
              c = (Consumer)consumers.remove(uid);
          }
          if (c == null && !cleanup) return;

          Destination d = null;
          if (c != null) {
              c.pause("MultiBroker - removing consumer");
              // remove it from the destination
              d= Destination.getDestination(c.getDestinationUID());

              // quit listening for busy events
              Object listener= listeners.remove(uid);
              if (listener != null) {
                  c.removeEventListener(listener);
              }

              // remove it from the active list
              activeConsumers.remove(c);
          }

          Set destroySet = new LinkedHashSet();
          LinkedHashSet openacks = new LinkedHashSet();
          LinkedHashSet openmsgs = new LinkedHashSet();
          // OK .. get the acks .. if its a FalconRemote
          // we sent the messages directly and must explicitly ack
          synchronized(deliveredMessages) {
              if (c != null) {
                  cleanupList.put(uid, c.getParentList());
              }
              Prioritized cparent = (Prioritized)cleanupList.get(uid);
              if (DEBUG) {
              logger.log(logger.INFO, "BrokerConsumers.removeConsumer:"+uid+
                          ", pending="+pendingMsgs+", cleanup="+cleanup+", cparent="+cparent);
              }
              Iterator itr = deliveredMessages.values().iterator();
              while (itr.hasNext()) {
                  ackEntry e = (ackEntry)itr.next();
                  if (!e.getConsumerUID().equals(uid)) continue; 
                  if (e.getTUID() != null) continue;
                  if (pendingMsgs != null && !cleanup) {
                      if (pendingMsgs.contains(e.getSysMessageID())) {
                          continue;
                      }
                  }
                  if (DEBUG && DEBUG_CLUSTER_MSG) {
                      logger.log(logger.DEBUG, 
                      "BrokerConsumers.removeConsumer:"+uid+", remove ackEntry="+e+ ", c="+c);
                  }
                  itr.remove();
                  if (c != null) {
                      if (c.isFalconRemote()) {
                          e.acknowledged(false);
                      } else {
                          PacketReference ref = e.getReference();
                          if (ref != null)  {
                              ref.removeInDelivery(e.getStoredConsumerUID());
                              destroySet.add(ref);
                          }
                      }
                      continue;
                  }
                  PacketReference ref = e.getReference();
                  if (ref != null) {
                      ref.removeInDelivery(e.getStoredConsumerUID());
                  }
                  openacks.add(e);
              }
              itr = openacks.iterator();
              while (itr.hasNext()) {
                  ackEntry e = (ackEntry)itr.next();
                  if (cparent == null) {
                      e.acknowledged(false);
                  } else { 
                      PacketReference ref = e.getReference();
                      if (ref != null) openmsgs.add(ref);
                  }
              }
              if (cparent != null && openmsgs.size() > 0) {
                  cparent.addAllOrdered(openmsgs);
              }
              if (cleanup || pendingMsgs == null) {
                  cleanupList.remove(uid);
                  pendingConsumerUIDs.remove(uid);
              } else {
                  pendingConsumerUIDs.put(uid, pendingMsgs);
              }
          }

          if (c != null) {
              c.destroyConsumer(destroySet, false, false);
              if (d != null)  {
              d.removeConsumer(uid, false);
              }
          }
    }

    /**
     *  This method must be called only when holding deliveredMessages lock
     */
    private void cleanupPendingConsumerUID(com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid, SysMessageID sysid) {

        assert Thread.holdsLock(deliveredMessages);

        Set pending = (Set)pendingConsumerUIDs.get(cuid);
        if (pending == null) return;

        pending.remove(sysid);

        if (pending.isEmpty()) {
            pendingConsumerUIDs.remove(cuid);
            cleanupList.remove(cuid);
        }
    }
                    


    public boolean acknowledgeMessageFromRemote(int ackType, SysMessageID sysid,
                              com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid,
                              Map optionalProps) throws BrokerException {

        if (ackType == ClusterBroadcast.MSG_DELIVERED) {
            Consumer c = Consumer.getConsumer(cuid);
            if (c != null) {
                if (optionalProps != null) {
                    Integer pref = (Integer)optionalProps.get(Consumer.PREFETCH);
                    if (pref != null) {
                        int prefetch = Consumer.calcPrefetch(c, pref.intValue());
                        if (prefetch <= 0 || prefetch > BTOBFLOW) {
                            prefetch = BTOBFLOW;
                        }
                        c.resumeFlow(prefetch); 
                    } else {
                        c.resumeFlow();
                    }
                } else {
                    c.resumeFlow();
                }
            }
            return true;
        }
        if (ackType == ClusterBroadcast.MSG_IGNORED) {
            String tid = null, tidi = null, tido = null;
            if (optionalProps != null && 
                ((tidi = (String)optionalProps.get(ClusterBroadcast.RB_RELEASE_MSG_INACTIVE)) != null ||
                 (tido = (String)optionalProps.get(ClusterBroadcast.RB_RELEASE_MSG_ORPHAN)) != null ||
                 optionalProps.get(ClusterBroadcast.RC_RELEASE_MSG_INACTIVE) != null)) {  

                tid = (tidi == null ? tido:tidi);

                String logstrop = " for rollback remote transaction "+tid;
                String logstrtyp = "";
                if (tidi == null && tido == null) {
                    logstrop = " CLIENT_ACKNOWLEDGE recover";
                } else {
                    logstrtyp = (tidi == null ? "orphaned":"inactive");
                }
                if (DEBUG) {
                    logger.log(Logger.INFO, 
                    "Releasing message ["+cuid+", "+sysid+"]("+logstrtyp+")"+logstrop);
                }
                ackEntry entry = new ackEntry(sysid, cuid, null);
                ackEntry value = null;
                synchronized(deliveredMessages) {
                    value = (ackEntry)deliveredMessages.remove(entry);
                    cleanupPendingConsumerUID(cuid, sysid);
                }
                if (value == null) {
                    if (DEBUG) {
                        logger.log(Logger.INFO, 
                        "Releasing message ["+cuid+", "+sysid+"]("+logstrtyp+")"+logstrop);
                    }
                    return true;
                }
                PacketReference ref =value.getReference();
                if (ref == null) {
                    if (DEBUG) {
                        logger.log(Logger.INFO, 
                        "Releasing message ["+value+"]("+logstrtyp+") ref null"+logstrop);
                    }
                    return true;
                }
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID sid = value.getStoredConsumerUID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = value.getConsumerUID();
                if (sid== null || sid == uid) {
                    try {
                        if (ref.acknowledged(uid, sid, !uid.isDupsOK(), false)) {
                            Destination d=Destination.getDestination(ref.getDestinationUID());
                            d.removeMessage(ref.getSysMessageID(), RemoveReason.ACKNOWLEDGED);
                        }
                    } catch (Exception e) {
                        logger.logStack(Logger.WARNING,
                        "Unable to cleanup message "+ref.getSysMessageID()+logstrop, e);
                    }
                    return true;
                }
                    
                ref.removeInDelivery(value.getStoredConsumerUID());
                ref.getDestination().forwardOrphanMessage(ref, value.getStoredConsumerUID()); 
                return true;
            }

            if (optionalProps != null && 
                optionalProps.get(ClusterBroadcast.MSG_NOT_SENT_TO_REMOTE) != null &&
                ((String)optionalProps.get(ClusterBroadcast.MSG_NOT_SENT_TO_REMOTE)).equals("true")) {
                ackEntry entry = new ackEntry(sysid, cuid, null);
                ackEntry value = null;
                synchronized(deliveredMessages) {
                    value = (ackEntry)deliveredMessages.get(entry);
                }
                if (value == null) return true;
                PacketReference ref =value.getReference();
                if (ref == null || ref.isDestroyed() || ref.isInvalid()) {
                    if (DEBUG) {
                    logger.log(Logger.INFO, "Cleanup dead message (not remote delivered): "+ value);
                    }
                    synchronized(deliveredMessages) {
                        deliveredMessages.remove(entry);
                    }
                }
                return true;
            }

           /* dont do anything .. we will soon be closing the consumer and that
            * will cause the right things to happen 
            */
            if (DEBUG) {
                logger.log(Logger.DEBUG, "got message ignored ack, can not process [" +
                                          sysid + "," + cuid+"]" + ackType);
            }
            return true;
        } 

        ackEntry entry = new ackEntry(sysid, cuid, null);
        ackEntry value = null;

            if (ackType == ClusterBroadcast.MSG_ACKNOWLEDGED) {
                synchronized(deliveredMessages) {
                    value = (ackEntry)deliveredMessages.remove(entry);
                    cleanupPendingConsumerUID(cuid, sysid);
                }
                if (value == null) {
                    return true;
                }
                return value.acknowledged(false);
            }

        synchronized(deliveredMessages) {
            value = (ackEntry)deliveredMessages.remove(entry);
            cleanupPendingConsumerUID(cuid, sysid);

            if (ackType == ClusterBroadcast.MSG_DEAD ||
                ackType == ClusterBroadcast.MSG_UNDELIVERABLE) {
                if (value != null && value.getTUID() != null) { //XXX 
                    logger.log(logger.WARNING, "Ignore mark message dead "+sysid+
                                      " for it's prepared with TUID= "+value.getTUID()); 
                    return false;
                }  
                if (value == null && !cuid.equals(PacketReference.getQueueUID())) {  
                    if (DEBUG || DEBUG_CLUSTER_TXN || DEBUG_CLUSTER_MSG) {
                        logger.log(logger.INFO, "Mark dead message: entry not found:"+sysid+","+cuid);
                    }
                    return false;
                }
                if (DEBUG) {
                    Globals.getLogger().log(logger.INFO,
                        "Dead message "+sysid+" notification from  "+cuid.getBrokerAddress()+
                        " for remote consumer "+ cuid);
                }

                if (value != null) {
                    PacketReference ref = value.getReference();
                    if (ref == null) {
                        ref = Destination.get(sysid);
                    }
                    if (ref != null) {
                        ref.removeInDelivery(value.getStoredConsumerUID()); 
                    }
                    removeRemoteDeadMessage(ackType, sysid, cuid, 
                                            value.getStoredConsumerUID(), optionalProps);

                } else if (value == null && cuid.equals(PacketReference.getQueueUID())) {
                    Iterator itr = deliveredMessages.values().iterator();
                    int i = 0;
                    while (itr.hasNext()) {
                        ackEntry e = (ackEntry)itr.next();
                        if (e.getTUID() != null) continue;
                        if (!e.getConsumerUID().getBrokerAddress().equals(cuid.getBrokerAddress())) continue;
                        if (!e.getSysMessageID().equals(sysid)) continue;

                        com.sun.messaging.jmq.jmsserver.core.ConsumerUID sid = e.getStoredConsumerUID();
                        com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = e.getConsumerUID();

                        PacketReference ref = e.getReference();
                        if (ref == null) {
                            ref = Destination.get(sysid);
                        }
                        if (ref != null) {
                            ref.removeInDelivery(e.getStoredConsumerUID()); 
                        }
                        removeRemoteDeadMessage(ackType, sysid, uid, sid, optionalProps);
                        if (DEBUG) {
                            logger.log(Logger.INFO, "Cleanup remote dead ack entries("+(i++)+"th): "+ e);
                        }
                        itr.remove();
                    }
                }
                return true;
            } else {
                logger.log(logger.ERROR, 
                "Internal Error: ackMessageFromRemote: unexpetected ackType:"+ackType);
                return false;
            }
        }
    }

    private boolean removeRemoteDeadMessage(int ackType, SysMessageID sysid, 
                                            ConsumerUID cuid, ConsumerUID storedid,
                                            Map optionalProps)
                                            throws BrokerException {
        PacketReference ref = Destination.get(sysid);
        if (ref == null) return true;

        Destination d= ref.getDestination();
        if (d == Destination.getDMQ()) {
            // already gone, ignore
            return true;
        }
        // first pull out properties
        String comment = null;
        RemoveReason reason = null;
        Exception ex = null;
        Integer deliverCnt = null;
        Integer reasonInt = null;
        String deadbkr = null;
        if (optionalProps != null) {
            comment = (String)optionalProps.get(DMQ.UNDELIVERED_COMMENT);
            ex = (Exception)optionalProps.get(DMQ.UNDELIVERED_EXCEPTION);
            deliverCnt = (Integer)optionalProps.get(Destination.TEMP_CNT);
            reasonInt = (Integer)optionalProps.get("REASON");
            deadbkr = (String)optionalProps.get(DMQ.DEAD_BROKER);
        }
        RemoveReason rr = null;
        if (ackType == ClusterBroadcast.MSG_UNDELIVERABLE) {
            rr = RemoveReason.UNDELIVERABLE;
        } else {
            rr = RemoveReason.ERROR;
            if (reasonInt != null) {
                rr = RemoveReason.findReason(reasonInt.intValue());
            }
        }
        if (comment == null) comment = "none";
                // OK
        if (ref.markDead(cuid, storedid, comment, ex, rr, 
            (deliverCnt == null ? 0 : deliverCnt.intValue()), deadbkr)) {
            if (ref.isDead()) { 
                if (DEBUG) {
                    Globals.getLogger().log(logger.INFO,
                    "Remove dead message "+sysid+
                    " for remote consumer "+ cuid +" on destination "+d+" with reason "+rr);
                }
                try {
                    d.removeDeadMessage(ref);
                } catch (Exception e) {
                    logger.log(logger.WARNING, 
                    "Unable to remove dead["+rr+", "+deadbkr+"] message "+ref+"["+cuid+"]: "+e.getMessage(), e);
                }
            }
            return true;
        } 
        return false;
    }

    public void acknowledgeMessageFromRemote2P(int ackType, SysMessageID[] sysids,
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID[] cuids, 
                Map optionalProps, Long txnID,
                com.sun.messaging.jmq.jmsserver.core.BrokerAddress txnHomeBroker)
                throws BrokerException
    {
        if (txnID == null) {
            throw new BrokerException("Internal Error: call with null txnID"); 
        }

        //handle 2-phase remote ack

        TransactionList translist = Globals.getTransactionList();
        TransactionUID tid = new TransactionUID(txnID.longValue());
        if (ackType == ClusterBroadcast.MSG_PREPARE) {

            TransactionAcknowledgement[] tas = new TransactionAcknowledgement[sysids.length];
            ackEntry entry = null, value = null;
            StringBuffer dbuf = new StringBuffer();
            AckEntryNotFoundException ae = null;
            synchronized(deliveredMessages) {
                for (int i = 0; i < sysids.length; i++) {
                    entry =  new ackEntry(sysids[i], cuids[i], null); 
                    value = (ackEntry)deliveredMessages.get(entry);
                    if (value == null) { //XXX
                        String emsg = "["+sysids[i]+":"+cuids[i]+"]TID="+tid+" not found, maybe rerouted";
                        if (ae == null) ae = new AckEntryNotFoundException(emsg);
                        ae.addAckEntry(sysids[i], cuids[i]);
                        logger.log(logger.WARNING,
                        "["+sysids[i]+":"+cuids[i]+"] not found for preparing remote transaction "+tid+", maybe rerouted");
                        continue;
                    }
                    if (value.getTUID() != null) { 
                        String emsg = "["+sysids[i]+":"+cuids[i]+"]TID="+tid+"  has been rerouted";
                        if (ae == null) ae = new AckEntryNotFoundException(emsg);
                        ae.addAckEntry(sysids[i], cuids[i]);
                        logger.log(logger.WARNING, "["+sysids[i]+":"+cuids[i] + "] for preparing remote transaction "
                        +tid + " conflict with transaction "+value.getTUID());
                        continue;
                    }
                    com.sun.messaging.jmq.jmsserver.core.ConsumerUID scuid = value.getStoredConsumerUID();
                    tas[i] = new TransactionAcknowledgement(sysids[i], cuids[i], scuid);
                    PacketReference ref = value.getReference();
                    if (!scuid.shouldStore() || (ref != null && !ref.isPersistent())) {
                        tas[i].setShouldStore(false); 
                    }
                    if (DEBUG_CLUSTER_TXN) {
                    dbuf.append("\n\t"+tas[i]);
                    }
                }
                if (ae != null) throw ae;

                TransactionState ts = new TransactionState();
                ts.setState(TransactionState.PREPARED);
                if (DEBUG_CLUSTER_TXN) {
                logger.log(logger.INFO, "Preparing remote transaction "+tid + " from "+txnHomeBroker+dbuf.toString());
                }
                Globals.getTransactionList().logRemoteTransaction(tid, ts, tas, 
                                       txnHomeBroker, false, true, true);
                for (int i = 0; i < sysids.length; i++) {
                    entry =  new ackEntry(sysids[i], cuids[i], null); 
                    value = (ackEntry)deliveredMessages.get(entry);
                    value.setTUID(tid);
                }
            }
            if (DEBUG_CLUSTER_TXN) {
                logger.log(logger.INFO, "Prepared remote transaction "+tid + " from "+txnHomeBroker+dbuf.toString());
            }
            return;
        }

        if (ackType == ClusterBroadcast.MSG_ROLLEDBACK) {
            if (DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "Rolling back remote transaction "+tid + " from "+txnHomeBroker);
            }
            if (translist.getRemoteTransactionState(tid) == null) {
                if (DEBUG_CLUSTER_TXN) {
                logger.log(logger.INFO, "Unknown remote transaction "+tid+ ", ignore");
                }
                return;
            }
            if (!translist.updateRemoteTransactionState(tid, 
                           TransactionState.ROLLEDBACK, false, false, true)) {
                return;
            }

            if (translist.getRecoveryRemoteTransactionAcks(tid) != null) {
                rollbackRecoveryRemoteTransaction(tid, txnHomeBroker); 
            }

            RemoteTransactionAckEntry tae =  translist.getRemoteTransactionAcks(tid);
            if (tae == null) {
            logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                       BrokerResources.I_NO_NONRECOVERY_TXNACK_TO_ROLLBACK, tid)); 
            } else if (tae.processed()) {
            logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                       BrokerResources.I_NO_MORE_TXNACK_TO_ROLLBACK, tid)); 
            } else {

            TransactionAcknowledgement[] tas = tae.getAcks();
            Set s = new LinkedHashSet();
            ackEntry entry = null, value = null;
            for (int i = 0; i < tas.length; i++) {
                SysMessageID sysid = tas[i].getSysMessageID(); 
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = tas[i].getConsumerUID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = tas[i].getStoredConsumerUID();
                if (suid ==  null) suid = uid;
                synchronized(deliveredMessages) {
                        entry =  new ackEntry(sysid, uid, null);
                        value = (ackEntry)deliveredMessages.get(entry);
                        if (value == null) {
                            if (DEBUG_CLUSTER_TXN) {
                            logger.log(logger.INFO, 
                            "["+sysid+":"+uid+"] not found in rolling back remote transaction "+tid);
                            }
                            continue; 
                        }
                        if (value.getTUID() == null || !value.getTUID().equals(tid)) {
                            if (DEBUG_CLUSTER_TXN) {
                            logger.log(logger.INFO, 
                            "["+sysid+":"+uid+"] with TUID="+value.getTUID()+
                            ", in rolling back remote transaction "+tid);
                            }
                            continue;
                        }
                        if (consumers.get(uid) == null) {
                            deliveredMessages.remove(entry);
                            cleanupPendingConsumerUID(uid, sysid);
                            s.add(tas[i]);
                        } else {
                            value.setTUID(null);
                        }
                }
            }
            Iterator itr = s.iterator();
            while (itr.hasNext()) {
                TransactionAcknowledgement ta = (TransactionAcknowledgement)itr.next();
                SysMessageID sysid = ta.getSysMessageID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = ta.getConsumerUID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = ta.getStoredConsumerUID();
                if (suid == null) suid = cuid;
                PacketReference ref = Destination.get(sysid);
                if (ref == null) {
                    if (DEBUG_CLUSTER_TXN) {
                    logger.log(logger.INFO, 
                    "["+sysid+":"+cuid+"] reference not found in rolling back remote transaction "+tid);
                    }
                    continue;
                }
                ref.removeInDelivery(suid); 
                ref.getDestination().forwardOrphanMessage(ref, suid); 
            }

            } //tas != null 

            try {
            Globals.getTransactionList().removeRemoteTransactionAck(tid); 
            } catch (Exception e) {
            logger.log(logger.WARNING, 
            "Unable to remove transaction ack for rolledback transaction "+tid+": "+e.getMessage());
            }
            try {
            Globals.getTransactionList().removeRemoteTransactionID(tid, true); 
            } catch (Exception e ) {
            logger.log(logger.WARNING, 
            "Unable to remove rolledback remote transaction "+tid+": "+e.getMessage());
            }
            return;
        }

        int cLogRecordCount = 0;
        ArrayList cLogDstList = null;
        ArrayList cLogMsgList = null;
        ArrayList cLogIntList = null;

        if (ackType == ClusterBroadcast.MSG_ACKNOWLEDGED) {
            if (DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "Committing remote transaction "+tid + " from "+txnHomeBroker);
            }
            if (!Globals.getTransactionList().updateRemoteTransactionState(tid,
                         TransactionState.COMMITTED, (sysids == null), true, true)) {
                if (DEBUG_CLUSTER_TXN) {
                logger.log(logger.INFO, "Remote transaction "+tid + " already committed, from "+txnHomeBroker);
                }
                return;
            }
            boolean done = true;
            if (translist.getRecoveryRemoteTransactionAcks(tid) != null) {
                done = commitRecoveryRemoteTransaction(tid, txnHomeBroker); 
            }
            RemoteTransactionAckEntry tae = translist.getRemoteTransactionAcks(tid);
            if (tae == null) {
                logger.log(logger.INFO, 
                "No non-recovery transaction acks to process for committing remote transaction "+tid);
            } else if (tae.processed()) {
                logger.log(logger.INFO, 
                "No more transaction acks to process for committing remote transaction "+tid);
            } else {

            boolean found = false;
            TransactionAcknowledgement[] tas = tae.getAcks();
            for (int i = 0; i < tas.length; i++) {
                SysMessageID sysid = tas[i].getSysMessageID();
                com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = tas[i].getConsumerUID();
                if (sysids != null && !found) {
                    if (sysid.equals(sysids[0]) && cuid.equals(cuids[0])) { 
                        found = true;
                    }
                }

                String dstName = null;
                if (Globals.txnLogEnabled()) {
                    if (cLogDstList == null) {
                        cLogDstList = new ArrayList();
                        cLogMsgList = new ArrayList();
                        cLogIntList = new ArrayList();
                    }

                    PacketReference ref = Destination.get(sysid);
                    if (ref != null && !ref.isDestroyed() && !ref.isInvalid()) {
                        Destination dst = Destination.getDestination(ref.getDestinationUID());
                        dstName = dst.getUniqueName();
                    }
                }

                if (acknowledgeMessageFromRemote(ackType, sysid, cuid, optionalProps)) {
                    if (dstName != null) {
                        // keep track for consumer txn log
                        com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = tas[i].getStoredConsumerUID();
                        if (suid == null) suid = cuid;
                        cLogRecordCount++;
                        cLogDstList.add(dstName);
                        cLogMsgList.add(sysid);
                        cLogIntList.add(suid);
                    }
                } else {
                    done = false;
                }
            }
            
            // notify that message acks have been written to store
            if(Globals.isNewTxnLogEnabled())
            {
              Globals.getStore().loggedCommitWrittenToMessageStore(tid, BaseTransaction.REMOTE_TRANSACTION_TYPE);
            }
            
            if (sysids != null && !found) {
                logger.log(logger.ERROR, 
                "Internal Error: ["+sysids[0]+":"+cuids[0]+"] not found in remote transaction " +tid);
                done = false;
            }

            } //tae != null
            if (done) {
                try {
                Globals.getTransactionList().removeRemoteTransactionAck(tid); 
                } catch (Exception e) {
                logger.logStack(logger.WARNING, 
                "Unable to remove transaction ack for committed remote transaction "+tid, e);
                }
                try {
                Globals.getTransactionList().removeRemoteTransactionID(tid, true); 
                } catch (Exception e ) {
                logger.logStack(logger.WARNING, 
                "Unable to remove committed remote transaction "+tid, e);
                }
            } else if (Globals.getHAEnabled()) {
                throw new BrokerException(
                "Remote transaction processing incomplete, TUID="+tid);
            }

            // log to txn log if enabled
            try {
                if (Globals.txnLogEnabled() && (cLogRecordCount > 0) ) {
                    // Log all acks for consuming txn
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(
                        (cLogRecordCount * (32 + SysMessageID.ID_SIZE + 8)) + 12);
                    DataOutputStream dos = new DataOutputStream(bos);
                    dos.writeLong(tid.longValue()); // Transaction ID (8 bytes)
                    dos.writeInt(cLogRecordCount); // Number of acks (4 bytes)
                    for (int i = 0; i < cLogRecordCount; i++) {
                        String dst = (String)cLogDstList.get(i);
                        dos.writeUTF(dst); // Destination
                        SysMessageID sysid = (SysMessageID)cLogMsgList.get(i);
                        sysid.writeID(dos); // SysMessageID
                        long intid =
                            ((com.sun.messaging.jmq.jmsserver.core.ConsumerUID)
                            cLogIntList.get(i)).longValue();
                        dos.writeLong(intid); // ConsumerUID
                    }
                    dos.close();
                    bos.close();
                    Globals.getStore().logTxn(
                        TransactionLogType.CONSUME_TRANSACTION, bos.toByteArray());
                }
            } catch (IOException ex) {
                logger.logStack(Logger.ERROR,
                    Globals.getBrokerResources().E_INTERNAL_BROKER_ERROR,
                    "Got exception while writing to transaction log", ex);
                throw new BrokerException(
                    "Internal Error: Got exception while writing to transaction log", ex);
            }

            return;
        }

        throw new BrokerException("acknowledgeMessageFromRemotePriv:Unexpected ack type:"+ackType);
    }

    private void rollbackRecoveryRemoteTransaction(TransactionUID tid, 
                 com.sun.messaging.jmq.jmsserver.core.BrokerAddress from)
                 throws BrokerException {
        logger.log(logger.INFO,"Rolling back recovery remote transaction " + tid + " from "+from);

        TransactionList translist = Globals.getTransactionList();
        TransactionState ts = translist.getRemoteTransactionState(tid);
        if (ts == null || ts.getState() != TransactionState.ROLLEDBACK) { 
            throw new BrokerException(Globals.getBrokerResources().E_INTERNAL_BROKER_ERROR,
            "Unexpected broker state "+ts+ " for processing Rolledback remote transaction "+tid);
        }
        TransactionBroker ba = translist.getRemoteTransactionHomeBroker(tid);
        BrokerAddress currba = (ba == null) ? null: ba.getCurrentBrokerAddress(); 
        if (currba == null || !currba.equals(from)) {
            logger.log(logger.WARNING, "Rolledback remote transaction "+tid+" home broker "+ba+ " not "+from);
        }
        RemoteTransactionAckEntry[] tae = translist.getRecoveryRemoteTransactionAcks(tid);
        if (tae == null) {
            logger.log(logger.WARNING, 
            "No recovery transaction acks to process for rolling back remote transaction "+tid);
            return;
        }
        for (int j = 0; j < tae.length; j++) { 
            if (tae[j].processed()) continue;
            TransactionAcknowledgement[] tas = tae[j].getAcks();
            for (int i = 0; i < tas.length; i++) {

            SysMessageID sysid = tas[i].getSysMessageID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = tas[i].getConsumerUID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = tas[i].getStoredConsumerUID();
            if (suid == null) suid = cuid;
            PacketReference ref = Destination.get(sysid);
            if (ref == null) {
                if (DEBUG_CLUSTER_TXN) {
                logger.log(logger.INFO, 
                "["+sysid+":"+cuid+"] reference not found in rolling back recovery remote transaction "+tid);
                }
                continue;
            }
            ref.getDestination().forwardOrphanMessage(ref, suid); 

            }
        }
    }

    private boolean commitRecoveryRemoteTransaction(TransactionUID tid, 
                 com.sun.messaging.jmq.jmsserver.core.BrokerAddress from) 
                 throws BrokerException {
        logger.log(logger.INFO,"Committing recovery remote transaction " + tid + " from "+from);

        TransactionList translist = Globals.getTransactionList();
        TransactionBroker ba = translist.getRemoteTransactionHomeBroker(tid);
        BrokerAddress currba = (ba == null) ? null: ba.getCurrentBrokerAddress();
        if (currba == null || !currba.equals(from)) {
            logger.log(logger.WARNING, "Committed remote transaction "+tid+" home broker "+ba+ " not "+from);
        }
        RemoteTransactionAckEntry[] tae = translist.getRecoveryRemoteTransactionAcks(tid);
        if (tae == null) {
            logger.log(logger.WARNING, 
            "No recovery transaction acks to process for committing remote transaction "+tid);
            return true;
        }
        boolean done = true;
        for (int j = 0; j < tae.length; j++) { 
            if (tae[j].processed()) continue;
            TransactionAcknowledgement[] tas = tae[j].getAcks();
            for (int i = 0; i < tas.length; i++) {

            SysMessageID sysid = tas[i].getSysMessageID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID uid = tas[i].getConsumerUID();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID suid = tas[i].getStoredConsumerUID();
            if (suid == null) suid = uid;
            PacketReference ref = Destination.get(sysid);
            if (ref == null || ref.isDestroyed() || ref.isInvalid()) continue;
            try {
                if (ref.acknowledged(uid, suid, true, true)) {
                    ref.getDestination().removeMessage(ref.getSysMessageID(), 
                                                       RemoveReason.ACKNOWLEDGED);
                }
            } catch (Exception ex) {
                done = false;
                logger.logStack(Logger.ERROR, 
                Globals.getBrokerResources().E_INTERNAL_BROKER_ERROR, ex.getMessage(), ex);
            }

            }
        }
        return done;
    }

    public void addConsumer(Consumer c) 
        throws BrokerException
    {
        if (DEBUG) {
            logger.log(logger.INFO, "BrokerConsumers.addConsumer: "+c);
        }

        com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid = c.getConsumerUID();

        if (consumers.get(cuid) != null) {
            String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.I_CONSUMER_ALREADY_ADDED, 
                              cuid, c.getDestinationUID());
            logger.log(logger.INFO, emsg+" (CLUSTER_ROUTER)");
            throw new ConsumerAlreadyAddedException(emsg);
        }

        if (! (c instanceof Subscription)) {
            consumers.put(cuid, c);
            pendingConsumerUIDs.put(cuid, null);
            listeners.put(cuid, c.addEventListener(this, 
                 EventType.BUSY_STATE_CHANGED, null));
        }

        DestinationUID duid = c.getDestinationUID();

        int type = (duid.isQueue() ? DestType.DEST_TYPE_QUEUE :
                     DestType.DEST_TYPE_TOPIC);

        Destination d= null;

        try {
            // ok handle adding a reference count
            // we'll try at most 2 times to get a
            // destination

            if (duid.isWildcard()) {
                d = null;

            } else {
                for (int i=0; i < 2 && d == null ; i ++) {
                    d = Destination.getDestination(duid.getName(),
                      type, true, true);
    
                    try {
                        // increment the reference count
                        // this make sure that the destination
                        // is not removed by autocreate prematurely    
                        if (d != null) {
                            d.incrementRefCount();
    
                            break; // well we should break anyway, but
                                   // this makes it explicit
                        }
    
                    } catch (BrokerException ex) {
                        // incrementRefCount throws a BrokerException
                        // if the destination was destroyed
                        // if we are here then the destination went away
                        // try to get the destination again
                        d = null;
                    }
               }
               if (d == null)
                   throw new BrokerException("Unable to attach to destination "
                        + duid);
           }
        } catch (IOException ex) {
            throw new BrokerException("Unable to autocreate destination " +
                   duid , ex);
        }

        try {
            // OK, if we made it here, we have incremented the reference
            // count on the object, we need to make sure we decrement the RefCount
            // before we exit (so cleanup can occur)
            if (!c.getDestinationUID().isQueue() && 
               (! (c instanceof Subscription)) &&
               c.getSubscription() == null) {
                // directly send messages
                c.setFalconRemote(true);
            } else {
                int mp = (d == null ? -1 : d.getMaxPrefetch());
                if (mp <= 0 || mp > BTOBFLOW) mp = BTOBFLOW;
                int prefetch = c.getRemotePrefetch();
                if (prefetch <= 0 || prefetch > mp) {
                    prefetch = mp;
                }
                Subscription sub = c.getSubscription();
                if (sub != null && sub.getShared()) {
                    prefetch = 1;
                }
                c.setPrefetch(prefetch);
            }

             try {
                 if (d == null && c.getSubscription() == null) {
                     //deal with wildcard subscription
                     List dsts = Destination.findMatchingIDs(c.getDestinationUID());
                     if (dsts.isEmpty()) {
                         // no matching destinations
                         //we should already be in the wildcard consumers list
                         // nothing to do
                     } else {
                         Iterator itr  = dsts.iterator();
                         while (itr.hasNext()) {
                             DestinationUID did = (DestinationUID)itr.next();
                             Destination dd = Destination.getDestination(did);
                             try {
                             dd.addConsumer(c, false);
                             } catch (ConsumerAlreadyAddedException e) {
                             logger.log(logger.INFO, e.getMessage()+" (CLUSTER_ROUTER)");
                             }
                         }
                     }
                 } else if (c.getSubscription() == null) {
                     try {
                     d.addConsumer(c, false);
                     } catch (ConsumerAlreadyAddedException e) {
                     logger.log(logger.INFO, e.getMessage()+" (CLUSTER_ROUTER)");
                     }
                 }
             } catch (SelectorFormatException ex) {
                 throw new BrokerException("unable to add destination " + d,
                       ex);
             }
        
            if (! (c instanceof Subscription)) {
                if (c.isBusy()) {
                    synchronized (activeConsumers) {
                        activeConsumers.add(c);
                        activeConsumers.notify();
                    }
                }
            }
        } finally {
            // decrement the ref count since we've finished
            // processing the add consumer
            if (d != null) { // not wildcard
                d.decrementRefCount();
            }
        }
    }

    public void run() {
        while (valid) {
            Consumer c = null;
            synchronized(activeConsumers) {
                while (valid && activeConsumers.isEmpty()) {
                    try {
                        activeConsumers.wait();
                    } catch (InterruptedException ex) {
                    }
                }
                if (valid) {
                    Iterator itr = activeConsumers.iterator();
                    c = (Consumer) itr.next();
                    itr.remove();
                    if (c.isBusy()) 
                        activeConsumers.add(c);
                }
            }
            if (c == null) continue;

            PacketReference ref =  null;
            HashSet s = null;
            boolean cb = false;
            synchronized(removeConsumerLock) {
                if (consumers.get(c.getConsumerUID()) == null) {
                    if (DEBUG || DEBUG_CLUSTER_TXN || DEBUG_CLUSTER_MSG) {
                    Globals.getLogger().log(Logger.INFO, 
                    "BrokerConsumers.run(): ignore removed consumer: "+c);
                    }
                    continue;
                }

                ref =  c.getAndFillNextPacket(null);
                if (ref == null) continue;

                s = new HashSet();
                s.add(c);
                cb = ref.getMessageDeliveredAck(c.getConsumerUID()) 
                                 || c.isPaused();

                if (!c.getConsumerUID().isNoAck()) {
                    ackEntry entry = new ackEntry(ref, c.getConsumerUID(), 
                                                  c.getStoredConsumerUID());
                    synchronized(deliveredMessages) {
                        deliveredMessages.put(entry, entry);
                        if (DEBUG && DEBUG_CLUSTER_MSG) {
                        logger.log(logger.DEBUG, "deliveredMessages:"+entry);
                        }
                    }
                }
            }
            protocol.sendMessage(ref, s, cb);
        }
    }
    
}

