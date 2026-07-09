#
# Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging
import os
from typing import List, Tuple

import difflib
import xml.dom.minidom
from jinja2 import Environment, FileSystemLoader
import controller_testlib.utils

from netconf_testlib.variables import variables

log = logging.getLogger(__name__)


def render_jinja_template(template_path: str, mapping: dict, filters: dict = None):
    file_dir, file_name = os.path.split(template_path)
    env = Environment(loader=FileSystemLoader(file_dir))
    if filters:
        env.filters.update(filters)
    template = env.get_template(file_name)

    return template.render(mapping)


def verify_jsons_match(
    json1: str,
    json2: str,
    json1_data_label: str = "json1",
    json2_data_label: str = "json2",
    volatiles_list: List[str] | Tuple[str] = (),
    jmes_path: str | None = None,
):
    """Verify if provided jsons are the same after normalization.

    Thin wrapper around controller_testlib.utils.verify_jsons_match that
    supplies netconf's own configured MAX_VISUAL_DIFF_LOG_SIZE.

    Args:
        json1 (str): First json value.
        json2 (str): Second json value.
        json1_data_label (str): Descrption of the first json value used as
            label.
        json2_data_label (str): Descrption of the second json value used as
            label.
        volatiles_list (List[str] | Tuple[str]): List of volatiles values,
            which should be ingored in comparison.
        jmes_path (str | None): Optional JMESPath expression used to query, filter,
            or extract a specific subset of the JSON data prior to comparison.

    Returns:
        None
    """
    controller_testlib.utils.verify_jsons_match(
        json1,
        json2,
        json1_data_label,
        json2_data_label,
        volatiles_list,
        jmes_path,
        max_visual_diff_log_size=variables.MAX_VISUAL_DIFF_LOG_SIZE,
    )


def normalize_xml_lines(xml_input):
    """Parses, cleans, and standardizes XML formatting into a list of lines.

    Args:
        xml_input (str | bytes): The raw XML content to be normalized.

    Returns:
        List[str]: A list of cleanly formatted and indented XML lines.
    """
    if isinstance(xml_input, bytes):
        xml_input = xml_input.decode("utf-8")

    dom = xml.dom.minidom.parseString(xml_input)

    # Strip out existing purely whitespace text nodes.
    # This stops minidom from double-spacing or preserving weird indents.
    for node in dom.getElementsByTagName("*"):
        for child in list(node.childNodes):
            if child.nodeType == xml.dom.Node.TEXT_NODE and not child.nodeValue.strip():
                node.removeChild(child)

    pretty_xml = dom.toprettyxml(indent=" " * 4)

    return [line.rstrip() for line in pretty_xml.splitlines() if line.strip()]


def verify_xmls_match(xml1, xml2, xml1_data_label1, xml2_data_label):
    """Verify if provided xmls are the same after normalization.

    Args:
        xml1 (str): First xml value.
        xml2 (str): Second xml value.
        xml1_data_label (str): Descrption of the first xml value used as label.
        xml2_data_label (str): Descrption of the second xml value used as label.

    Returns:
        None
    """
    normalized_xml_lines1 = normalize_xml_lines(xml1)
    normalized_xml_lines2 = normalize_xml_lines(xml2)

    log.debug(f"{normalized_xml_lines1=}")
    log.debug(f"{normalized_xml_lines2=}")

    diff = list(
        difflib.unified_diff(
            normalized_xml_lines1,
            normalized_xml_lines2,
            fromfile=xml1_data_label1,
            tofile=xml2_data_label,
            lineterm="",
        )
    )

    if diff:
        diff_message = "\n".join(diff)
        raise AssertionError(f"XMLs do not match! Differences found:\n{diff_message}")
