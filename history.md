# History

## v0.9.32 — 2026-11-18
- Fix the ambiguous `getProperty` lookup on Freeplane's config object (typed keys and fallbacks), which surfaced as "Ambiguous method overloading".
- Add `docs/DEMO.md` and `tools/demo-map.sh` so the lens, enmeshment and disagreement flows can be run end to end in five minutes.

## v0.9.31 — 2026-11-18
- Add relationship lenses: the map draws only the families you choose (dependency, blocking, structure, tracking, reference, code, governance, contradiction).
- Add "Enmesh This Node" (`Ctrl+Alt+E`): a judgement model proposes missing relationships through the chosen lens, drawn dashed and grey until accepted.
- An empty expansion now diagnoses itself: no relationships at all (enmeshment gap) versus links that resolve nowhere (substrate gap), tagged on the node.
- Add `tools/mesh-gaps.sh`: a scope-wide report of enmeshment and substrate gaps.

## v0.9.30 — 2026-11-17
- Judgement is Jev only: one transport, one key, no provider knobs.
- Jev vision: images travel as assets, fitted to the accepted size bounds (pad small frames, downscale large ones).
- `tools/jev-check.sh` and `tools/jev-vision-check.sh` verify text and vision judgements outside Freeplane.

## v0.9.29 — 2026-11-17
- The decision transport's model and endpoint are overridable from the environment, so a replacement for the retired model drops in without code changes.
- Add `tools/judgement-check.sh` to test a transport end to end outside Freeplane.

## v0.9.28 — 2026-11-17
- Add "Find neurOS Disagreements": compare the selected entry with the entries it links to and draw what the judgement supports.
- Each pair resolves to a contradiction, a reconciliation under a condition, or unresolved; only the first two are drawn.
- Contradictions are dashed crimson; reconciliations are solid. Unresolved pairs are reported, never guessed.
- Bounded at eight pairs per run; confidence below 0.75 is left unresolved.

## v0.9.27 — 2026-11-17
- Add a typed judgement client: one call asks named questions about a state and returns choices with confidence, or probabilities.
- Two transports: Jev at TypeSafe (verified live) and the decision model at OpenRouter.
- Credentials resolve from the environment first; a saved key is only a convenience, and keys are never written into a map.

## v0.9.26 — 2026-11-17
- The image prompt editor now offers the image service and model, and remembers both.
- A config that still names `openai` as its provider says how to migrate instead of only refusing.

## v0.9.25 — 2026-11-17
- Purge the OpenAI naming: settings live under `llm.*`, with existing `openai.*` values migrated once.
- Remove OpenAI as a provider; OpenRouter is the default and any chat-completions URL can be used.
- Image generation defaults to OpenRouter image models (openrouter key), with Novita kept as an option.
- A missing chat key no longer blocks image generation; the prompt is simply used as written.
- Chat parameters are written once for every provider instead of per-vendor spellings.

## v0.9.24 — 2026-11-17
- Draw the typed relationships a card or diagram declares as labelled map connectors.
- Reuse entries already in the map; add missing ones under a bounded Related knowledge branch.
- Treat contradiction as its own family: crimson and dashed, distinct from a reference.
- Read relationship vocabulary from the entry, so markers the cartridge has not named still read.
- Report links that point outside the scope instead of inventing placeholder nodes.
- Add `tools/mesh-plan.sh`, a Freeplane-free dry run of what will be drawn.
- Remove OpenAI-centric wording from the README and help, and rename the legacy screenshots.
- Fix the API-key prompt for image generation: Freeplane's input dialog now takes a node, not a frame.
- Refresh the default model list to the current catalogue, excluding OpenAI and Anthropic on cost grounds.
- Prompt enhancement uses the map's configured model instead of a hardcoded legacy id.

## v0.9.23 — 2026-11-17
- Draw typed relationships from a card or diagram as labelled map connectors.
- Reuse related entries already in the map; add missing ones under a bounded branch.
- Read relationship vocabulary from the entry, so unnamed markers still read.
- Report links that point outside the scope instead of drawing placeholders.
- Reduce embedded Markdown preview zoom to 35%.
- Replace generated raw notes with clean preview Markdown.
- Check preview cache before rendering so unchanged content is not regenerated.
- Size embedded Markdown preview images to the rendered text instead of the pane minimum.

## v0.9.22 — 2026-11-17
- Render Markdown preview HTML to a cached PNG and attach it as an embedded external object.
- Keep Markdown source in the note for native rollover; both dialog and map preview are available.

## v0.9.21 — 2026-11-17
- Strip CARD-<uuid>- prefix from browsed card filenames.
- Add the five-card relationship-wave doctrine set and validated diagram.

## v0.9.20 — 2026-11-17
- Use `latest/download/version.txt` for the update asset because GitHub's latest-download route returns 404 for `version.properties`.

