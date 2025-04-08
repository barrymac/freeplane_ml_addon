package com.barrymac.freeplane.addons.llm.utils

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException

class ValidationHelper {
    static void validateApiResponse(response, String nodeName) {
        if (!response) {
            throw new LlmAddonException("Empty response for ${nodeName}")
        }
    }

    static void validateDimensions(sourceDim, targetDim) {
        if (sourceDim != targetDim) {
            throw new LlmAddonException("Dimension mismatch: ${sourceDim} vs ${targetDim}")
        }
    }
}
