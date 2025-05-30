/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.protocol.http;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.pages.BrowserInfoPage;
import org.apache.wicket.protocol.http.request.WebClientInfo;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;

/**
 * A session subclass for the HTTP protocol.
 * 
 * @author Jonathan Locke
 */
public class WebSession extends Session
{
	private static final long serialVersionUID = 1L;

	public static WebSession get()
	{
		return (WebSession)Session.get();
	}

	/**
	 * Constructor. Note that {@link RequestCycle} is not available until this constructor returns.
	 * 
	 * @param request
	 *            The current request
	 */
	public WebSession(Request request)
	{
		super(request);
	}

	/**
	 * Call signOut() and remove the logon data from whereever they have been persisted (e.g.
	 * Cookies)
	 * 
	 * @see org.apache.wicket.Session#invalidate()
	 */
	@Override
	public void invalidate()
	{
		if (isSessionInvalidated() == false)
		{
			getApplication().getSecuritySettings().getAuthenticationStrategy().remove();

			super.invalidate();
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * <p>
	 * To gather the client information this implementation redirects temporarily to a special page
	 * ({@link BrowserInfoPage}).
	 * <p>
	 * Note: Do <strong>not</strong> call this method from your custom {@link Session} constructor
	 * because the temporary page needs a constructed {@link Session} to be able to work.
	 * <p>
	 * If you need to set a default client info property then better use
	 * {@link #setClientInfo(org.apache.wicket.core.request.ClientInfo)} directly.
	 */
	@Override
	public WebClientInfo getClientInfo()
	{
		if (clientInfo == null)
		{
			RequestCycle requestCycle = RequestCycle.get();
			clientInfo = new WebClientInfo(requestCycle);

			if (getApplication().getRequestCycleSettings().getGatherExtendedBrowserInfo())
			{
				WebPage browserInfoPage = newBrowserInfoPage();
				throw new RestartResponseAtInterceptPageException(browserInfoPage);
			}
		}
		return (WebClientInfo)clientInfo;
	}

	/**
	 * Override this method if you want to use a custom page for gathering the client's browser
	 * information.<br/>
	 * The easiest way is just to extend {@link BrowserInfoPage} and provide your own markup file
	 * 
	 * @return the {@link WebPage} which should be used while gathering browser info
	 */
	protected WebPage newBrowserInfoPage()
	{
		return new BrowserInfoPage();
	}

	@Override
	protected String generateNewSessionId() 
	{
		ServletWebRequest servletRequest = (ServletWebRequest)RequestCycle.get().getRequest();
		HttpServletRequest httpRequest = servletRequest.getContainerRequest();
		
		return httpRequest.changeSessionId();
	}
}
