<map version="freeplane 1.12.1">
<!--To view this file, download free mind mapping software Freeplane from https://www.freeplane.org -->
<node TEXT="Prompt-LLM-AddOn" FOLDED="false" ID="ID_696401721" CREATED="1685111823904" MODIFIED="1744144756093" LINK="https://github.com/barrymac/freeplane_openai_addon" BACKGROUND_COLOR="#97c7dc" STYLE="oval" MAX_WIDTH="20 cm">
<font SIZE="16" BOLD="true" ITALIC="true"/>
<hook NAME="MapStyle">
    <properties show_icon_for_attributes="true" edgeColorConfiguration="#808080ff,#ff0000ff,#0000ffff,#00ff00ff,#ff00ffff,#00ffffff,#7c0000ff,#00007cff,#007c00ff,#7c007cff,#007c7cff,#7c7c00ff" mapUsesOwnSaveOptions="true" save_modification_times="false" show_tags="UNDER_NODES" save_last_visited_node="default" show_note_icons="true" associatedTemplateLocation="template:/standard-1.6.mm" save_folding="save_folding_if_map_is_changed" fit_to_viewport="false" show_icons="BESIDE_NODES"/>
    <tags category_separator="::"/>

<map_styles>
<stylenode LOCALIZED_TEXT="styles.root_node" STYLE="oval" UNIFORM_SHAPE="true" VGAP_QUANTITY="24 pt">
<font SIZE="24"/>
<stylenode LOCALIZED_TEXT="styles.predefined" POSITION="bottom_or_right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="default" ID="ID_271890427" ICON_SIZE="12 pt" COLOR="#000000" STYLE="fork">
<arrowlink SHAPE="CUBIC_CURVE" COLOR="#000000" WIDTH="2" TRANSPARENCY="200" DASH="" FONT_SIZE="9" FONT_FAMILY="SansSerif" DESTINATION="ID_271890427" STARTARROW="NONE" ENDARROW="DEFAULT"/>
<font NAME="SansSerif" SIZE="10" BOLD="false" ITALIC="false"/>
<richcontent TYPE="DETAILS" CONTENT-TYPE="plain/auto"/>
<richcontent TYPE="NOTE" CONTENT-TYPE="plain/auto"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.details"/>
<stylenode LOCALIZED_TEXT="defaultstyle.tags">
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.attributes">
<font SIZE="9"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.note" COLOR="#000000" BACKGROUND_COLOR="#ffffff" TEXT_ALIGN="LEFT"/>
<stylenode LOCALIZED_TEXT="defaultstyle.floating">
<edge STYLE="hide_edge"/>
<cloud COLOR="#f0f0f0" SHAPE="ROUND_RECT"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.selection" BACKGROUND_COLOR="#afd3f7" BORDER_COLOR_LIKE_EDGE="false" BORDER_COLOR="#afd3f7"/>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.user-defined" POSITION="bottom_or_right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="styles.topic" COLOR="#18898b" STYLE="fork">
<font NAME="Liberation Sans" SIZE="10" BOLD="true"/>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.subtopic" COLOR="#cc3300" STYLE="fork">
<font NAME="Liberation Sans" SIZE="10" BOLD="true"/>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.subsubtopic" COLOR="#669900">
<font NAME="Liberation Sans" SIZE="10" BOLD="true"/>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.important" ID="ID_67550811">
<icon BUILTIN="yes"/>
<arrowlink COLOR="#003399" TRANSPARENCY="255" DESTINATION="ID_67550811"/>
</stylenode>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.AutomaticLayout" POSITION="bottom_or_right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="AutomaticLayout.level.root" COLOR="#000000" STYLE="oval" SHAPE_HORIZONTAL_MARGIN="10 pt" SHAPE_VERTICAL_MARGIN="10 pt">
<font SIZE="18"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,1" COLOR="#0033ff">
<font SIZE="16"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,2" COLOR="#00b439">
<font SIZE="14"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,3" COLOR="#990000">
<font SIZE="12"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,4" COLOR="#111111">
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,5"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,6"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,7"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,8"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,9"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,10"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,11"/>
</stylenode>
</stylenode>
</map_styles>
</hook>
<hook NAME="AutomaticEdgeColor" COUNTER="18" RULE="ON_BRANCH_CREATION"/>
<attribute_layout NAME_WIDTH="112.61538 pt" VALUE_WIDTH="333.23076 pt"/>
<attribute NAME="name" VALUE="promptLlmAddOn"/>
<attribute NAME="version" VALUE="v0.5.0"/>
<attribute NAME="author" VALUE="barry, dpolivaev"/>
<attribute NAME="freeplaneVersionFrom" VALUE="v1.11.3" OBJECT="org.freeplane.features.format.FormattedObject|java.lang.String&amp;#x7c;v1.11.3|number:decimal:#0.####"/>
<attribute NAME="freeplaneVersionTo" VALUE=""/>
<attribute NAME="homepage" VALUE="github.com/barrymac/freeplane_ml_addon"/>
<attribute NAME="downloadUrl" VALUE="https://${homepage}/releases/download/${version}/LLM-AddOn-${version}.addon.mm"/>
<attribute NAME="changelogUrl" VALUE="https://${homepage}/commits/main"/>
<attribute NAME="addonsMenu" VALUE="/menu_bar/LLM"/>
<attribute NAME="updateUrl" VALUE="https://raw.githubusercontent.com/barrymac/freeplane_openai_addon/release/version.properties"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      The homepage of this add-on should be set as the link of the root node.
    </p>
    <p>
      The basic properties of this add-on. They can be used in script names and other attributes, e.g. &quot;${name}.groovy&quot;.
    </p>
    <ul>
      <li>
        name: The name of the add-on, normally a technically one (no spaces, no special characters except _.-).
      </li>
      <li>
        author: Author's name(s) and (optionally) email adresses.
      </li>
      <li>
        version: Since it's difficult to protect numbers like 1.0 from Freeplane's number parser it's advised to prepend a 'v' to the number, e.g. 'v1.0'.
      </li>
      <li>
        freeplane-version-from: The oldest compatible Freeplane version. The add-on will not be installed if the Freeplane version is too old.
      </li>
      <li>
        freeplane-version-to: Normally empty: The newest compatible Freeplane version. The add-on will not be installed if the Freeplane version is too new.
      </li>
      <li>
        updateUrl: URL of the file containing information (version, download url) on the latest version of this add-on. By default: &quot;${homepage}/version.properties&quot;
      </li>
      <li>
        downloadUrl: add-on file download URL added to version.properties, by default ${homepage}/$name-$version.addon.mm&quot;
      </li>
    </ul>
  </body>
