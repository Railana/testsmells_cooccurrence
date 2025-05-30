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
package org.apache.wicket.request.cycle;

import java.util.Optional;

import org.apache.wicket.Application;
import org.apache.wicket.IWicketInternalException;
import org.apache.wicket.MetaDataEntry;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.Page;
import org.apache.wicket.Session;
import org.apache.wicket.ThreadContext;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.core.request.handler.BookmarkablePageRequestHandler;
import org.apache.wicket.core.request.handler.IPageProvider;
import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.event.IEventSink;
import org.apache.wicket.protocol.http.IRequestLogger;
import org.apache.wicket.request.IExceptionMapper;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.IRequestMapper;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.RequestHandlerExecutor;
import org.apache.wicket.request.RequestHandlerExecutor.ReplaceHandlerException;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.UrlRenderer;
import org.apache.wicket.request.component.IRequestablePage;
import org.apache.wicket.request.handler.resource.ResourceReferenceRequestHandler;
import org.apache.wicket.request.handler.resource.ResourceRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.IResource;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.request.resource.caching.IStaticCacheableResource;
import org.apache.wicket.util.lang.Args;
import org.apache.wicket.util.lang.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RequestCycle} consists of two steps:
 * <ol>
 * <li>Resolve request handler
 * <li>Execute request handler
 * </ol>
 * During {@link IRequestHandler} execution the handler can schedule another {@link IRequestHandler}
 * to run after it is done, or replace all {@link IRequestHandler}s on stack with another
 * {@link IRequestHandler}.
 * 
 * @see #scheduleRequestHandlerAfterCurrent(IRequestHandler)
 * @see #replaceAllRequestHandlers(IRequestHandler)
 * 
 * @author Matej Knopp
 * @author igor.vaynberg
 */
public class RequestCycle implements IRequestCycle, IEventSink
{
	private static final Logger log = LoggerFactory.getLogger(RequestCycle.class);

	/**
	 * An additional logger which is used to log extra information.
	 * Could be disabled separately than the main logger if the application developer
	 * does not want to see this extra information.
	 */
	private static final Logger logExtra = LoggerFactory.getLogger("RequestCycleExtra");

	/**
	 * Returns request cycle associated with current thread.
	 * 
	 * @return request cycle instance or <code>null</code> if no request cycle is associated with
	 *         current thread.
	 */
	public static RequestCycle get()
	{
		return ThreadContext.getRequestCycle();
	}

	/**
	 * 
	 * @param requestCycle
	 */
	private static void set(RequestCycle requestCycle)
	{
		ThreadContext.setRequestCycle(requestCycle);
	}

	private Request request;

	private final Response originalResponse;

	private final IRequestMapper requestMapper;

	private final IExceptionMapper exceptionMapper;

	private final RequestCycleListenerCollection listeners;

	private UrlRenderer urlRenderer;

	/** MetaDataEntry array. */
	private MetaDataEntry<?>[] metaData;

	/** the time that this request cycle object was created. */
	private final long startTime;

	private final RequestHandlerExecutor requestHandlerExecutor;

	private Response activeResponse;

	/**
	 * Construct.
	 * 
	 * @param context
	 */
	public RequestCycle(RequestCycleContext context)
	{
		Args.notNull(context, "context");
		Args.notNull(context.getRequest(), "context.request");
		Args.notNull(context.getResponse(), "context.response");
		Args.notNull(context.getRequestMapper(), "context.requestMapper");
		Args.notNull(context.getExceptionMapper(), "context.exceptionMapper");

		listeners = new RequestCycleListenerCollection();
		startTime = System.currentTimeMillis();
		requestHandlerExecutor = new HandlerExecutor();
		activeResponse = context.getResponse();
		request = context.getRequest();
		originalResponse = context.getResponse();
		requestMapper = context.getRequestMapper();
		exceptionMapper = context.getExceptionMapper();
	}

