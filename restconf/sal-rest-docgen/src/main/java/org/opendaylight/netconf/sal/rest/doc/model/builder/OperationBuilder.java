/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.model.builder;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.netconf.sal.rest.doc.swagger.Operation;
import org.opendaylight.netconf.sal.rest.doc.swagger.Parameter;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

public final class OperationBuilder {

    public static final String OPERATIONAL = "(operational)";
    public static final String CONFIG = "(config)";
    public static final String TOP = "-TOP";

    public static final List<String> CONSUMES_PUT_POST = new ArrayList<>();

    static {
        CONSUMES_PUT_POST.add("application/json");
        CONSUMES_PUT_POST.add("application/xml");
    }

    public static class Get {

        protected Operation spec;
        protected DataSchemaNode schemaNode;
        private static final String METHOD_NAME = "GET";

        public Get(final DataSchemaNode node, final boolean isConfig) {
            this.schemaNode = node;
            spec = new Operation();
            spec.setMethod(METHOD_NAME);
            spec.setNickname(METHOD_NAME + "-" + node.getQName().getLocalName());
            spec.setType((isConfig ? CONFIG : OPERATIONAL) + node.getQName().getLocalName());
            spec.setNotes(node.getDescription().orElse(null));
        }

        public Get pathParams(final List<Parameter> params) {
            final List<Parameter> pathParameters = new ArrayList<>(params);
            spec.setParameters(pathParameters);
            return this;
        }

        public Operation build() {
            return spec;
        }
    }

    public static class Put {
        protected Operation spec;
        protected String nodeName;
        protected String parentName;
        private static final String METHOD_NAME = "PUT";

        public Put(final String nodeName, final String description, final String parentName) {
            this.nodeName = nodeName;
            this.parentName = parentName;
            spec = new Operation();
            spec.setType(parentName + CONFIG + nodeName + TOP);
            spec.setNotes(description);
            spec.setConsumes(CONSUMES_PUT_POST);
        }

        public Put pathParams(final List<Parameter> params) {
            final List<Parameter> parameters = new ArrayList<>(params);
            final Parameter payload = new Parameter();
            payload.setParamType("body");
            payload.setType(parentName + CONFIG + nodeName + TOP);
            payload.setName(CONFIG + nodeName);
            parameters.add(payload);
            spec.setParameters(parameters);
            return this;
        }

        public Operation build() {
            spec.setMethod(METHOD_NAME);
            spec.setNickname(METHOD_NAME + "-" + nodeName);
            return spec;
        }
    }

    public static final class Post extends Put {

        public static final String METHOD_NAME = "POST";
        private final DataNodeContainer dataNodeContainer;

        public Post(final String nodeName, final String parentName, final String description,
                    final DataNodeContainer dataNodeContainer) {
            super(nodeName, description, parentName.replace("_module", ""));
            this.dataNodeContainer = dataNodeContainer;
            spec.setType(CONFIG + nodeName + METHOD_NAME);
            spec.setConsumes(CONSUMES_PUT_POST);
        }

        @Override
        public Operation build() {
            spec.setMethod(METHOD_NAME);
            spec.setNickname(METHOD_NAME + "-" + nodeName);
            return spec;
        }

        @Override
        public Put pathParams(final List<Parameter> params) {
            final List<Parameter> parameters = new ArrayList<>(params);
            for (final DataSchemaNode node : dataNodeContainer.getChildNodes()) {
                if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                    final Parameter payload = new Parameter();
                    payload.setParamType("body");
                    payload.setType(parentName + CONFIG + node.getQName().getLocalName() + TOP);
                    payload.setName("**" + CONFIG + node.getQName().getLocalName());
                    parameters.add(payload);
                }
            }
            spec.setParameters(parameters);
            return this;
        }

        public Post summary(final String summary) {
            spec.setSummary(summary);
            return this;
        }
    }

    public static final class Delete extends Get {
        private static final String METHOD_NAME = "DELETE";

        public Delete(final DataSchemaNode node) {
            super(node, false);
        }

        @Override
        public Operation build() {
            spec.setMethod(METHOD_NAME);
            spec.setNickname(METHOD_NAME + "-" + schemaNode.getQName().getLocalName());
            spec.setType(null);
            return spec;
        }
    }
}