</html></richcontent>
<node TEXT="description" POSITION="top_or_left" ID="ID_365830098" CREATED="1685111823975" MODIFIED="1742834859965">
<edge COLOR="#ff0000"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      Description would be awkward to edit as an attribute.
    </p>
    <p>
      So you have to put the add-on description as a child of the <i>'description'</i>&#xa0;node.
    </p>
    <p>
      To translate the description you have to define a translation for the key 'addons.${name}.description'.
    </p>
  </body>
</html></richcontent>
<node TEXT="Add on to use Language model APIs to generate mind map content" ID="ID_110054152" CREATED="1685111823976" MODIFIED="1743044046669"/>
</node>
<node TEXT="changes" POSITION="top_or_left" ID="ID_138977336" CREATED="1685111823976" MODIFIED="1685111823976">
<edge COLOR="#0000ff"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      Change log of this add-on: append one node for each noteworthy version and put the details for each version into a child node.
    </p>
  </body>
</html></richcontent>
</node>
<node TEXT="license" FOLDED="true" POSITION="top_or_left" ID="ID_950695085" CREATED="1685111823976" MODIFIED="1742834859969">
<edge COLOR="#00ff00"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      The add-ons's license that the user has to accept before she can install it.
    </p>
    <p>
      
    </p>
    <p>
      The License text has to be entered as a child of the <i>'license'</i>&#xa0;node, either as plain text or as HTML.
    </p>
  </body>
</html></richcontent>
<node TEXT="&#xa;This add-on is free software: you can redistribute it and/or modify&#xa;it under the terms of the GNU General Public License as published by&#xa;the Free Software Foundation, either version 2 of the License, or&#xa;(at your option) any later version.&#xa;&#xa;This program is distributed in the hope that it will be useful,&#xa;but WITHOUT ANY WARRANTY; without even the implied warranty of&#xa;MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the&#xa;GNU General Public License for more details.&#xa;" ID="ID_726417928" CREATED="1685111823976" MODIFIED="1685111823976"/>
</node>
<node TEXT="preferences.xml" POSITION="top_or_left" ID="ID_902316079" CREATED="1685111823976" MODIFIED="1743347818956">
<edge COLOR="#ff00ff"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      <span style="color: #000000; font-family: SansSerif, sans-serif;">The child node contains the add-on configuration as an extension to mindmapmodemenu.xml (in Tools-&gt;Preferences-&gt;Add-ons). </span>
    </p>
    <p>
      <span style="color: #000000; font-family: SansSerif, sans-serif;">Every property in the configuration should receive a default value in <i>default.properties</i>&#xa0;node.</span>
    </p>
  </body>
