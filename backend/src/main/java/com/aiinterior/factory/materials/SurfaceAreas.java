package com.aiinterior.factory.materials;

import com.aiinterior.factory.domain.WallSide;

/** Quantities derived only from approved geometry. */
public record SurfaceAreas(double floorM2, double ceilingM2, double wallGrossM2, double openingsM2, double wallNetM2,
                           double perimeterM, double skirtingM, WallSide featureWall, double featureWallM2) {
}
