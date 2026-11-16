package com.barrymac.freeplane.addons.llm.mock

interface ConfigTest {
    Object getProperty(String key, Object defaultValue) // covers int/double/String defaults
    String getProperty(String key) // For available_models check
    void setProperty(String key, Object value) // Needed for some config interactions if tested elsewhere
    String getFreeplaneUserDirectory()
}
