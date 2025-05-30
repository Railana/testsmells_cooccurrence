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

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Locale;

import org.apache.wicket.mock.MockWebRequest;
import org.apache.wicket.request.Url;
import org.apache.wicket.util.tester.WicketTestCase;
import org.junit.Test;

public class SessionDestroyTest extends WicketTestCase
{
	/**
	 * https://issues.apache.org/jira/browse/WICKET-6310
	 */
	@Test
	public void whenSessionIsDestroyed_thenItShouldResetItsState()
	{
		final Locale locale = Locale.ENGLISH;
		MockWebRequest request = new MockWebRequest(Url.parse("/"))
		{
			@Override
			public Locale getLocale()
			{
				return locale;
			}
		};

		final WebSession session = spy(new WebSession(request));

		// initially #invalidateNow() (and destroy()) are not called
		verify(session, never()).invalidateNow();
		assertThat(session.isSessionInvalidated(), is(false));

		// schedule invalidation
		session.invalidate();

		// the invalidation will happen on #end(), so #destroy() is still not called
		verify(session, never()).endRequest();
		assertThat(session.isSessionInvalidated(), is(true));

		session.endRequest();

		// the session has been detached so #destroy() has been called and 'sessionInvalidated' is reset
		verify(session, times(1)).endRequest();
		assertThat(session.isSessionInvalidated(), is(false));

		// no matter how many times #detach() is called #destroy() should not be called
		session.endRequest();
		verify(session, times(1)).invalidateNow();
		session.endRequest();
		session.endRequest();
		verify(session, times(1)).invalidateNow();
		assertThat(session.isSessionInvalidated(), is(false));

	}
}
