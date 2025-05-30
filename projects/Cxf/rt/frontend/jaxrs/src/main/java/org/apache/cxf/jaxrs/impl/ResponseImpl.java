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

package org.apache.cxf.jaxrs.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.NoContentException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;
import javax.xml.stream.XMLStreamReader;

import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.ReaderInputStream;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;

public final class ResponseImpl extends Response {
    public static final String RESPONSE_STREAM_AUTO_CLOSE = "response.stream.auto.close";
    private static final Pattern LINK_DELIMITER = Pattern.compile(",\\s*(?=\\<|$)");

    private StatusType status;
    private Object entity;
    private Annotation[] entityAnnotations;
    private MultivaluedMap<String, Object> metadata;

    private Message outMessage;
    private boolean entityClosed;
    private boolean entityBufferred;
    private Object lastEntity;

    ResponseImpl(int statusCode) {
        this.status = createStatusType(statusCode, null);
    }

    ResponseImpl(int statusCode, Object entity) {
        this(statusCode);
        this.entity = entity;
    }

    ResponseImpl(int statusCode, Object entity, String reasonPhrase) {
        this.status = createStatusType(statusCode, reasonPhrase);
        this.entity = entity;
    }

    public void addMetadata(MultivaluedMap<String, Object> meta) {
        this.metadata = meta;
    }

    public void setStatus(int statusCode) {
        this.status = createStatusType(statusCode, null);
    }

    public void setStatus(int statusCode, String reasonPhrase) {
        this.status = createStatusType(statusCode, reasonPhrase);
    }

    public void setEntity(Object e, Annotation[] anns) {
        this.entity = e;
        this.entityAnnotations = anns;
    }

    public void setEntityAnnotations(Annotation[] anns) {
        this.entityAnnotations = anns;
    }

    public Annotation[] getEntityAnnotations() {
        return entityAnnotations;
    }

    public void setOutMessage(Message message) {
        this.outMessage = message;
    }

    public Message getOutMessage() {
        return this.outMessage;
    }

    @Override
    public int getStatus() {
        return status.getStatusCode();
    }

    @Override
    public StatusType getStatusInfo() {
        return status;
    }

    public Object getActualEntity() {
        checkEntityIsClosed();
        return lastEntity != null ? lastEntity : entity;
    }

    @Override
    public Object getEntity() {
        return InjectionUtils.getEntity(getActualEntity());
    }

    @Override
    public boolean hasEntity() {
        // per spec, need to check if the stream exists and if it has data.
        Object actualEntity = getActualEntity();
        if (actualEntity == null) {
            return false;
        } else if (entityBufferred) {
            // if actualEntity is not null and entity was buffered, the response definitely has entity
            return true;
        } else if (actualEntity instanceof InputStream) {
            final InputStream is = (InputStream) actualEntity;
            try {
                if (is.markSupported()) {
                    is.mark(1);
                    int i = is.read();
                    is.reset();
                    return i != -1;
                } else {
                    try {
                        if (is.available() > 0) {
                            return true;
                        }
                    } catch (IOException ioe) {
                        //Do nothing
                    }
                    int b = is.read();
                    if (b == -1) {
                        return false;
                    }
                    PushbackInputStream pbis;
                    if (is instanceof PushbackInputStream) {
                        pbis = (PushbackInputStream) is;
                    } else {
                        pbis = new PushbackInputStream(is, 1);
                        if (lastEntity != null) {
                            lastEntity = pbis;
                        } else {
                            entity = pbis;
                        }
                    }
                    pbis.unread(b);
                    return true;
                }
            } catch (IOException ex) {
                throw new ProcessingException(ex);
            }
        }
        return true;
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return getHeaders();
    }

    @Override
    public MultivaluedMap<String, Object> getHeaders() {
        return metadata;
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        MetadataMap<String, String> headers = new MetadataMap<>(false, true);
        for (Map.Entry<String, List<Object>> entry : metadata.entrySet()) {
            String headerName = entry.getKey();
            headers.put(headerName, toListOfStrings(entry.getValue()));
        }
        return headers;
    }

    @Override
    public String getHeaderString(String header) {
        List<Object> methodValues = metadata.get(header);
        return HttpUtils.getHeaderString(toListOfStrings(methodValues));
    }