## v0.9.19 — 2026-11-17
- Use the latest-release version.properties asset for update checks.

## v0.9.18 — 2026-11-17
- Use canonical refs/heads/main raw URL for update checks.

## v0.9.17 — 2026-11-17
- Sanitized public tracked content and removed generated neurOS card artifacts.
- Added https://onticfabric.com/ as the demos and project-updates link.

## v0.9.16 — 2026-11-17
- Treat Preview on a surface directory as a guidance message, not a source read error.

## v0.9.15 — 2026-11-17
- Add an explicit post-population collapse pass for default-collapsed sections.

## v0.9.14 — 2026-11-17
- Apply default folding after children are created so new catalogs stay collapsed.

## v0.9.13 — 2026-11-17
- Use explicit setFolded(true) for default-collapsed catalog sections.

## v0.9.12 — 2026-11-17
- Prefer the actual listing file for source previews and stop falling back to filename-only previews.
- Handle dynamic mounts where isFile()/by-id can be unavailable despite a readable listing file.

## v0.9.11 — 2026-11-17
- Rename Knowledge types to All knowledge types and fold it plus Supported content by default.
- Clarify curated surfaces versus full primitive vocabulary in catalog notes.

## v0.9.10 — 2026-11-17
- Package markedj and jsoup so Markdown preview works at runtime.
- Add `Ctrl+Alt+P` for on-demand preview.

## v0.9.9 — 2026-11-17
- Filter stateful surfaces using declared state-machine properties so only state-tagged work items appear as tasks.
- Show the declared state in result notes without hardcoding surfaces or lanes.

## v0.9.8 — 2026-11-17
- Attach cached PlantUML PNGs as external objects for an embedded map preview alongside rollover notes.

## v0.9.7 — 2026-11-17
- Browse refresh replaces only generated entries so newly ingested content appears without restarting.
- Verified direct 9P PlantUML write → VISIBLE ingest → diagrams/ projection → by-tag and semantic visibility.

## v0.9.6 — 2026-11-17
- Added preview lifecycle diagnostics: source type, cache path, note priming and dialog display.

## v0.9.5 — 2026-11-17
- Direct knowledge-surface maps are read-only for branch saving; local watched workspaces are required for writes.
- Removed mount-type write detection rather than working around 9P lock semantics.

## v0.9.4 — 2026-11-17
- Accept `cartridge-contract` as an enablement marker while `README.cartridge` is warming.
- Fall back to the 9P mount's `aname` for scope while `watcher-status` is unavailable.

## v0.9.3 — 2026-11-16
- Added on-demand Markdown and PlantUML previews in Swing dialogs.
- Markdown notes are primed for Freeplane's native rollover renderer.
- PlantUML PNGs are cached and inserted as Markdown previews for later rollovers.
- Process execution is limited to the preview actions.

## v0.9.2 — 2026-11-16
- Changed the browse accelerator to `Ctrl+Alt+I` after checking shortcut tables.
- Result activation now opens source material instead of recursively creating catalogs.
- Metadata entries no longer create nested catalogs.

## v0.9.1 — 2026-11-16
- Added right-click node context-menu browsing through `node_popup_scripting`.
- Added the `Ctrl+Alt+B` browse accelerator and preserved it in packaged installs.
- Shared the browse implementation between top-menu and context-menu actions.

## v0.9.0 — 2026-11-16
- Added the lazy Knowledge Catalog and Browse mode (`LLM > Browse neurOS Knowledge`).
- Dynamic surface roots, primitive types, MIME routing and door capabilities from live cartridge metadata.
- Bounded pages with Load more, explicit truncation, and no per-entry stat storm.
- Architecture: `docs/diagrams/01-neuros-knowledge-catalog.puml`.

## v0.8.3 — 2026-11-16
- Enable saving on scoped writable network surfaces detected from the live mount table.
- Keep scoped FUSE knowledge surfaces search-only unless a watched workspace supplies the destination.

## v0.8.2 — 2026-11-16
- Open dynamic query surfaces directly with bounded retries.
- Replaced misleading configuration errors with honest warming/busy messages.

## v0.8.1 — 2026-11-16
- Replaced the neurOS configuration dialog with automatic map-location discovery.
- Scope now follows the map opened through ordinary Freeplane hyperlinks.
- Plain maps simply hear that neurOS is not enabled.

## v0.8.0 — 2026-11-16
- Map-scoped neurOS knowledge: per-map source, explicit scope and write destination; no inherited defaults.
- Context-rich single-query search with bounded ancestor, child and note context.
- Human-first results: readable titles, open-source links, no visible identifiers or transport details.
- Transport-neutral knowledge locations, including global multi-scope surfaces and writable 9P filesystems.
- Safe bounded defaults with loud validation and honest availability reporting.

## v0.7.0 — 2026-11-16
- Add-on refresh and the first opt-in neurOS bridge (search and save-branch).
