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

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Configurable;
import javax.ws.rs.core.Configuration;

import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.common.util.PropertyUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.jaxrs.client.AbstractClient;
import org.apache.cxf.jaxrs.client.ClientProperties;
import org.apache.cxf.jaxrs.client.ClientProxyImpl;
import org.apache.cxf.jaxrs.client.ClientState;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.spec.TLSConfiguration;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.FilterProviderInfo;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.provider.jsrjsonb.JsrJsonbProvider;
import org.apache.cxf.jaxrs.provider.jsrjsonp.JsrJsonpProvider;
import org.apache.cxf.microprofile.client.cdi.CDIInterceptorWrapper;
import org.apache.cxf.microprofile.client.proxy.MicroProfileClientProxyImpl;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

public class MicroProfileClientFactoryBean extends JAXRSClientFactoryBean {
    private final Comparator<ProviderInfo<?>> comparator;
    private final List<Object> registeredProviders;
    private final Configurable<RestClientBuilder> configurable;
    private final Configuration configuration;
    private ClassLoader proxyLoader;
    private boolean inheritHeaders;
    private ExecutorService executorService;
    private TLSConfiguration secConfig;

    public MicroProfileClientFactoryBean(MicroProfileClientConfigurableImpl<RestClientBuilder> configurable,
                                         String baseUri, Class<?> aClass, ExecutorService executorService,
                                         TLSConfiguration secConfig) {
        super(new MicroProfileServiceFactoryBean());
        this.configurable = configurable;
        this.configuration = configurable.getConfiguration();
        this.comparator = MicroProfileClientProviderFactory.createComparator(this);
        this.executorService = (executorService == null) ? Utils.defaultExecutorService() : executorService; 
        this.secConfig = secConfig;
        super.setAddress(baseUri);
        super.setServiceClass(aClass);
        super.setProviderComparator(comparator);
        super.setProperties(this.configuration.getProperties());
        registeredProviders = new ArrayList<>();
        registeredProviders.addAll(processProviders());
        if (!configurable.isDefaultExceptionMapperDisabled()) {
            registeredProviders.add(new ProviderInfo<>(new DefaultResponseExceptionMapper(), getBus(), false));
        }
        registeredProviders.add(new ProviderInfo<>(new JsrJsonpProvider(), getBus(), false));
        registeredProviders.add(new ProviderInfo<>(new JsrJsonbProvider(), getBus(), false));
        super.setProviders(registeredProviders);
    }

    @Override
    public void setClassLoader(ClassLoader loader) {
        super.setClassLoader(loader);
        this.proxyLoader = loader;
    }

    @Override
    public void setInheritHeaders(boolean inheritHeaders) {
        super.setInheritHeaders(inheritHeaders);
        this.inheritHeaders = inheritHeaders;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    protected void initClient(AbstractClient client, Endpoint ep, boolean addHeaders) {
        super.initClient(client, ep, addHeaders);

        TLSClientParameters tlsParams = secConfig.getTlsClientParams();
        if (tlsParams.getSSLSocketFactory() != null
            || tlsParams.getTrustManagers() != null
            || tlsParams.getHostnameVerifier() != null) {
            client.getConfiguration().getHttpConduit().setTlsClientParameters(tlsParams);
        }

        if (PropertyUtils.isTrue(configuration.getProperty(ClientProperties.HTTP_AUTOREDIRECT_PROP))) {
            client.getConfiguration().getHttpConduit().getClient().setAutoRedirect(true);
        }

        String proxyHost = (String) configuration.getProperty(ClientProperties.HTTP_PROXY_SERVER_PROP);
        if (proxyHost != null) {
            client.getConfiguration().getHttpConduit().getClient().setProxyServer(proxyHost);
            int proxyPort = (int) configuration.getProperty(ClientProperties.HTTP_PROXY_SERVER_PORT_PROP);
            client.getConfiguration().getHttpConduit().getClient().setProxyServerPort(proxyPort);
        }

        MicroProfileClientProviderFactory factory = MicroProfileClientProviderFactory.createInstance(getBus(),
                comparator);
        factory.setUserProviders(registeredProviders);
        ep.put(MicroProfileClientProviderFactory.CLIENT_FACTORY_NAME, factory);
    }

    @Override
    protected ClientProxyImpl createClientProxy(ClassResourceInfo cri, boolean isRoot,
                                                ClientState actualState, Object[] varValues) {
        CDIInterceptorWrapper interceptorWrapper = CDIInterceptorWrapper.createWrapper(getServiceClass());
        if (actualState == null) {
            return new MicroProfileClientProxyImpl(URI.create(getAddress()), proxyLoader, cri, isRoot,
                    inheritHeaders, executorService, configuration, interceptorWrapper, secConfig, varValues);
        } else {
            return new MicroProfileClientProxyImpl(actualState, proxyLoader, cri, isRoot,
                    inheritHeaders, executorService, configuration, interceptorWrapper, secConfig, varValues);
        }
    }
    
    @Override
    protected <C extends Configurable<C>> Configurable<?> getConfigurableFor(C context) {
        return configurable;
    }

    Configuration getConfiguration() {
        return configuration;
    }

    private Set<Object> processProviders() {
        Set<Object> providers = new LinkedHashSet<>();
        for (Object provider : configuration.getInstances()) {
            Class<?> providerCls = ClassHelper.getRealClass(getBus(), provider);
            if (provider instanceof ClientRequestFilter || provider instanceof ClientResponseFilter) {
                FilterProviderInfo<Object> filter = new FilterProviderInfo<>(providerCls, providerCls,
                        provider, getBus(), configuration.getContracts(providerCls));
                providers.add(filter);
            } else {
                providers.add(provider);
            }
        }
        return providers;
    }
}
