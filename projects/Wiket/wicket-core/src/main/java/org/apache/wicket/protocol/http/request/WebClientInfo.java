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
package org.apache.wicket.protocol.http.request;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.wicket.core.request.ClientInfo;
import org.apache.wicket.markup.html.pages.BrowserInfoPage;
import org.apache.wicket.protocol.http.ClientProperties;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.util.string.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Default client info object for web applications.
 * 
 * @author Eelco Hillenius
 */
public class WebClientInfo extends ClientInfo
{
	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(WebClientInfo.class);

	/**
	 * The user agent string from the User-Agent header, app. Theoretically, this might differ from
	 * {@link org.apache.wicket.protocol.http.ClientProperties#isNavigatorJavaEnabled()} property, which is
	 * not set until an actual reply from a browser (e.g. using {@link BrowserInfoPage} is set.
	 */
	private final String userAgent;

	/** Client properties object. */
	private final ClientProperties properties;

	/**
	 * Construct.
	 * 
	 * @param requestCycle
	 *            the request cycle
	 */
	public WebClientInfo(RequestCycle requestCycle)
	{
		this(requestCycle, new ClientProperties());
	}

	/**
	 * Construct.
	 * 
	 * @param requestCycle
	 *            the request cycle
	 */
	public WebClientInfo(RequestCycle requestCycle, ClientProperties properties)
	{
		this(requestCycle, ((ServletWebRequest)requestCycle.getRequest()).getContainerRequest()
			.getHeader("User-Agent"), properties);
	}

	/**
	 * Construct.
	 * 
	 * @param requestCycle
	 *            the request cycle
	 * @param userAgent
	 *            The User-Agent string
	 */
	public WebClientInfo(final RequestCycle requestCycle, final String userAgent)
	{
		this(requestCycle, userAgent, new ClientProperties());
	}

	/**
	 * Construct.
	 * 
	 * @param requestCycle
	 *            the request cycle
	 * @param userAgent
	 *            The User-Agent string
	 * @param properties
	 *			  properties of client            
	 */
	public WebClientInfo(final RequestCycle requestCycle, final String userAgent, final ClientProperties properties)
	{
		super();

		this.userAgent = userAgent;

		this.properties = properties;
		properties.setRemoteAddress(getRemoteAddr(requestCycle));

		init();
	}

	/**
	 * Gets the client properties object.
	 * 
	 * @return the client properties object
	 */
	public final ClientProperties getProperties()
	{
		return properties;
	}

	/**
	 * returns the user agent string.
	 * 
	 * @return the user agent string
	 */
	public final String getUserAgent()
	{
		return userAgent;
	}

	/**
	 * returns the user agent string (lower case).
	 * 
	 * @return the user agent string
	 */
	private String getUserAgentStringLc()
	{
		return (getUserAgent() != null) ? getUserAgent().toLowerCase(Locale.ROOT) : "";
	}

	/**
	 * Returns the IP address from {@code HttpServletRequest.getRemoteAddr()}.
	 *
	 * @param requestCycle
	 *            the request cycle
	 * @return remoteAddr IP address of the client, using
	 *         {@code getHttpServletRequest().getRemoteAddr()}
	 */
	protected String getRemoteAddr(RequestCycle requestCycle)
	{
		ServletWebRequest request = (ServletWebRequest)requestCycle.getRequest();
		return request.getContainerRequest().getRemoteAddr();
	}

	/**
	 * Initialize the client properties object
	 */
	private void init()
	{
		setInternetExplorerProperties();
		setOperaProperties();
		setMozillaProperties();
		setKonquerorProperties();
		setChromeProperties();
		setEdgeProperties();
		setSafariProperties();

		log.debug("determined user agent: {}", properties);
	}

	/**
	 * sets the konqueror specific properties
	 */
	private void setKonquerorProperties()
	{
		properties.setBrowserKonqueror(UserAgent.KONQUEROR.matches(getUserAgent()));

		if (properties.isBrowserKonqueror())
		{
			// e.g.: Mozilla/5.0 (compatible; Konqueror/4.2; Linux) KHTML/4.2.96 (like Gecko)
			setMajorMinorVersionByPattern("konqueror/(\\d+)\\.(\\d+)");
		}
	}

