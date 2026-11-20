#!/usr/bin/env python3
"""Package a Freeplane add-on without launching Freeplane.

The gradle plugin's packageAddon opens a GUI dialog ("Create release package"),
which makes headless iteration impossible. This builds the same .addon.mm from
sources alone: the add-on definition map plus script, lib and image payloads,
using an existing package as a structural template so the archive shape stays
identical to what Freeplane's own packager produces.

Usage:
  tools/package-addon.py [template.addon.mm] [output.addon.mm]
"""
from __future__ import annotations

import base64
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


def child_by_text(parent: ET.Element, text: str) -> ET.Element | None:
    for node in parent.findall('node'):
        if (node.get('TEXT') or '') == text:
            return node
    return None


def richcontent_inner(rc: ET.Element) -> str:
    xml = ET.tostring(rc, encoding='unicode')
    start = xml.find('>', xml.find('<richcontent') + 1) + 1
    end = xml.rfind('</richcontent>')
    return xml[start:end]


def set_payload(node: ET.Element, text: str, is_base64: bool) -> None:
    """Replace the payload of a script/image/lib node.

    The payload lives in the node's first child <node>, either as a
    <richcontent> body or as the child's TEXT attribute.
    """
    kids = node.findall('node')
    if kids:
        child = kids[0]
    else:
        child = ET.SubElement(node, 'node')
    rc = child.find('richcontent')
    if rc is not None:
        # Replace the richcontent body with the new payload.
        for sub in list(rc):
            rc.remove(sub)
        rc.text = text
    else:
        child.set('TEXT', text)


def repack(template: Path, output: Path) -> None:
    tree = ET.parse(template)
    root = tree.getroot().find('node')
    if root is None:
        raise SystemExit(f'{template}: no root <node>')

    # Refresh the definition map's default node (metadata + localization) from
    # src, so a new menuTitleKey resolves instead of showing a raw key.
    src_root = ET.parse(REPO / 'src' / 'add-on' / 'LLM-AddOn.mm').getroot().find('node')
    src_default = child_by_text(src_root, 'default')
    current_default = child_by_text(root, 'default')
    if src_default is not None and current_default is not None:
        index = list(root).index(current_default)
        root.remove(current_default)
        root.insert(index, src_default)

    # Take each script's attributes from the CURRENT definition map, not from
    # the template: a permission changed in src must reach the package.
    definition = ET.parse(REPO / 'src' / 'add-on' / 'LLM-AddOn.mm')
    definition_scripts = {}
    for node in definition.getroot().iter('node'):
        text = node.get('TEXT') or ''
        if text.endswith('.groovy'):
            definition_scripts[text] = {a.get('NAME'): a.get('VALUE')
                                       for a in node.findall('attribute')}

    scripts_node = child_by_text(root, 'scripts')
    scripts_dir = REPO / 'src' / 'add-on' / 'scripts'
    if scripts_node is not None and scripts_dir.is_dir():
        existing = {n.get('TEXT'): n for n in scripts_node.findall('node')}
        for path in sorted(scripts_dir.glob('*.groovy')):
            name = path.name
            body = path.read_text(encoding='utf-8')
            if name in existing:
                # Refresh attributes from the definition map as well as the body.
                for attr_name, attr_value in definition_scripts.get(name, {}).items():
                    for attr in existing[name].findall('attribute'):
                        if attr.get('NAME') == attr_name:
                            attr.set('VALUE', attr_value)
                set_payload(existing[name], body, is_base64=False)
            else:
                # Clone an existing script node so attributes follow the
                # template's shape, then set name and body.
                import copy
                prototype = next(iter(existing.values()))
                new_node = copy.deepcopy(prototype)
                new_node.set('TEXT', name)
                set_payload(new_node, body, is_base64=False)
                scripts_node.append(new_node)

    for section, directory in (('lib', REPO / 'build' / 'addon' / 'lib'),
                               ('images', REPO / 'src' / 'add-on' / 'images')):
        section_node = child_by_text(root, section)
        if section_node is None or not directory.is_dir():
            continue
        existing = {n.get('TEXT'): n for n in section_node.findall('node')}
        for path in sorted(directory.iterdir()):
            if not path.is_file():
                continue
            data = base64.b64encode(path.read_bytes()).decode('ascii')
            if path.name in existing:
                set_payload(existing[path.name], data, is_base64=True)
            else:
                import copy
                prototype = next(iter(existing.values())) if existing else None
                if prototype is not None:
                    new_node = copy.deepcopy(prototype)
                    new_node.set('TEXT', path.name)
                    set_payload(new_node, data, is_base64=True)
                    section_node.append(new_node)

    version_node = child_by_text(root, 'version.properties')
    if version_node is not None and (REPO / 'version.properties').is_file():
        set_payload(version_node, (REPO / 'version.properties').read_text(encoding='utf-8'),
                    is_base64=False)

    output.write_bytes(ET.tostring(tree.getroot(), encoding='utf-8', xml_declaration=True))
    print(f'[+] packaged {output}')


def main() -> None:
    template = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if template is None:
        candidates = sorted((REPO / 'build' / 'addon').glob('LLM-AddOn-*.addon.mm'),
                            key=lambda p: p.stat().st_mtime, reverse=True)
        if not candidates:
            raise SystemExit('no template package found in build/addon')
        template = candidates[0]
    output = Path(sys.argv[2]) if len(sys.argv) > 2 else template
    repack(template, output)


if __name__ == '__main__':
    main()
