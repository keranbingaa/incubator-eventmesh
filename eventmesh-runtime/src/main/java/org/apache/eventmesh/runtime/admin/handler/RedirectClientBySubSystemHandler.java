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
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;

/**
 * redirect subsystem for subsys and dcn
 */
@EventHttpHandler(path = "/clientManage/redirectClientBySubSystem")
public class RedirectClientBySubSystemHandler extends AbstractHttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedirectClientBySubSystemHandler.class);

    private final EventMeshTCPServer eventMeshTCPServer;

    public RedirectClientBySubSystemHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);
            String destEventMeshIp = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_IP);
            String destEventMeshPort = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_PORT);

            if (!StringUtils.isNumeric(subSystem)
                    || StringUtils.isBlank(destEventMeshIp) || StringUtils.isBlank(destEventMeshPort)
                    || !StringUtils.isNumeric(destEventMeshPort)) {
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                result = "params illegal!";
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            logger.info("redirectClientBySubSystem in admin,subsys:{},destIp:{},destPort:{}====================",
                    subSystem, destEventMeshIp, destEventMeshPort);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            StringBuilder redirectResult = new StringBuilder();
            try {
                if (!sessionMap.isEmpty()) {
                    for (Session session : sessionMap.values()) {
                        if (session.getClient().getSubsystem().equals(subSystem)) {
                            redirectResult.append("|");
                            redirectResult.append(EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer,
                                    destEventMeshIp, Integer.parseInt(destEventMeshPort),
                                    session, clientSessionGroupMapping));
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("clientManage|redirectClientBySubSystem|fail|subSystem={}|destEventMeshIp"
                        +
                        "={}|destEventMeshPort={},errMsg={}", subSystem, destEventMeshIp, destEventMeshPort, e);
                result = String.format("redirectClientBySubSystem fail! sessionMap size {%d}, {subSystem=%s "
                                +
                                "destEventMeshIp=%s destEventMeshPort=%s}, result {%s}, errorMsg : %s",
                        sessionMap.size(), subSystem, destEventMeshIp, destEventMeshPort, redirectResult, e
                                .getMessage());
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            result = String.format("redirectClientBySubSystem success! sessionMap size {%d}, {subSystem=%s "
                            +
                            "destEventMeshIp=%s destEventMeshPort=%s}, result {%s} ",
                    sessionMap.size(), subSystem, destEventMeshIp, destEventMeshPort, redirectResult);
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            logger.error("redirectClientBySubSystem fail...", e);
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
