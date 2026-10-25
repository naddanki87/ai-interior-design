package com.aiinterior.factory.design;

import com.aiinterior.factory.geometry.GeometrySpec;
import java.util.List;

public record LayoutAlternative(String id, String name, String strategy, List<GeometrySpec.Item> furniture,
                                List<String> unplaced, String concept, String tradeOffs, int score) {
}
