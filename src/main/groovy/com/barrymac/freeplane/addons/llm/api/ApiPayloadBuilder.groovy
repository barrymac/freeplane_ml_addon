package com.barrymac.freeplane.addons.llm.api


import com.barrymac.freeplane.addons.llm.prompts.Message

class ApiPayloadBuilder {
    static ApiRequest buildPayload(String model, String systemMessage, String userPrompt, Map apiConfig) {
        new ApiRequest(
                model: model,
                messages: [
                        new Message(role: 'system', content: systemMessage),
                        new Message(role: 'user', content: userPrompt)
                ],
                temperature: apiConfig.temperature,
                maxTokens: apiConfig.maxTokens
        )
    }
    
    /**
     * Builds a payload for Novita image generation API
     * 
     * @param prompt The text prompt for image generation
     * @param steps Number of diffusion steps (higher = more detail but slower)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param imageNum Number of images to generate
     * @param seed Random seed for reproducibility (default: current timestamp)
     * @return Map containing the Novita API payload
     * @throws IllegalArgumentException if any parameter is outside valid range
     */
    static Map buildNovitaImagePayload(String prompt, int steps=4, int width=512, 
                                     int height=512, int imageNum=4, long seed=System.currentTimeMillis()) {
        // Validate parameters
        if(steps < 1 || steps > 50) throw new IllegalArgumentException("Steps must be 1-50")
        if(width < 256 || width > 1024) throw new IllegalArgumentException("Width must be 256-1024")
        if(height < 256 || height > 1024) throw new IllegalArgumentException("Height must be 256-1024")
        if(imageNum < 1 || imageNum > 4) throw new IllegalArgumentException("Image count must be 1-4")
        
        return [
            prompt: prompt,
            steps: steps,
            width: width,
            height: height,
            image_num: imageNum,
            seed: seed,
            response_image_type: "png"
        ]
    }
}
