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

package com.sun.messaging.jmq.jmsserver.persist.jdbc.ext;

import java.util.*;
import java.sql.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.StoreBeingTakenOverException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.DBManager;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.BaseDAOImpl;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.Util;
import com.sun.messaging.bridge.service.DupKeyException;
import com.sun.messaging.bridge.service.KeyNotFoundException;


/**
 * @author amyk
 */
public class JMSBGDAOImpl extends BaseDAOImpl implements JMSBGDAO {

    private String tableName = null;

    protected String insertSQL;
    protected String updateBrokerIdSQL;
    protected String deleteSQL;
    protected String selectBrokerIdSQL;
    protected String selectUpdatedTimeSQL;
    protected String selectCreatedTimeSQL;
    protected String selectNamesByBrokerIdSQL;

    /**
     */
    public JMSBGDAOImpl() throws BrokerException {

        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName(TABLE_NAME_PREFIX);

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( NAME_COLUMN ).append( ", " )
            .append( BROKER_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN ).append( ", " )
            .append( UPDATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?)" )
            .toString();

        updateBrokerIdSQL = new StringBuffer(128)
            .append( "UPDATE " ).append( tableName )
            .append( " SET " )
            .append( BROKER_ID_COLUMN ).append( " = ?, " )
			.append( UPDATED_TS_COLUMN ).append( " = ? " )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
			.append( " AND " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .append( Util.brokerNotTakenOverClause(dbMgr) )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
			.append( " AND " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .toString();

        selectBrokerIdSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( BROKER_ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
            .toString();

        selectUpdatedTimeSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( UPDATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
            .toString();

        selectCreatedTimeSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CREATED_TS_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( NAME_COLUMN ).append( " = ?" )
            .toString();

        selectNamesByBrokerIdSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( NAME_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( BROKER_ID_COLUMN ).append( " = ?" )
            .toString();
    }

    /**
     */
    public final String getTableNamePrefix() {
        return TABLE_NAME_PREFIX;
    }

    /**
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * @param conn database connection
     * @param name to identify the TM 
     * @param logger_ can be null
     * @throws DupKeyException if already exist
     *         else Exception on error
     */
    public void insert(Connection conn,
                       String name,
                       java.util.logging.Logger logger_)
                       throws DupKeyException, Exception {

        Connection myconn = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            try {
                pstmt = conn.prepareStatement(insertSQL);
                pstmt.setString(1, name);
                pstmt.setString(2, dbMgr.getBrokerID());
                pstmt.setLong(3, System.currentTimeMillis());
                pstmt.setLong(4, 0L);
                pstmt.executeUpdate();
            } catch (Exception e) {
                myex = e;
                try {
                    if ((conn != null) && !conn.getAutoCommit()) {
                        conn.rollback();
                    }
                } catch (SQLException e1) {
                    String emsg = BrokerResources.X_DB_ROLLBACK_FAILED;
                    logger.log(Logger.ERROR, emsg, e1);
                    Util.logExt(logger_, java.util.logging.Level.SEVERE, emsg, e1);
                }

                checkDupKeyOnException(conn, name, logger_);

                throw e;
            }
        } catch (Exception e) {
            myex = e;
            throw e;
        } finally {
            closeSQL(null, pstmt, myconn, myex, logger_);
        }
    }

    /**
     * @param conn database connection
     * @param name jmsbridge name 
     * @param newBrokerId
     * @param expectedBrokerId
     * @param logger_ can be null
     * @throws KeyNotFoundException if not found
     *         StoreBeingTakenOverException if being takeover 
     *         Exception on any other error
     */
    public void updateBrokerId(Connection conn,
                               String name, 
                               String newBrokerId,
                               String expectedBrokerId,
                               java.util.logging.Logger logger_)
                               throws KeyNotFoundException,
                               StoreBeingTakenOverException,
							   Exception {

        Connection myconn = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            pstmt = conn.prepareStatement(updateBrokerIdSQL);
            pstmt.setString(1, newBrokerId);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, name);
            pstmt.setString(4, expectedBrokerId);
            if (Globals.getHAEnabled()) {
                pstmt.setString(5, dbMgr.getBrokerID());
            }

            if (pstmt.executeUpdate() == 0) {
                Util.checkBeingTakenOver(conn, dbMgr, logger, logger_);
                throw new KeyNotFoundException(
                "Name "+name+" not found in store");
            }

        } catch ( Exception e ) {
            myex = e;
            try {
                if ((conn != null) && !conn.getAutoCommit()) {
                   conn.rollback();
                }
            } catch (SQLException e1) {
                String emsg = BrokerResources.X_DB_ROLLBACK_FAILED;
                logger.log( Logger.ERROR, emsg, e1);
                Util.logExt(logger_, java.util.logging.Level.SEVERE, emsg, e1);
            }

            throw e;
        } finally {
            closeSQL(null, pstmt, myconn, myex, logger_);
        }
    }

    /**
     * @param conn database connection
     * @param name to identify the TM
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public void delete(Connection conn,
                       String name,
                       java.util.logging.Logger logger_)
                       throws Exception {

        Connection myconn = null;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = dbMgr.getConnection(true);
                myconn = conn;
            }

            pstmt = conn.prepareStatement(deleteSQL);
            pstmt.setString(1, name);
            pstmt.setString(2, dbMgr.getBrokerID());
            if (pstmt.executeUpdate() == 0) {
                throw new KeyNotFoundException("Name "+name+" not found in store ");
            } 
        } catch (Exception e) {
            myex = e;
            try {
                if ((conn != null) && !conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (SQLException e1) {
                String emsg = BrokerResources.X_DB_ROLLBACK_FAILED;
                logger.log(Logger.ERROR, emsg, e1);
                Util.logExt(logger_, java.util.logging.Level.SEVERE, emsg, e1);
            }

            throw e;

        } finally {
            closeSQL(null,pstmt, myconn, myex, logger_);
        }
    }

    /**
     * Delete all entries for this broker
     * @param conn database connection
     * @throws BrokerException
     */
    public void deleteAll(Connection conn) throws BrokerException {

        DBManager dbMgr = DBManager.getDBManager();

        String whereClause = new StringBuffer(128)
               .append( BROKER_ID_COLUMN ).append( " = '" )
               .append( dbMgr.getBrokerID() ).append( "'" )
               .toString();
 
        deleteAll(conn, whereClause, null, 0);
    }


    /**
     * @param conn database connection
     * @param name jmsbridge name 
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public String getBrokerId(Connection conn,
                              String name,
                              java.util.logging.Logger logger_)
                              throws Exception {
        String brokerId = null;

        Connection myconn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            if (conn == null) {
                conn = DBManager.getDBManager().getConnection(true);
                myconn = conn;
            }

            pstmt = conn.prepareStatement(selectBrokerIdSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new KeyNotFoundException(
                "Name "+name+" not found in store");
            }
            brokerId = rs.getString(1);
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectBrokerIdSQL+"]", rbe );
            }
            throw e;

        } finally {
            closeSQL(rs, pstmt, myconn, myex, logger_);
        }

        return brokerId;
    }

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getUpdatedTime(Connection conn, String name,
                               java.util.logging.Logger logger_)
                               throws KeyNotFoundException, Exception {

        long updatedTime = -1;

        Connection myconn = conn;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            if (conn == null) {
                conn = DBManager.getDBManager().getConnection(true);
                myconn = conn;
            }

            pstmt = conn.prepareStatement(selectUpdatedTimeSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new KeyNotFoundException("Name "+name+" not found in store");
            }
            updatedTime = rs.getLong(1);
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectUpdatedTimeSQL+"]", rbe );
            }
            throw e;

        } finally {
            closeSQL(rs, pstmt, myconn, myex,  logger_);
        }

        return updatedTime;
    }

    /**
     * @param conn database connection
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getCreatedTime(Connection conn, String name,
                               java.util.logging.Logger logger_)
                               throws KeyNotFoundException, Exception {
        long createdTime = -1;

        Connection myconn = conn;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            if (conn == null) {
                conn = DBManager.getDBManager().getConnection(true);
                myconn = conn;
            }

            pstmt = conn.prepareStatement(selectCreatedTimeSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new KeyNotFoundException(
                "Name "+name+" not found in store");
            }
            createdTime = rs.getLong(1);
         } catch (Exception e) {
             myex = e;
             try {
                 if ( (conn != null) && !conn.getAutoCommit() ) {
                     conn.rollback();
                 }
             } catch ( SQLException rbe ) {
                 logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectCreatedTimeSQL+"]", rbe );
             }
             throw e;

        } finally {
            closeSQL(rs, pstmt, myconn, myex, logger_);
        }

        return createdTime;
    }

    /**
     * @param conn database connection
     * @param brokerID 
     * @param logger_ can be null;
     * @return list of names
     * @throws Exception
     */
    public List getNamesByBroker(Connection conn, String brokerID,
                                 java.util.logging.Logger logger_)
                                 throws Exception {
        List list = new ArrayList();

        Connection myconn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            DBManager dbMgr = DBManager.getDBManager();
            if (conn == null) {
                conn = DBManager.getDBManager().getConnection(true);
                myconn = conn;
            }

            pstmt = conn.prepareStatement(selectNamesByBrokerIdSQL);
            pstmt.setString(1, brokerID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add((rs.getString(1)));
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectNamesByBrokerIdSQL+"]", rbe );
            }
            throw e;

        } finally {
            closeSQL(rs, pstmt, myconn, myex, logger_);
        }

        return list;
    }

