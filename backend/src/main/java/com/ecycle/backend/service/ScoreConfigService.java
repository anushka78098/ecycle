package com.ecycle.backend.service;

import com.ecycle.backend.entity.ScoreConfig;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.ScoreConfigRepository;
import com.ecycle.shared.Dtos.ConfigEntryDto;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ScoreConfigService {
    private final ScoreConfigRepository repo; private final AuditService audit;

    @Transactional(readOnly = true)
    public Map<String, Double> all() {
        Map<String, Double> m = new HashMap<>();
        repo.findAll().forEach(c -> m.put(c.getConfigKey(), c.getConfigValue().doubleValue()));
        return m;
    }
    @Transactional(readOnly = true)
    public List<ConfigEntryDto> list() {
        return repo.findAll().stream().sorted(Comparator.comparing(ScoreConfig::getConfigKey))
                .map(c -> new ConfigEntryDto(c.getConfigKey(), c.getConfigValue().doubleValue(), c.getDescription())).toList();
    }
    @Transactional
    public ConfigEntryDto update(Long adminId, String key, double value) {
        ScoreConfig c = repo.findByConfigKey(key).orElseThrow(() -> ApiException.notFound("Unknown config key " + key));
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) throw ApiException.badRequest("Value must be a non-negative number");
        if (key.startsWith("w.") || key.startsWith("bid.w.")) { if (value > 1) throw ApiException.badRequest("Weights must be between 0 and 1"); }
        if (key.startsWith("t.") && value > 100) throw ApiException.badRequest("Thresholds must be between 0 and 100");
        BigDecimal old = c.getConfigValue();
        c.setConfigValue(BigDecimal.valueOf(value));
        audit.log(adminId, "CONFIG_UPDATED", "score_config", c.getId(), key + ": " + old + " -> " + value);
        return new ConfigEntryDto(key, value, c.getDescription());
    }
}
