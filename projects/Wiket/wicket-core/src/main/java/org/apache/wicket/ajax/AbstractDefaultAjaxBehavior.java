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
package org.apache.wicket.ajax;

import java.util.List;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONException;
import com.github.openjson.JSONObject;
import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.ajax.attributes.AjaxAttributeName;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes.Method;
import org.apache.wicket.ajax.attributes.CallbackParameter;
import org.apache.wicket.ajax.attributes.IAjaxCallListener;
import org.apache.wicket.ajax.attributes.ThrottlingSettings;
import org.apache.wicket.ajax.json.JSONFunction;
import org.apache.wicket.ajax.json.JsonUtils;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.IComponentAwareHeaderContributor;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.resource.CoreLibrariesContributor;
import org.apache.wicket.util.string.Strings;
import org.apache.wicket.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

/**
 * The base class for Wicket's default AJAX implementation.
 * 
 * @since 1.2
 * 
 * @author Igor Vaynberg (ivaynberg)
 * 
 */
public abstract class AbstractDefaultAjaxBehavior extends AbstractAjaxBehavior
{
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(AbstractDefaultAjaxBehavior.class);

	/** reference to the default indicator gif file. */
	public static final ResourceReference INDICATOR = new PackageResourceReference(
		AbstractDefaultAjaxBehavior.class, "indicator.gif");

	private static final String DYNAMIC_PARAMETER_FUNCTION_SIGNATURE = "function(attrs)";
	private static final String PRECONDITION_FUNCTION_SIGNATURE = "function(attrs)";
	private static final String COMPLETE_HANDLER_FUNCTION_SIGNATURE = "function(attrs, jqXHR, textStatus)";
	private static final String FAILURE_HANDLER_FUNCTION_SIGNATURE = "function(attrs, jqXHR, errorMessage, textStatus)";
	private static final String SUCCESS_HANDLER_FUNCTION_SIGNATURE = "function(attrs, jqXHR, data, textStatus)";
	private static final String AFTER_HANDLER_FUNCTION_SIGNATURE = "function(attrs)";
	private static final String BEFORE_SEND_HANDLER_FUNCTION_SIGNATURE = "function(attrs, jqXHR, settings)";
	private static final String BEFORE_HANDLER_FUNCTION_SIGNATURE = "function(attrs)";
	private static final String INIT_HANDLER_FUNCTION_SIGNATURE = "function(attrs)";
	private static final String DONE_HANDLER_FUNCTION_SIGNATURE = "function(attrs)";

	/**
	 * Subclasses should call super.onBind()
	 * 
	 * @see org.apache.wicket.behavior.AbstractAjaxBehavior#onBind()
	 */
	@Override
	protected void onBind()
	{
		final Component component = getComponent();
		
		component.setOutputMarkupId(true);
		
		if (getStatelessHint(component))
		{
			//generate behavior id
			component.getBehaviorId(this);
		}
	}

	/**
	 * @see org.apache.wicket.behavior.AbstractAjaxBehavior#renderHead(Component,
	 *      org.apache.wicket.markup.head.IHeaderResponse)
	 */
	@Override
	public void renderHead(final Component component, final IHeaderResponse response)
	{
		super.renderHead(component, response);

		CoreLibrariesContributor.contributeAjax(component.getApplication(), response);

		RequestCycle requestCycle = component.getRequestCycle();
		Url baseUrl = requestCycle.getUrlRenderer().getBaseUrl();
		CharSequence ajaxBaseUrl = Strings.escapeMarkup(baseUrl.toString());
		response.render(JavaScriptHeaderItem.forScript("Wicket.Ajax.baseUrl=\"" + ajaxBaseUrl
			+ "\";", "wicket-ajax-base-url"));

		renderExtraHeaderContributors(component, response);
	}

	/**
	 * Renders header contribution by IAjaxCallListener instances which additionally implement
	 * IComponentAwareHeaderContributor interface.
	 * 
	 * @param component
	 *            the component assigned to this behavior
	 * @param response
	 *            the current header response
	 */
	private void renderExtraHeaderContributors(final Component component,
		final IHeaderResponse response)
	{
		AjaxRequestAttributes attributes = getAttributes();

		List<IAjaxCallListener> ajaxCallListeners = attributes.getAjaxCallListeners();
		for (IAjaxCallListener ajaxCallListener : ajaxCallListeners)
		{
			if (ajaxCallListener instanceof IComponentAwareHeaderContributor)
			{
				IComponentAwareHeaderContributor contributor = (IComponentAwareHeaderContributor)ajaxCallListener;
				contributor.renderHead(component, response);
			}
		}
	}

