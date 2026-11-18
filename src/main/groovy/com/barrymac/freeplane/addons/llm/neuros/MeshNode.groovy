package com.barrymac.freeplane.addons.llm.neuros

/** One entry a relationship can point at, as the reader should see it. */
class MeshNode {

    String id
    String title
    File file

    String toString() { title }
}