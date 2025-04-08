package com.barrymac.freeplane.addons.llm

import com.barrymac.freeplane.addons.llm.mock.NodeModelTest
import groovy.swing.SwingBuilder
import org.freeplane.core.util.LogUtils
import spock.lang.Specification
import spock.lang.Unroll

import javax.swing.*
import java.awt.*

// Simple marker interface for node context

class DialogHelperSpec extends Specification {

    def mockUi = Mock(UITest)
    def mockConfig = Mock(ConfigTest)
    def mockNode = Mock(NodeModelTest)
    def mockSwingBuilder = Mock(SwingBuilder) // Mock SwingBuilder

    // Mocks for components created by SwingBuilder
    def mockDialog = Mock(JDialog)
    def mockPanel = Mock(JPanel)
    def mockLabel = Mock(JLabel)
    def mockComboBox = Mock(JComboBox)
    def mockComboEditor = Mock(ComboBoxEditor)
    def mockScrollPane = Mock(JScrollPane)
    def mockTextArea = Mock(JTextArea)
    def mockButton = Mock(JButton)

    def setupSpec() {
        // Mock LogUtils and SwingUtilities statically
        GroovyMock(LogUtils, global: true)
        GroovyMock(SwingUtilities, global: true)
        // Mock SwingBuilder globally to intercept its creation
        GroovyMock(SwingBuilder, global: true)
    }

    def setup() {
        // Reset interactions for static mocks if needed (though usually handled by Spock)
        // Stub SwingBuilder constructor/methods if necessary, but often mocking interactions is enough
        // Stub getCurrentFrame
        mockUi.getCurrentFrame() >> new Frame() // Return a dummy frame
    }

    @Unroll
    def "showComparisonDialog handles scenario: #scenario"() {
        given: "Configured properties and default types"
        def defaultTypes = ["Default 1", "Default 2"]
        def initialCustomTypes = scenario.contains("Existing Custom") ? ["Custom A"] : []
        def configKey = "promptLlmAddOn.comparisonTypes"
        mockConfig.getProperty(configKey, '') >> initialCustomTypes.join('|')

        // --- Mocking SwingBuilder ---
        // We need to simulate the structure SwingBuilder creates and capture the closures
        def okAction, cancelAction
        def capturedComboBox // To access the editor item

        // Mock the dialog creation and capture button actions
        _ * new SwingBuilder() >> mockSwingBuilder // Intercept SwingBuilder creation
        1 * mockSwingBuilder.dialog(_) >> { Map args, Closure cl ->
            // Simulate building the dialog structure within the closure
            cl.delegate = mockSwingBuilder // Set delegate for nested calls
            cl.call() // Execute the closure to trigger nested builder calls
            return mockDialog
        }
        // Mock nested panel, label, comboBox etc.
        _ * mockSwingBuilder.panel(_, _) >> { Map args, Closure cl -> cl.call(); return mockPanel }
        _ * mockSwingBuilder.label(_) >> mockLabel
        _ * mockSwingBuilder.comboBox(_) >> { Map args ->
            capturedComboBox = mockComboBox // Store the mock combo box
            mockComboBox.getEditor() >> mockComboEditor // Stub editor access
            return mockComboBox
        }
        // Mock buttons and capture their actionPerformed closures
        _ * mockSwingBuilder.button(_) >> { Map args ->
            if (args.text == 'OK') {
                okAction = args.actionPerformed // Capture OK action
            } else if (args.text == 'Cancel') {
                cancelAction = args.actionPerformed // Capture Cancel action
            }
            return mockButton
        }
        // Mock window ancestor for dispose
        _ * SwingUtilities.getWindowAncestor(_) >> mockDialog

        // --- Simulate Dialog Interaction ---
        // Set the item that will be returned by the combo box editor
        mockComboEditor.getItem() >> selectedItemInput

        when: "Showing the comparison dialog"
        // This call sets up the mocks above
        def resultPromise = DialogHelper.showComparisonDialog(mockUi, mockConfig, mockNode, "Test Message", defaultTypes, configKey)

        and: "Simulating button click"
        if (actionToSimulate == "OK") {
            okAction.call([source: mockButton]) // Simulate OK click
        } else if (actionToSimulate == "Cancel") {
            cancelAction.call([source: mockButton]) // Simulate Cancel click
        }
        // The actual result is determined by the action closure simulation
        def result = resultPromise // In a real scenario, dialog.visible = true would block

        then: "Verify logging"
        1 * LogUtils.info("Showing comparison dialog with ${expectedTotalTypes} types (* defaults, * custom)") // Use wildcard for counts as they vary

        and: "Verify config interactions"
        configSetCalls * mockConfig.setProperty(configKey, expectedSavedTypes)

        and: "Verify dialog disposal"
        disposeCalls * mockDialog.dispose() // Should be called once per action

        and: "Verify final result and logging"
        result == expectedResult
        if (expectedResult != null) {
            1 * LogUtils.info("User selected comparison type: ${expectedResult}")
        } else {
            1 * LogUtils.info("User cancelled comparison type selection")
        }
        0 * mockUi.errorMessage(_) // No errors expected

        where:
        scenario                 | selectedItemInput | actionToSimulate | expectedResult | expectedTotalTypes | configSetCalls | expectedSavedTypes | disposeCalls
        "Select Default Type"    | "Default 1"       | "OK"             | "Default 1"    | 2..3               | 0              | _                  | 1 // 2 defaults, 0 or 1 custom
        "Select Existing Custom" | "Custom A"        | "OK"             | "Custom A"     | 3                  | 0              | _                  | 1 // Needs initial custom type
        "Enter New Custom Type"  | "New Custom B"    | "OK"             | "New Custom B" | 2..3               | 1              | "Custom A|New Custom B"     | 1 // Assumes "Custom A" initial
        "Enter Empty Type"       | ""                | "OK"             | ""             | 2..3               | 0              | _                  | 1
        "Cancel Dialog"          | "Anything"        | "Cancel"         | null           | 2..3               | 0              | _                  | 1
    }


