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
 * @(#)XAResourceUnsupportedImpl.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import javax.transaction.xa.*;


/**
  * Implementation class for XAResource which is used when distributed
  * transactions are unsupported - i.e. XAResourceUnsupportedImpl. Every
  * XAResource interface method in this class throws an XAException of
  * type XAER_RMFAIL
  *
  *
  * <p>The XAResource interface is a Java mapping of the industry standard
  * XA interface based on the X/Open CAE Specification (Distributed
  * Transaction Processing: The XA Specification).
  *
  * <p>The XA interface defines the contract between a Resource Manager
  * and a Transaction Manager in a distributed transaction processing
  * (DTP) environment. A JDBC driver or a JMS provider implements
  * this interface to support the association between a global transaction
  * and a database or message service connection.
  *
  * <p>The XAResource interface can be supported by any transactional
  * resource that is intended to be used by application programs in an
  * environment where transactions are controlled by an external
  * transaction manager. An example of such a resource is a database
  * management system. An application may access data through multiple
  * database connections. Each database connection is enlisted with
  * the transaction manager as a transactional resource. The transaction
  * manager obtains an XAResource for each connection participating
  * in a global transaction. The transaction manager uses the
  * <code>start</code> method
  * to associate the global transaction with the resource, and it uses the
  * <code>end</code> method to disassociate the transaction from
  * the resource. The resource
  * manager is responsible for associating the global transaction to all
  * work performed on its data between the start and end method invocations.
  *
  * <p>At transaction commit time, the resource managers are informed by
  * the transaction manager to prepare, commit, or rollback a transaction
  * according to the two-phase commit protocol.</p>
  *
  * @see javax.transaction.xa.XAResource
  */

public class XAResourceUnsupportedImpl implements XAResource {


    public XAResourceUnsupportedImpl() {
    }

    /**
     * Commits the global transaction specified by xid.
     *
     * @param foreignXid A global transaction identifier
     *
     * @param onePhase If true, the resource manager should use a one-phase
     * commit protocol to commit the work done on behalf of xid.
     *
     * @exception XAException An error has occurred. Possible XAExceptions
     * are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB, XA_HEURMIX, XAER_RMERR,
     * XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     * <P>If the resource manager did not commit the transaction and the
     *  parameter onePhase is set to true, the resource manager may throw
     *  one of the XA_RB* exceptions. Upon return, the resource manager has
     *  rolled back the branch's work and has released all held resources.
     */
    public void commit(Xid foreignXid, boolean onePhase) throws XAException {
         throw new XAException(XAException.XAER_RMFAIL);
    }


    /**
     * Ends the work performed on behalf of a transaction branch.
     * The resource manager disassociates the XA resource from the
     * transaction branch specified and lets the transaction
     * complete.
     *
     * <p>If TMSUSPEND is specified in the flags, the transaction branch
     * is temporarily suspended in an incomplete state. The transaction
     * context is in a suspended state and must be resumed via the
     * <code>start</code> method with TMRESUME specified.</p>
     *
     * <p>If TMFAIL is specified, the portion of work has failed.
     * The resource manager may mark the transaction as rollback-only</p>
     *
     * <p>If TMSUCCESS is specified, the portion of work has completed
     * successfully.</p>
     *
     * @param foreignXid A global transaction identifier that is the same as
     * the identifier used previously in the <code>start</code> method.
     *
     * @param flags One of TMSUCCESS, TMFAIL, or TMSUSPEND.
     *
     * @exception XAException An error has occurred. Possible XAException
     * values are XAER_RMERR, XAER_RMFAILED, XAER_NOTA, XAER_INVAL,
     * XAER_PROTO, or XA_RB*.
     */
    public void end(Xid foreignXid, int flags) throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }

    /**
     * Tells the resource manager to forget about a heuristically
     * completed transaction branch.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or
     * XAER_PROTO.
     */
    public void forget(Xid foreignXid) throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }

    /**
     * Obtains the current transaction timeout value set for this
     * XAResource instance. If <CODE>XAResource.setTransactionTimeout</CODE>
     * was not used prior to invoking this method, the return value
     * is the default timeout set for the resource manager; otherwise,
     * the value used in the previous <CODE>setTransactionTimeout</CODE>
     * call is returned.
     *
     * @return the transaction timeout value in seconds.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR and XAER_RMFAIL.
     */
    public int getTransactionTimeout() throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }

    /**
     * This method is called to determine if the resource manager
     * instance represented by the target object is the same as the
     * resouce manager instance represented by the parameter <i>xares</i>.
     *
     * @param foreignXaRes An XAResource object whose resource manager instance
     *      is to be compared with the resource manager instance of the
     *      target object.
     *
     * @return <i>true</i> if it's the same RM instance; otherwise
     *       <i>false</i>.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR and XAER_RMFAIL.
     *
     */
    public boolean isSameRM(XAResource foreignXaRes) throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }


    /**
     * Ask the resource manager to prepare for a transaction commit
     * of the transaction specified in xid.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are: XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL,
     * or XAER_PROTO.
     *
     * @return A value indicating the resource manager's vote on the
     * outcome of the transaction. The possible values are: XA_RDONLY
     * or XA_OK. If the resource manager wants to roll back the
     * transaction, it should do so by raising an appropriate XAException
     * in the prepare method.
     */
    public int prepare(Xid foreignXid) throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }

    /**
     * Obtains a list of prepared transaction branches from a resource
     * manager. The transaction manager calls this method during recovery
     * to obtain the list of transaction branches that are currently in
     * prepared or heuristically completed states.
     *
     * @param flags One of TMSTARTRSCAN, TMENDRSCAN, TMNOFLAGS. TMNOFLAGS
     * must be used when no other flags are set in the parameter.
     *
     * @exception XAException An error has occurred. Possible values are
     * XAER_RMERR, XAER_RMFAIL, XAER_INVAL, and XAER_PROTO.
     *
     * @return The resource manager returns zero or more XIDs of the
     * transaction branches that are currently in a prepared or
     * heuristically completed state. If an error occurs during the
     * operation, the resource manager should throw the appropriate
     * XAException.
     *
     */
    public Xid[] recover(int flags) throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }

    /**
     * Informs the resource manager to roll back work done on behalf
     * of a transaction branch.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred.
     */
    public void rollback(Xid foreignXid) throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }


    /**
     * <P>Sets the current transaction timeout value for this <CODE>XAResource</CODE>
     * instance. Once set, this timeout value is effective until
     * <code>setTransactionTimeout</code> is invoked again with a different
     * value. To reset the timeout value to the default value used by the resource
     * manager, set the value to zero.
     *
     * If the timeout operation is performed successfully, the method returns
     * <i>true</i>; otherwise <i>false</i>. If a resource manager does not
     * support explicitly setting the transaction timeout value, this method
     * returns <i>false</i>.
     *
     * @param transactionTimeout The transaction timeout value in seconds.
     *
     * @return <i>true</i> if the transaction timeout value is set successfully;
     *       otherwise <i>false</i>.
     *
     * @exception XAException An error has occurred. Possible exception values
     * are XAER_RMERR, XAER_RMFAIL, or XAER_INVAL.
     */
    public boolean setTransactionTimeout(int transactionTimeout) throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }

    /**
     * Starts work on behalf of a transaction branch specified in
     * <code>foreignXid</code>.
     *
     * If TMJOIN is specified, the start applies to joining a transaction
     * previously seen by the resource manager. If TMRESUME is specified,
     * the start applies to resuming a suspended transaction specified in the
     * parameter <code>foreignXid</code>.
     *
     * If neither TMJOIN nor TMRESUME is specified and the transaction
     * specified by <code>foreignXid</code> has previously been seen by the resource
     * manager, the resource manager throws the XAException exception with
     * XAER_DUPID error code.
     *
     * @param foreignXid A global transaction identifier to be associated
     * with the resource.
     *
     * @param flags One of TMNOFLAGS, TMJOIN, or TMRESUME.
     *
     * @exception XAException An error has occurred. Possible exceptions
     * are XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_DUPID, XAER_OUTSIDE,
     * XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     */
    public void start(Xid foreignXid, int flags) throws XAException {
        throw new XAException(XAException.XAER_RMFAIL);
    }

}
