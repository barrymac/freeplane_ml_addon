#!/usr/bin/env python3
"""Install a Freeplane .addon.mm package without the GUI.

Reimplements what installScriptAddOn.groovy does when the user clicks
"Install": writes the scripts, lib and images under the user directory and
generates <name>.script.xml in the exact shape ScriptAddOnProperties.toXml()
produces (see freeplane source). No shortcuts are registered.
"""
from __future__ import annotations

import argparse
import base64
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def child_by_text(parent: ET.Element, text: str) -> ET.Element | None:
    for node in parent.findall('node'):
        if (node.get('TEXT') or '') == text:
            return node
    return None


def attrs_as_map(node: ET.Element) -> dict[str, str]:
    return {a.get('NAME'): a.get('VALUE') for a in node.findall('attribute')}


def richcontent_inner(rc: ET.Element) -> str:
    """Serialize the children of <richcontent> without the wrapper tag."""
    xml = ET.tostring(rc, encoding='unicode')
    start = xml.find('>')
    end = xml.rfind('</richcontent>')
    return xml[start + 1:end].strip()


def node_text(node: ET.Element) -> str:
    """Freeplane proxy Node.text(): richcontent NODE content, else TEXT."""
    for rc in node.findall('richcontent'):
        if rc.get('TYPE') == 'NODE':
            return richcontent_inner(rc)
    return node.get('TEXT') or ''


def first_child_text(node: ET.Element) -> str:
    kids = node.findall('node')
    if not kids:
        return ''
    return node_text(kids[0])


def expand(value, variables):
    if value is None:
        return None
    s = str(value)
    return re.sub(r'\$\{([^}]+)\}', lambda m: str(variables.get(m.group(1), m.group(0))), s)


def cdata(content: str) -> str:
    return '<![CDATA[' + content.replace(']]>', ']]]]><![CDATA[>') + ']]>'


