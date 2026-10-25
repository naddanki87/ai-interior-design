package com.aiinterior.factory.agents;

public enum AgentId {
    SUPERVISOR("Supervisor"),
    REQUIREMENTS("Requirements"),
    ROOM_PLANNING("Room Planning"),
    INTERIOR_DESIGN("Interior Design"),
    FURNITURE("Furniture"),
    GEOMETRY("Geometry"),
    LIGHTING("Lighting"),
    MATERIALS("Materials & BOQ"),
    RENDERING("Rendering"),
    QUALITY_ASSURANCE("Quality Assurance"),
    SKETCHUP_EXPORTER("SketchUp Exporter");

    private final String displayName;

    AgentId(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
