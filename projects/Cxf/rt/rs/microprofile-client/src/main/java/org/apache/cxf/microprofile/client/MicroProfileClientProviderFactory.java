/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.microprofile.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Configuration;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.message.Message;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

public final class MicroProfileClientProviderFactory extends ProviderFactory {
    static final String CLIENT_FACTORY_NAME = MicroProfileClientProviderFactory.class.getName();
    private static final Class<?> ASYNC_II_FACTORY_CLASS;
    private static final Logger LOG = LogUtils.getL7dLogger(MicroProfileClientProviderFactory.class);
    private List<ProviderInfo<ResponseExceptionMapper<?>>> responseExceptionMappers = new ArrayList<>(1);
    private List<ProviderInfo<Object>> asyncInvocationInterceptorFactories = new ArrayList<>();
    private final Comparator<ProviderInfo<?>> comparator;
    

    static {
        Class<?> c;
        try {
            c = ClassLoaderUtils.loadClass("org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory",
                                           MicroProfileClientProviderFactory.class);
        } catch (ClassNotFoundException ex) {
            // expected if using the MP Rest Client 1.0 APIs
            c = null;
            LOG.log(Level.FINEST, ex, () -> {
                return "Caught ClassNotFoundException - expected if using MP Rest Client 1.0 APIs"; });
        }
        ASYNC_II_FACTORY_CLASS = c;
    }

    private MicroProfileClientProviderFactory(Bus bus, Comparator<ProviderInfo<?>> comparator) {
        super(bus);
        this.comparator = comparator;
    }

    public static MicroProfileClientProviderFactory createInstance(Bus bus,
                                                                   Comparator<ProviderInfo<?>> comparator) {
        if (bus == null) {
            bus = BusFactory.getThreadDefaultBus();
        }
        MicroProfileClientProviderFactory factory = new MicroProfileClientProviderFactory(bus, comparator);
        ProviderFactory.initFactory(factory);
        factory.setBusProviders();
        return factory;
    }

    public static MicroProfileClientProviderFactory getInstance(Message m) {
        Endpoint e = m.getExchange().getEndpoint();
        return getInstance(e);
    }

    public static MicroProfileClientProviderFactory getInstance(Endpoint e) {
        return (MicroProfileClientProviderFactory)e.get(CLIENT_FACTORY_NAME);
    }

    static Comparator<ProviderInfo<?>> createComparator(MicroProfileClientFactoryBean bean) {
        Comparator<ProviderInfo<?>> parent = (o1, o2) -> compareCustomStatus(o1, o2);
        return new ContractComparator(bean, parent);
    }

    @Override
    protected void setProviders(boolean custom, boolean busGlobal, Object... providers) {
        List<ProviderInfo<?>> theProviders =
                prepareProviders(custom, busGlobal, providers, null);
        super.setCommonProviders(theProviders, RuntimeType.CLIENT);
        for (ProviderInfo<?> provider : theProviders) {
            Class<?> providerCls = ClassHelper.getRealClass(getBus(), provider.getProvider());

            // Check if provider is constrained to client
            if (!constrainedTo(providerCls, RuntimeType.CLIENT)) {
                continue;
            }

            if (ResponseExceptionMapper.class.isAssignableFrom(providerCls)) {
                addProviderToList(responseExceptionMappers, provider);
            }
            if (ASYNC_II_FACTORY_CLASS != null && ASYNC_II_FACTORY_CLASS.isAssignableFrom(providerCls)) {
                addProviderToList(asyncInvocationInterceptorFactories, provider);
            }
        }
        responseExceptionMappers.sort(comparator);
        asyncInvocationInterceptorFactories.sort(comparator);

        injectContextProxies(responseExceptionMappers);
        injectContextProxies(asyncInvocationInterceptorFactories);
    }

    public List<ResponseExceptionMapper<?>> createResponseExceptionMapper(Message m, Class<?> paramType) {

        if (responseExceptionMappers.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(responseExceptionMappers
                                            .stream()
                                            .map(ProviderInfo::getProvider)
                                            .sorted(new ResponseExceptionMapperComparator())
                                            .collect(Collectors.toList()));
    }

    public List<ProviderInfo<Object>> getAsyncInvocationInterceptorFactories() {
        return asyncInvocationInterceptorFactories;
    }

    @Override
    public void clearProviders() {
        super.clearProviders();
        responseExceptionMappers.clear();
        asyncInvocationInterceptorFactories.clear();
    }

    @Override
    public Configuration getConfiguration(Message m) {
        return (Configuration)m.getExchange().getOutMessage()
                .getContextualProperty(Configuration.class.getName());
    }

    private final class ResponseExceptionMapperComparator implements Comparator<ResponseExceptionMapper<?>> {

        @Override
        public int compare(ResponseExceptionMapper<?> oLeft, ResponseExceptionMapper<?> oRight) {
            return oLeft.getPriority() - oRight.getPriority();
        }
    }
}
