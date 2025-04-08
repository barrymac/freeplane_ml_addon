package com.barrymac.freeplane.addons.llm.mock

interface LoggerTest {
    void info(String message)
    void info(String format, Object... args)
    void warn(String message)
    void warn(String format, Object... args)
    void severe(String message)
    void severe(String format, Object... args)
}
