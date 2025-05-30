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

package org.apache.cxf.systest.http2.netty;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.cxf.jaxrs.ext.StreamingResponse;
import org.apache.cxf.jaxrs.ext.TRACE;

@Path("/web/bookstore")
public class BookStore {
    private static ExecutorService executor = Executors.newSingleThreadExecutor();

    @GET
    @Path("/booknames")
    @Produces("text/plain")
    public byte[] getBookNames() {
        return "CXF in Action".getBytes();
    }

    @GET
    @Path("/book names")
    @Produces("text/plain")
    public byte[] getBookNamesEncoded() {
        return "CXF in Action".getBytes();
    }

    @GET
    @Path("/bookstream")
    @Produces("application/xml")
    public StreamingResponse<Book> getBookStream() {
        return new StreamingResponse<Book>() {
            public void writeTo(final StreamingResponse.Writer<Book> out) throws IOException {
                out.write(new Book("Book1", 1));
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            for (int i = 2; i <= 5; i++) {
                                Thread.sleep(500);
                                out.write(new Book("Book" + i, i));
                                out.getEntityStream().flush();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        };
    }
    
    @TRACE
    @Produces("application/xml")
    @Consumes("application/xml")
    @Path("/trace")
    public Response traceBook() {
        return Response.status(Status.NOT_ACCEPTABLE).build();
    }
}


