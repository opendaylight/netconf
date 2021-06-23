/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.restconf.wadl.generator

import static java.util.Objects.requireNonNull

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList
import java.util.List
import org.opendaylight.yangtools.yang.common.XMLNamespace
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode
import org.opendaylight.yangtools.yang.model.api.Module

final class WadlTemplate {
    static val PATH_DELIMETER = '/'

    val EffectiveModelContext context
    val Module module
    val List<DataSchemaNode> configData = new ArrayList
    val List<DataSchemaNode> operationalData = new ArrayList

    var List<LeafSchemaNode> pathListParams

    new(EffectiveModelContext context, Module module) {
        this.context = requireNonNull(context)
        this.module = requireNonNull(module)

        for (child : module.childNodes) {
            if (child instanceof ContainerSchemaNode || child instanceof ListSchemaNode) {
                if (child.configuration) {
                    configData.add(child)
                } else {
                    operationalData.add(child)
                }
            }
        }
    }

    def body() {
        if (!module.rpcs.empty || !configData.empty || !operationalData.empty) {
            return application()
        }
        return null
    }

    private def application() '''
        <?xml version="1.0"?>
        <application xmlns="http://wadl.dev.java.net/2009/02" «module.importsAsNamespaces» xmlns:«module.prefix»="«module.namespace»">

            <grammars>
                <include href="«module.name».yang"/>
                «FOR imprt : module.imports»
                    <include href="«imprt.moduleName».yang"/>
                «ENDFOR»
            </grammars>

            <resources base="http://localhost:9998/restconf">
                «IF !operationalData.nullOrEmpty»
                <resource path="operational">
                    «FOR schemaNode : operationalData»
                        «schemaNode.firstResource(false)»
                    «ENDFOR»
                </resource>
                «ENDIF»
                «IF !configData.nullOrEmpty»
                <resource path="config">
                    «FOR schemaNode : configData»
                        «schemaNode.mehodPost»
                    «ENDFOR»
                    «FOR schemaNode : configData»
                        «schemaNode.firstResource(true)»
                    «ENDFOR»
                </resource>
                «ENDIF»
                «IF !module.rpcs.nullOrEmpty»
                <resource path="operations">
                    «FOR rpc : module.rpcs»
                        <resource path="«module.name»:«rpc.QName.localName»">
                            «methodPostRpc(rpc.input !== null, rpc.output !== null)»
                        </resource>
                    «ENDFOR»
                </resource>
                «ENDIF»
            </resources>
        </application>
    '''

    private def importsAsNamespaces(Module module) '''
        «FOR imprt : module.imports»
            xmlns:«imprt.prefix»="«context.findModule(imprt.moduleName, imprt.revision).get.namespace»"
        «ENDFOR»
    '''

    private def String firstResource(DataSchemaNode schemaNode, boolean config) '''
        <resource path="«module.name»:«schemaNode.createPath»">
            «resourceBody(schemaNode, config)»
        </resource>
    '''

    private def String resource(DataSchemaNode schemaNode, boolean config) '''
        <resource path="«schemaNode.createPath»">
            «resourceBody(schemaNode, config)»
        </resource>
    '''

    private def String createPath(DataSchemaNode schemaNode) {
        pathListParams = new ArrayList
        var StringBuilder path = new StringBuilder
        path.append(schemaNode.QName.localName)
        if (schemaNode instanceof ListSchemaNode) {
            for (listKey : schemaNode.keyDefinition) {
                pathListParams.add((schemaNode as DataNodeContainer).getDataChildByName(listKey) as LeafSchemaNode)
                path.append(PATH_DELIMETER).append('{').append(listKey.localName).append('}')
            }
        }
        return path.toString
    }

    private def String resourceBody(DataSchemaNode schemaNode, boolean config) '''
        «IF !pathListParams.nullOrEmpty»
            «resourceParams»
        «ENDIF»
        «schemaNode.methodGet»
        «val children = (schemaNode as DataNodeContainer).childNodes.filter[it|it.listOrContainer]»
        «IF config»
            «schemaNode.methodDelete»
            «schemaNode.methodPut»
            «FOR child : children»
                «child.mehodPost»
            «ENDFOR»
        «ENDIF»
        «FOR child : children»
            «child.resource(config)»
        «ENDFOR»
    '''

    private def resourceParams() '''
        «FOR pathParam : pathListParams»
            «IF pathParam !== null»
            «val type = pathParam.type.QName.localName»
            <param required="true" style="template" name="«pathParam.QName.localName»" type="«type»"/>
            «ENDIF»
        «ENDFOR»
    '''

    private static def methodGet(DataSchemaNode schemaNode) '''
        <method name="GET">
            <response>
                «representation(schemaNode.QName.namespace, schemaNode.QName.localName)»
            </response>
        </method>
    '''

    private static def methodPut(DataSchemaNode schemaNode) '''
        <method name="PUT">
            <request>
                «representation(schemaNode.QName.namespace, schemaNode.QName.localName)»
            </request>
        </method>
    '''

    private static def mehodPost(DataSchemaNode schemaNode) '''
        <method name="POST">
            <request>
                «representation(schemaNode.QName.namespace, schemaNode.QName.localName)»
            </request>
        </method>
    '''

    private static def methodPostRpc(boolean input, boolean output) '''
        <method name="POST">
            «IF input»
            <request>
                «representation(null, "input")»
            </request>
            «ENDIF»
            «IF output»
            <response>
                «representation(null, "output")»
            </response>
            «ENDIF»
        </method>
    '''

    private static def methodDelete(DataSchemaNode schemaNode) '''
        <method name="DELETE" />
    '''

    private static def representation(XMLNamespace prefix, String name) '''
        «val elementData = name»
        <representation mediaType="application/xml" element="«elementData»"/>
        <representation mediaType="text/xml" element="«elementData»"/>
        <representation mediaType="application/json" element="«elementData»"/>
        <representation mediaType="application/yang.data+xml" element="«elementData»"/>
        <representation mediaType="application/yang.data+json" element="«elementData»"/>
    '''

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
                justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static def boolean isListOrContainer(DataSchemaNode schemaNode) {
        return schemaNode instanceof ListSchemaNode || schemaNode instanceof ContainerSchemaNode
    }
}