	/**
	 * 
	 * @return a new url renderer
	 */
	protected UrlRenderer newUrlRenderer()
	{
		// All URLs will be rendered relative to current request (can be overridden afterwards)
		return new UrlRenderer(getRequest());
	}

	/**
	 * Get the original response the request was created with. Access to the original response may
	 * be necessary if the response has been temporarily replaced but the components require methods
	 * from original response (i.e. cookie methods of WebResponse, etc).
	 * 
	 * @return The original response object.
	 */
	public Response getOriginalResponse()
	{
		return originalResponse;
	}

	/**
	 * Returns {@link UrlRenderer} for this {@link RequestCycle}.
	 * 
	 * @return UrlRenderer instance.
	 */
	@Override
	public final UrlRenderer getUrlRenderer()
	{
		if (urlRenderer == null)
		{
			urlRenderer = newUrlRenderer();
		}
		return urlRenderer;
	}

	/**
	 * Resolves current request to a {@link IRequestHandler}.
	 * 
	 * @return RequestHandler instance
	 */
	protected IRequestHandler resolveRequestHandler()
	{
		return requestMapper.mapRequest(request);
	}

	/**
	 * @return How many times will Wicket attempt to render the exception request handler before
	 *         giving up.
	 */
	protected int getExceptionRetryCount()
	{
		int retries = 10;
		if (Application.exists())
		{
			retries = Application.get().getRequestCycleSettings().getExceptionRetryCount();
		}
		return retries;
	}

	/**
	 * Convenience method that processes the request and detaches the {@link RequestCycle}.
	 * 
	 * @return <code>true</code> if the request resolved to a Wicket request, <code>false</code>
	 *         otherwise.
	 */
	public boolean processRequestAndDetach()
	{
		boolean result;
		try
		{
			result = processRequest();
		}
		finally
		{
			detach();
		}
		return result;
	}

	/**
	 * Processes the request.
	 * 
	 * @return <code>true</code> if the request resolved to a Wicket request, <code>false</code>
	 *         otherwise.
	 */
	public boolean processRequest()
	{
		try
		{
			set(this);
			listeners.onBeginRequest(this);
			onBeginRequest();
			IRequestHandler handler = resolveRequestHandler();
			if (handler == null)
			{
				// Did not find any suitable handler, thus not executing the request
				log.debug(
					"No suitable handler found for URL {}, falling back to container to process this request",
					request.getUrl());
			}
			else
			{
				execute(handler);
				return true;
			}
		}
		catch (Exception exception)
		{
			return executeExceptionRequestHandler(exception, getExceptionRetryCount());
		}
		finally
		{
			try
			{
				listeners.onEndRequest(this);
				onEndRequest();
			}
			catch (RuntimeException e)
			{
				log.error("Exception occurred during onEndRequest", e);
			}

			set(null);
		}

		return false;
	}

	/**
	 * Execute a request handler and notify registered {@link IRequestCycleListener}s.
	 * 
	 * @param handler
	 */
	private void execute(IRequestHandler handler)
	{
		Args.notNull(handler, "handler");

		while (handler != null) {
			try
			{
				listeners.onRequestHandlerResolved(this, handler);
				IRequestHandler next = requestHandlerExecutor.execute(handler);
				listeners.onRequestHandlerExecuted(this, handler);
				
				handler = next;
			}
			catch (RuntimeException e)
			{
				ReplaceHandlerException replacer = Exceptions.findCause(e, ReplaceHandlerException.class);

				if (replacer == null)
				{
					throw e;
				}

				if (replacer.getRemoveScheduled())
				{
					requestHandlerExecutor.schedule(null);
				}

				handler = replacer.getReplacementRequestHandler();
			}
		}
	}

