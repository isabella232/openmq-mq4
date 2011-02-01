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
 * @(#)MetricCounters.java	1.11 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.io.Serializable;

/**
 * Class for performing packet counting
 */

public class MetricCounters implements Cloneable, Serializable
{
    // We use two locks because counters are typically incremented by
    // seperate input and output threads.
    transient private Object inLock;
    transient private Object outLock;

    // Number of JMS messages in and out
    public long    messagesIn = 0;
    public long    messagesOut = 0;

    // Number of JMS message bytes in and out
    public long    messageBytesIn = 0;
    public long    messageBytesOut = 0;

    // Number of packets (control and JMS) in and out
    public long    packetsIn = 0;
    public long    packetsOut = 0;

    // Number of packet bytes (control and JMS) in and out
    public long    packetBytesIn = 0;
    public long    packetBytesOut = 0;

    // JVM memory usage
    public long     totalMemory = 0;
    public long     freeMemory = 0;

    // Thread metrics
    public int      threadsActive = 0;
    public int      threadsHighWater = 0;
    public int      threadsLowWater = 0;

    // Timestamp of when counters were last updated. May be 0 if whomever
    // is doing the counting does not want to incur the cost of generating
    // the timestamp
    public long    timeStamp = 0;

    // Number of connections this data represents
    public int     nConnections = 1;

    public MetricCounters() {
        inLock = new Object();
        outLock = new Object();
        reset();
    }

    /**
     * Reset counters to 0
     */
    public void reset() {

        synchronized (inLock) {
            messagesIn = messageBytesIn = 0;
            packetsIn =  packetBytesIn  = 0;
        }

        synchronized (outLock) {
            messagesOut =  messageBytesOut = 0;
            packetsOut =  packetBytesOut = 0;
        }
    }

    /**
     * Updated input counters
     */
    public synchronized void updateIn(long messagesIn, long messageBytesIn,
                               long packetsIn,  long packetBytesIn) {

        synchronized (inLock) {
            this.messagesIn += messagesIn;
            this.messageBytesIn += messageBytesIn;
            this.packetsIn += packetsIn;
            this.packetBytesIn += packetBytesIn;
        }
    }

    /**
     * Update output counters
     */
    public synchronized void updateOut(long messagesOut, long messageBytesOut,
                                long packetsOut,  long packetBytesOut) {

        synchronized (outLock) {
            this.messagesOut += messagesOut;
            this.messageBytesOut += messageBytesOut;
            this.packetsOut += packetsOut;
            this.packetBytesOut += packetBytesOut;
        }
    }

    /**
     * Update counters using values from another MetricCounters
     */
    public synchronized void update(MetricCounters counter) {

        synchronized (inLock) {
            this.messagesIn += counter.messagesIn;
            this.messageBytesIn += counter.messageBytesIn;
            this.packetsIn += counter.packetsIn;
            this.packetBytesIn += counter.packetBytesIn;
        }

        synchronized (outLock) {
            this.messagesOut += counter.messagesOut;
            this.messageBytesOut += counter.messageBytesOut;
            this.packetsOut += counter.packetsOut;
            this.packetBytesOut += counter.packetBytesOut;

            this.threadsActive    = counter.threadsActive;
            this.threadsHighWater = counter.threadsHighWater;
            this.threadsLowWater  = counter.threadsLowWater;
        }
    }

    public String toString() {
        synchronized (outLock) {
            synchronized (inLock) {
                return 
        " In: " + messagesIn + " messages(" + messageBytesIn + " bytes)\t" +
                  packetsIn  + " packets(" + packetBytesIn  + " bytes)\n" +
        "Out: " + messagesOut + " messages(" + messageBytesOut + " bytes)\t" +
                  packetsOut  + " packets(" + packetBytesOut  + " bytes)\n";
            }
        }
    }

    public Object clone() {

	// Bug id 6359793
	// 9 Oct 2006
	// Tom Ross

	// old line
	//MetricCounters counter = new MetricCounters();
	//
	// new lines

	MetricCounters counter = null;

	try {
		counter = (MetricCounters) super.clone();
	} catch ( CloneNotSupportedException e ){
		System.out.println("Class MetricCounters could not be cloned.");
		return null;
	}
	// do deep clone
	counter.inLock = new Object();
	counter.outLock = new Object();

	// do just shallow clone
        synchronized (inLock) {
            counter.messagesIn = this.messagesIn;
            counter.messageBytesIn = this.messageBytesIn;
            counter.packetsIn = this.packetsIn;
            counter.packetBytesIn = this.packetBytesIn;
        }

        synchronized (outLock) {
            counter.messagesOut = this.messagesOut;
            counter.messageBytesOut = this.messageBytesOut;
            counter.packetsOut = this.packetsOut;
            counter.packetBytesOut = this.packetBytesOut;
        }

	return counter;
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException
    {
        s.defaultReadObject();

        // Instantiate transient locks
        inLock = new Object();
        outLock = new Object();
    }
}
