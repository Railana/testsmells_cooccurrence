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

package org.apache.cxf.systest.jaxrs.reactive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.testutil.common.AbstractServerTestServerBase;

public abstract class AbstractCompletableFutureServer extends AbstractServerTestServerBase {
    @Override
    protected Server createServer(Bus bus) throws Exception {
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setResourceClasses(CompletableFutureService.class);
        sf.setResourceProvider(CompletableFutureService.class,
                               new SingletonResourceProvider(new CompletableFutureService(), true));
        sf.setAddress(getProtocol() + "://localhost:" + getPort() + "/");
        sf.setProvider(new MappedExceptionMapper());
        return sf.create();
    }

    public static void main(String[] args) throws Exception {
        new CompletableFutureHttpServer().start();
    }

    @Consumes("text/boolean")
    @Produces("text/boolean")
    public static class BooleanReaderWriter implements
        MessageBodyReader<Object>, MessageBodyWriter<Boolean> {

        @Override
        public boolean isReadable(Class<?> arg0, Type arg1, Annotation[] arg2, MediaType arg3) {
            return true;
        }

        @Override
        public Object readFrom(Class<Object> arg0, Type arg1, Annotation[] arg2, MediaType arg3,
                             MultivaluedMap<String, String> arg4, InputStream is) throws IOException,
            WebApplicationException {
            return Boolean.valueOf(IOUtils.readStringFromStream(is));
        }

        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations,
                                   MediaType mediaType) {
            return true;
        }

        @Override
        public long getSize(Boolean t, Class<?> type, Type genericType, Annotation[] annotations,
                            MediaType mediaType) {
            return -1;
        }

        @Override
        public void writeTo(Boolean t, Class<?> type, Type genericType, Annotation[] annotations,
                            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                            OutputStream os) throws IOException, WebApplicationException {
            byte[] bytes = t.toString().getBytes("UTF-8");
            os.write(bytes);

        }
    }

    public  abstract String getPort();
    public  abstract String getProtocol();
}