	/**
	 * Process the given exception.
	 * 
	 * @param exception
	 * @param retryCount
	 */
	private boolean executeExceptionRequestHandler(Exception exception, int retryCount)
	{
		IRequestHandler handler = handleException(exception);
		if (handler == null)
		{
			log.error("Error during request processing. URL=" + request.getUrl(), exception);
			return false;
		}

		scheduleRequestHandlerAfterCurrent(null);

		try
		{
			listeners.onExceptionRequestHandlerResolved(this, handler, exception);

			execute(handler);
			
			return true;
		}
		catch (Exception e)
		{
			if (retryCount > 0)
			{
				return executeExceptionRequestHandler(exception, retryCount - 1);
			}
			else
			{
				log.error("Exception retry count exceeded", e);
				return false;
			}
		}
	}

	/**
	 * Return {@link IRequestHandler} for the given exception.
	 * 
	 * @param e exception to handle
	 * @return RequestHandler instance
	 *
	 * @see IRequestCycleListener#onException(RequestCycle, Exception)
	 * @see IExceptionMapper#map(Exception)
	 */
	protected IRequestHandler handleException(final Exception e)
	{

		if (Application.exists() && Application.get().usesDevelopmentConfig())
		{
			/*
			 * Call out the fact that we are processing an exception in a loud way, helps to notice
			 * them when developing even if they get wrapped or processed in a custom handler.
			 */
			logExtra.warn("********************************");
			logExtra.warn("Handling the following exception", e);
			logExtra.warn("********************************");
		}

		IRequestHandler handler = listeners.onException(this, e);
		if (handler != null)
		{
			return handler;
		}
		return exceptionMapper.map(e);
	}

	/**
	 * @return current request
	 */
	@Override
	public Request getRequest()
	{
		return request;
	}

	/**
	 * INTERNAL This method is for internal Wicket use. Do not call it yourself unless you know what
	 * you are doing.
	 * 
	 * @param request
	 */
	public void setRequest(Request request)
	{
		// It would be mighty nice if request was final. However during multipart it needs to be set
		// to
		// MultipartServletWebRequest by Form. It can't be done before creating the request cycle
		// (in wicket filter)
		// because only the form knows max upload size
		this.request = request;
	}

	/**
	 * Sets the metadata for this request cycle using the given key. If the metadata object is not
	 * of the correct type for the metadata key, an IllegalArgumentException will be thrown. For
	 * information on creating MetaDataKeys, see {@link MetaDataKey}.
	 * 
	 * @param key
	 *            The singleton key for the metadata
	 * @param object
	 *            The metadata object
	 * @param <T>
	 * @throws IllegalArgumentException
	 * @see MetaDataKey
	 */
	public final <T> RequestCycle setMetaData(final MetaDataKey<T> key, final T object)
	{
		metaData = key.set(metaData, object);
		return this;
	}

	/**
	 * Gets metadata for this request cycle using the given key.
	 * 
	 * @param <T>
	 *            The type of the metadata
	 * 
	 * @param key
	 *            The key for the data
	 * @return The metadata or null if no metadata was found for the given key
	 * @see MetaDataKey
	 */
	public final <T> T getMetaData(final MetaDataKey<T> key)
	{
		return key.get(metaData);
	}

	/**
	 * Returns URL for the request handler or <code>null</code> if the handler couldn't have been
	 * encoded.
	 * <p>
	 * <strong>Note</strong>: The produced URL is relative to the filter path. Application code most
	 * probably need URL relative to the currently used page, for this use
	 * {@linkplain #urlFor(org.apache.wicket.request.IRequestHandler)}
	 * </p>
	 * 
	 * @param handler
	 *            the {@link IRequestHandler request handler} for which to create a callback url
	 * @return Url instance or <code>null</code>
	 */
	public Url mapUrlFor(IRequestHandler handler)
	{
		final Url url = requestMapper.mapHandler(handler);
		listeners.onUrlMapped(this, handler, url);
		return url;
	}

