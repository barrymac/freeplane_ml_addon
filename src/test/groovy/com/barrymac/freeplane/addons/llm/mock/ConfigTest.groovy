package com.barrymac.freeplane.addons.llm.mock

interface ConfigTest {
    String getProperty(String key, String defaultValue)
    String getProperty(String key) // For available_models check
    void setProperty(String key, String value) // Needed for some config interactions if tested elsewhere
    String getFreeplaneUserDirectory()
}
