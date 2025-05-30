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
package org.apache.cxf.transport.servlet;

import javax.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseUrlHelperTest {
    private String testGetBaseURL(String requestUrl, String contextPath,
                                  String servletPath, String pathInfo) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURL()).thenReturn(new StringBuffer(requestUrl));

        when(req.getContextPath()).thenReturn(contextPath);
        when(req.getServletPath()).thenReturn(servletPath);

        when(req.getPathInfo()).thenReturn(pathInfo);

        String basePath = contextPath + servletPath;
        if (basePath.length() == 0) {
            when(req.getRequestURI()).thenReturn(pathInfo);
        }

        return BaseUrlHelper.getBaseURL(req);
    }

    @Test
    public void testGetRequestURLWithRepeatingValues() throws Exception {
        String url = testGetBaseURL("http://services.com/services/bar", "/services", "", "/bar");
        Assert.assertEquals("http://services.com/services", url);
    }

    @Test
    public void testGetRequestURL() throws Exception {
        String url = testGetBaseURL("http://localhost:8080/services/bar", "", "/services", "/bar");
        Assert.assertEquals("http://localhost:8080/services", url);
    }

    @Test
    public void testGetRequestURL2() throws Exception {
        String url = testGetBaseURL("http://localhost:8080/services/bar", "/services", "", "/bar");
        Assert.assertEquals("http://localhost:8080/services", url);
    }

    @Test
    public void testGetRequestURL3() throws Exception {
        String url = testGetBaseURL("http://localhost:8080/services/bar", "", "", "/services/bar");
        Assert.assertEquals("http://localhost:8080", url);
    }

    @Test
    public void testGetRequestURLSingleMatrixParam() throws Exception {
        String url = testGetBaseURL("http://localhost:8080/services/bar;a=b", "", "/services", "/bar");
        Assert.assertEquals("http://localhost:8080/services", url);
    }

    @Test
    public void testGetRequestURLMultipleMatrixParam() throws Exception {
        String url = testGetBaseURL("http://localhost:8080/services/bar;a=b;c=d;e=f",
                                    "", "/services", "/bar");
        Assert.assertEquals("http://localhost:8080/services", url);

    }

    @Test
    public void testGetRequestURLMultipleMatrixParam2() throws Exception {
        String url = testGetBaseURL("http://localhost:8080/services/bar;a=b;c=d;e=f", "", "/services",
                                    "/bar;a=b;c=d");
        Assert.assertEquals("http://localhost:8080/services", url);

    }

    @Test
    public void testGetRequestURLMultipleMatrixParam3() throws Exception {
        String url = testGetBaseURL("http://localhost:8080/services/bar;a=b;c=d;e=f", "", "/services",
                                    "/bar;a=b");
        Assert.assertEquals("http://localhost:8080/services", url);

    }

    @Test
    public void testGetRequestURLMultipleMatrixParam4() throws Exception {
        String url = testGetBaseURL("http://localhost:8080/services/bar;a=b;c=d;e=f;", "", "/services",
                                    "/bar;a=b");
        Assert.assertEquals("http://localhost:8080/services", url);

    }
}
