#!/usr/bin/env python3
"""Render a neurOS judge scope as a Freeplane mind map with connectors.

Consumer-organ deliverable: by-tag/judge -> judge nodes, by-dep/<uuid>/<EDGE> ->
target nodes + labelled connectors. Read-only against the substrate; the only
file written is the map itself, locally.
"""
import os
import sys
import xml.etree.ElementTree as ET


def ensure_workspace(mount):
    """Give the rendered map a neurOS-enabled home: a directory with a
    *_KNOWLEDGE symlink to the mount, so discovery works when it is opened."""
    import pathlib
    work = pathlib.Path('/tmp/judge-demo')
    work.mkdir(parents=True, exist_ok=True)
    link = work / 'LEGAL_KNOWLEDGE'
    if not link.exists():
        link.symlink_to(mount)
    return work

MOUNT = sys.argv[1] if len(sys.argv) > 1 else '/mnt/neuros/hohfeldian/legalease-legal'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/judge-render.mm'
SURFACE = sys.argv[3] if len(sys.argv) > 3 else 'judge'


def title_of(name):
    base = name
    for suffix in ('.md', '.judge', '.matter', '.card', '.card.md'):
        if base.endswith(suffix):
            base = base[: -len(suffix)]
    return base.replace('-', ' ').replace('_', ' ').strip()


def uuid_of(mount, filename):
    path = os.path.join(mount, 'ingest-status', filename)
    try:
        for line in open(path):
            if line.startswith('uuid:'):
                return line.split()[1].strip()
    except OSError:
        return None
    return None


def edges_from(mount, uuid):
    edges = []
    dep = os.path.join(mount, 'by-dep', uuid)
    if not os.path.isdir(dep):
        return edges
    for edge_type in sorted(os.listdir(dep)):
        typed = os.path.join(dep, edge_type)
        if not os.path.isdir(typed):
            continue
        for target in sorted(os.listdir(typed)):
            edges.append((edge_type, target))
    return edges


def main():
    surface_dir = os.path.join(MOUNT, 'by-tag', SURFACE)
    judges = sorted(os.listdir(surface_dir))
    judges = [f for f in judges if os.path.isfile(os.path.join(surface_dir, f)) and not f.startswith('.')]
    print(f'{SURFACE}: {len(judges)} entries')

    map_xml = ET.Element('map', version='freeplane 1.12.1')
    root = ET.SubElement(map_xml, 'node', TEXT=f'Judges and their matters ({len(judges)} judges)', ID='ID_ROOT')
    judges_branch = ET.SubElement(root, 'node', TEXT='Judges', ID='ID_JUDGES')
    matters_branch = ET.SubElement(root, 'node', TEXT='Matters', ID='ID_MATTERS')

    node_ids = {}
    edges = []
    next_id = [0]

    def new_id():
        next_id[0] += 1
        return f'ID_{next_id[0]:03d}'

    def node_for(branch, filename, kind):
        if filename in node_ids:
            return node_ids[filename]
        node_id = new_id()
        ET.SubElement(branch, 'node', TEXT=title_of(filename), ID=node_id,
                      LINK=f'file://{os.path.join(MOUNT, "by-tag", SURFACE, filename)}')
        node_ids[filename] = node_id
        return node_id

    total_edges = 0
    for filename in judges:
        source_id = node_for(judges_branch, filename, SURFACE)
        uuid = uuid_of(MOUNT, filename)
        if not uuid:
            continue
        for edge_type, target in edges_from(MOUNT, uuid):
            target_id = node_for(matters_branch, target, 'matter')
            edges.append((source_id, target_id, edge_type))
            total_edges += 1

    for source, target, label in edges:
        conn = ET.SubElement(map_xml.find(f'.//node[@ID="{source}"]'), 'connector',
                             DESTINATION=target, MIDDLE_LABEL=label)
        conn.set('COLOR', '#4060a0' if label == 'PRESIDES_OVER' else '#707070')

    workspace = ensure_workspace(MOUNT)
    output_path = workspace / 'JudgeRender.mm'
    ET.ElementTree(map_xml).write(str(output_path), encoding='utf-8', xml_declaration=True)
    print(f'nodes: {len(node_ids)} (judges + matters)')
    print(f'edges: {total_edges}')
    print(f'written: {output_path}')


if __name__ == '__main__':
    main()
