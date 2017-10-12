/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.customrpc;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.bind.JAXB;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;

/**
 * Mapping between RPCs and responses.
 */
class RpcMapping {

    private final Multimap<Request, Document> requestToResponseMap = ArrayListMultimap.create();

    /**
     * Creates new mapping from file.
     *
     * @param config config file
     */
    RpcMapping(final File config) {
        final Rpcs rpcs = JAXB.unmarshal(config, Rpcs.class);
        for (final Rpc rpc : rpcs.getRpcs()) {
            final Stream<Document> stream = rpc.getOutput().stream()
                    .map(XmlData::getData);
            final XmlElement element = XmlElement.fromDomDocument(rpc.getInput().getData());
            requestToResponseMap.putAll(new Request(element), stream::iterator);
        }
    }

    /**
     * Returns response matching given input. If multiple responses are configured for the same
     * request, every invocation of this method returns next response as they are defined in the config file.
     * Last configured response is used, when number of invocations is higher than number of configured responses
     * for rpc.
     *
     * @param input request
     * @return response document, or absent if mapping is not defined
     */
    Optional<Document> getResponse(final XmlElement input) {
        final Collection<Document> responses = requestToResponseMap.get(new Request(input));
        final Iterator<Document> iterator = responses.iterator();
        if (iterator.hasNext()) {
            final Document response = iterator.next();
            if (iterator.hasNext()) {
                iterator.remove();
            }
            return Optional.of(response);
        }
        return Optional.empty();
    }

    /**
     * Rpc input wrapper. Needed because of custom {@link Request#equals(Object)}
     * and {@link Request#hashCode()} implementations.
     */
    private static class Request {
        private final XmlElement xmlElement;
        private final int hashCode;

        Request(final XmlElement element) {
            this.xmlElement = element;
            hashCode = XmlUtil.toString(element)
                    .replaceAll("message-id=.*(>| )", "") //message id is variable, remove it
                    .replaceAll("\\s+", "") //remove whitespaces
                    .hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final Request request = (Request) obj;
            return documentEquals(this.xmlElement, request.xmlElement);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static boolean documentEquals(final XmlElement e1, final XmlElement e2) {
            if (!e1.getNamespaceOptionally().equals(e2.getNamespaceOptionally())) {
                return false;
            }
            if (!e1.getName().equals(e2.getName())) {
                return false;
            }
            if (attributesNotEquals(e1, e2)) {
                return false;
            }

            final List<XmlElement> e1Children = e1.getChildElements();
            final List<XmlElement> e2Children = e2.getChildElements();
            if (e1Children.size() != e2Children.size()) {
                return false;
            }
            final Iterator<XmlElement> e1Iterator = e1Children.iterator();
            final Iterator<XmlElement> e2Iterator = e2Children.iterator();
            while (e1Iterator.hasNext() && e2Iterator.hasNext()) {
                if (!documentEquals(e1Iterator.next(), e2Iterator.next())) {
                    return false;
                }
            }

            if (e1Children.isEmpty() && e1Children.isEmpty()) {
                try {
                    return e1.getTextContent().equals(e2.getTextContent());
                } catch (final DocumentedException e) {
                    return false;
                }
            }
            return true;
        }

        private static boolean attributesNotEquals(final XmlElement e1, final XmlElement e2) {
            final Map<String, Attr> e1Attrs = e1.getAttributes();
            final Map<String, Attr> e2Attrs = e2.getAttributes();
            final Iterator<Map.Entry<String, Attr>> e1AttrIt = e1Attrs.entrySet().iterator();
            final Iterator<Map.Entry<String, Attr>> e2AttrIt = e2Attrs.entrySet().iterator();
            while (e1AttrIt.hasNext() && e2AttrIt.hasNext()) {
                final Map.Entry<String, Attr> e1Next = e1AttrIt.next();
                final Map.Entry<String, Attr> e2Next = e2AttrIt.next();
                if (e1Next.getKey().equals("message-id") && e2Next.getKey().equals("message-id")) {
                    continue;
                }
                if (e1Next.getKey().equals("xmlns") && e2Next.getKey().equals("xmlns")) {
                    continue;
                }
                if (!e1Next.getKey().equals(e2Next.getKey())) {
                    return true;
                }
                if (!e1Next.getValue().getValue().equals(e2Next.getValue().getValue())) {
                    return true;
                }
            }
            return false;
        }

    }
}