def esc(content: str) -> str:
    return (content.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;'))


def install(package: Path, base: Path) -> None:
    tree = ET.parse(package)
    root = tree.getroot().find('node')
    if root is None:
        raise SystemExit(f'{package}: no root <node>')

    props = attrs_as_map(root)
    name = props.get('name')
    if not name:
        raise SystemExit(f'{package}: add-on has no name')
    variables = dict(props)
    variables['name'] = name
    variables['installationbase'] = str(base)

    # --- gather sections -------------------------------------------------
    description = ''
    license = ''
    preferences_xml = None
    default_props: dict[str, str] = {}
    translations: list[tuple[str, list[tuple[str, str]]]] = []
    deinstall: list[str] = []
    scripts: list[dict[str, object]] = []
    images: list[tuple[str, bytes]] = []
    libs: list[tuple[str, bytes]] = []

    desc_node = child_by_text(root, 'description')
    if desc_node is not None:
        description = first_child_text(desc_node)
    lic_node = child_by_text(root, 'license')
    if lic_node is not None:
        license = first_child_text(lic_node)
    pref_node = child_by_text(root, 'preferences.xml')
    if pref_node is not None and pref_node.findall('node'):
        preferences_xml = first_child_text(pref_node)
    default_node = child_by_text(root, 'default.properties')
    if default_node is not None:
        default_props = {expand(k, variables): expand(v, variables)
                         for k, v in attrs_as_map(default_node).items()}
    trans_node = child_by_text(root, 'translations')
    if trans_node is not None:
        for locale in trans_node.findall('node'):
            entries = [(expand(k, variables), v)
                       for k, v in attrs_as_map(locale).items()]
            translations.append(((locale.get('TEXT') or ''), entries))
    deinstall_node = child_by_text(root, 'deinstall')
    if deinstall_node is not None:
        for attr in deinstall_node.findall('attribute'):
            if attr.get('NAME') == 'delete':
                deinstall.append(expand(attr.get('VALUE'), variables))
    scripts_node = child_by_text(root, 'scripts')
    if scripts_node is not None:
        for script in scripts_node.findall('node'):
            attrs = attrs_as_map(script)
            scripts.append({
                'name': expand(script.get('TEXT'), variables),
                'menuTitleKey': expand(attrs.get('menuTitleKey'), variables),
                'menuLocation': expand(attrs.get('menuLocation'), variables),
                'executionMode': attrs.get('executionMode'),
                'keyboardShortcut': attrs.get('keyboardShortcut', ''),
                'permissions': {k: v for k, v in attrs.items()
                                if k.startswith('execute_scripts_')},
                'body': first_child_text(script),
            })
    images_node = child_by_text(root, 'images')
    if images_node is not None:
        for image in images_node.findall('node'):
            file_name = expand(image.get('TEXT'), variables)
            images.append((file_name, base64.b64decode(first_child_text(image))))
    lib_node = child_by_text(root, 'lib')
    if lib_node is not None:
        for lib in lib_node.findall('node'):
            file_name = expand(lib.get('TEXT'), variables)
            libs.append((file_name, base64.b64decode(first_child_text(lib))))

    # --- write files ----------------------------------------------------
    addon_dir = base / 'addons' / name
    scripts_dir = addon_dir / 'scripts'
    scripts_dir.mkdir(parents=True, exist_ok=True)
    for script in scripts:
        target = scripts_dir / str(script['name'])
        target.write_text(str(script['body']), encoding='utf-8')
        print(f'[+] script {target}')

    if libs:
        lib_dir = addon_dir / 'lib'
        lib_dir.mkdir(parents=True, exist_ok=True)
        for file_name, data in libs:
            target = lib_dir / file_name
            target.write_bytes(data)
            print(f'[+] lib {target}')

    if images:
        images_dir = base / 'resources' / 'images'
        images_dir.mkdir(parents=True, exist_ok=True)
        for file_name, data in images:
            target = images_dir / file_name
            target.write_bytes(data)
            print(f'[+] image {target}')

    # --- generate <name>.script.xml --------------------------------------
    lines: list[str] = []
    lines.append('<?xml version="1.0" encoding="UTF-8"?>')
    attrs = [
        ('name', name),
        ('version', props.get('version', '')),
        ('latestVersion', ''),
        ('freeplaneVersionFrom', props.get('freeplaneVersionFrom', '')),
    ]
    if props.get('freeplaneVersionTo'):
        attrs.append(('freeplaneVersionTo', props['freeplaneVersionTo']))
    homepage = props.get('homepage') or root.get('LINK') or ''
    if homepage:
        attrs.append(('homepage', homepage))
    if props.get('updateUrl'):
        attrs.append(('updateUrl', props['updateUrl']))
    if props.get('author'):
        attrs.append(('author', props['author']))
    attrs.append(('active', 'true'))
    attr_str = ' '.join(f'{k}="{esc(str(v))}"' for k, v in attrs)
    lines.append(f'<addon {attr_str}>')

    def open_close(tag: str, content: str | None, use_cdata: bool = True):
        if content is None:
            lines.append(f'<{tag}/>')
        elif use_cdata:
            lines.append(f'<{tag}>{cdata(content)}</{tag}>')
        else:
            lines.append(f'<{tag}>{esc(content)}</{tag}>')

    open_close('description', description)
    open_close('license', license)
    open_close('preferences.xml', preferences_xml)

    lines.append('<translations>')
    for locale, entries in translations:
        lines.append(f'<locale name="{esc(locale)}">')
        for key, value in entries:
            lines.append(f'<entry key="{esc(key)}">{esc(value)}</entry>')
        lines.append('</locale>')
    lines.append('</translations>')

    if default_props:
        attr_str = ' '.join(f'{k}="{esc(v)}"' for k, v in default_props.items())
        lines.append(f'<default.properties {attr_str}/>')
    else:
        lines.append('<default.properties/>')

    if images:
        lines.append('<images>')
        for file_name, _ in images:
            lines.append(f'<image name="{esc(file_name)}"/>')
        lines.append('</images>')
    else:
        lines.append('<images/>')

    lines.append('<deinstall>')
    for rule in deinstall:
        lines.append(f'<delete>{cdata(rule)}</delete>')
    lines.append('</deinstall>')

    lines.append('<scripts>')
    for script in scripts:
        sattrs = [
            ('name', script['name']),
            ('menuTitleKey', script['menuTitleKey']),
            ('menuLocation', script['menuLocation']),
            ('executionMode', script['executionMode']),
        ]
        if script.get('keyboardShortcut'):
            sattrs.append(('keyboardShortcut', script['keyboardShortcut']))
        for perm, value in sorted(script['permissions'].items()):
            sattrs.append((perm, value))
        sattr_str = ' '.join(f'{k}="{esc(str(v))}"' for k, v in sattrs)
        lines.append(f'<script {sattr_str}/>')
    lines.append('</scripts>')

    if libs:
        lines.append('<libs>')
        for file_name, _ in libs:
            lines.append(f'<lib name="{esc(file_name)}"/>')
        lines.append('</libs>')
    else:
        lines.append('<libs/>')

    lines.append('</addon>')
    xml_path = base / 'addons' / f'{name}.script.xml'
    xml_path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'[+] xml {xml_path}')
    register_accelerators(base, scripts)