	/**
	 * @return the Ajax settings for this behavior
	 * @since 6.0
	 */
	protected final AjaxRequestAttributes getAttributes()
	{
		AjaxRequestAttributes attributes = new AjaxRequestAttributes();
		WebApplication application = (WebApplication)getComponent().getApplication();
		AjaxRequestTargetListenerCollection ajaxRequestTargetListeners = application
			.getAjaxRequestTargetListeners();
		for (AjaxRequestTarget.IListener listener : ajaxRequestTargetListeners)
		{
			listener.updateAjaxAttributes(this, attributes);
		}
		updateAjaxAttributes(attributes);
		return attributes;
	}

	/**
	 * This method decides whether to continue processing or to abort the Ajax request when the method
	 * is different than the {@link AjaxRequestAttributes#getMethod()}'s method.
	 *
	 * @return response that can either abort or continue the processing of the Ajax request
	 */
	protected Form.MethodMismatchResponse onMethodMismatch()
	{
		return Form.MethodMismatchResponse.CONTINUE;
	}

	/**
	 * Gives a chance to the specializations to modify the attributes.
	 * 
	 * @param attributes
	 * @since 6.0
	 */
	protected void updateAjaxAttributes(AjaxRequestAttributes attributes)
	{
	}

	/**
	 * <pre>
	 * 				{
	 * 					u: 'editable-label?6-1.IBehaviorListener.0-text1-label',  // url
	 * 					m: 'POST',          // method name. Default: 'GET'
	 * 					c: 'label7',        // component id (String) or window for page
	 * 					e: 'click',         // event name
	 * 					sh: [],             // list of success handlers
	 * 					fh: [],             // list of failure handlers
	 * 					pre: [],            // list of preconditions. If empty set default : Wicket.$(settings{c}) !== null
	 * 					ep: {},             // extra parameters
	 * 					async: true|false,  // asynchronous XHR or not
	 * 					ch: 'someName|d',   // AjaxChannel
	 * 					i: 'indicatorId',   // indicator component id
	 * 					ad: true,           // allow default
	 * 				}
	 * </pre>
	 * 
	 * @param component
	 *            the component with that behavior
	 * @return the attributes as string in JSON format
	 */
	protected final CharSequence renderAjaxAttributes(final Component component)
	{
		AjaxRequestAttributes attributes = getAttributes();
		return renderAjaxAttributes(component, attributes);
	}

