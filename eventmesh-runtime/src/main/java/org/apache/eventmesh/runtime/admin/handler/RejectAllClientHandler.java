/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;

@EventHttpHandler(path = "/clientManage/rejectAllClient")
public class RejectAllClientHandler extends AbstractHttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RejectAllClientHandler.class);

    private final EventMeshTCPServer eventMeshTCPServer;

    public RejectAllClientHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    /**
     * remove all clients accessed by eventMesh
     *
     * @param httpExchange
     * @throws IOException
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            final List<InetSocketAddress> successRemoteAddrs = new ArrayList<>();
            try {
                logger.info("rejectAllClient in admin====================");
                if (!sessionMap.isEmpty()) {
                    for (Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                        InetSocketAddress addr = EventMeshTcp2Client.serverGoodby2Client(
                                eventMeshTCPServer, entry.getValue(), clientSessionGroupMapping);
                        if (addr != null) {
                            successRemoteAddrs.add(addr);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("clientManage|rejectAllClient|fail", e);
                result = String.format("rejectAllClient fail! sessionMap size {%d}, had reject {%s}, errorMsg : %s",
                        sessionMap.size(), NetUtils.addressToString(successRemoteAddrs), e.getMessage());
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            result = String.format("rejectAllClient success! sessionMap size {%d}, had reject {%s}", sessionMap.size(),
                    NetUtils.addressToString(successRemoteAddrs));
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            logger.error("rejectAllClient fail...", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.warn("out close failed...", e);
                }
            }
        }
    }
}