	/**
	 * Returns a {@link Url} for the resource reference
	 * <p>
	 * <strong>Note</strong>: The produced URL is relative to the filter path. Application code most
	 * probably need URL relative to the currently used page, for this use
	 * {@linkplain #urlFor(org.apache.wicket.request.resource.ResourceReference, org.apache.wicket.request.mapper.parameter.PageParameters)}
	 * </p>
	 * 
	 * @param reference
	 *            resource reference
	 * @param params
	 *            parameters for the resource or {@code null} if none
	 * @return {@link Url} for the reference
	 */
	public Url mapUrlFor(ResourceReference reference, PageParameters params)
	{
		return mapUrlFor(new ResourceReferenceRequestHandler(reference, params));
	}

	/**
	 * Returns a bookmarkable URL that references a given page class using a given set of page
	 * parameters. Since the URL which is returned contains all information necessary to instantiate
	 * and render the page, it can be stored in a user's browser as a stable bookmark.
	 * <p>
	 * <strong>Note</strong>: The produced URL is relative to the filter path. Application code most
	 * probably need URL relative to the currently used page, for this use
	 * {@linkplain #urlFor(Class, org.apache.wicket.request.mapper.parameter.PageParameters)}
	 * </p>
	 * 
	 * @param <C>
	 *            The type of the page
	 * @param pageClass
	 *            Class of page
	 * @param parameters
	 *            Parameters to page or {@code null} if none
	 * @return Bookmarkable URL to page
	 */
	public final <C extends Page> Url mapUrlFor(final Class<C> pageClass,
		final PageParameters parameters)
	{
		IRequestHandler handler = new BookmarkablePageRequestHandler(new PageProvider(pageClass,
			parameters));
		return mapUrlFor(handler);
	}

	/**
	 * Returns a rendered {@link Url} for the resource reference
	 * 
	 * @param reference
	 *            resource reference
	 * @param params
	 *            parameters for the resource or {@code null} if none
	 * @return {@link Url} for the reference
	 */
	public final CharSequence urlFor(ResourceReference reference, PageParameters params)
	{
		ResourceReferenceRequestHandler handler = new ResourceReferenceRequestHandler(reference,
			params);
		return urlFor(handler);
	}

	/**
	 * Returns a rendered bookmarkable URL that references a given page class using a given set of
	 * page parameters. Since the URL which is returned contains all information necessary to
	 * instantiate and render the page, it can be stored in a user's browser as a stable bookmark.
	 * 
	 * @param <C>
	 * 
	 * @param pageClass
	 *            Class of page
	 * @param parameters
	 *            Parameters to page or {@code null} if none
	 * @return Bookmarkable URL to page
	 */
	public final <C extends Page> CharSequence urlFor(final Class<C> pageClass,
		final PageParameters parameters)
	{
		IRequestHandler handler = new BookmarkablePageRequestHandler(new PageProvider(pageClass,
			parameters));
		return urlFor(handler);
	}

	/**
	 * Returns the rendered URL for the request handler or <code>null</code> if the handler couldn't
	 * have been rendered.
	 * <p>
	 * The resulting URL will be relative to current page.
	 * 
	 * @param handler
	 * @return Url String or <code>null</code>
	 */
	public CharSequence urlFor(IRequestHandler handler)
	{
		try
		{
			Url mappedUrl = mapUrlFor(handler);
			CharSequence url = renderUrl(mappedUrl, handler);
			return url;
		}
		catch (Exception x)
		{
			throw new WicketRuntimeException(String.format(
				"An error occurred while generating an Url for handler '%s'", handler), x);
		}

	}