	/**
	 * 
	 * @param component
	 * @param attributes
	 * @return the attributes as string in JSON format
	 */
	protected final CharSequence renderAjaxAttributes(final Component component,
		AjaxRequestAttributes attributes)
	{
		JSONObject attributesJson = new JSONObject();

		try
		{
			attributesJson.put(AjaxAttributeName.URL.jsonName(), getCallbackUrl());
			Method method = attributes.getMethod();
			if (Method.POST == method)
			{
				attributesJson.put(AjaxAttributeName.METHOD.jsonName(), method);
			}

			if (component instanceof Page == false)
			{
				String componentId = component.getMarkupId();
				attributesJson.put(AjaxAttributeName.MARKUP_ID.jsonName(), componentId);
			}

			String formId = attributes.getFormId();
			if (Strings.isEmpty(formId) == false)
			{
				attributesJson.put(AjaxAttributeName.FORM_ID.jsonName(), formId);
			}

			if (attributes.isMultipart())
			{
				attributesJson.put(AjaxAttributeName.IS_MULTIPART.jsonName(), true);
			}

			String submittingComponentId = attributes.getSubmittingComponentName();
			if (Strings.isEmpty(submittingComponentId) == false)
			{
				attributesJson.put(AjaxAttributeName.SUBMITTING_COMPONENT_NAME.jsonName(),
					submittingComponentId);
			}

			CharSequence childSelector = attributes.getChildSelector();
			if (Strings.isEmpty(childSelector) == false)
			{
				attributesJson.put(AjaxAttributeName.CHILD_SELECTOR.jsonName(),
						childSelector);
			}

			if (attributes.isSerializeRecursively())
			{
				attributesJson.put(AjaxAttributeName.SERIALIZE_RECURSIVELY.jsonName(), true);
			}

			String indicatorId = findIndicatorId();
			if (Strings.isEmpty(indicatorId) == false)
			{
				attributesJson.put(AjaxAttributeName.INDICATOR_ID.jsonName(), indicatorId);
			}

			for (IAjaxCallListener ajaxCallListener : attributes.getAjaxCallListeners())
			{
				if (ajaxCallListener != null)
				{
					CharSequence initHandler = ajaxCallListener.getInitHandler(component);
					appendListenerHandler(initHandler, attributesJson,
						AjaxAttributeName.INIT_HANDLER.jsonName(),
						INIT_HANDLER_FUNCTION_SIGNATURE);

						CharSequence beforeHandler = ajaxCallListener.getBeforeHandler(component);
					appendListenerHandler(beforeHandler, attributesJson,
						AjaxAttributeName.BEFORE_HANDLER.jsonName(),
						BEFORE_HANDLER_FUNCTION_SIGNATURE);

					CharSequence beforeSendHandler = ajaxCallListener
						.getBeforeSendHandler(component);
					appendListenerHandler(beforeSendHandler, attributesJson,
						AjaxAttributeName.BEFORE_SEND_HANDLER.jsonName(),
						BEFORE_SEND_HANDLER_FUNCTION_SIGNATURE);

					CharSequence afterHandler = ajaxCallListener.getAfterHandler(component);
					appendListenerHandler(afterHandler, attributesJson,
						AjaxAttributeName.AFTER_HANDLER.jsonName(), AFTER_HANDLER_FUNCTION_SIGNATURE);

					CharSequence successHandler = ajaxCallListener.getSuccessHandler(component);
					appendListenerHandler(successHandler, attributesJson,
						AjaxAttributeName.SUCCESS_HANDLER.jsonName(),
						SUCCESS_HANDLER_FUNCTION_SIGNATURE);

					CharSequence failureHandler = ajaxCallListener.getFailureHandler(component);
					appendListenerHandler(failureHandler, attributesJson,
						AjaxAttributeName.FAILURE_HANDLER.jsonName(),
						FAILURE_HANDLER_FUNCTION_SIGNATURE);

					CharSequence completeHandler = ajaxCallListener.getCompleteHandler(component);
					appendListenerHandler(completeHandler, attributesJson,
						AjaxAttributeName.COMPLETE_HANDLER.jsonName(),
						COMPLETE_HANDLER_FUNCTION_SIGNATURE);

					CharSequence precondition = ajaxCallListener.getPrecondition(component);
					appendListenerHandler(precondition, attributesJson,
						AjaxAttributeName.PRECONDITION.jsonName(), PRECONDITION_FUNCTION_SIGNATURE);

					CharSequence doneHandler = ajaxCallListener.getDoneHandler(component);
					appendListenerHandler(doneHandler, attributesJson,
						AjaxAttributeName.DONE_HANDLER.jsonName(),
						DONE_HANDLER_FUNCTION_SIGNATURE);

				}
			}

			JSONArray extraParameters = JsonUtils.asArray(attributes.getExtraParameters());

			if (extraParameters.length() > 0)
			{
				attributesJson.put(AjaxAttributeName.EXTRA_PARAMETERS.jsonName(), extraParameters);
			}

			List<CharSequence> dynamicExtraParameters = attributes.getDynamicExtraParameters();
			if (dynamicExtraParameters != null)
			{
				for (CharSequence dynamicExtraParameter : dynamicExtraParameters)
				{
					JSONFunction function = getJsonFunction(DYNAMIC_PARAMETER_FUNCTION_SIGNATURE, dynamicExtraParameter);
					attributesJson.append(AjaxAttributeName.DYNAMIC_PARAMETER_FUNCTION.jsonName(),
						function);
				}
			}

			if (attributes.isAsynchronous() == false)
			{
				attributesJson.put(AjaxAttributeName.IS_ASYNC.jsonName(), false);
			}

			String[] eventNames = attributes.getEventNames();
			if (eventNames.length == 1)
			{
				attributesJson.put(AjaxAttributeName.EVENT_NAME.jsonName(), eventNames[0]);
			}
			else
			{
				for (String eventName : eventNames)
				{
					attributesJson.append(AjaxAttributeName.EVENT_NAME.jsonName(), eventName);
				}
			}

			AjaxChannel channel = attributes.getChannel();
			if (channel != null && channel.equals(AjaxChannel.DEFAULT) == false)
			{
				attributesJson.put(AjaxAttributeName.CHANNEL.jsonName(), channel);
			}

			if (attributes.isPreventDefault())
			{
				attributesJson.put(AjaxAttributeName.IS_PREVENT_DEFAULT.jsonName(), true);
			}

			if (AjaxRequestAttributes.EventPropagation.STOP
				.equals(attributes.getEventPropagation()))
			{
				attributesJson.put(AjaxAttributeName.EVENT_PROPAGATION.jsonName(), "stop");
			}
			else if (AjaxRequestAttributes.EventPropagation.STOP_IMMEDIATE.equals(attributes
				.getEventPropagation()))
			{
				attributesJson.put(AjaxAttributeName.EVENT_PROPAGATION.jsonName(), "stopImmediate");
			}

			Duration requestTimeout = attributes.getRequestTimeout();
			if (requestTimeout != null)
			{
				attributesJson.put(AjaxAttributeName.REQUEST_TIMEOUT.jsonName(),
					requestTimeout.getMilliseconds());
			}

			boolean wicketAjaxResponse = attributes.isWicketAjaxResponse();
			if (wicketAjaxResponse == false)
			{
				attributesJson.put(AjaxAttributeName.IS_WICKET_AJAX_RESPONSE.jsonName(), false);
			}

			String dataType = attributes.getDataType();
			if (AjaxRequestAttributes.XML_DATA_TYPE.equals(dataType) == false)
			{
				attributesJson.put(AjaxAttributeName.DATATYPE.jsonName(), dataType);
			}

			ThrottlingSettings throttlingSettings = attributes.getThrottlingSettings();
			if (throttlingSettings != null)
			{
				JSONObject throttlingSettingsJson = new JSONObject();
				String throttleId = throttlingSettings.getId();
				if (throttleId == null)
				{
					throttleId = component.getMarkupId();
				}
				throttlingSettingsJson.put(AjaxAttributeName.THROTTLING_ID.jsonName(), throttleId);
				throttlingSettingsJson.put(AjaxAttributeName.THROTTLING_DELAY.jsonName(),
					throttlingSettings.getDelay().getMilliseconds());
				if (throttlingSettings.getPostponeTimerOnUpdate())
				{
					throttlingSettingsJson.put(
						AjaxAttributeName.THROTTLING_POSTPONE_ON_UPDATE.jsonName(), true);
				}
				attributesJson.put(AjaxAttributeName.THROTTLING.jsonName(), throttlingSettingsJson);
			}

			postprocessConfiguration(attributesJson, component);
		}
		catch (JSONException e)
		{
			throw new WicketRuntimeException(e);
		}

		String attributesAsJson = attributesJson.toString();

		return attributesAsJson;
	}

