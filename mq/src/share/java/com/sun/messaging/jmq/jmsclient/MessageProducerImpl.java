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
 * @(#)MessageProducerImpl.java	1.40 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Hashtable;
import java.util.Enumeration;
import javax.jms.*;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import java.util.logging.*;



/** A client uses a message producer to send messages to a Destination. It is
  * created by passing a Destination to a create message producer method
  * supplied by a Session.
  *
  * <P>A client also has the option of creating a message producer without
  * supplying a Destination. In this case, a Destination must be input on
  * every send operation. A typical use for this style of message producer is
  * to send replies to requests using the request's replyTo Destination.
  *
  * <P>A client can specify a default delivery mode, priority and time-to-live
  * for messages sent by a message producer. It can also specify delivery
  * mode, priority and time-to-live per message.
  *
  * <P>A client can specify a time-to-live value in milliseconds for each
  * message it sends. This value defines a message expiration time which
  * is the sum of the message's time-to-live and the GMT it is sent (for
  * transacted sends, this is the time the client sends the message, not
  * the time the transaction is committed).
  *
  * <P>A JMS provider should do its best to accurately expire messages;
  * however, JMS does not define the accuracy provided.
  *
  * @see         javax.jms.TopicPublisher
  * @see         javax.jms.QueueSender
  * @see         javax.jms.Session
  */

public class MessageProducerImpl implements MessageProducer {
    protected boolean isClosed = false;
    protected boolean disableMessageId = false;  //default in spec.
    protected boolean disableMessageTimestamp = false; //default in spec.
    protected int deliveryMode = DeliveryMode.PERSISTENT; //default in spec.
    protected int priority = 4; //default;
    protected long timeToLive = 0; // default

    protected Destination destination = null;
    protected SessionImpl session = null;

    protected MessageConvert messageConvert = null;

    //dest used when sending a message producer pkt to the broker.
    protected Destination addProducerDest = null;

    //XXX PROTOCOL3.5 --
    //Producer flow control.
    protected Hashtable destinations = new Hashtable();
    protected Hashtable producerStates = new Hashtable();
    
    //contains destName/xmlValidator
    //private static Hashtable validationTable = new Hashtable();
    
    private boolean debug = Debug.debug;

    private boolean _forJMSBridge = false;

    private static final Logger sessionLogger = SessionImpl.sessionLogger;
    
    public MessageProducerImpl (SessionImpl session, Destination destination)
        throws JMSException {
        try {
            this.session = session;
            this.destination = destination;

            if (destination != null) {
                session.getProtocolHandler().createMessageProducer(this);
            }

            session.addMessageProducer(this);

            if ( sessionLogger.isLoggable(Level.FINE) ) {
                this.logLifeCycle(ClientResources.I_PRODUCER_CREATED);
            }
            
            

        } catch (JMSException jmse) {
            ExceptionHandler.throwJMSException(jmse);
        }
    }

    /**
     * This method is called by ConnectionRecover during connection
     * failover or auto-reconnect.
     */
    public void recreateProducer() throws JMSException {
        if (destination != null) {
            // Create the producer again.
            session.getProtocolHandler().createMessageProducer(this);
        }
        else {
            // No need to physically recreate any producers! Instead
            // cleanup all the unbound producers created before. They
            // will be created again on demand...
            Enumeration enum2 = destinations.elements();
            while (enum2.hasMoreElements()) {
                ProducerState ps = (ProducerState) enum2.nextElement();
                session.connection.removeMessageProducer(
                    new Long(ps.getProducerID()));
            }

            destinations.clear();
            producerStates.clear();
        }
    }

    /**
    * Check if this is a JMQ message.  If this is, the same reference
    * is returned.  Otherwise, the message is converted to JMQ message
    * implementation and returned.
    */
    protected Message checkJMQMessage(Message message) throws JMSException {

        if (message instanceof MessageImpl){
            return message;
        }
        //this is a foreign message
        if (messageConvert == null){
            messageConvert = MessageConvert.getInstance();
        }
        return messageConvert.convertJMSMessage (message);
    }

    protected void checkState() throws JMSException {

        if (isClosed){
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_PRODUCER_CLOSED);
            JMSException jmse =
            new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_PRODUCER_CLOSED);

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    /**
    * Reset foreign message header after it is sent.
    */
    protected void
    resetForeignMessageHeader (Message message, Message foreignMessage) throws JMSException {
        if (!_forJMSBridge) {
        messageConvert.resetForeignMessageHeader( message, foreignMessage );
        }
    }

    protected void
    writeJMSMessage(Message message) throws JMSException {
        writeJMSMessage(destination, message);
    }

    protected void
    writeJMSMessage(Destination dest, Message message) throws JMSException {
        session.connection.checkReconnecting(null);

        checkFlowControl(dest, message);

        try {

            session.writeJMSMessage(message);

            if (sessionLogger.isLoggable(Level.FINER)) {

                //String pktType =
                //       PacketType.getString
                //       (((MessageImpl) message).getPacket().getPacketType());

                //String param = pktType + ", " + session.toString() + getDestInfo (dest);

                //sessionLogger.log(Level.FINEST,
                //                  ClientResources.I_PRODUCER_SENDING_MESSAGE,
                //                  param);
            	
            	this.logMessageProduced(dest, message);
            	
            }

        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_CAUGHT_EXCEPTION);
        }
    }
    
    private void logMessageProduced (Destination dest, Message message) throws JMSException {
    	
    	if (sessionLogger.isLoggable(Level.FINER)) {

            //String pktType =
            //       PacketType.getString
            //       (((MessageImpl) message).getPacket().getPacketType());

            //String param = pktType + ", " + session.toString() + getDestInfo (dest);

            //sessionLogger.log(Level.FINEST,
            //                  ClientResources.I_PRODUCER_SENDING_MESSAGE,
            //                  param);
        	
        	com.sun.messaging.Destination mqDest = ((com.sun.messaging.Destination) dest);
        	String domain = (mqDest.isQueue()? "Queue":"Topic");
        	
        	String pktType = PacketType.getString( ((MessageImpl) message).getPacket().getPacketType());
        	
        	ProducerState ps = (ProducerState) destinations.get(mqDest.getName());
            long pid = ps.getProducerID();
        	
        	String param = "MQTrace=MessageProducer" +
        					", ThreadID=" + Thread.currentThread().getId() +
        					", ClientID=" + session.connection.getClientID() +
        					", ConnectionID=" + session.connection.getConnectionID() +
        					", SessionID=" + session.getBrokerSessionID() + 
        					", ProducerID=" + pid +
        					", Destination=" + mqDest.getName() +
        					", Domain=" + domain +
        					", MessageID=" + message.getJMSMessageID() +
        					", MessageType=" + pktType;
        	
        	sessionLogger.log(Level.FINER, ClientResources.I_PRODUCER_SENDING_MESSAGE, param);
        	
        	if (sessionLogger.isLoggable(Level.FINEST)) {
        		param = "MQTrace=MessageProducer" +
        				", ProducerID=" + pid + 
        				", Message=" + message.toString();
        		
        		sessionLogger.log(Level.FINEST, ClientResources.I_PRODUCER_SENDING_MESSAGE, param);
        	}
        }

    }

    /** Gets the Destination associated with this <CODE>MessageProducer</CODE>.
    *
    * @return this sender's Destination
    *
    * @exception JMSException if the JMS provider fails to get the destination for
    *                         this <CODE>MessageProducer</CODE>
    *                         due to some internal error.
    * @since 1.1
    */
    public Destination
    getDestination() throws JMSException {
        checkState();
        return destination;
    }

    /** Set whether message IDs are disabled.
    *
    * <P>Since message ID's take some effort to create and increase a
    * message's size, some JMS providers may be able to optimize message
    * overhead if they are given a hint that message ID is not used by
    * an application. JMS message Producers provide a hint to disable
    * message ID. When a client sets a Producer to disable message ID
    * they are saying that they do not depend on the value of message
    * ID for the messages it produces. These messages must either have
    * message ID set to null or, if the hint is ignored, messageID must
    * be set to its normal unique value.
    *
    * <P>Message IDs are enabled by default.
    *
    * @param value indicates if message IDs are disabled.
    *
    * @exception JMSException if JMS fails to set disabled message
    *                         Id due to some internal error.
    */
    public void
    setDisableMessageID(boolean value) throws JMSException {

        checkState();

        disableMessageId = value;
    }


    /** Get an indication of whether message IDs are disabled.
    *
    * @return an indication of whether message IDs are disabled.
    *
    * @exception JMSException if JMS fails to get disabled message
    *                         Id due to some internal error.
    */
    public boolean
    getDisableMessageID() throws JMSException {
        checkState();
        return disableMessageId;
    }


    /** Set whether message timestamps are disabled.
    *
    * <P>Since timestamps take some effort to create and increase a
    * message's size, some JMS providers may be able to optimize message
    * overhead if they are given a hint that timestamp is not used by an
    * application. JMS message Producers provide a hint to disable
    * timestamps. When a client sets a producer to disable timestamps
    * they are saying that they do not depend on the value of timestamp
    * for the messages it produces. These messages must either have
    * timestamp set to null or, if the hint is ignored, timestamp must
    * be set to its normal value.
    *
    * <P>Message timestamps are enabled by default.
    *
    * @param value indicates if message timestamps are disabled.
    *
    * @exception JMSException if JMS fails to set disabled message
    *                         timestamp due to some internal error.
    */
    public void
    setDisableMessageTimestamp(boolean value) throws JMSException {
        checkState();

        disableMessageTimestamp = value;
    }


    /** Get an indication of whether message timestamps are disabled.
    *
    * @return an indication of whether message IDs are disabled.
    *
    * @exception JMSException if JMS fails to get disabled message
    *                         timestamp due to some internal error.
    */
    public boolean
    getDisableMessageTimestamp() throws JMSException {
        checkState();
        return disableMessageTimestamp;
    }


    /** Set the producer's default delivery mode.
    *
    * <P>Delivery mode is set to PERSISTENT by default.
    *
    * @param deliveryMode the message delivery mode for this message
    * producer. Legal values are <code>DeliveryMode.NON_PERSISTENT</code>
    * or <code>DeliveryMode.PERSISTENT</code>.
    *
    * @exception JMSException if JMS fails to set delivery mode
    *                         due to some internal error.
    *
    * @see javax.jms.MessageProducer#getDeliveryMode
    * @see javax.jms.DeliveryMode#NON_PERSISTENT
    * @see javax.jms.DeliveryMode#PERSISTENT
    * @see javax.jms.Message#DEFAULT_DELIVERY_MODE
    */
    public void
    setDeliveryMode(int deliveryMode) throws JMSException {

        checkState();

        if (deliveryMode != DeliveryMode.NON_PERSISTENT &&
             deliveryMode != DeliveryMode.PERSISTENT){

            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_DELIVERY_PARAM,
                                 "DeliveryMode", String.valueOf(deliveryMode));

            ExceptionHandler.throwJMSException
            (new JMSException(errorString, AdministeredObject.cr.X_INVALID_DELIVERY_PARAM));
        }

        this.deliveryMode = deliveryMode;
    }


    /** Get the producer's default delivery mode.
    *
    * @return the message delivery mode for this message producer.
    *
    * @exception JMSException if JMS fails to get delivery mode
    *                         due to some internal error.
    *
    * @see javax.jms.MessageProducer#setDeliveryMode
    */
    public int
    getDeliveryMode() throws JMSException {
        checkState();
        return deliveryMode;
    }


    /** Set the producer's default priority.
    *
    * <P>JMS defines a 10 level priority value with 0 as the lowest
    * and 9 as the highest. Clients should consider 0-4 as
    * gradients of normal priority and 5-9 as gradients of expedited
    * priority. Priority is set to 4, by default.
    *
    * @param priority the message priority for this message producer.
    *                 Priority must be a value between 0 and 9.
    *
    *
    * @exception JMSException if JMS fails to set priority
    *                         due to some internal error.
    *
    * @see javax.jms.MessageProducer#getPriority
    * @see javax.jms.Message#DEFAULT_PRIORITY
    */
    public void
    setPriority(int defaultPriority) throws JMSException {

        checkState();

        if ( defaultPriority < 0 || defaultPriority > 9 ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_DELIVERY_PARAM,
                                 "DeliveryPriority", String.valueOf(defaultPriority));
            ExceptionHandler.throwJMSException
            (new JMSException(errorString, AdministeredObject.cr.X_INVALID_DELIVERY_PARAM));
        }

        this.priority = defaultPriority;
    }

    /** Get the producer's default priority.
    *
    * @return the message priority for this message producer.
    *
    * @exception JMSException if JMS fails to get priority
    *                         due to some internal error.
    *
    * @see javax.jms.MessageProducer#setPriority
    */
    public int
    getPriority() throws JMSException {
        checkState();
        return priority;
    }


    /** Set the default length of time in milliseconds from its dispatch time
    * that a produced message should be retained by the message system.
    *
    * <P>Time to live is set to zero by default.
    *
    * @param timeToLive the message time to live in milliseconds; zero is
    * unlimited
    *
    * @exception JMSException if JMS fails to set Time to Live
    *                         due to some internal error.
    *
    * @see javax.jms.MessageProducer#getTimeToLive
    * @see javax.jms.Message#DEFAULT_TIME_TO_LIVE
    */
    public void
    setTimeToLive(long timeToLive) throws JMSException {

        checkState();

        if ( timeToLive < 0 ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_DELIVERY_PARAM,
                                 "TimeToLive", String.valueOf(timeToLive));
            ExceptionHandler.throwJMSException
            (new JMSException(errorString, AdministeredObject.cr.X_INVALID_DELIVERY_PARAM));
        }

        this.timeToLive = timeToLive;
    }


    /** Get the default length of time in milliseconds from its dispatch time
    * that a produced message should be retained by the message system.
    *
    * @return the message time to live in milliseconds; zero is unlimited
    *
    * @exception JMSException if JMS fails to get Time to Live
    *                         due to some internal error.
    *
    * @see javax.jms.MessageProducer#setTimeToLive
    */
    public long
    getTimeToLive() throws JMSException {
        checkState();
        return timeToLive;
    }


    /** Since a provider may allocate some resources on behalf of a
    * MessageProducer outside the JVM, clients should close them when they
    * are not needed. Relying on garbage collection to eventually reclaim
    * these resources may not be timely enough.
    *
    * @exception JMSException if JMS fails to close the producer
    *                         due to some error.
    */
    //XXX note currently producers are not cleaned up on session close
    //need to address this for 2.1
    public void
    close() throws JMSException {
        //XXX PROTOCOL3.5 --
        // Producer flow control. Raptor broker keeps track of
        // producers.
        try {
            
            Enumeration enum2 = producerStates.elements();
            while (enum2.hasMoreElements()) {
                ProducerState ps = (ProducerState) enum2.nextElement();

                if (session.connection.getBrokerProtocolLevel() >=
                     PacketType.VERSION350 && session.getProtocolHandler() != null) {

                    session.connection.removeMessageProducer(
                        new Long(ps.getProducerID()));

                    //delete only if connection is not broken
                    if (session.connection.isBroken() == false) {
                        session.getProtocolHandler().deleteMessageProducer(
                            ps.getProducerID());
                    }
                }
                ps.close();
            }

            session.removeMessageProducer(this);
            destinations.clear();
            producerStates.clear();
        } finally {
        	
        	isClosed = true;
        	
            //log producer closed
            if (session.sessionLogger.isLoggable(Level.FINE) ) {
                logLifeCycle(ClientResources.I_PRODUCER_CLOSED);
            }

        }
    }

    /** Sends a message using the <CODE>MessageProducer</CODE>'s
    * default delivery mode, priority, and time to live.
    *
    * @param message the message to send
    *
    * @exception JMSException if the JMS provider fails to send the message
    *                         due to some internal error.
    * @exception MessageFormatException if an invalid message is specified.
    * @exception InvalidDestinationException if a client uses
    *                         this method with a <CODE>MessageProducer</CODE> with
    *                         an invalid Destination.
    * @exception java.lang.UnsupportedOperationException if a client uses this
    *                         method with a <CODE>MessageProducer</CODE> that did
    *                         not specify a destination at creation time.
    *
    * @see javax.jms.Session#createProducer
    * @see javax.jms.MessageProducer#getDeliveryMode
    * @see javax.jms.MessageProducer#getTimeToLive
    * @see javax.jms.MessageProducer#getPriority
    * @since 1.1
    */
    public void
    send(Message message) throws JMSException {
        Message foreignMessage = null;
        checkState();
        if (destination == null) {
            throw new UnsupportedOperationException();
        }
        checkTemporaryDestination(destination);
        if ((message instanceof MessageImpl) == false ) {
            foreignMessage = message;
        }
        message = checkJMQMessage(message);
        message.setJMSDestination(destination);
        message.setJMSDeliveryMode(getDeliveryMode());
        message.setJMSPriority(getPriority());
        message.setJMSExpiration(getTimeToLive());
        if (session.connection.jmqOverrideJMSMsgHeaders){
            Destination d = message.getJMSDestination();
            if (session.connection.jmqOverrideMsgsToTempDests
                    || ((!(d instanceof TemporaryQueue))
                        && (!(d instanceof TemporaryTopic)))){
                if (session.connection.jmqOverrideJMSDeliveryMode){
                    message.setJMSDeliveryMode(session.connection.jmqJMSDeliveryMode);
                }
                if (session.connection.jmqOverrideJMSPriority){
                    message.setJMSPriority(session.connection.jmqJMSPriority);
                }
                if (session.connection.jmqOverrideJMSExpiration){
                    message.setJMSExpiration(session.connection.jmqJMSExpiration);
                }
            }
        }
        writeJMSMessage(message);
        if (foreignMessage != null) {
            resetForeignMessageHeader(message, foreignMessage);
        }
    }

    /** Sends a message to the Destination, specifying delivery mode, priority, and
    * time to live.
    *
    * @param message the message to send
    * @param deliveryMode the delivery mode to use
    * @param priority the priority for this message
    * @param timeToLive the message's lifetime (in milliseconds)
    *
    * @exception JMSException if the JMS provider fails to send the message
    *                         due to some internal error.
    * @exception MessageFormatException if an invalid message is specified.
    * @exception InvalidDestinationException if a client uses
    *                         this method with a <CODE>MessageProducer</CODE> with
    *                         an invalid Destination.
    * @exception java.lang.UnsupportedOperationException if a client uses this
    *                         method with a <CODE>MessageProducer</CODE> that did
    *                         not specify a destination at creation time.
    *
    * @see javax.jms.Session#createProducer
    * @since 1.1
    */
    public void
    send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        Message foreignMessage = null;
        checkState();
        if (destination == null){
            throw new UnsupportedOperationException();
        }
        checkTemporaryDestination(destination);
        if ((message instanceof MessageImpl) == false){
            foreignMessage = message;
        }
        message = checkJMQMessage(message);
        message.setJMSDestination(destination);
        message.setJMSDeliveryMode(deliveryMode);
        message.setJMSPriority(priority);
        message.setJMSExpiration(timeToLive);
        if (session.connection.jmqOverrideJMSMsgHeaders){
            Destination d = message.getJMSDestination();
            if (session.connection.jmqOverrideMsgsToTempDests
                    || ((!(d instanceof TemporaryQueue))
                        && (!(d instanceof TemporaryTopic)))){
                if (session.connection.jmqOverrideJMSDeliveryMode){
                    message.setJMSDeliveryMode(session.connection.jmqJMSDeliveryMode);
                }
                if (session.connection.jmqOverrideJMSPriority){
                    message.setJMSPriority(session.connection.jmqJMSPriority);
                }
                if (session.connection.jmqOverrideJMSExpiration){
                    message.setJMSExpiration(session.connection.jmqJMSExpiration);
                }
            }
        }
        writeJMSMessage(message);
        if (foreignMessage != null){
            resetForeignMessageHeader (message, foreignMessage);
        }
    }


    /**Sends a message to a Destination for an unidentified message producer.
    * Uses the <CODE>MessageProducer</CODE>'s default delivery mode, priority,
    * and time to live.
    *
    * <P>Typically, a message producer is assigned a destination at creation
    * time; however, the JMS API also supports unidentified message producers,
    * which require that the Destination be supplied every time a message is
    * sent.
    *
    * @param destination the destination to send this message to
    * @param message the message to send
    *
    * @exception JMSException if the JMS provider fails to send the message
    *                         due to some internal error.
    * @exception MessageFormatException if an invalid message is specified.
    * @exception InvalidDestinationException if a client uses
    *                         this method with an invalid Destination.
    * @exception java.lang.UnsupportedOperationException if a client uses this
    *                         method with a <CODE>MessageProducer</CODE> that did
    *                         specify a destination at creation time.
    *
    * @see javax.jms.Session#createProducer
    * @see javax.jms.MessageProducer#getDeliveryMode
    * @see javax.jms.MessageProducer#getTimeToLive
    * @see javax.jms.MessageProducer#getPriority
    * @since 1.1
    */
    public void send(Destination destination, Message message) throws JMSException {
        Message foreignMessage = null;
        checkState();
        if (destination == null) {
            String errorString =
                AdministeredObject.cr.getKString(AdministeredObject.cr.X_DESTINATION_NOTFOUND, "null");

            ExceptionHandler.throwJMSException (
            new InvalidDestinationException(errorString,
                AdministeredObject.cr.X_DESTINATION_NOTFOUND));
        }

        if (this.destination != null) {
            throw new UnsupportedOperationException();
        }
        checkTemporaryDestination(destination);
        if ((message instanceof MessageImpl) == false){
            foreignMessage = message;
        }
        checkForUnspecifiedProducer(destination);
        message = checkJMQMessage(message);
        message.setJMSDestination(destination);
        message.setJMSDeliveryMode(getDeliveryMode());
        message.setJMSPriority(getPriority());
        message.setJMSExpiration(getTimeToLive());
        if (session.connection.jmqOverrideJMSMsgHeaders){
            Destination d = message.getJMSDestination();
            if (session.connection.jmqOverrideMsgsToTempDests
                    || ((!(d instanceof TemporaryQueue))
                        && (!(d instanceof TemporaryTopic)))){
                if (session.connection.jmqOverrideJMSDeliveryMode){
                    message.setJMSDeliveryMode(session.connection.jmqJMSDeliveryMode);
                }
                if (session.connection.jmqOverrideJMSPriority){
                    message.setJMSPriority(session.connection.jmqJMSPriority);
                }
                if (session.connection.jmqOverrideJMSExpiration){
                    message.setJMSExpiration(session.connection.jmqJMSExpiration);
                }
            }
        }
        writeJMSMessage(destination, message);
        if (foreignMessage != null){
            resetForeignMessageHeader (message, foreignMessage);
        }
    }

    /** Sends a message to a Destination for an unidentified message producer,
    * specifying delivery mode, priority and time to live.
    *
    * <P>Typically, a message producer is assigned a destination at creation
    * time; however, the JMS API also supports unidentified message producers,
    * which require that the Destination be supplied every time a message is
    * sent.
    *
    * @param destination the destination to send this message to
    * @param message the message to send
    * @param deliveryMode the delivery mode to use
    * @param priority the priority for this message
    * @param timeToLive the message's lifetime (in milliseconds)
    *
    * @exception JMSException if the JMS provider fails to send the message
    *                         due to some internal error.
    * @exception MessageFormatException if an invalid message is specified.
    * @exception InvalidDestinationException if a client uses
    *                         this method with an invalid Destination.
    * @exception java.lang.UnsupportedOperationException if a client uses this
    *                         method with a <CODE>MessageConsumer</CODE> that
    *                         specified a destination at creation time.
    *
    * @see javax.jms.Session#createProducer
    * @since 1.1
    */
    public void
    send(Destination destination, Message message, int deliveryMode,
                 int priority, long timeToLive) throws JMSException {
        Message foreignMessage = null;
        checkState();
        if (destination == null) {

            String errorString =
                AdministeredObject.cr.getKString(AdministeredObject.cr.X_DESTINATION_NOTFOUND, "null");

            ExceptionHandler.throwJMSException (
            new InvalidDestinationException(errorString,
                AdministeredObject.cr.X_DESTINATION_NOTFOUND));
        }

        if (this.destination != null) {
            throw new UnsupportedOperationException();
        }
        checkTemporaryDestination(destination);
        if ((message instanceof MessageImpl) == false){
            foreignMessage = message;
        }
        checkForUnspecifiedProducer(destination);
        message = checkJMQMessage(message);
        message.setJMSDestination(destination);
        message.setJMSDeliveryMode(deliveryMode);
        message.setJMSPriority(priority);
        message.setJMSExpiration(timeToLive);
        if (session.connection.jmqOverrideJMSMsgHeaders){
            Destination d = message.getJMSDestination();
            if (session.connection.jmqOverrideMsgsToTempDests || (!(d instanceof TemporaryQueue))) {
                if (session.connection.jmqOverrideJMSDeliveryMode){
                    message.setJMSDeliveryMode(session.connection.jmqJMSDeliveryMode);
                }
                if (session.connection.jmqOverrideJMSPriority){
                    message.setJMSPriority(session.connection.jmqJMSPriority);
                }
                if (session.connection.jmqOverrideJMSExpiration){
                    message.setJMSExpiration(session.connection.jmqJMSExpiration);
                }
            }
        }
        writeJMSMessage(destination, message);
        if (foreignMessage != null){
            resetForeignMessageHeader (message, foreignMessage);
        }
    }

    public void _setForJMSBridge() {
        _forJMSBridge = true;
    }


    /*
    protected long getProducerID() {
        return producerID;
    }
    */

    protected void setProducerID(Destination dest, long producerID) {
        String dn = ((com.sun.messaging.Destination) dest).getName();
        ProducerState ps = (ProducerState) destinations.get(dn);

        if (ps == null) {
            ps = new ProducerState();
            destinations.put(dn, ps);
        }
        else {
            if (debug) {
                Debug.println("Replacing ProducerID. old = " +
                    ps.getProducerID() + ", new = " + producerID);
            }
            producerStates.remove(new Long(ps.getProducerID()));
            session.connection.removeMessageProducer(
                new Long(ps.getProducerID()));
        }

        ps.setProducerID(producerID);
        producerStates.put(new Long(producerID), ps);
        session.connection.addMessageProducer(new Long(producerID), this);
    }

    protected void setFlowLimit(long producerID, int flowLimit) {
        if (debug) {
            Debug.println("Setting flowLimit = " + flowLimit +
                " for producerID = " + producerID);
        }
        ProducerState ps = (ProducerState) producerStates.get(
            new Long(producerID));
        ps.setFlowLimit(flowLimit);
    }

    protected void setFlowBytesLimit(long producerID, long flowBytesLimit) {
        if (debug) {
            Debug.println("Setting flowBytesLimit = " + flowBytesLimit +
                " for producerID = " + producerID);
        }
        ProducerState ps = (ProducerState) producerStates.get(
            new Long(producerID));
        ps.setFlowBytesLimit(flowBytesLimit);
    }

    private void checkFlowControl(Destination dest, Message message) throws JMSException {
        String dn = ((com.sun.messaging.Destination) dest).getName();
        ProducerState ps = (ProducerState) destinations.get(dn);
        if (ps != null)
            ps.checkFlowControl(message);
    }

    protected SessionImpl getSession() {
        return session;
    }

    public String toString() {
        //return "MessageProducer: " + session.getConnection().toString();
        String destName = null;
        long pid = -1;

        try {
            if ( destination != null ) {
                destName = ((com.sun.messaging.Destination) destination).
                           getName();

                ProducerState ps = (ProducerState) destinations.get(destName);
                pid = ps.getProducerID();
            }

        } catch (Exception e) {
            //if we catch exception, pid would be -1
            if ( debug) {
                Debug.printStackTrace(e);
            }
        }

        return session.toString() +
               ", ProducerID=" + pid + ", DestName=" + destName;
    }

    protected void checkForUnspecifiedProducer(Destination dest)
        throws JMSException {
        //XXX: Should not be using d.getName();
        com.sun.messaging.Destination d =
            (com.sun.messaging.Destination) dest;
        String dn = d.getName();
        if (destinations.get(dn) == null) {
            session.getProtocolHandler().createMessageProducer(this, d);
        }
    }

    protected void checkTemporaryDestination(Destination dest) throws JMSException {
		if (dest instanceof TemporaryDestination) {
			// check whether the temporary destination has been deleted
			if (((TemporaryDestination) dest).isDeleted()) {
				String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_TEMP_DESTINATION_DELETED,
						((TemporaryDestination) dest).getName());
				ExceptionHandler.throwJMSException(new InvalidDestinationException(errorString,
						AdministeredObject.cr.X_TEMP_DESTINATION_DELETED));
			}

			// check whether the owning connection is closed
			ConnectionImpl connection = ((TemporaryDestination) dest).connection;
			if (connection != null) { // local temp destination.
				if (connection.isClosed) {
					String errorString = AdministeredObject.cr.getKString(
							AdministeredObject.cr.X_TEMP_DESTINATION_INVALID, ((TemporaryDestination) dest).getName());
					ExceptionHandler.throwJMSException(new InvalidDestinationException(errorString,
							AdministeredObject.cr.X_TEMP_DESTINATION_INVALID));
				}
			}
		}
	}

    public void logLifeCycle (String key) {

        if ( sessionLogger.isLoggable(Level.FINE) ) {
            sessionLogger.log(Level.FINE, key, this);
        }

    }

    private String getDestInfo (Destination destination) {
        long pid = -1;
        String info = null;

        try {
            if (destination != null) {
                String destName = ((com.sun.messaging.Destination) destination).
                           getName();

                ProducerState ps = (ProducerState) destinations.get(destName);
                pid = ps.getProducerID();

                info = ", destName=" + destName + ", producerID=" + pid;
            }

        } catch (Exception e) {
            //if we catch exception, pid would be -1
            if (debug) {
                Debug.printStackTrace(e);
            }
        }

        return info;
    }


    class ProducerState {
        private long flowBytesLimit;
        private int flowLimit;
        private long producerID;
        private boolean blocked = false;
        private boolean _psclosed = false;

        private int TEST_minResume = Integer.MAX_VALUE;
        private int TEST_maxResume = -1;
        private int TEST_resumeCount = 0;
        private int TEST_pauseCount = 0;
        private int TEST_forcePauseCount = 0;

        protected long getFlowBytesLimit() {
            return flowBytesLimit;
        }

        protected synchronized void setFlowBytesLimit(long flowBytesLimit) {
            this.flowBytesLimit = flowBytesLimit;
        }

        protected int getFlowLimit() {
            return flowLimit;
        }

        protected synchronized void setFlowLimit(int flowLimit) {
            this.flowLimit = flowLimit;

            if (flowLimit < TEST_minResume)
                TEST_minResume = flowLimit;
            if (flowLimit > TEST_maxResume)
                TEST_maxResume = flowLimit;

            if (flowLimit != 0)
                TEST_resumeCount++;
            else
                TEST_forcePauseCount++;

            notifyAll();
        }

        protected long getProducerID() {
            return producerID;
        }

        protected void setProducerID(long producerID) {
            this.producerID = producerID;
        }

        protected synchronized void close() {
            _psclosed = true;
            notifyAll();
        }

        private synchronized void checkFlowControl(Message message) throws JMSException {
            while (flowLimit == 0 && ! isClosed && !_psclosed) {
                try {
                    blocked = true;
                    wait();
                    blocked = false;
                }
                catch (InterruptedException e) {}
            }
            if (isClosed || _psclosed) {
                String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_PRODUCER_CLOSED);
                JMSException jmse =
                new javax.jms.JMSException(errorString, AdministeredObject.cr.X_PRODUCER_CLOSED);
                ExceptionHandler.throwJMSException(jmse);
            }

            if (flowLimit > 0)
                flowLimit--;

            if (flowLimit == 0) {
                TEST_pauseCount++;
            }

            ReadWritePacket pkt = ((MessageImpl) message).getPacket();
            pkt.setProducerID(producerID);
            pkt.setConsumerFlow((flowLimit == 0));
        }

        protected Hashtable getDebugState(boolean verbose) {
            Hashtable ht = new Hashtable();

            ht.put("producerID", String.valueOf(producerID));
            ht.put("flowLimit", String.valueOf(flowLimit));
            ht.put("flowBytesLimit", String.valueOf(flowBytesLimit));
            ht.put("blocked", String.valueOf(blocked));

            ht.put("pauseCount", String.valueOf(TEST_pauseCount));
            ht.put("resumeCount", String.valueOf(TEST_resumeCount));
            ht.put("maxResume", String.valueOf(TEST_maxResume));
            ht.put("minResume", String.valueOf(TEST_minResume));
            ht.put("forcedPauses", String.valueOf(TEST_forcePauseCount));

            return ht;
        }
    }

    protected Hashtable getDebugState(boolean verbose) {
        Hashtable ht = new Hashtable();

        ht.put("isClosed", String.valueOf(isClosed));
        ht.put("deliveryMode", String.valueOf(deliveryMode));
        ht.put("priority", String.valueOf(priority));
        ht.put("timeToLive", String.valueOf(timeToLive));

        ht.put("disableMessageId", String.valueOf(disableMessageId));
        ht.put("disableTimestamp",
            String.valueOf(disableMessageTimestamp));

        if (destination != null) {
            ht.put("Destination Class", destination.getClass().getName());
            if (destination instanceof com.sun.messaging.Destination) {
                ht.put("Destination", ((com.sun.messaging.Destination)
                    destination).getName());
            }

            Enumeration enum2 = producerStates.elements();
            while (enum2.hasMoreElements()) {
                ProducerState ps = (ProducerState) enum2.nextElement();
                ht.putAll(ps.getDebugState(verbose));
            }
        }
        else {
            // Unbound producer...
            ht.put("isBound", "false");
            ht.put("# Destinations", String.valueOf(destinations.size()));

            int n = 0;
            Enumeration enum2 = destinations.keys();
            while (enum2.hasMoreElements()) {
                String dn = (String) enum2.nextElement();
                ProducerState ps = (ProducerState) destinations.get(dn);
                Hashtable tmp = ps.getDebugState(verbose);
                tmp.put("Destination", dn);

                ht.put("ProducerState[" + n + "]", tmp);
                n++;
            }
        }

        return ht;
    }
    
}