	private String renderUrl(Url url, IRequestHandler handler)
	{
		if (url != null)
		{
			boolean shouldEncodeStaticResource = Application.exists() &&
				Application.get().getResourceSettings().isEncodeJSessionId();

			String renderedUrl = getUrlRenderer().renderUrl(url);
			if (handler instanceof ResourceReferenceRequestHandler)
			{
				ResourceReferenceRequestHandler rrrh = (ResourceReferenceRequestHandler)handler;
				IResource resource = rrrh.getResource();
				if (resource != null && !(resource instanceof IStaticCacheableResource) ||
					shouldEncodeStaticResource)
				{
					renderedUrl = getOriginalResponse().encodeURL(renderedUrl);
				}
			}
			else if (handler instanceof ResourceRequestHandler)
			{
				ResourceRequestHandler rrh = (ResourceRequestHandler)handler;
				IResource resource = rrh.getResource();
				if (resource != null && !(resource instanceof IStaticCacheableResource) ||
					shouldEncodeStaticResource)
				{
					renderedUrl = getOriginalResponse().encodeURL(renderedUrl);
				}
			}
			else
			{
				renderedUrl = getOriginalResponse().encodeURL(renderedUrl);
			}
			return renderedUrl;
		}
		else
		{
			return null;
		}
	}

	/**
	 * Detaches {@link RequestCycle} state. Called after request processing is complete
	 */
	public final void detach()
	{
		set(this);
		try
		{
			onDetach();
		}
		finally
		{
			try
			{
				onInternalDetach();
			}
			finally
			{
				set(null);
			}
		}
	}

	private void onInternalDetach()
	{
		if (Session.exists())
		{
			Session.get().internalDetach();
		}

		if (Application.exists())
		{
			IRequestLogger requestLogger = Application.get().getRequestLogger();
			if (requestLogger != null)
				requestLogger.performLogging();
		}
	}

	/**
	 * Called after request processing is complete, usually takes care of detaching state
	 */
	public void onDetach()
	{
		try
		{
			requestHandlerExecutor.detach();
		}
		catch (RuntimeException exception)
		{
			handleDetachException(exception);
		}
		finally
		{
			listeners.onDetach(this);
		}

		if (Session.exists())
		{
			Session.get().detach();
		}

	}

	/**
	 * Called to handle a {@link java.lang.RuntimeException} that might be 
	 * thrown during detaching phase. 
	 * 
	 * @param exception
	 */
	private void handleDetachException(RuntimeException exception) 
	{
		if (!(exception instanceof IWicketInternalException))
		{
			log.error("Error detaching RequestCycle", exception);
		}
	}

	/**
	 * Convenience method for setting next page to be rendered.
	 * 
	 * @param page
	 */
	public void setResponsePage(IRequestablePage page)
	{
		if (page instanceof Page)
		{
			((Page) page).setStatelessHint(false);
		}

		scheduleRequestHandlerAfterCurrent(new RenderPageRequestHandler(new PageProvider(page),
			RenderPageRequestHandler.RedirectPolicy.AUTO_REDIRECT));
	}

	/**
	 * Convenience method for setting next page to be rendered.
	 * 
	 * @param pageClass
	 *              The class of the page to render
	 */
	public void setResponsePage(Class<? extends IRequestablePage> pageClass)
	{
		setResponsePage(pageClass, null, RenderPageRequestHandler.RedirectPolicy.ALWAYS_REDIRECT);
	}

	/**
	 * Convenience method for setting next page to be rendered.
	 *
	 * @param pageClass
	 *              The class of the page to render
	 * @param redirectPolicy
	 *              The policy to use when deciding whether to redirect or not
	 */
	public void setResponsePage(Class<? extends IRequestablePage> pageClass, RenderPageRequestHandler.RedirectPolicy redirectPolicy)
	{
		setResponsePage(pageClass, null, redirectPolicy);
	}

	/**
	 * Convenience method for setting next page to be rendered.
	 * 
	 * @param pageClass
	 *              The class of the page to render
	 * @param parameters
	 *              The query parameters for the page to be rendered
	 */
	public void setResponsePage(Class<? extends IRequestablePage> pageClass,
		PageParameters parameters)
	{
		setResponsePage(pageClass, parameters, RenderPageRequestHandler.RedirectPolicy.ALWAYS_REDIRECT);
	}