	/**
	 * sets the chrome specific properties
	 */
	private void setChromeProperties()
	{
		properties.setBrowserChrome(UserAgent.CHROME.matches(getUserAgent()));

		if (properties.isBrowserChrome())
		{
			// e.g.: Mozilla/5.0 (Windows NT 6.1) AppleWebKit/534.24 (KHTML, like Gecko)
// Chrome/12.0.702.0 Safari/534.24
			setMajorMinorVersionByPattern("chrome/(\\d+)\\.(\\d+)");
		}
	}

	/**
	 * sets the Edge specific properties
	 */
	private void setEdgeProperties()
	{
		properties.setBrowserEdge(UserAgent.EDGE.matches(getUserAgent()));

		if (properties.isBrowserEdge())
		{
			// e.g.: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)
			// Chrome/52.0.2743.116 Safari/537.36 Edge/15.15063
			setMajorMinorVersionByPattern("edge/(\\d+)\\.(\\d+)");
		}
	}

	/**
	 * sets the safari specific properties
	 */
	private void setSafariProperties()
	{
		properties.setBrowserSafari(UserAgent.SAFARI.matches(getUserAgent()));

		if (properties.isBrowserSafari())
		{
			String userAgent = getUserAgentStringLc();

			if (userAgent.contains("version/"))
			{
				// e.g.: Mozilla/5.0 (Windows; U; Windows NT 6.1; sv-SE) AppleWebKit/533.19
				// (KHTML, like Gecko) Version/5.0.3 Safari/533.19.4
				setMajorMinorVersionByPattern("version/(\\d+)\\.(\\d+)");
			}
		}
	}

	/**
	 * sets the mozilla/firefox specific properties
	 */
	private void setMozillaProperties()
	{
		properties.setBrowserMozillaFirefox(UserAgent.FIREFOX.matches(getUserAgent()));
		properties.setBrowserMozilla(UserAgent.MOZILLA.matches(getUserAgent()));

		if (properties.isBrowserMozilla())
		{
			if (properties.isBrowserMozillaFirefox())
			{
				// e.g.: Mozilla/5.0 (X11; U; Linux i686; pl-PL; rv:1.9.0.2) Gecko/20121223
				// Ubuntu/9.25 (jaunty) Firefox/3.8
				setMajorMinorVersionByPattern("firefox/(\\d+)\\.(\\d+)");
			}
		}
	}

	/**
	 * sets the opera specific properties
	 */
	private void setOperaProperties()
	{
		properties.setBrowserOpera(UserAgent.OPERA.matches(getUserAgent()));

		if (properties.isBrowserOpera())
		{
			String userAgent = getUserAgentStringLc();

			if (userAgent.startsWith("opera/") && userAgent.contains("version/"))
			{
				// e.g.: Opera/9.80 (Windows NT 6.0; U; nl) Presto/2.6.30 Version/10.60
				setMajorMinorVersionByPattern("version/(\\d+)\\.(\\d+)");
			}
			else if (userAgent.startsWith("opera/") && !userAgent.contains("version/"))
			{
				// e.g.: Opera/9.80 (Windows NT 6.0; U; nl) Presto/2.6.30
				setMajorMinorVersionByPattern("opera/(\\d+)\\.(\\d+)");
			}
			else
			{
				// e.g.: Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 6.0; tr) Opera 10.10
				setMajorMinorVersionByPattern("opera (\\d+)\\.(\\d+)");
			}
		}
	}

	/**
	 * sets the ie specific properties
	 */
	private void setInternetExplorerProperties()
	{
		properties.setBrowserInternetExplorer(UserAgent.INTERNET_EXPLORER.matches(getUserAgent()));

		if (properties.isBrowserInternetExplorer())
		{
			// modern IE browsers (>=IE11) uses new user agent format
			if (getUserAgentStringLc().contains("like gecko"))
			{
				setMajorMinorVersionByPattern("rv:(\\d+)\\.(\\d+)");
			}
			else
			{
				setMajorMinorVersionByPattern("msie (\\d+)\\.(\\d+)");
			}
		}
	}

	/**
	 * extracts the major and minor version out of the userAgentString string.
	 * 
	 * @param patternString
	 *            The pattern must contain two matching groups
	 */
	private void setMajorMinorVersionByPattern(String patternString)
	{
		String userAgent = getUserAgentStringLc();
		Matcher matcher = Pattern.compile(patternString).matcher(userAgent);

		if (matcher.find())
		{
			properties.setBrowserVersionMajor(StringValue.valueOf(matcher.group(1)).toInt(-1));
			properties.setBrowserVersionMinor(StringValue.valueOf(matcher.group(2)).toInt(-1));
		}
	}
}
