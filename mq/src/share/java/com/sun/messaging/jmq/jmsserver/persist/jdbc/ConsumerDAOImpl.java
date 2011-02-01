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
 * @(#)ConsumerDAOImpl.java	1.16 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.io.Status;

import java.util.*;
import java.sql.*;
import java.io.IOException;

/**
 * This class implement a generic ConsumerDAO.
 */
class ConsumerDAOImpl extends BaseDAOImpl implements ConsumerDAO {

    protected String tableName;

    // SQLs
    protected String insertSQL;
    protected String deleteSQL;
    protected String selectSQL;
    protected String selectAllSQL;
    protected String selectExistSQL;
    protected String selectExistByIDSQL;

    /**
     * Constructor
     * @throws BrokerException
     */
    ConsumerDAOImpl() throws BrokerException {

        // Initialize all SQLs
        DBManager dbMgr = DBManager.getDBManager();

        tableName = dbMgr.getTableName( TABLE_NAME_PREFIX );

        insertSQL = new StringBuffer(128)
            .append( "INSERT INTO " ).append( tableName )
            .append( " ( " )
            .append( ID_COLUMN ).append( ", " )
            .append( CONSUMER_COLUMN ).append( ", " )
            .append( DURABLE_NAME_COLUMN ).append( ", " )
            .append( CLIENT_ID_COLUMN ).append( ", " )
            .append( CREATED_TS_COLUMN )
            .append( ") VALUES ( ?, ?, ?, ?, ? )" )
            .toString();

        deleteSQL = new StringBuffer(128)
            .append( "DELETE FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();

        selectAllSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( CONSUMER_COLUMN )
            .append( " FROM " ).append( tableName )
            .toString();

        selectExistSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( DURABLE_NAME_COLUMN ).append( " = ?" )
            .append( " AND " )
            .append( CLIENT_ID_COLUMN ).append( " = ?" )
            .toString();

        selectExistByIDSQL = new StringBuffer(128)
            .append( "SELECT " )
            .append( ID_COLUMN )
            .append( " FROM " ).append( tableName )
            .append( " WHERE " )
            .append( ID_COLUMN ).append( " = ?" )
            .toString();
    }

    /**
     * Get the prefix name of the table.
     * @return table name
     */
    public final String getTableNamePrefix() {
        return TABLE_NAME_PREFIX;
    }

    /**
     * Get the name of the table.
     * @return table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Insert a new entry.
     * @param conn database connection
     * @param consumer the Consumer
     * @param createdTS timestamp
     * @throws BrokerException if entry exists in the store already
     */
    public void insert( Connection conn, Consumer consumer, long createdTS )
        throws BrokerException {

        ConsumerUID consumerUID = consumer.getConsumerUID();
        String durableName = null;
        String clientID = null;
        if ( consumer instanceof Subscription ) {
            Subscription sub = (Subscription)consumer;
            durableName = sub.getDurableName();
            clientID = sub.getClientID();
        }

        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = DBManager.getDBManager().getConnection( true );
                myConn = true;
            }

            if ( checkConsumer( conn, consumer ) ) {
                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTEREST_EXISTS_IN_STORE,
                    consumerUID ) );
            }

            pstmt = conn.prepareStatement( insertSQL );
            pstmt.setLong( 1, consumerUID.longValue() );
            Util.setObject( pstmt, 2, consumer );
            Util.setString( pstmt, 3, durableName );
            Util.setString( pstmt, 4, clientID );
            pstmt.setLong( 5, createdTS );
            pstmt.executeUpdate();
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof IOException ) {
                ex = DBManager.wrapIOException("[" + insertSQL + "]", (IOException)e);
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + insertSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_PERSIST_INTEREST_FAILED,
                consumerUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }
    }

    /**
     * Delete an existing entry.
     * @param conn database connection
     * @param consumer the Consumer
     * @throws BrokerException if entry does not exists in the store
     */
    public void delete( Connection conn, Consumer consumer )
        throws BrokerException {

        ConsumerUID consumerUID = consumer.getConsumerUID();

        boolean deleted = false;
        boolean myConn = false;
        PreparedStatement pstmt = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = DBManager.getDBManager().getConnection( true );
                myConn = true;
            }

            pstmt = conn.prepareStatement( deleteSQL );
            pstmt.setLong( 1, consumerUID.longValue() );
            if ( pstmt.executeUpdate() > 0 ) {
                deleted = true;
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + deleteSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_REMOVE_INTEREST_FAILED,
                consumerUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( null, pstmt, conn, myex );
            } else {
                Util.close( null, pstmt, null, myex );
            }
        }

        if ( !deleted ) {
            DestinationUID destinationUID = consumer.getDestinationUID();
            throw new BrokerException(
                br.getKString( BrokerResources.E_INTEREST_NOT_FOUND_IN_STORE,
                consumerUID, destinationUID ), Status.NOT_FOUND );
        }
    }

    /**
     * Delete all entries.
     * @param conn database connection
     * @throws BrokerException
     */
    public void deleteAll( Connection conn )
        throws BrokerException {

        if ( Globals.getHAEnabled() ) {
            return; // Share table cannot be reset    
        } else {
            super.deleteAll( conn );
        }
    }

    /**
     * Get a Consumer.
     * @param conn database connection
     * @param consumerUID the consumer ID
     * @return Consumer
     * @throws BrokerException
     */
    public Consumer getConsumer( Connection conn, ConsumerUID consumerUID )
        throws BrokerException {

        Consumer consumer = null;

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = DBManager.getDBManager().getConnection( true );
                myConn = true;
            }

            pstmt = conn.prepareStatement( selectSQL );
            pstmt.setLong( 1, consumerUID.longValue() );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                try {
                    consumer = (Consumer)Util.readObject( rs, 1 );
                } catch ( IOException e ) {
                    // fail to parse consumer object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_INTEREST_FAILED, e );
                }
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_RETRIEVE_INTEREST_FAILED,
                    consumerUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return consumer;
    }

    /**
     * Retrieve all consumers in the store.
     * @param conn database connection
     * @return a List of Consumer objects; an empty List is returned
     * if no consumers exist in the store
     */
    public List getAllConsumers( Connection conn ) throws BrokerException {

        ArrayList list = new ArrayList();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = DBManager.getDBManager().getConnection( true );
                myConn = true;
            }

            pstmt = conn.prepareStatement( selectAllSQL );
            rs = pstmt.executeQuery();

            while ( rs.next() ) {
                try {
                    Consumer consumer = (Consumer)Util.readObject( rs, 1 );
                    list.add( consumer );
                } catch ( IOException e ) {
                    // fail to parse consumer object; just log it
                    logger.logStack( Logger.ERROR,
                        BrokerResources.X_PARSE_INTEREST_FAILED, e );
                }
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectAllSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_LOAD_INTERESTS_FAILED ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return list;
    }

    /**
     * Check whether the specified consumer exists.
     * @param conn database connection
     * @param consumer the Consumer
     * @return return true if the specified consumer exists
     * @throws BrokerException
     */
    public boolean checkConsumer( Connection conn, Consumer consumer ) throws BrokerException {

        boolean found = false;
        ConsumerUID consumerUID = consumer.getConsumerUID();

        boolean myConn = false;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Exception myex = null;
        try {
            // Get a connection
            if ( conn == null ) {
                conn = DBManager.getDBManager().getConnection( true );
                myConn = true;
            }

            pstmt = conn.prepareStatement( selectExistByIDSQL );
            pstmt.setLong( 1, consumerUID.longValue() );
            rs = pstmt.executeQuery();
            if ( rs.next() ) {
                found = true;
            }
        } catch ( Exception e ) {
            myex = e;
            try {
                if ( (conn != null) && !conn.getAutoCommit() ) {
                    conn.rollback();
                }
            } catch ( SQLException rbe ) {
                logger.log( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED+"["+selectExistByIDSQL+"]", rbe );
            }

            Exception ex;
            if ( e instanceof BrokerException ) {
                throw (BrokerException)e;
            } else if ( e instanceof SQLException ) {
                ex = DBManager.wrapSQLException("[" + selectExistByIDSQL + "]", (SQLException)e);
            } else {
                ex = e;
            }

            throw new BrokerException(
                br.getKString( BrokerResources.X_RETRIEVE_INTEREST_FAILED,
                consumerUID ), ex );
        } finally {
            if ( myConn ) {
                Util.close( rs, pstmt, conn, myex );
            } else {
                Util.close( rs, pstmt, null, myex );
            }
        }

        return found;
    }

    /**
     * Get debug information about the store.
     * @param conn database connection
     * @return a HashMap of name value pair of information
     */
    public HashMap getDebugInfo( Connection conn ) {

        HashMap map = new HashMap();
        int count = -1;

        try {
            // Get row count
            count = getRowCount( null, null );
        } catch ( Exception e ) {
            logger.log( Logger.ERROR, e.getMessage(), e.getCause() );
        }

        map.put( "Consumers(" + tableName + ")", String.valueOf( count ) );
        return map;
    }
}
