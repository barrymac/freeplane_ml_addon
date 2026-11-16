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


## neurOS Knowledge (v0.8.3, opt-in, per map)

The neurOS features discover their connection from the current map's location.
There is no neurOS location or scope configuration tool.

A map is enabled when it is inside a neurOS knowledge surface with
`README.cartridge`, or in a watched workspace with a valid `*_KNOWLEDGE` link to
one. A map in a plain directory simply hears that neurOS is not enabled for it.

The map's scope is read from the knowledge source itself. Following an ordinary
Freeplane hyperlink to a map in another scope therefore works naturally: the
newly opened map searches its own scope.

Optional per-map safety budgets (stored in the map) include:

*   **Card folder** — relative folder inside the write destination (default
    `neuros-cards`).
*   **Search results** — maximum results per question (default 5, maximum 25).
*   **Availability wait** — how long saving waits for availability (default 15
    seconds, maximum 60).
*   **Map context** — bounded ancestor, child and note budgets used to make one
    precise query.

Invalid or unsafe values are refused with a clear message; nothing falls back
silently.

### Searching (`LLM > Search neurOS Knowledge`)

1.  Select a node. Its text becomes the primary question, which you can edit.
2.  Optionally include bounded map context — parent titles, immediate child
    titles and the node's note — to make one precise query. The dialog shows
    the exact query before you search.
3.  Results appear as a readable branch. Each result carries a link to its
    source material; identifiers and implementation paths are not shown.

The search always stays inside the map's current knowledge scope and issues
exactly one semantic query.

Right after connecting a new source, the semantic index may still be warming —
an empty result usually means "retry in a minute", and the add-on says so.

### Saving a branch (`LLM > Save Branch to neurOS`)

1.  Select a node (its children come along as an outline).
2.  Confirm the destination. The branch is written into the workspace
    discovered from the map as a work-graph card.
3.  The add-on confirms availability using whatever surface the source
    provides and reports honestly when availability is still unconfirmed.
4.  The source node is tagged `neurOS card` and links to its source material
    without exposing an identifier in the map.
