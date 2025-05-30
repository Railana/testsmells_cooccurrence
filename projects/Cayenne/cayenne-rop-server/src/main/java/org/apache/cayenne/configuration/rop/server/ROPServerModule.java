/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/
package org.apache.cayenne.configuration.rop.server;

import java.util.Map;

import org.apache.cayenne.configuration.Constants;
import org.apache.cayenne.di.Binder;
import org.apache.cayenne.di.ListBuilder;
import org.apache.cayenne.di.MapBuilder;
import org.apache.cayenne.di.Module;
import org.apache.cayenne.remote.RemoteService;
import org.apache.cayenne.rop.ROPConstants;
import org.apache.cayenne.rop.ROPSerializationService;
import org.apache.cayenne.rop.ServerHessianSerializationServiceProvider;
import org.apache.cayenne.rop.ServerHttpRemoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A DI module that defines services for the server-side of an ROP application based on
 * Caucho Hessian.
 * 
 * @since 3.1
 */
public class ROPServerModule implements Module {

    private static final Logger logger = LoggerFactory.getLogger(ROPServerModule.class);

    protected Map<String, String> eventBridgeProperties;

    /**
     * @since 4.0
     */
    public static MapBuilder<String> contributeROPBridgeProperties(Binder binder) {
        return binder.bindMap(String.class, Constants.SERVER_ROP_EVENT_BRIDGE_PROPERTIES_MAP);
    }

    /**
     * @since 4.2
     */
    public static ListBuilder<String> contributeSerializationWhitelist(Binder binder) {
        return binder.bindList(String.class, ROPConstants.SERIALIZATION_WHITELIST);
    }


    public ROPServerModule() {}

    /**
     * @deprecated since 4.2 ROPServerModule became autoloaded.
     * You need to contribute eventBridgeProperties yourself.
     * Use {@link #contributeROPBridgeProperties(Binder)} to
     * contribute properties.
     */
    @Deprecated
    public ROPServerModule(Map<String, String> eventBridgeProperties) {
        this.eventBridgeProperties = eventBridgeProperties;
    }

    public void configure(Binder binder) {
        logger.warn("Since 4.2 cayenne-rop-server module was deprecated.");

        contributeSerializationWhitelist(binder);
        MapBuilder<String> mapBuilder = contributeROPBridgeProperties(binder);
        if(eventBridgeProperties != null) {
            mapBuilder.putAll(eventBridgeProperties);
        }
        binder.bind(RemoteService.class).to(ServerHttpRemoteService.class);
		binder.bind(ROPSerializationService.class).toProvider(ServerHessianSerializationServiceProvider.class);
    }

}
