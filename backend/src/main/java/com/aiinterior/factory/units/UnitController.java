package com.aiinterior.factory.units;

import com.aiinterior.factory.domain.LengthUnit;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    public record ConvertRequest(String value, LengthUnit unit) {
    }

    @PostMapping("/convert")
    public Map<String, Object> convert(@RequestBody ConvertRequest req) {
        return Map.of("input", req.value(), "mm", LengthParser.toMm(req.value(), req.unit()));
    }
}