    def "createProgressDialog builds and returns dialog"() {
        given: "Mocks for Swing components"
        // --- Mocking SwingBuilder ---
        1 * new SwingBuilder() >> mockSwingBuilder
        1 * mockSwingBuilder.dialog(_) >> { Map args, Closure cl ->
            // Verify basic dialog properties passed
            assert args.title == "Test Title"
            assert args.modal == false
            assert args.resizable == true
            assert args.defaultCloseOperation == WindowConstants.DO_NOTHING_ON_CLOSE
            // Execute closure to build content
            cl.delegate = mockSwingBuilder
            cl.call()
            return mockDialog
        }
        1 * mockSwingBuilder.panel(_, _) >> { Map args, Closure cl -> cl.call(); return mockPanel }
        1 * mockSwingBuilder.scrollPane(_) >> { Map args, Closure cl -> cl.call(); return mockScrollPane }
        1 * mockSwingBuilder.textArea(_) >> { Map args ->
            // Verify text area properties
            assert args.text == "Test Message"
            assert args.lineWrap == true
            assert args.wrapStyleWord == true
            assert args.editable == false
            return mockTextArea
        }
        // Mock dialog methods called after creation
        1 * mockDialog.pack()
        1 * mockDialog.setMinimumSize({ it instanceof Dimension && it.width == 300 && it.height == 150 })
        1 * mockUi.setDialogLocationRelativeTo(mockDialog, _) // Verify centering call

        when: "Creating the progress dialog"
        def resultDialog = DialogHelper.createProgressDialog(mockUi, "Test Title", "Test Message")

        then: "Dialog is created and configured"
        resultDialog == mockDialog // Should return the mocked dialog
        1 * LogUtils.info("Creating progress dialog: Test Title")
        0 * LogUtils.severe(_) // No errors expected
    }

    def "createProgressDialog handles exceptions"() {
        given: "SwingBuilder throws an exception during dialog creation"
        1 * new SwingBuilder() >> mockSwingBuilder
        1 * mockSwingBuilder.dialog(_) >> { throw new RuntimeException("Swing Error") }

        when: "Creating the progress dialog"
        def resultDialog = DialogHelper.createProgressDialog(mockUi, "Error Title", "Error Message")

        then: "Error is logged and a minimal dialog is returned"
        1 * LogUtils.severe("Error creating progress dialog: Swing Error")
        resultDialog != null
        resultDialog instanceof JDialog // Should return a basic JDialog
        resultDialog.title == "Error Title" // Title should still be set
        // Interactions for pack, setMinimumSize, setDialogLocationRelativeTo should not happen
        0 * mockDialog.pack()
        0 * mockUi.setDialogLocationRelativeTo(_, _)
    }
}