	/**
	 * Convenience method for setting next page to be rendered.
	 *
	 * @param pageClass
	 *              The class of the page to render
	 * @param parameters
	 *              The query parameters for the page to be rendered
	 * @param redirectPolicy
	 *              The policy to use when deciding whether to redirect or not
	 */
	public void setResponsePage(Class<? extends IRequestablePage> pageClass,
	                            PageParameters parameters, RenderPageRequestHandler.RedirectPolicy redirectPolicy)
	{
		IPageProvider provider = new PageProvider(pageClass, parameters);
		scheduleRequestHandlerAfterCurrent(new RenderPageRequestHandler(provider,
				redirectPolicy));
	}

	/**
	 * @return The start time for this request
	 */
	public final long getStartTime()
	{
		return startTime;
	}

	/** {@inheritDoc} */
	@Override
	public void onEvent(IEvent<?> event)
	{
	}

	/**
	 * Called when the request cycle object is beginning its response
	 */
	protected void onBeginRequest()
	{
	}

	/**
	 * Called when the request cycle object has finished its response
	 */
	protected void onEndRequest()
	{
		if (Session.exists())
		{
			Session.get().endRequest();
		}
	}

	/**
	 * @return listeners
	 */
	public RequestCycleListenerCollection getListeners()
	{
		return listeners;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Response getResponse()
	{
		return activeResponse;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Response setResponse(final Response response)
	{
		Response current = activeResponse;
		activeResponse = response;
		return current;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void scheduleRequestHandlerAfterCurrent(IRequestHandler handler)
	{
		// just delegating the call to {@link IRequestHandlerExecutor} and invoking listeners
		requestHandlerExecutor.schedule(handler);

		// only forward calls to the listeners when handler is null
		if (handler != null)
			listeners.onRequestHandlerScheduled(this, handler);
	}

	/**
	 * @see RequestHandlerExecutor#getActive()
	 * @return active handler on executor
	 */
	public IRequestHandler getActiveRequestHandler()
	{
		return requestHandlerExecutor.getActive();
	}

	/**
	 * @see RequestHandlerExecutor#next()
	 * @return the handler scheduled to be executed after current by the executor
	 */
	public IRequestHandler getRequestHandlerScheduledAfterCurrent()
	{
		return requestHandlerExecutor.next();
	}

	/**
	 * @see RequestHandlerExecutor#replaceAll(IRequestHandler)
	 * @param handler
	 */
	public void replaceAllRequestHandlers(final IRequestHandler handler)
	{
		requestHandlerExecutor.replaceAll(handler);
	}

	/**
	 * Finds a IRequestHandler which is either the currently executing handler or is scheduled to be
	 * executed.
	 * 
	 * @return the found IRequestHandler or {@link Optional#empty()}
	 */
	@SuppressWarnings("unchecked")
	public <T extends IRequestHandler> Optional<T> find(final Class<T> type)
	{
		if (type == null)
		{
			return Optional.empty();
		}

		IRequestHandler result = getActiveRequestHandler();
		if (type.isInstance(result))
		{
			return (Optional<T>)Optional.of(result);
		}
		
		result = getRequestHandlerScheduledAfterCurrent();
		if (type.isInstance(result))
		{
			return (Optional<T>)Optional.of(result);
		}

		return Optional.empty();
	}

	/**
	 * Adapts {@link RequestHandlerExecutor} to this {@link RequestCycle}
	 * 
	 * @author Igor Vaynberg
	 */
	private class HandlerExecutor extends RequestHandlerExecutor
	{

		@Override
		protected void respond(IRequestHandler handler)
		{
			Response originalResponse = getResponse();
			try
			{
				handler.respond(RequestCycle.this);
			}
			finally
			{
				setResponse(originalResponse);
			}
		}

		@Override
		protected void detach(IRequestHandler handler)
		{
			handler.detach(RequestCycle.this);
		}

	}

}
