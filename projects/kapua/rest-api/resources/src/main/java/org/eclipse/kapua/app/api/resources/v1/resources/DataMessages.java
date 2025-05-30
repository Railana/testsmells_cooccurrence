/*******************************************************************************
 * Copyright (c) 2016, 2022 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.app.api.resources.v1.resources;

import com.google.common.base.Strings;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.KapuaIllegalNullArgumentException;
import org.eclipse.kapua.app.api.core.resources.AbstractKapuaResource;
import org.eclipse.kapua.app.api.core.model.CountResult;
import org.eclipse.kapua.app.api.core.model.DateParam;
import org.eclipse.kapua.app.api.core.model.MetricType;
import org.eclipse.kapua.app.api.core.model.ScopeId;
import org.eclipse.kapua.app.api.core.model.StorableEntityId;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.message.device.data.KapuaDataMessage;
import org.eclipse.kapua.model.type.ObjectValueConverter;
import org.eclipse.kapua.service.KapuaService;
import org.eclipse.kapua.service.datastore.MessageStoreFactory;
import org.eclipse.kapua.service.datastore.MessageStoreService;
import org.eclipse.kapua.service.datastore.internal.mediator.ChannelInfoField;
import org.eclipse.kapua.service.datastore.internal.mediator.MessageField;
import org.eclipse.kapua.service.datastore.internal.schema.MessageSchema;
import org.eclipse.kapua.service.datastore.model.DatastoreMessage;
import org.eclipse.kapua.service.datastore.model.MessageListResult;
import org.eclipse.kapua.service.datastore.model.query.MessageQuery;
import org.eclipse.kapua.service.datastore.model.query.predicate.DatastorePredicateFactory;
import org.eclipse.kapua.service.elasticsearch.client.model.InsertResponse;
import org.eclipse.kapua.service.storable.model.query.SortDirection;
import org.eclipse.kapua.service.storable.model.query.SortField;
import org.eclipse.kapua.service.storable.model.query.StorableFetchStyle;
import org.eclipse.kapua.service.storable.model.query.predicate.AndPredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.RangePredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.StorablePredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.TermPredicate;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Path("{scopeId}/data/messages")
public class DataMessages extends AbstractKapuaResource {

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();
    private static final MessageStoreService MESSAGE_STORE_SERVICE = LOCATOR.getService(MessageStoreService.class);
    private static final MessageStoreFactory MESSAGE_STORE_FACTORY = LOCATOR.getFactory(MessageStoreFactory.class);
    private static final DatastorePredicateFactory DATASTORE_PREDICATE_FACTORY = LOCATOR.getFactory(DatastorePredicateFactory.class);

    /**
     * Gets the {@link DatastoreMessage} list in the scope.
     *
     * @param scopeId        The {@link ScopeId} in which to search results.
     * @param clientId       The client id to filter results.
     * @param channel        The channel id to filter results. It allows '#' wildcard in last channel level.
     * @param strictChannel  Restrict the search only to this channel ignoring its children. Only meaningful if channel is set.
     * @param startDateParam The start date to filter the results. Must come before endDate parameter.
     * @param endDateParam   The end date to filter the results. Must come after startDate parameter
     * @param offset         The result set offset.
     * @param limit          The result set limit.
     * @return The {@link MessageListResult} of all the datastoreMessages associated to the current selected scope.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @GET
    @Produces({MediaType.APPLICATION_XML})
    public <V extends Comparable<V>> MessageListResult simpleQuery(@PathParam("scopeId") ScopeId scopeId,
                                                                   @QueryParam("clientId") String clientId,
                                                                   @QueryParam("channel") String channel,
                                                                   @QueryParam("strictChannel") boolean strictChannel,
                                                                   @QueryParam("startDate") DateParam startDateParam,
                                                                   @QueryParam("endDate") DateParam endDateParam,
                                                                   @QueryParam("metricName") String metricName,
                                                                   @QueryParam("metricType") MetricType<V> metricType,
                                                                   @QueryParam("metricMin") String metricMinValue,
                                                                   @QueryParam("metricMax") String metricMaxValue,
                                                                   @QueryParam("sortDir") @DefaultValue("DESC") SortDirection sortDir,
                                                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                                                   @QueryParam("limit") @DefaultValue("50") int limit)
            throws KapuaException {

        AndPredicate andPredicate = DATASTORE_PREDICATE_FACTORY.newAndPredicate();
        if (!Strings.isNullOrEmpty(clientId)) {
            TermPredicate clientIdPredicate = DATASTORE_PREDICATE_FACTORY.newTermPredicate(MessageField.CLIENT_ID, clientId);
            andPredicate.getPredicates().add(clientIdPredicate);
        }

        if (!Strings.isNullOrEmpty(channel)) {
            andPredicate.getPredicates().add(getChannelPredicate(channel, strictChannel));
        }

        Date startDate = startDateParam != null ? startDateParam.getDate() : null;
        Date endDate = endDateParam != null ? endDateParam.getDate() : null;
        if (startDate != null || endDate != null) {
            RangePredicate timestampPredicate = DATASTORE_PREDICATE_FACTORY.newRangePredicate(ChannelInfoField.TIMESTAMP, startDate, endDate);
            andPredicate.getPredicates().add(timestampPredicate);
        }

        if (!Strings.isNullOrEmpty(metricName)) {
            andPredicate.getPredicates().add(getMetricPredicate(metricName, metricType, metricMinValue, metricMaxValue));
        }

        MessageQuery query = MESSAGE_STORE_FACTORY.newQuery(scopeId);
        query.setPredicate(andPredicate);
        query.setOffset(offset);
        query.setLimit(limit);

        List<SortField> sort = new ArrayList<>();
        sort.add(SortField.of(MessageSchema.MESSAGE_TIMESTAMP, sortDir));
        query.setSortFields(sort);

        return query(scopeId, query);
    }

    /**
     * Stores a new Message under the account of the currently connected user.
     * In this case, the provided message will only be stored in the back-end
     * database and it will not be forwarded to the message broker.
     *
     * @param message The {@link KapuaDataMessage } to be stored
     * @return an {@link InsertResponse} object encapsulating the response from
     * the datastore
     * @throws KapuaException Whenever something bad happens. See specific
     *                        {@link KapuaService} exceptions.
     */
    @POST
    @Consumes({MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_XML})
    public Response storeMessage(@PathParam("scopeId") ScopeId scopeId,
                                 KapuaDataMessage message)
            throws KapuaException {
        message.setScopeId(scopeId);
        return returnCreated(new StorableEntityId(MESSAGE_STORE_SERVICE.store(message).toString()));
    }

    /**
     * Queries the results with the given {@link MessageQuery} parameter.
     *
     * @param scopeId The {@link ScopeId} in which to search results.
     * @param query   The {@link MessageQuery} to used to filter results.
     * @return The {@link MessageListResult} of all the result matching the given {@link MessageQuery} parameter.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @POST
    @Path("_query")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML})
    public MessageListResult query(@PathParam("scopeId") ScopeId scopeId,
                                   MessageQuery query)
            throws KapuaException {
        query.setScopeId(scopeId);

        return MESSAGE_STORE_SERVICE.query(query);
    }

    /**
     * Counts the results with the given {@link MessageQuery} parameter.
     *
     * @param scopeId The {@link ScopeId} in which to search results.
     * @param query   The {@link MessageQuery} to used to filter results.
     * @return The count of all the result matching the given {@link MessageQuery} parameter.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @POST
    @Path("_count")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public CountResult count(@PathParam("scopeId") ScopeId scopeId,
                             MessageQuery query)
            throws KapuaException {
        query.setScopeId(scopeId);

        return new CountResult(MESSAGE_STORE_SERVICE.count(query));
    }

    /**
     * Returns the DatastoreMessage specified by the "datastoreMessageId" path parameter.
     *
     * @param datastoreMessageId The id of the requested DatastoreMessage.
     * @return The requested DatastoreMessage object.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     * @since 1.0.0
     */
    @GET
    @Path("{datastoreMessageId}")
    @Produces({MediaType.APPLICATION_XML})
    public DatastoreMessage find(@PathParam("scopeId") ScopeId scopeId,
                                 @PathParam("datastoreMessageId") StorableEntityId datastoreMessageId)
            throws KapuaException {
        DatastoreMessage datastoreMessage = MESSAGE_STORE_SERVICE.find(scopeId, datastoreMessageId, StorableFetchStyle.SOURCE_FULL);

        return returnNotNullEntity(datastoreMessage);
    }

    private StorablePredicate getChannelPredicate(String channel, boolean strictChannel) {
        StorablePredicate channelPredicate;
        if (strictChannel) {
            channelPredicate = DATASTORE_PREDICATE_FACTORY.newTermPredicate(ChannelInfoField.CHANNEL, channel);
        } else {
            channelPredicate = DATASTORE_PREDICATE_FACTORY.newChannelMatchPredicate(channel);
        }
        return channelPredicate;
    }

    private <V extends Comparable<V>> StorablePredicate getMetricPredicate(String metricName, MetricType<V> metricType, String metricMinValue, String metricMaxValue) throws KapuaIllegalNullArgumentException {
        if (metricMinValue == null && metricMaxValue == null) {
            Class<V> type = metricType != null ? metricType.getType() : null;
            return DATASTORE_PREDICATE_FACTORY.newMetricExistsPredicate(metricName, type);
        } else {
            if (metricType == null) {
                throw new KapuaIllegalNullArgumentException("metricType");
            }
            V minValue = (V) ObjectValueConverter.fromString(metricMinValue, metricType.getType());
            V maxValue = (V) ObjectValueConverter.fromString(metricMaxValue, metricType.getType());

            return DATASTORE_PREDICATE_FACTORY.newMetricPredicate(metricName, metricType.getType(), minValue, maxValue);
        }
    }
}
