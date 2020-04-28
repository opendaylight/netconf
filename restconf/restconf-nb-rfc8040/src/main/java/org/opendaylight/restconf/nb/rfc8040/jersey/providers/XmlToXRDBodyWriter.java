/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.codecs.RestCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Provider
@Produces({ Rfc8040.MediaTypes.XRD + RestconfConstants.XML})
public class XmlToXRDBodyWriter implements MessageBodyWriter<String> {

    private static final Logger LOG = LoggerFactory.getLogger(RestCodec.class);

    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    /**
     * Ascertain if the MessageBodyWriter supports a particular type.
     *
     * @param type        the class of instance that is to be written.
     * @param genericType the type of instance to be written, obtained either
     *                    by reflection of a resource method return type or via inspection
     *                    of the returned instance. {@link GenericEntity}
     *                    provides a way to specify this information at runtime.
     * @param annotations an array of the annotations attached to the message entity instance.
     * @param mediaType   the media type of the HTTP entity.
     * @return {@code true} if the type is supported, otherwise {@code false}.
     */
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type.equals(String.class);
    }

    /**
     * Originally, the method has been called before {@code writeTo} to ascertain the length in bytes of
     * the serialized form of {@code t}. A non-negative return value has been used in a HTTP
     * {@code Content-Length} header.
     * <p>
     * As of JAX-RS 2.0, the method has been deprecated and the value returned by the method is ignored
     * by a JAX-RS runtime. All {@code MessageBodyWriter} implementations are advised to return {@code -1}
     * from the method. Responsibility to compute the actual {@code Content-Length} header value has been
     * delegated to JAX-RS runtime.
     * </p>
     *
     * @param size        the instance to write
     * @param type        the class of instance that is to be written.
     * @param genericType the type of instance to be written. {@link GenericEntity}
     *                    provides a way to specify this information at runtime.
     * @param annotations an array of the annotations attached to the message entity instance.
     * @param mediaType   the media type of the HTTP entity.
     * @return length in bytes or -1 if the length cannot be determined in advance.
     */
    @Override
    public long getSize(String size, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return size == null ? 0 : size.length();
    }

    /**
     * Write a type to an HTTP message. The message header map is mutable
     * but any changes must be made before writing to the output stream since
     * the headers will be flushed prior to writing the message body.
     *
     * @param input        the instance to write.
     * @param type         the class of instance that is to be written.
     * @param genericType  the type of instance to be written. {@link GenericEntity}
     *                     provides a way to specify this information at runtime.
     * @param annotations  an array of the annotations attached to the message entity instance.
     * @param mediaType    the media type of the HTTP entity.
     * @param httpHeaders  a mutable map of the HTTP message headers.
     * @param entityStream the {@link OutputStream} for the HTTP entity. The
     *                     implementation should not close the output stream.
     * @throws WebApplicationException if a specific HTTP error response needs to be produced.
     *                                 Only effective if thrown prior to the message being committed.
     */
    @Override
    public void writeTo(String input, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
                        throws WebApplicationException {
        try {
            final XMLStreamWriter xmlWriter = XML_FACTORY.createXMLStreamWriter(entityStream,
                      StandardCharsets.UTF_8.name());
            xmlWriter.writeDTD(input);
            xmlWriter.flush();
        } catch (XMLStreamException e) {
            LOG.debug(e.toString());
        }
    }
}
