package com.aiinterior.factory.agents;

/** Capabilities an agent may use. Agents get no direct filesystem, shell or network access. */
public enum AgentTool {
    /** Ask other agents for help. */
    MESSAGING,
    /** Text generation through {@code AiGateway} (mock or Hugging Face). */
    AI_TEXT,
    /** Concept image generation through an {@code ImageProvider}. */
    IMAGE_GENERATION,
    /** Write files into this session's export folder. */
    EXPORT_FILES,
    /** Launch the configured SketchUp executable with the generated build script. */
    SKETCHUP_PROCESS,
    /** Ask the human for approval or input. */
    HUMAN_APPROVAL
}
