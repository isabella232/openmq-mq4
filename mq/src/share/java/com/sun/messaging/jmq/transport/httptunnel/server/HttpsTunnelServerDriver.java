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
 * @(#)HttpsTunnelServerDriver.java	1.14 09/11/07
 */ 
 
package com.sun.messaging.jmq.transport.httptunnel.server;

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;


/**
 * This class extends HttpTunnelServerDriver and uses SSL sockets
 * to communicate with the tunneling servlet.
 */
public class HttpsTunnelServerDriver extends HttpTunnelServerDriver {
    private static boolean DEBUG = getDEBUG();
    private static Logger logger = Logger.getLogger("Http Tunneling");
    protected boolean trustServlet = true;

    public HttpsTunnelServerDriver(String serviceName, boolean trust)
        throws IOException {
        this(serviceName, InetAddress.getLocalHost().getHostAddress(),
            DEFAULT_HTTPS_TUNNEL_PORT, trust);
    }

    public HttpsTunnelServerDriver(String serviceName,
        String webServerHostName, int webServerPort, boolean trust)
        throws IOException {
        super(serviceName, webServerHostName, webServerPort);

        trustServlet = trust;
        setName("HttpsTunnelServerDriver");

        if (DEBUG || DEBUGLINK) {
            log("Created HttpsTunnelServerDriver for " + serviceName + " to " +
                webServerHostName + ":" + webServerPort);
        }
    }

    /**
     * Create secured connection to the servlet. If accepted by
     * the servlet successfully, this method sends
     * the current state of the connection table to the servlet
     * and resumes normal operation.
     */
    protected void createLink() {
        totalRetryWaited = 0;

        if (DEBUG) {
            log("http:connecting to " + webServerHost + ":" + webServerPort);
        }

        while (true) {
            try {
                if (rxBufSize > 0) {
                    serverConn = getSSLSocket(webServerHost, webServerPort,
                            rxBufSize, trustServlet);
                } else {
                    serverConn = getSSLSocket(webServerHost, webServerPort,
                            trustServlet);
                }

                try {
                serverConn.setTcpNoDelay(true);
                } catch (SocketException e) {
                log(Level.WARNING, "HTTPS socket["+webServerHost+":"+webServerPort+
                                    "]setTcpNoDelay: "+e.toString(), e);
                }

                if (DEBUG) {
                    log("######## rcvbuf = " +
                        serverConn.getReceiveBufferSize());
                }

                is = serverConn.getInputStream();
                os = serverConn.getOutputStream();

                if (DEBUG || DEBUGLINK) {
                    log("Broker HTTPS link up");
                }

                totalRetryWaited = 0;

                break;
            } catch (Exception e) {
                if (DEBUG || DEBUGLINK) {
                    log("Got exception while connecting to servlet: " +
                        e.getMessage());
                }
            }

            try {
                Thread.sleep(CONNECTION_RETRY_INTERVAL);
                totalRetryWaited += CONNECTION_RETRY_INTERVAL;

                if (totalRetryWaited >= (inactiveConnAbortInterval * 1000)) {
                    if (DEBUG || DEBUGLINK) {
                        log("Retry connect to servlet timeout " +
                            "- cleanup all (" + connTable.size() + ") " +
                            "connections and stop retry ...");
                    }

                    cleanupAllConns();
                    totalRetryWaited = 0;
                }
            } catch (Exception se) {
                if (se instanceof IllegalStateException) {
                    throw (IllegalStateException) se;
                }
            }
        }

        sendLinkInitPacket();
        sendListenStatePacket();
    }

    /**
     * Create secured connection to the specified server.
     */
    private static SSLSocket getSSLSocket(InetAddress host, int port,
        boolean trust) throws IOException {
        if (DEBUG || DEBUGLINK) {
            logger.log(Level.INFO, "Creating SSL Socket...");
        }

        try {
            SSLSocketFactory factory = null;

            if (trust) {
                factory = getTrustedSocketFactory();
            } else {
                factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }

            SSLSocket s = (SSLSocket) factory.createSocket(host, port);

            return s;
        } catch (Exception e) {
            if (!(e instanceof IOException)) {
                IOException ex = new IOException(e.getMessage());
                ex.setStackTrace(e.getStackTrace());
                throw ex;
            } else {
                throw (IOException) e;
            }
        }
    }

    /**
     * Create secured connection to the specified server.
     */
    private static SSLSocket getSSLSocket(InetAddress host, int port,
        int rxBufSize, boolean trust) throws IOException {
        if (DEBUG || DEBUGLINK) {
            logger.log(Level.INFO, "Creating SSL Socket with rxBufSize...");
        }

        try {
            SSLSocketFactory factory = null;

            if (trust) {
                factory = getTrustedSocketFactory();
            } else {
                factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }

            SSLSocket s = (SSLSocket) factory.createSocket();
            try {
            s.setReceiveBufferSize(rxBufSize);
            } catch (SocketException e) {
            logger.log(Level.WARNING, "HTTPS socket["+host+":"+port+
                   "]setReceiveBufferSize("+rxBufSize+"): "+e.toString(), e);
            }
            InetSocketAddress addr = new InetSocketAddress(host, port);
            s.connect(addr);

            return s;
        } catch (Exception e) {
            if (!(e instanceof IOException)) {
                IOException ex = new IOException(e.getMessage());
                ex.setStackTrace(e.getStackTrace());
                throw ex;
            } else {
                throw (IOException) e;
            }
        }
    }

    /**
     * Return a socket factory that uses the our DefaultTrustManager
     */
    private static SSLSocketFactory getTrustedSocketFactory()
        throws Exception {
        SSLContext ctx;
        ctx = SSLContext.getInstance("TLS");

        TrustManager[] tm = new TrustManager[1];
        tm[0] = new DefaultTrustManager();

        ctx.init(null, tm, null);

        SSLSocketFactory factory = ctx.getSocketFactory();

        return factory;
    }
}

/*
 * EOF
 */