    // This conversion is needed as some values may not be Strings
    private List<String> toListOfStrings(List<Object> values) {
        if (values == null) {
            return null;
        }
        List<String> stringValues = new ArrayList<>(values.size());
        HeaderDelegate<Object> hd = HttpUtils.getHeaderDelegate(values.get(0));
        for (Object value : values) {
            String actualValue = hd == null ? value.toString() : hd.toString(value);
            stringValues.add(actualValue);
        }
        return stringValues;
    }

    @Override
    public Set<String> getAllowedMethods() {
        List<Object> methodValues = metadata.get(HttpHeaders.ALLOW);
        if (methodValues == null) {
            return Collections.emptySet();
        }
        Set<String> methods = new HashSet<>();
        for (Object o : methodValues) {
            methods.add(o.toString());
        }
        return methods;
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        List<Object> cookieValues = metadata.get(HttpHeaders.SET_COOKIE);
        if (cookieValues == null) {
            return Collections.emptyMap();
        }
        Map<String, NewCookie> cookies = new HashMap<>();
        for (Object o : cookieValues) {
            NewCookie newCookie = NewCookie.valueOf(o.toString());
            cookies.put(newCookie.getName(), newCookie);
        }
        return cookies;
    }

    @Override
    public Date getDate() {
        return doGetDate(HttpHeaders.DATE);
    }

    private Date doGetDate(String dateHeader) {
        Object value = metadata.getFirst(dateHeader);
        return value == null || value instanceof Date ? (Date)value
            : HttpUtils.getHttpDate(value.toString());
    }

    @Override
    public EntityTag getEntityTag() {
        Object header = metadata.getFirst(HttpHeaders.ETAG);
        return header == null || header instanceof EntityTag ? (EntityTag)header
            : EntityTag.valueOf(header.toString());
    }

    @Override
    public Locale getLanguage() {
        Object header = metadata.getFirst(HttpHeaders.CONTENT_LANGUAGE);
        return header == null || header instanceof Locale ? (Locale)header
            : HttpUtils.getLocale(header.toString());
    }

    @Override
    public Date getLastModified() {
        return doGetDate(HttpHeaders.LAST_MODIFIED);
    }

    @Override
    public int getLength() {
        Object header = metadata.getFirst(HttpHeaders.CONTENT_LENGTH);
        return HttpUtils.getContentLength(header == null ? null : header.toString());
    }

    @Override
    public URI getLocation() {
        Object header = metadata.getFirst(HttpHeaders.LOCATION);
        return header == null || header instanceof URI ? (URI)header
            : URI.create(header.toString());
    }

    @Override
    public MediaType getMediaType() {
        Object header = metadata.getFirst(HttpHeaders.CONTENT_TYPE);
        return header == null || header instanceof MediaType ? (MediaType)header
            : (MediaType)JAXRSUtils.toMediaType(header.toString());
    }

