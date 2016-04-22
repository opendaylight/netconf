/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.parser;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class ParserIdentifier {

    private static final Logger LOG = LoggerFactory.getLogger(ParserIdentifier.class);

    private ParserIdentifier() {
        throw new UnsupportedOperationException("Util class.");
    }

    public static InstanceIdentifierContext<?> toInstanceIdentifier(final String identifier) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Make a {@link QName} from identifier
     *
     * @param {@link
     *            String}
     * @return {@link QName}
     */
    public static QName makeQNameFromIdentifier(final String identifier) {
        final int mountIndex = identifier.indexOf(RestconfConstants.MOUNT);
        String moduleNameAndRevision = "";
        if (mountIndex >= 0) {
            moduleNameAndRevision = identifier.substring(mountIndex + RestconfConstants.MOUNT.length());
        } else {
            moduleNameAndRevision = identifier;
        }

        final Splitter splitter = Splitter.on("/").omitEmptyStrings();
        final Iterable<String> split = splitter.split(moduleNameAndRevision);
        final List<String> pathArgs = Lists.<String> newArrayList(split);
        if (pathArgs.size() < 2) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' " + identifier);
            throw new RestconfDocumentedException(
                    "URI has bad format. End of URI should be in format \'moduleName/yyyy-MM-dd\'", ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        try {
            final String moduleName = pathArgs.get(0);
            final String revision = pathArgs.get(1);
            final Date moduleRevision = RestconfConstants.REVISION_FORMAT.parse(revision);

            return QName.create(null, moduleRevision, moduleName);
        } catch (final ParseException e) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' " + identifier);
            throw new RestconfDocumentedException("URI has bad format. It should be \'moduleName/yyyy-MM-dd\'",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
    }

    public static Iterator<String> toSchemaIdentifier(final String mountAndModule) {
        final List<Object> list = new ArrayList<>();
        final Iterable<String> pathComponents = RestconfConstants.SLASH_SPLITTER.split(mountAndModule);
        final Iterator<String> componentIter = pathComponents.iterator();
        if (Iterables.contains(pathComponents, RestconfConstants.MOUNT)) {
            final StringBuilder pathBuilder = new StringBuilder();
            while (componentIter.hasNext()) {
                final String current = componentIter.next();
                if (pathBuilder.length() != 0) {
                    pathBuilder.append("/");
                }
                pathBuilder.append(current);
                if (RestconfConstants.MOUNT.equals(current)) {
                    break;
                }
            }
            list.add(pathBuilder);
            list.add(componentIter);
        }
        return componentIter;
    }

}