	private void appendListenerHandler(final CharSequence handler, final JSONObject attributesJson,
		final String propertyName, final String signature) throws JSONException
	{
		if (Strings.isEmpty(handler) == false)
		{
			final JSONFunction function;
			if (handler instanceof JSONFunction)
			{
				function = (JSONFunction)handler;
			}
			else
			{
				function = getJsonFunction(signature, handler);
			}
			attributesJson.append(propertyName, function);
		}
	}

	private JSONFunction getJsonFunction(String signature, CharSequence body) {
		String func = signature + "{" + body + "}";
		return new JSONFunction(func);
	}

	/**
	 * Gives a chance to modify the JSON attributesJson that is going to be used as attributes for
	 * the Ajax call.
	 * 
	 * @param attributesJson
	 *            the JSON object created by #renderAjaxAttributes()
	 * @param component
	 *            the component with the attached Ajax behavior
	 * @throws JSONException
	 *             thrown if an error occurs while modifying {@literal attributesJson} argument
	 */
	protected void postprocessConfiguration(JSONObject attributesJson, Component component)
		throws JSONException
	{
	}

	/**
	 * @return javascript that will generate an ajax GET request to this behavior with its assigned
	 *         component
	 */
	public CharSequence getCallbackScript()
	{
		return getCallbackScript(getComponent());
	}

	/**
	 * @param component
	 *            the component to use when generating the attributes
	 * @return script that can be used to execute this Ajax behavior.
	 */
	// 'protected' because this method is intended to be called by other Behavior methods which
	// accept the component as parameter
	protected CharSequence getCallbackScript(final Component component)
	{
		CharSequence ajaxAttributes = renderAjaxAttributes(component);
		return "Wicket.Ajax.ajax(" + ajaxAttributes + ");";
	}