</html></richcontent>
</node>
<node TEXT="default.properties" POSITION="top_or_left" ID="ID_1363888784" CREATED="1685111823976" MODIFIED="1685111823976">
<attribute_layout NAME_WIDTH="138.75 pt" VALUE_WIDTH="138.75 pt"/>
<attribute NAME="${name}.icon" VALUE="/images/${name}.svg"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      These properties are used for:
    </p>
    <ul>
      <li>
        Each property defined in the preferences should have a default value in the attributes of this node.
      </li>
      <li>
        For each menu item with an icon add an attribute with the icon key (use developer tool menuItemInfo) as key and the icon path as value. Example: '${name}.icon': '/images/${name}-icon.png'
      </li>
    </ul>
  </body>
</html></richcontent>
<edge COLOR="#00ffff"/>
</node>
<node TEXT="translations" POSITION="top_or_left" ID="ID_1606264590" CREATED="1685111823976" MODIFIED="1685111823976">
<edge COLOR="#7c0000"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      The translation keys that this script uses. Define one child node per supported locale. The attributes contain the translations. Define at least
    </p>
    <ul>
      <li>
        'addons.${name}' for the add-on's name
      </li>
      <li>
        'addons.${name}.description' for the description, e.g. in the add-on overview dialog (not necessary for English)
      </li>
      <li>
        'addons.${name}.&lt;scriptname&gt;' for each script since it will be the menu title.
      </li>
    </ul>
  </body>
</html></richcontent>
<node TEXT="en" ID="ID_1097454652" CREATED="1685111823977" MODIFIED="1742847796035">
<attribute_layout NAME_WIDTH="159 pt" VALUE_WIDTH="102 pt"/>
<attribute NAME="addons.${name}" VALUE="LLM AddOn"/>
<attribute NAME="addons.${name}.askGPT" VALUE="Configure Prompts and Model"/>
<attribute NAME="addons.${name}.quickPrompt" VALUE="Quick Prompt (Ctrl+Alt+G)"/>
<attribute NAME="addons.${name}.compareConnectedNodes" VALUE="Compare Connected Nodes"/>
</node>
</node>
<node TEXT="deinstall" POSITION="top_or_left" ID="ID_1927303474" CREATED="1685111823977" MODIFIED="1744143852417">
<edge COLOR="#00007c"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      List of files and/or directories to remove on uninstall
    </p>
  </body>
