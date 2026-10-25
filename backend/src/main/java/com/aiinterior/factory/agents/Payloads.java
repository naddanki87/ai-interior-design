package com.aiinterior.factory.agents;

import com.aiinterior.factory.design.FurnitureSpec;
import com.aiinterior.factory.design.LayoutAlternative;
import com.aiinterior.factory.domain.Budget;
import com.aiinterior.factory.domain.DesignBrief;
import com.aiinterior.factory.domain.DimensionSource;
import com.aiinterior.factory.domain.RoomType;
import com.aiinterior.factory.export.SkpVerification;
import com.aiinterior.factory.geometry.BuildManifest;
import com.aiinterior.factory.geometry.GeometrySpec;
import com.aiinterior.factory.geometry.GeometryValidationReport;
import com.aiinterior.factory.lighting.LightingSchedule;
import com.aiinterior.factory.materials.BoqEstimate;
import com.aiinterior.factory.materials.MaterialLine;
import com.aiinterior.factory.materials.SurfaceAreas;
import java.time.Instant;
import java.util.List;

/** Typed inputs and outputs exchanged between agents. */
public final class Payloads {

    private Payloads() {
    }

    public record Fact(String text, String basis) {
    }

    public record RequirementsDoc(String roomType, String roomLabel, String style, List<String> palette,
                                  List<String> materialPreferences, Budget budget, List<String> requiredElements,
                                  List<String> preferredElements, List<String> optionalEnhancements,
                                  List<String> mustNotChange, List<String> functionalRequirements,
                                  List<String> lightingRequirements, List<String> explicitConstraints, List<Fact> facts,
                                  List<String> assumptions, List<String> conflicts, String summary, String aiProvider,
                                  List<String> notes) {
    }

    public record MeasurementCheck(String field, Double valueMm, DimensionSource source, String status, String note) {
    }

    public record RoomSpec(GeometrySpec shell, List<MeasurementCheck> measurements, List<String> missing,
                           List<String> warnings, boolean blocking, double floorAreaM2) {
    }

    public record FurnitureRequest(RoomType roomType, List<String> requests, List<String> removals, String style) {
    }

    public record FurnitureSelection(List<FurnitureSpec> items, List<String> notes) {
    }

    public record LayoutCheckRequest(GeometrySpec shell, List<GeometrySpec.Item> furniture) {
    }

    public record DesignRequest(RequirementsDoc requirements, RoomSpec room, String revisionNotes, String preferredLayoutId,
                                int designVersion) {
    }

    public record StructuralProposal(String description, String reason, String status, boolean applied) {
    }

    public record AlternativeReview(LayoutAlternative layout, GeometryValidationReport validation, int score) {
    }

    public record DesignProposal(String conceptTitle, String concept, List<String> featureWallIdeas, List<String> tradeOffs,
                                 List<AlternativeReview> alternatives, String recommendedId,
                                 List<StructuralProposal> structuralProposals, String aiProvider, List<String> notes) {

        public AlternativeReview alternative(String id) {
            return alternatives.stream().filter(a -> a.layout().id().equals(id)).findFirst().orElse(null);
        }
    }

    public record GeometryRequest(GeometrySpec shell, LayoutAlternative layout, String sessionId, int designVersion, String title) {
    }

    public record GeometryResult(GeometrySpec spec, GeometryValidationReport validation, BuildManifest manifest, String floorPlanSvg) {
    }

    public record FurnitureLine(String id, String name, String category, double widthMm, double depthMm, double heightMm,
                                double xMm, double yMm, String againstWall, double frontClearanceMm, String priority,
                                String sizeSource, String clearanceStatus, String note) {
    }

    public record FurnitureSchedule(List<FurnitureLine> lines, List<String> issues) {
    }

    public record LightingRequest(RoomType roomType, GeometrySpec spec, List<String> requirements, String style) {
    }

    public record MaterialsRequest(RequirementsDoc requirements, GeometrySpec spec, LightingSchedule lighting) {
    }

    public record MaterialsResult(SurfaceAreas areas, List<MaterialLine> schedule, BoqEstimate boq, String aiProvider,
                                  List<String> notes) {
    }

    public record RenderRequest(RequirementsDoc requirements, GeometryResult geometry, MaterialsResult materials, String title) {
    }

    public record RenderSet(String floorPlanSvg, String conceptBoardSvg, String provider, String model, boolean mock,
                            String prompt, String negativePrompt, List<String> cameras, String disclaimer,
                            Instant requestedAt, Instant completedAt) {
    }

    public record QaCheck(String id, String category, String status, String message) {
    }

    public record QaReport(String overall, List<QaCheck> checks, Instant generatedAt, int designVersion) {
    }

    public record QaRequest(DesignBrief brief, RoomSpec room, DesignProposal design, GeometryResult geometry,
                            FurnitureSchedule furniture, MaterialsResult materials, LightingSchedule lighting,
                            RenderSet render, int designVersion) {
    }

    public record ExportRequest(String sessionId, int designVersion, GeometrySpec spec, BuildManifest manifest,
                                String floorPlanSvg, String boqCsv) {
    }

    public record ExportFile(String name, String format, long sizeBytes, String sha256, boolean verified, String note) {
    }

    public record ExportResult(String status, String message, List<ExportFile> files, SkpVerification skp,
                               boolean sketchupConfigured, String sketchupLauncher, List<String> manualSteps,
                               int designVersion) {
    }

    public record VerifyExportsRequest(ExportResult export, QaReport designQa, int designVersion) {
    }
}