	/**
	 * Generates a javascript function that can take parameters and performs an AJAX call which
	 * includes these parameters. The generated code looks like this:
	 * 
	 * <pre>
	 * function(param1, param2) {
	 *    var attrs = attrsJson;
	 *    var params = {'param1': param1, 'param2': param2};
	 *    attrs.ep = jQuery.extend(attrs.ep, params);
	 *    Wicket.Ajax.ajax(attrs);
	 * }
	 * </pre>
	 * 
	 * @param extraParameters
	 * @return A function that can be used as a callback function in javascript
	 */
	public CharSequence getCallbackFunction(CallbackParameter... extraParameters)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("function (");
		boolean first = true;
		for (CallbackParameter curExtraParameter : extraParameters)
		{
			if (curExtraParameter.getFunctionParameterName() != null)
			{
				if (!first)
					sb.append(',');
				else
					first = false;
				sb.append(curExtraParameter.getFunctionParameterName());
			}
		}
		sb.append(") {\n");
		sb.append(getCallbackFunctionBody(extraParameters));
		sb.append("}\n");
		return sb;
	}

	/**
	 * Generates the body the {@linkplain #getCallbackFunction(CallbackParameter...) callback
	 * function}. To embed this code directly into a piece of javascript, make sure any context
	 * parameters are available as local variables, global variables or within the closure.
	 * 
	 * @param extraParameters
	 * @return The body of the {@linkplain #getCallbackFunction(CallbackParameter...) callback
	 *         function}.
	 */
	public CharSequence getCallbackFunctionBody(CallbackParameter... extraParameters)
	{
		AjaxRequestAttributes attributes = getAttributes();
		attributes.setEventNames();
		CharSequence attrsJson = renderAjaxAttributes(getComponent(), attributes);
		StringBuilder sb = new StringBuilder();
		sb.append("var attrs = ");
		sb.append(attrsJson);
		sb.append(";\n");
		JSONArray jsonArray = new JSONArray();
		for (CallbackParameter curExtraParameter : extraParameters)
		{
			if (curExtraParameter.getAjaxParameterName() != null)
			{
				try
				{
					JSONObject object = new JSONObject();
					object.put("name", curExtraParameter.getAjaxParameterName());
					object.put("value", new JSONFunction(curExtraParameter.getAjaxParameterCode()));
					jsonArray.put(object);
				}
				catch (JSONException e)
				{
					throw new WicketRuntimeException(e);
				}
			}
		}
		sb.append("var params = ").append(jsonArray).append(";\n");
		sb.append("attrs.").append(AjaxAttributeName.EXTRA_PARAMETERS)
				.append(" = params.concat(attrs.")
				.append(AjaxAttributeName.EXTRA_PARAMETERS).append(" || []);\n");
		sb.append("Wicket.Ajax.ajax(attrs);\n");
		return sb;
	}

	/**
	 * Finds the markup id of the indicator. The default search order is: component, behavior,
	 * component's parent hierarchy.
	 * 
	 * @return markup id or <code>null</code> if no indicator found
	 */
	protected String findIndicatorId()
	{
		if (getComponent() instanceof IAjaxIndicatorAware)
		{
			return ((IAjaxIndicatorAware)getComponent()).getAjaxIndicatorMarkupId();
		}

		if (this instanceof IAjaxIndicatorAware)
		{
			return ((IAjaxIndicatorAware)this).getAjaxIndicatorMarkupId();
		}

		Component parent = getComponent().getParent();
		while (parent != null)
		{
			if (parent instanceof IAjaxIndicatorAware)
			{
				return ((IAjaxIndicatorAware)parent).getAjaxIndicatorMarkupId();
			}
			parent = parent.getParent();
		}
		return null;
	}

	@Override
	public final void onRequest()
	{
		Form.MethodMismatchResponse methodMismatch = onMethodMismatch();
		if (methodMismatch == Form.MethodMismatchResponse.ABORT)
		{
			AjaxRequestAttributes attrs = getAttributes();
			String desiredMethod = attrs.getMethod().toString();
			String actualMethod = ((HttpServletRequest) RequestCycle.get().getRequest().getContainerRequest()).getMethod();
			if (!desiredMethod.equalsIgnoreCase(actualMethod))
			{
				LOG.debug("Ignoring the Ajax request because its method '{}' is different than the expected one '{}",
				          actualMethod, desiredMethod);
				return;
			}
		}

		WebApplication app = (WebApplication)getComponent().getApplication();
		AjaxRequestTarget target = app.newAjaxRequestTarget(getComponent().getPage());

		RequestCycle requestCycle = RequestCycle.get();
		requestCycle.scheduleRequestHandlerAfterCurrent(target);

		respond(target);
	}

	/**
	 * @param target
	 *            The AJAX target
	 */
	protected abstract void respond(AjaxRequestTarget target);

}
