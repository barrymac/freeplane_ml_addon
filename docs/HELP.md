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

