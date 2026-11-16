# LLM Add-On for Freeplane - Help

This document provides guidance on using the features of the LLM Add-On.

## How Image Generation Works

This feature uses the Novita.ai API to generate images based on the text of a selected node and potentially its context within the mind map.

**Workflow:**

1.  **Select Node:** Click on the mind map node whose text you want to use as the basis for the image prompt.
2.  **Run Script:** Execute the "Generate Image" script (e.g., via Tools -> Scripts -> Generate Image, or a hotkey if configured).
3.  **API Key Checks (First Time / If Missing):**
    *   The script will first check for a configured Novita.ai API key. If missing, you'll be prompted to enter it. This key is required for the actual image generation step and will be saved in your Freeplane user configuration.
    *   Next, it checks if a custom image prompt template has been saved by the user.
    *   **If no custom template is saved:** The script will attempt to enhance the selected node's text using a separate LLM (like GPT or OpenRouter models) to create a more detailed image prompt. It will check for the corresponding LLM provider's API key (e.g., OpenAI or OpenRouter). If missing, you'll be prompted to enter it, and it will be saved in your configuration. A brief, non-cancellable "Generating Prompt" dialog will appear during this step.
    *   **If a custom template *is* saved:** The LLM enhancement step is skipped, and the saved template is used directly.
4.  **Edit Parameters Dialog:** The "Edit Image Generation Parameters" dialog appears.
    *   **Header:** Shows whether a user-saved template/parameters are being used or the system defaults.
    *   **Prompt Area:** Displays the image prompt template (either your saved one or the default/LLM-enhanced one). You can edit this text directly. Variables like `$nodeContent` or `$generatedPrompt` will be replaced later.
    *   **Available Variables:** Lists placeholders you can use in the prompt template.
    *   **Generation Parameters:** Sliders allow you to adjust:
        *   `Steps`: Quality/time trade-off (typically 4-50).
        *   `Width`/`Height`: Image dimensions (256-1024 pixels).
        *   `Number of Images`: How many variations to generate (1-4).
        *   *(Note: Adjusting sliders automatically saves the new value to your user configuration when you release the mouse button, making it the default for the next time you run the script.)*
    *   **Buttons:**
        *   `Generate`: Proceeds to generate the image(s) using the current prompt text and slider values. Closes the dialog.
        *   `Save Template`: Saves the *current text* in the prompt area AND the *current slider values* as your user defaults for next time. The dialog stays open.
        *   `Reset to Default`: Reverts the prompt text and slider values to the system defaults and saves them. Requires confirmation. The dialog stays open.
        *   `Cancel`: Closes the dialog without generating images.
5.  **Image Generation:** After clicking "Generate":
    *   A "Generating Image" progress dialog appears. This process can take some time depending on the parameters and API load. You can click "Cancel" on this dialog to abort the generation request.
6.  **Image Selection / Attachment:**
    *   **If Cancelled:** The process stops.
    *   **If Successful:**
        *   **If you requested only 1 image:** The generated image is automatically downloaded and attached directly to the originally selected node.
        *   **If you requested multiple images (2-4):** A new "Select Generated Image" dialog appears, displaying the generated images.
            *   Click on the image you want to use.
            *   A brief, non-cancellable "Downloading Image" dialog appears while the selected image is downloaded and attached to the originally selected node.
            *   If you click "Cancel" in the selection dialog, no image is attached.

**Configuration:**

*   API keys (Novita, OpenRouter) are stored in your Freeplane user properties (`user.properties` file).
*   Your saved image prompt template and parameter slider values are also stored there, prefixed with `llm.addon.`.


## neurOS Bridge (v0.7.0, opt-in)

The neurOS bridge connects Freeplane to a neurOS knowledge mount — a live
FUSE filesystem over a personal knowledge substrate. It is entirely opt-in:
until you configure it, the menu items only explain what is missing, and no
defaults are guessed.

### Configuration (`LLM > Configure neurOS`)

*   **Mount path** — the FUSE root of your mount, e.g.
    `/mnt/neuros/software/<scope>`. Must contain a live mount (the dialog
    checks for `search/` or `README.cartridge`).
*   **Workspace path** — the watched directory the mount ingests from (the
    plain workspace of the scope). Cards are written here.
*   **Search results limit** — maximum hits attached per search (default 5).
*   **Card directory** — subdirectory of the workspace for pushed cards
    (default `neuros-cards`).

All four are stored in your Freeplane user properties, prefixed
`llm.addon.neuros.`.

### Searching (`LLM > neurOS: Search Knowledge from Node`)

1.  Select a node — its text seeds the query (edit it in the dialog if you
    want a different phrasing).
2.  The mount's semantic search surface (`search/<question>`) is queried.
3.  Results appear as a tagged branch; each hit node's note carries the
    hit's uuid, subtype and on-mount read path (`by-id/<uuid>`).

Right after a fresh mount the semantic projections may still be warming —
an empty result usually means "retry in a minute", and the add-on says so.

### Pushing a branch as a card (`LLM > neurOS: Push Branch as Card`)

1.  Select a node (its children come along as an outline).
2.  Confirm the push — the card is written as `<uuid>-<slug>.card.md` into
    the configured card directory, in the initial `Backlog` list state.
3.  The add-on waits for the substrate to ingest the file (up to 15s) and
    reports the ingest status and the new card's uuid.
4.  The source node is tagged `neuros:card`; its note records the uuid so
    you can wire `[[card:<uuid>]]` edges later.

On strict (state-machine enforcing) mounts, `Backlog` is the legal initial
state, so pushed cards are accepted as-is.
