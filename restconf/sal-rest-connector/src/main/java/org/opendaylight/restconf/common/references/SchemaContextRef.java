/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.references;

import java.lang.ref.SoftReference;
import java.net.URI;
import java.util.Date;
import java.util.Set;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.Rfc8040;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * This class creates {@link SoftReference} of actual {@link SchemaContext}
 * object and even if the {@link SchemaContext} changes, this will be sticks
 * reference to the old {@link SchemaContext} and provides work with the old
 * {@link SchemaContext}.
 *
 */
public final class SchemaContextRef {

    private final SoftReference<SchemaContext> schemaContextRef;

    /**
     * Create {@link SoftReference} of actual {@link SchemaContext}.
     *
     * @param schemaContext
     *             actual {@link SchemaContext}
     */
    public SchemaContextRef(final SchemaContext schemaContext) {
        this.schemaContextRef = new SoftReference<>(schemaContext);
    }

    /**
     * Get {@link SchemaContext} from reference.
     *
     * @return {@link SchemaContext}
     */
    public SchemaContext get() {
        return this.schemaContextRef.get();
    }

    /**
     * Get all modules like {@link Set} of {@link Module} from
     * {@link SchemaContext}.
     *
     * @return {@link Set} of {@link Module}
     */
    public Set<Module> getModules() {
        return get().getModules();
    }

    /**
     * Get all modules like {@link Set} of {@link Module} from
     * {@link SchemaContext} of {@link DOMMountPoint}.
     *
     * @param mountPoint
     *             mount point
     *
     * @return {@link Set} of {@link Module}
     */
    public Set<Module> getModules(final DOMMountPoint mountPoint) {
        final SchemaContext schemaContext = mountPoint == null ? null : mountPoint.getSchemaContext();
        return schemaContext == null ? null : schemaContext.getModules();
    }

    /**
     * Get {@link Module} by ietf-restconf qname from
     * {@link Rfc8040.RestconfModule}.
     *
     * @return {@link Module}
     */
    public Module getRestconfModule() {
        return this.findModuleByNamespaceAndRevision(Rfc8040.RestconfModule.IETF_RESTCONF_QNAME.getNamespace(),
                Rfc8040.RestconfModule.IETF_RESTCONF_QNAME.getRevision());
    }

    /**
     * Find {@link Module} in {@link SchemaContext} by {@link URI} and
     * {@link Date}.
     *
     * @param namespace
     *             namespace of module
     * @param revision
     *             revision of module
     * @return {@link Module}
     */
    public Module findModuleByNamespaceAndRevision(final URI namespace, final Date revision) {
        return this.get().findModuleByNamespaceAndRevision(namespace, revision);
    }


    /**
     * Find {@link Module} in {@link SchemaContext} of {@link DOMMountPoint} by
     * {@link QName} of {@link Module}.
     *
     * @param mountPoint
     *             mount point
     * @param moduleQname
     *             {@link QName} of module
     * @return {@link Module}
     */
    public Module findModuleInMountPointByQName(final DOMMountPoint mountPoint, final QName moduleQname) {
        final SchemaContext schemaContext = mountPoint == null ? null : mountPoint.getSchemaContext();
        return schemaContext == null ? null
                : schemaContext.findModuleByName(moduleQname.getLocalName(), moduleQname.getRevision());
    }

    /**
     * Find {@link Module} in {@link SchemaContext} by {@link QName}.
     *
     * @param moduleQname
     *             {@link QName} of module
     * @return {@link Module}
     */
    public Module findModuleByQName(final QName moduleQname) {
        return this.findModuleByNameAndRevision(moduleQname.getLocalName(), moduleQname.getRevision());
    }

    /**
     * Find {@link Module} in {@link SchemaContext} by {@link String} localName
     * and {@link Date} revision.
     *
     * @param localName
     *             local name of module
     * @param revision
     *             revision of module
     * @return {@link Module}
     */
    public Module findModuleByNameAndRevision(final String localName, final Date revision) {
        return this.get().findModuleByName(localName, revision);
    }
}