</html></richcontent>
<attribute_layout NAME_WIDTH="37.5 pt" VALUE_WIDTH="268.49999 pt"/>
<attribute NAME="delete" VALUE="${installationbase}/addons/${name}.script.xml"/>
<attribute NAME="delete" VALUE="${installationbase}/images/${name}.svg"/>
<attribute NAME="delete" VALUE="${installationbase}/images/${name}-icon.svg"/>
<attribute NAME="delete" VALUE="${installationbase}/addons/${name}/scripts/AskLm.groovy"/>
<attribute NAME="delete" VALUE="${installationbase}/addons/${name}/scripts/QuickPrompt.groovy"/>
<attribute NAME="delete" VALUE="${installationbase}/addons/${name}/scripts/CompareConnectedNodes.groovy"/>
<attribute NAME="delete" VALUE="${installationbase}/resources/images/promptLlmAddOn.svg"/>
<attribute NAME="delete" VALUE="${installationbase}/resources/images/promptLlmAddOn-icon.svg"/>
<attribute NAME="delete" VALUE="${installationbase}/addons/${name}/lib/Exceptions.groovy"/>
<attribute NAME="delete" VALUE="${installationbase}/addons/${name}/lib/llm.jar"/>
<attribute NAME="delete" VALUE="${installationbase}/addons/${name}/lib"/>
</node>
<node TEXT="scripts" POSITION="bottom_or_right" ID="ID_684917236" CREATED="1685111823977" MODIFIED="1742834859980">
<edge COLOR="#007c00"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      An add-on may contain multiple scripts. The node text defines the script name (e.g. insertInlineImage.groovy). The name must have a suffix of a supported script language like .groovy or .js and may only consist of letters and digits. The script properties have to be configured via attributes:
    </p>
    <p>
      
    </p>
    <p>
      * menuLocation: &lt;locationkey&gt;
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- Defines the menu location, defaults a sub menu 'main_menu_scripting/addons.${name}'.
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;-&#xa0;Use developer tool menuItemInfo to inspect menu location keys.
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- This attribute is mandatory
    </p>
    <p>
      
    </p>
    <p>
      * menuTitleKey: &lt;key&gt;
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- The menu item title will be looked up under the translation key &lt;key&gt; - don't forget to define its translation.
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- This attribute is mandatory
    </p>
    <p>
      
    </p>
    <p>
      * executionMode: &lt;mode&gt;
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- The execution mode as described in the Freeplane wiki (http://freeplane.sourceforge.net/wiki/index.php/Scripting)
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- ON_SINGLE_NODE: Execute the script once. The <i>node</i>&#xa0;variable is set to the selected node.
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- ON_SELECTED_NODE: Execute the script n times for n selected nodes, once for each node.
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- ON_SELECTED_NODE_RECURSIVELY: Execute the script on every selected node and recursively on all of its children.
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- In doubt use ON_SINGLE_NODE.
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- This attribute is mandatory
    </p>
    <p>
      
    </p>
    <p>
      * keyboardShortcut: &lt;shortcut&gt;
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- Optional: keyboard combination / accelerator for this script, e.g. control alt I
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- Use lowercase letters for modifiers and uppercase for letters. Use no + signs.
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- The available key names are listed at http://download.oracle.com/javase/1.4.2/docs/api/java/awt/event/KeyEvent.html#VK_0
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;&#xa0;&#xa0;In the list only entries with a 'VK_' prefix count. Omit the prefix in the shortcut definition.
    </p>
    <p>
      
    </p>
    <p>
      * Permissions&#xa0;that the script(s) require, each either false or true:
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- execute_scripts_without_asking
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- execute_scripts_without_file_restriction: permission to read files
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- execute_scripts_without_write_restriction: permission to create/change/delete files
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- execute_scripts_without_exec_restriction: permission to execute other programs
    </p>
    <p>
      &#xa0;&#xa0;&#xa0;- execute_scripts_without_network_restriction: permission to access the network
    </p>
    <p>
      &#xa0;&#xa0;Notes:
    </p>
    <p>
      &#xa0;&#xa0;- The set of permissions is fixed.
    </p>
    <p>
      &#xa0;&#xa0;- Don't change the attribute names, don't omit one.
    </p>
    <p>
      &#xa0;&#xa0;- Set the values either to true or to false
    </p>
    <p>
      &#xa0;&#xa0;- In any case set execute_scripts_without_asking to true unless you want to annoy users.
    </p>
  </body>
</html></richcontent>
<node TEXT="AskLm.groovy" ID="ID_231711959" CREATED="1685111823978" MODIFIED="1742830474533">
<attribute_layout NAME_WIDTH="202.49999 pt" VALUE_WIDTH="156.75 pt"/>
<attribute NAME="menuTitleKey" VALUE="addons.${name}.askGPT"/>
<attribute NAME="menuLocation" VALUE="${addonsMenu}"/>
<attribute NAME="executionMode" VALUE="on_single_node"/>
<attribute NAME="keyboardShortcut" VALUE=""/>
<attribute NAME="execute_scripts_without_asking" VALUE="true"/>
<attribute NAME="execute_scripts_without_file_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_write_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_exec_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_network_restriction" VALUE="true"/>
</node>
<node TEXT="QuickPrompt.groovy" ID="ID_QUICK_PROMPT" CREATED="1743347818938" MODIFIED="1743347818938">
<attribute_layout NAME_WIDTH="202.49999 pt" VALUE_WIDTH="156.75 pt"/>
<attribute NAME="menuTitleKey" VALUE="addons.${name}.quickPrompt"/>
<attribute NAME="menuLocation" VALUE="${addonsMenu}"/>
<attribute NAME="executionMode" VALUE="on_single_node"/>
<attribute NAME="keyboardShortcut" VALUE="control alt G"/>
<attribute NAME="execute_scripts_without_asking" VALUE="true"/>
<attribute NAME="execute_scripts_without_file_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_write_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_exec_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_network_restriction" VALUE="true"/>
</node>
<node TEXT="CompareConnectedNodes.groovy" ID="ID_COMPARE_NODES" CREATED="1743400000000" MODIFIED="1743400000000">
<attribute_layout NAME_WIDTH="202.49999 pt" VALUE_WIDTH="156.75 pt"/>
<attribute NAME="menuTitleKey" VALUE="addons.${name}.compareConnectedNodes"/>
<attribute NAME="menuLocation" VALUE="${addonsMenu}"/>
<attribute NAME="executionMode" VALUE="on_single_node"/>
<attribute NAME="keyboardShortcut" VALUE=""/>
<attribute NAME="execute_scripts_without_asking" VALUE="true"/>
<attribute NAME="execute_scripts_without_file_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_write_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_exec_restriction" VALUE="true"/>
<attribute NAME="execute_scripts_without_network_restriction" VALUE="true"/>
</node>
</node>
<node TEXT="lib" POSITION="bottom_or_right" ID="ID_1845190577" CREATED="1685111823978" MODIFIED="1742834860055">
<edge COLOR="#7c007c"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      An add-on may contain any number of nodes containing binary files (normally .jar files) to be added to the add-on's classpath.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- The immediate child nodes contain the name of the file, e.g. 'mysql-connector-java-5.1.25.jar'). Put the file into a 'lib' subdirectory of the add-on base directory.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- The child nodes of these nodes contain the actual files.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- Any lib file will be extracted in &lt;installationbase&gt;/&lt;addonname&gt;/lib.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- The files will be processed in the sequence as seen in the map.
    </p>
  </body>
</html></richcontent>
<node TEXT="llm.jar" ID="ID_943135829" CREATED="1743859158919" MODIFIED="1743859162350">
<attribute NAME="downloadURL" VALUE="${homepage}/releases/download/${version}/llm.jar"/>
<attribute NAME="checksum" VALUE=""/>
</node>
</node>
<node TEXT="resources" POSITION="bottom_or_right" ID="ID_RESOURCES" CREATED="1742836000000" MODIFIED="1742836000000">
<edge COLOR="#7c7c00"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      An add-on may contain resource files that will be copied to the add-on's resources directory.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- The immediate child nodes contain the name of the resource file.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- Any resource file will be extracted in &lt;installationbase&gt;/&lt;addonname&gt;/resources.
    </p>
  </body>
</html></richcontent>
</node>
<node TEXT="zips" POSITION="bottom_or_right" ID="ID_169986368" CREATED="1685111823978" MODIFIED="1685111823978">
<edge COLOR="#007c7c"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      An add-on may contain any number of nodes containing zip files.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- The immediate child nodes contain a description of the zip. The devtools script releaseAddOn.groovy allows automatic zip creation if the name of this node matches a directory in the current directory.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- The child nodes of these nodes contain the actual zip files.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- Any zip file will be extracted in the &lt;installationbase&gt;. Currently, &lt;installationbase&gt; is always Freeplane's &lt;userhome&gt;, e.g. ~/.freeplane/1.3.
    </p>
    <p>
      
    </p>
    <p>
      &#xa0;- The files will be processed in the sequence as seen in the map.
    </p>
  </body>
</html></richcontent>
</node>
<node TEXT="images" POSITION="bottom_or_right" ID="ID_295383454" CREATED="1685111823978" MODIFIED="1742834860064">
<edge COLOR="#7c0000"/>
<richcontent TYPE="NOTE">
<html>
  <head>
    
  </head>
  <body>
    <p>
      An add-on may define any number of images as child nodes of the images node. The actual image data has to be placed as base64 encoded binary data into the text of a subnode.
    </p>
    <p>
      The images are saved to the <i>${installationbase}/resources/images</i>&#xa0;directory.
    </p>
    <p>
      
    </p>
    <p>
      The following images should be present:
    </p>
    <ul>
      <li>
        <i>${name}-icon.png</i>, like <i>oldicons-theme-icon.png</i>. This will be used in the app-on overview.
      </li>
      <li>
        <i>${name}-screenshot-1.png</i>, like <i>oldicons-theme-screenshot-1.png</i>. This will be used in the app-on details dialog. Further images can be included but they are not used yet.
      </li>
    </ul>
    <p>
      Images can be added automatically by releaseAddOn.groovy or must be uploaded into the map via the script <i>Tools-&gt;Scripts-&gt;Insert Binary</i>&#xa0;since they have to be (base64) encoded as simple strings.
    </p>
  </body>
</html></richcontent>
<attribute_layout NAME_WIDTH="104.25 pt"/>
<node TEXT="${name}.svg" ID="ID_1000617651" CREATED="1685111823979" MODIFIED="1685111823979">
<attribute_layout VALUE_WIDTH="100 pt"/>
</node>
<node TEXT="${name}-icon.svg" ID="ID_510373247" CREATED="1685111823979" MODIFIED="1685111823979">
<attribute_layout VALUE_WIDTH="100 pt"/>
</node>
</node>
</node>
</map>
