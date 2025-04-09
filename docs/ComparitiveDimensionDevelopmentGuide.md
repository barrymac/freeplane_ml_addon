Developer Guideline: Freeplane Node Comparison Plugin
1. Goal:
   Develop a Freeplane plugin that allows users to select two or more nodes (representing concepts or ideas).
   The plugin will use a Large Language Model (LLM) to compare these nodes based on a user-defined dimension (e.g., "Easy vs Hard", "Cost vs Benefit").
   Crucially, the comparison must be relative: The analysis for each node should explicitly state how it compares to the other selected node(s) along the specified dimension.
   The results will be added back into the Freeplane map in a structured way.
2. Core Workflow:
   Node Selection: User selects 2 or more nodes in Freeplane.
   Trigger Comparison: User initiates the comparison via a UI element (e.g., context menu item "Compare Nodes...").
   Define Dimension: The user specifies the comparison dimension (the spectrum). Methods:
   Dialog Input: Prompt user to enter the dimension (e.g., "Cheap vs Expensive"). The plugin needs to parse this into distinct poles (e.g., ["Cheap", "Expensive"]).
   (Optional) Connector Label: Use the text label of a connector between two selected nodes.
   (Optional) Dedicated Node: Use the text content of a third selected node.
   (Optional) Predefined List: Offer common dimensions (Pros/Cons, etc.).
   Prepare LLM Request:
   Gather the text content of the selected nodes.
   Get the comparison dimension poles (e.g., ["Pole A", "Pole B"]).
   Construct a prompt for the LLM. (See Section 4).
   Call LLM API: Send the request asynchronously (to avoid UI freezing). Include API key, selected model, and the prompt. Handle potential API errors (network, auth, rate limits).
   Receive & Parse Response: Expect a JSON response matching the specified schema (See Section 4). Handle JSON parsing errors robustly.
   Update Freeplane Map: Add the comparison results as new nodes (See Section 5).
3. Key Concept: Relative Comparison
   The core value is relative insight, not just individual analysis.
   The LLM prompt must instruct the model to compare each item against the others.
   Example: If comparing A and B on "Easy vs Hard", a point for A under "Easy" should be like: "A is easier to learn than B because..." NOT just "A is easy to learn."
   The text within the "point" field of the resulting JSON must reflect this relative nature.
4. LLM Interaction & JSON Schema
   Prompt Engineering:
   The prompt must clearly instruct the LLM to:
   Compare Concept A, Concept B, [Concept C...] relative to each other.
   Use the provided dimension poles (e.g., ["Easy", "Hard"]) as the basis for comparison.
   Generate only a JSON output matching the schema below.
   Ensure the text in the point fields reflects the relative comparison.
   Example Prompt Snippet: "Compare the following concepts: [Concept A text], [Concept B text] along the dimension defined by these poles: '[Pole 1]', '[Pole 2]'. Provide your analysis strictly as a JSON object conforming to the provided schema. Ensure each 'point' describes the concept's characteristic relative to the other concepts regarding the specified 'pole'."
   Allow for configurable prompts (advanced).
   Required JSON Output Schema: Instruct the LLM to return JSON matching this structure precisely.
   {
   "$schema": "http://json-schema.org/draft-07/schema#",
   "title": "Schema for Comparative Analysis of Concepts",
   "description": "Defines the structure for an LLM response comparing two or more concepts along a specified dimension, where each analytical point is relative to the other concept(s).",
   "type": "object",
   "properties": {
   "comparison": {
   "type": "object",
   "properties": {
   "dimension": {
   "type": "object",
   "properties": {
   "name": { "type": "string", "description": "Optional name for the overall dimension." },
   "poles": {
   "type": "array", "items": {"type": "string"}, "minItems": 2,
   "description": "The ordered points defining the comparison spectrum (e.g., ['Easy', 'Hard'])."
   }
   },
   "required": ["poles"]
   },
   "concepts": {
   "type": "array",
   "description": "An array containing the comparative analysis for each concept.",
   "items": {
   "type": "object",
   "properties": {
   "id": { "type": "string", "description": "Identifier for the concept (e.g., node text)." },
   "analysis": {
   "type": "array",
   "description": "List of points analyzing this concept *relative to the other concept(s)*.",
   "items": {
   "type": "object",
   "properties": {
   "pole": { "type": "string", "description": "The pole from dimension.poles this point relates to." },
   "point": { "type": "string", "description": "The analytical statement *comparing this concept to others* regarding this pole." }
   },
   "required": ["pole", "point"]
   }
   }
   },
   "required": ["id", "analysis"]
   }
   },
   "summary": { "type": "string", "description": "Optional overall summary statement comparing the concepts." }
   },
   "required": ["dimension", "concepts"]
   }
   },
   "required": ["comparison"]
   }
   Use code with caution.
   Json
5. Data Processing & Freeplane Output
   Parse JSON: Use a standard JSON library to parse the LLM response string into a usable object, validating against the schema if possible.
   Create Output Structure (Recommended):
   Create a new central node in Freeplane (e.g., "Comparison: [Dimension Name or Poles]").
   Link this central node back to the original selected nodes.
   For each object in the comparison.concepts array:
   Create a child node under the central comparison node representing the concept (use concept.id or truncated node text).
   For each object in that concept's analysis array:
   Consider creating sub-nodes grouped by pole (e.g., a node for "Easy", a node for "Hard").
   Add the point text (which contains the relative comparison) as a child node under the relevant pole node.
   If comparison.summary exists, add it as a child node to the central comparison node.
   Apply Styling: Use Freeplane's styling (node colors, icons) to visually distinguish the comparison results (e.g., different colors for different poles, specific icons for concepts). Apply tags indicating the LLM used.
6. Technical Considerations:
   Asynchronous Operations: LLM calls must be non-blocking. Use background threads or appropriate async mechanisms.
   Error Handling: Gracefully handle API errors, network issues, invalid JSON responses, and parsing failures. Inform the user.
   Configuration: Provide settings for:
   LLM API Key(s).
   LLM Model Selection.
   (Advanced) Custom prompt templates.
   Caching (Optional): Implement caching based on selected node content + dimension to avoid redundant API calls and save costs/time.
7. Comparison Dimensions (Examples for Context):
   Easy vs Hard
   Cheap vs Expensive
   Short-Term vs Long-Term
   High Risk vs Low Risk
   User-Friendly vs Expert-Oriented
   Flexible vs Rigid
   Left Wing vs Right Wing
   User can define any relevant opposing poles.
