# LLM plugin for freeplane

1. Compare Model outputs and analyse biases
2. Image generation and attachment
      ```
      HttpResponse<String> response = Unirest.post("https://api.novita.ai/v3beta/flux-1-schnell")
        .header("Content-Type", "<content-type>")
        .header("Authorization", "<authorization>")
        .body("{\n  \"response_image_type\": \"<string>\",\n  \"prompt\": \"<string>\",\n  \"seed\": 123,\n  \"steps\": 123,\n  \"width\": 123,\n  \"height\": 123,\n  \"image_num\": 123\n}")
        .asString();
      ```
      Example:

      ```
       curl --location 'https://api.novita.ai/v3beta/flux-1-schnell' \
       --header 'Authorization: Bearer {{API Key}}' \
       --header 'Content-Type: application/json' \
       --data '{
       "prompt": "Extreme close-up of a single tiger eye, direct frontal view. Detailed iris and pupil. Sharp focus on eye texture and color. Natural lighting to capture authentic eye shine and depth. The word \"Novita AI\" is painted over it in big, white brush strokes with visible texture",
       "width": 512,
       "height": 512,
       "seed": 2024,
       "steps": 4,
       "image_num": 1
       }'
       ```

      Response

       ```
        {
        "images": [
        {
        "image_url": "https://model-api-output.5e61b0cbce9f453eb9db49fdd85c7cac.r2.cloudflarestorage.com/xxx",
        "image_url_ttl": 604800,
        "image_type": "png"
        }
        ],
        "task": {
        "task_id": "xxx"
        }
        }
       ```

4. Serial thought Vectors, expand an idea with a direction, specified in a couple of words
5. Prompt refresh sub tree, just a shorcut for delete and recreate 