    /**
     * (same impl as in other DAO impls)
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo(Connection conn) {

        HashMap map = new HashMap();
        int count = -1;

        try {
            count = getRowCount(null, null);
        } catch ( Exception e ) {
            logger.log( Logger.ERROR, e.getMessage(), e.getCause() );
        }

        map.put("JMSBridge(" + tableName + ")", String.valueOf(count));
        return map;
    }

    /**
     * To be called when exception occurred on a connection
     *
     * @Exception DupKeyException if xid already exists
     */
    private void checkDupKeyOnException(Connection conn,
                                        String name,
                                        java.util.logging.Logger logger_)
                                        throws DupKeyException {
        if (conn == null) return; 

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(selectCreatedTimeSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                throw new DupKeyException("Name "+name +" already exists in DB");
            }
        } catch (Exception e) {
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectCreatedTimeSQL+"]", rbe );
            }

            if (e instanceof DupKeyException) throw (DupKeyException)e;

            String emsg = br.getKString(BrokerResources.X_INTERNAL_EXCEPTION,
                                        "Exception on checkDupKey for name " + name);
            logger.log(Logger.WARNING, emsg, e);
            Util.logExt(logger_, java.util.logging.Level.WARNING, emsg, e);
        } 
    }

    private void closeSQL(ResultSet rset, 
                          PreparedStatement pstmt, 
                          Connection conn, Throwable myex,
                          java.util.logging.Logger logger_) {
        try {
            Util.close(rset, pstmt, conn, myex);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            String emsg = "Unable to close SQL connection or statement: "+
                           e.getMessage()+(cause == null ? "":" - "+cause.getMessage());
            logger.log(Logger.WARNING, emsg, e);
            Util.logExt(logger_, java.util.logging.Level.WARNING, emsg, e);
        }
    }

}