def register_accelerators(base: Path, scripts: list[dict]) -> None:
    """Write the keyboard shortcuts Freeplane expects in accelerator.properties.

    A script's <keyboardShortcut> attribute is only a declaration: Freeplane binds
    the key when its own installer records it here. Installing a package by hand
    skips that step, which is why shortcuts appear in the menu without but do
    nothing when pressed. The file is shared, so existing entries are preserved.
    """
    wanted: dict[str, str] = {}
    for script in scripts:
        shortcut = script.get('keyboardShortcut')
        if not shortcut:
            continue
        action = script.get('menuTitleKey') or script['name']
        wanted[f'acceleratorFor.MindMap/{action}_on_single_node'] = shortcut

    if not wanted:
        return

    path = base / 'accelerator.properties'
    lines = path.read_text(encoding='utf-8').splitlines() if path.exists() else []
    updated: dict[str, str] = {}
    order: list[str] = []

    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith('#'):
            order.append(line)
            continue
        key, _, value = stripped.partition('=')
        order.append(key)
        updated[key] = value

    written = 0
    for key, shortcut in wanted.items():
        if updated.get(key) != shortcut:
            if key not in updated:
                order.append(key)
            updated[key] = shortcut
            written += 1

    if not written:
        print(f'[=] accelerators already current: {len(wanted)}')
        return

    out = [line for line in order if line.startswith('#') or not line.strip()]
    out.extend(f'{key}={value}' for key, value in updated.items())
    path.write_text('\n'.join(out) + '\n', encoding='utf-8')
    print(f'[+] accelerators {path} ({written} updated)')


def warn_if_running() -> None:
    """A jar replaced under a running Freeplane breaks lazily loaded classes.

    The JVM keeps an open handle on the old archive; the next class the script
    touches — often a generated closure — is missing from the replaced file and
    surfaces as NoClassDefFoundError. Say it at install time instead.
    """
    running = []
    proc = Path('/proc')
    for entry in proc.iterdir() if proc.is_dir() else []:
        try:
            cmdline = (entry / 'cmdline').read_bytes().decode('utf-8', 'ignore')
        except Exception:
            continue
        if 'freeplanelauncher.jar' in cmdline:
            running.append(entry.name)
    if running:
        print(f'[!] Freeplane is running (pid {", ".join(running)}): restart it, or the '
              f'new jar will fail with NoClassDefFoundError on first use.')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('package', type=Path)
    parser.add_argument('--base', type=Path,
                        default=Path.home() / '.config/freeplane/1.12.x')
    args = parser.parse_args()
    if not args.package.exists():
        raise SystemExit(f'no such package: {args.package}')
    args.base.mkdir(parents=True, exist_ok=True)
    install(args.package, args.base)
    warn_if_running()


if __name__ == '__main__':
    main()
