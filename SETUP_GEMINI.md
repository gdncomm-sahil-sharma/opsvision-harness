# Google Gemini API Setup Guide

## Getting Your Google GenAI API Key

1. **Visit Google AI Studio**
   - Go to [Google AI Studio](https://aistudio.google.com/app/apikey)
   - Sign in with your Google account

2. **Create API Key**
   - Click on "Create API Key"
   - Choose "Create API key in new project" or select an existing project
   - Copy the generated API key

3. **Set Environment Variable**
   ```bash
   export GOOGLE_GENAI_API_KEY=your_api_key_here
   ```
   
   Note: The Google GenAI SDK also supports `GOOGLE_API_KEY`, but this application is configured to use `GOOGLE_GENAI_API_KEY` to avoid conflicts.

4. **Or Add to Application Properties**
   ```yaml
   spring:
     ai:
       google:
         genai:
           api-key: your_api_key_here
   ```

## Supported Models

The application is configured to use `gemini-2.0-flash-exp` by default, but you can change to other available models:

- `gemini-2.0-flash-exp` (Latest experimental model - Recommended)
- `gemini-2.0-flash` (Stable Flash model)
- `gemini-1.5-pro` (Pro model - Higher quality, slower)
- `gemini-1.5-flash` (Flash model - Faster, good quality)
- `gemini-2.5-flash` (Latest production model)

## Model Configuration

To change the model, update your `application.yml`:

```yaml
spring:
  ai:
    google:
      genai:
        chat:
          options:
            model: gemini-1.5-pro  # Change to desired model
            temperature: 0.1
            max-output-tokens: 2000
```

## Rate Limits and Quotas

- Free tier: 15 requests per minute
- Check [Google AI Studio](https://aistudio.google.com/) for current quotas
- Consider upgrading to paid tier for production use

## Troubleshooting

### Common Issues:

1. **Invalid API Key**
   - Verify the key is correct and not expired
   - Check that the key has proper permissions

2. **Rate Limiting**
   - Reduce request frequency
   - Implement exponential backoff in production

3. **Model Not Available**
   - Check model name spelling
   - Verify model availability in your region

### Debug Logging

Enable debug logging to see API interactions:

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
    com.opsvision.harness.service.ai: DEBUG
```