    @Override
    public boolean hasLink(String relation) {
        List<Object> linkValues = metadata.get(HttpHeaders.LINK);
        if (linkValues != null) {
            for (Object o : linkValues) {
                if (o instanceof Link && relation.equals(((Link)o).getRel())) {
                    return true;
                }

                String[] links = LINK_DELIMITER.split(o.toString());
                for (String splitLink : links) {
                    Link link = Link.valueOf(splitLink);
                    if (relation.equals(link.getRel())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Link getLink(String relation) {
        Set<Link> links = getAllLinks();
        for (Link link : links) {
            if (link.getRel() != null && link.getRel().equals(relation)) {
                return link;
            }
        }
        return null;
    }

    @Override
    public Link.Builder getLinkBuilder(String relation) {
        Link link = getLink(relation);
        return link == null ? null : Link.fromLink(link);
    }

    @Override
    public Set<Link> getLinks() {
        return new HashSet<>(getAllLinks());
    }

    private Set<Link> getAllLinks() {
        List<Object> linkValues = metadata.get(HttpHeaders.LINK);
        if (linkValues == null) {
            return Collections.emptySet();
        }
        Set<Link> links = new LinkedHashSet<>();
        for (Object o : linkValues) {
            List<Link> parsedLinks = parseLink(o);

            links.addAll(parsedLinks);
        }
        return links;
    }

    private Link makeAbsoluteLink(Link link) {
        if (!link.getUri().isAbsolute()) {
            URI requestURI = URI.create((String)outMessage.get(Message.REQUEST_URI));
            link = Link.fromLink(link).baseUri(requestURI).build();
        }

        return link;
    }

    private List<Link> parseLink(Object o) {
        if (o instanceof Link) {
            return Collections.singletonList(makeAbsoluteLink((Link) o));
        }

        List<Link> links = new ArrayList<>();
        String[] linkArray = LINK_DELIMITER.split(o.toString());

        for (String textLink : linkArray) {
            Link link = Link.valueOf(textLink);
            links.add(makeAbsoluteLink(link));
        }

        return Collections.unmodifiableList(links);
    }

    @Override
    public <T> T readEntity(Class<T> cls) throws ProcessingException, IllegalStateException {
        return readEntity(cls, new Annotation[]{});
    }

    @Override
    public <T> T readEntity(GenericType<T> genType) throws ProcessingException, IllegalStateException {
        return readEntity(genType, new Annotation[]{});
    }

    @Override
    public <T> T readEntity(Class<T> cls, Annotation[] anns) throws ProcessingException, IllegalStateException {
        return doReadEntity(cls, cls, anns);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readEntity(GenericType<T> genType, Annotation[] anns)
        throws ProcessingException, IllegalStateException {
        return doReadEntity((Class<T>) genType.getRawType(),
                            genType.getType(), anns);
    }

    public <T> T doReadEntity(Class<T> cls, Type t, Annotation[] anns)
        throws ProcessingException, IllegalStateException {

        checkEntityIsClosed();

        if (lastEntity != null && cls.isAssignableFrom(lastEntity.getClass())
            && !(lastEntity instanceof InputStream)) {
            return cls.cast(lastEntity);
        }

        MediaType mediaType = getMediaType();
        if (mediaType == null) {
            mediaType = MediaType.WILDCARD_TYPE;
        }

        // the stream is available if entity is IS or
        // message contains XMLStreamReader or Reader
        boolean entityStreamAvailable = entityStreamAvailable();
        InputStream entityStream = null;
        if (!entityStreamAvailable) {
            // try create a stream if the entity is String or Number
            entityStream = convertEntityToStreamIfPossible();
            entityStreamAvailable = entityStream != null;
        } else if (entity instanceof InputStream) {
            entityStream = InputStream.class.cast(entity);
        } else {
            Message inMessage = getResponseMessage();
            Reader reader = inMessage.getContent(Reader.class);
            if (reader != null) {
                entityStream = InputStream.class.cast(new ReaderInputStream(reader));
            }
        }

        // we need to check for readers even if no IS is set - the readers may still do it
        List<ReaderInterceptor> readers = outMessage == null ? null : ProviderFactory.getInstance(outMessage)
            .createMessageBodyReaderInterceptor(cls, t, anns, mediaType, outMessage, entityStreamAvailable, null);

        if (readers != null) {
            // By default, the response entity was never closed automatically which is not compliant
            // with JAX-RS specification and TCK. The auto-close behavior is controlled by 
            // ResponseImpl#RESPONSE_STREAM_AUTO_CLOSE property which is set to "false" by default. 
            // The autoCloseHint alters the default value of this property to be "true" for all 
            // non-streaming and non-streaming like responses (to minimize the impact of the change
            // as much as possible) in order to follow the specification.
            final boolean autoCloseHint = !JAXRSUtils.isStreamingLikeOutType(cls, t);
            try {
                if (entityBufferred) {
                    InputStream.class.cast(entity).reset();
                }

                Message responseMessage = getResponseMessage();
                responseMessage.put(Message.PROTOCOL_HEADERS, getHeaders());

                lastEntity = JAXRSUtils.readFromMessageBodyReader(readers, cls, t,
                                                                  anns,
                                                                  entityStream,
                                                                  mediaType,
                                                                  responseMessage);
                // close the entity after readEntity is called.
                T tCastLastEntity = castLastEntity();
                autoCloseWithHint(cls, autoCloseHint, false);
                return tCastLastEntity;
            } catch (NoContentException ex) {
                //when content is empty, return null instead of throw exception to pass TCK
                //check if basic type. Basic type throw exception, else return null.
                if (isBasicType(cls)) {
                    autoClose(cls, true);
                    reportMessageHandlerProblem("MSG_READER_PROBLEM", cls, mediaType, ex);
                } else {
                    autoCloseWithHint(cls, autoCloseHint, false);
                    return null;
                }
            } catch (Exception ex) {
                autoClose(cls, true);
                reportMessageHandlerProblem("MSG_READER_PROBLEM", cls, mediaType, ex);
            } finally {
                ProviderFactory pf = ProviderFactory.getInstance(outMessage);
                if (pf != null) {
                    pf.clearThreadLocalProxies();
                }
            }
        } else if (entity != null && cls.isAssignableFrom(entity.getClass())) {
            lastEntity = entity;
            return castLastEntity();
        } else if (entityStreamAvailable) {
            reportMessageHandlerProblem("NO_MSG_READER", cls, mediaType, null);
        }

        throw new IllegalStateException("The entity is not backed by an input stream, entity class is : "
            + (entity != null ? entity.getClass().getName() : cls.getName()));

    }

    @SuppressWarnings("unchecked")
    private <T> T castLastEntity() {
        return (T)lastEntity;
    }

    public InputStream convertEntityToStreamIfPossible() {
        String stringEntity = null;
        if (entity instanceof String || entity instanceof Number) {
            stringEntity = entity.toString();
        }
        if (stringEntity != null) {
            try {
                return new ByteArrayInputStream(stringEntity.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                throw new ProcessingException(ex);
            }
        }
        return null;
    }

    private boolean entityStreamAvailable() {
        if (entity == null) {
            Message inMessage = getResponseMessage();
            return inMessage != null && (inMessage.getContent(XMLStreamReader.class) != null
                || inMessage.getContent(Reader.class) != null);
        }
        return entity instanceof InputStream;
    }

    private Message getResponseMessage() {
        Message responseMessage = outMessage.getExchange().getInMessage();
        if (responseMessage == null) {
            responseMessage = outMessage.getExchange().getInFaultMessage();
        }
        return responseMessage;
    }

    private void reportMessageHandlerProblem(String name, Class<?> cls, MediaType ct, Throwable cause) {
        String errorMessage = JAXRSUtils.logMessageHandlerProblem(name, cls, ct);
        throw new ResponseProcessingException(this, errorMessage, cause);
    }

    protected void autoClose(Class<?> cls, boolean exception) {
        autoCloseWithHint(cls, false, exception);
    }

    protected void autoCloseWithHint(Class<?> cls, boolean autoCloseHint, boolean exception) {
        if (!entityBufferred && !JAXRSUtils.isStreamingOutType(cls)
            && (exception || MessageUtils.getContextualBoolean(outMessage,
                RESPONSE_STREAM_AUTO_CLOSE, autoCloseHint))) {
            close();
        }
    }

    @Override
    public boolean bufferEntity() throws ProcessingException {
        checkEntityIsClosed();
        if (!entityBufferred && entity instanceof InputStream) {
            try {
                InputStream oldEntity = (InputStream)entity;
                entity = IOUtils.loadIntoBAIS(oldEntity);
                oldEntity.close();
                entityBufferred = true;
            } catch (IOException ex) {
                throw new ResponseProcessingException(this, ex);
            }
        }
        return entityBufferred;
    }

    @Override
    public void close() throws ProcessingException {
        if (!entityClosed) {
            if (!entityBufferred && entity instanceof InputStream) {
                try {
                    ((InputStream)entity).close();
                } catch (IOException ex) {
                    throw new ResponseProcessingException(this, ex);
                }
            }
            entity = null;
            entityClosed = true;
        }

    }

    private void checkEntityIsClosed() {
        if (entityClosed) {
            throw new IllegalStateException("Entity is not available");
        }
    }

    private Response.StatusType createStatusType(int statusCode, String reasonPhrase) {
        return new Response.StatusType() {

            @Override
            public Family getFamily() {
                return Response.Status.Family.familyOf(statusCode);
            }

            @Override
            public String getReasonPhrase() {
                if (reasonPhrase != null) {
                    return reasonPhrase;
                }
                Response.Status statusEnum = Response.Status.fromStatusCode(statusCode);
                return statusEnum != null ? statusEnum.getReasonPhrase() : "";
            }

            @Override
            public int getStatusCode() {
                return statusCode;
            }

        };
    }

    private static boolean isBasicType(Class<?> type) {
        return type.isPrimitive() || Number.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)
            || Character.class.isAssignableFrom(type);
    }

    @Override
    public String toString() {
        return "ResponseImpl{"
                + "status=" + (status == null ? "null" : status.getStatusCode())
                + "}";
    }
}
