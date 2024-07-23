/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.yanglib.writer.YangLibrarySchemaSourceUrlProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yangtools.yang.common.Revision;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Component composing schema source URL value on per YANG resource basis.
 *
 * <p>The URL is expected to be requested by {@link org.opendaylight.netconf.yanglib.writer.YangLibraryWriter
 * YangLibraryWriter} when yang-library data is being constructed, only default module-set name ("ODL_modules")
 * is supported. The composed URL for resource download expected to be served by
 * {@link JaxRsRestconf#modulesYangGET(String, String, javax.ws.rs.container.AsyncResponse)} et al.
 */
@Singleton
@Component(immediate = true)
public final class JaxRsYangLibrary implements YangLibrarySchemaSourceUrlProvider {
    private final String modulesPath;

    public JaxRsYangLibrary(final String restconf) {
        modulesPath = "/" + requireNonNull(restconf) + "/" + JaxRsRestconf.MODULES_SUBPATH + "/";
    }

    @Inject
    @Activate
    public JaxRsYangLibrary(@Reference final JaxRsNorthbound northbound) {
        this(northbound.configuration().restconf());
    }

    @Override
    public Set<Uri> getSchemaSourceUrl(final String moduleSetName, final String moduleName,
            final Revision revision) {
        if ("ODL_modules".equals(moduleSetName)) {
            final var sb = new StringBuilder(modulesPath).append(moduleName);
            if (revision != null) {
                sb.append("?" + JaxRsRestconf.MODULES_REVISION_QUERY + "=").append(revision);
            }
            return Set.of(new Uri(sb.toString()));
        }
        return Set.of();
    }
}
