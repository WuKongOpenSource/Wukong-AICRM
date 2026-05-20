package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kakarote.ai_crm.ai.AiModelSource;
import com.kakarote.ai_crm.ai.provider.AiProviderRegistry;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.PO.AiModelPricing;
import com.kakarote.ai_crm.entity.VO.AiModelOptionVO;
import com.kakarote.ai_crm.mapper.AiModelPricingMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiModelPricingService {

    private static final BigDecimal DEFAULT_MULTIPLIER = BigDecimal.ONE;

    private final AiModelPricingMapper mapper;

    public AiModelPricingService(AiModelPricingMapper mapper) {
        this.mapper = mapper;
    }

    public List<AiModelOptionVO> listEnabledOptions(Collection<String> availableProviders) {
        Set<String> providers = normalizeProviders(availableProviders);
        if (providers.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<AiModelPricing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelPricing::getEnabled, true)
            .in(AiModelPricing::getProvider, providers)
            .orderByAsc(AiModelPricing::getSortOrder)
            .orderByAsc(AiModelPricing::getProvider)
            .orderByAsc(AiModelPricing::getModelName);

        return mapper.selectList(wrapper).stream()
            .map(pricing -> toOptionVO(pricing, AiModelSource.SYSTEM))
            .toList();
    }

    public List<AiModelOptionVO> listConfiguredOptions(Collection<ModelOptionSeed> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return List.of();
        }

        Map<String, ModelOptionSeed> deduplicatedSeeds = new LinkedHashMap<>();
        for (ModelOptionSeed seed : seeds) {
            if (seed == null || StrUtil.hasBlank(seed.provider(), seed.modelName(), seed.modelSource())) {
                continue;
            }
            String provider = normalizeProvider(seed.provider());
            String model = StrUtil.nullToEmpty(seed.modelName()).trim();
            String source = AiModelSource.normalize(seed.modelSource());
            if (StrUtil.hasBlank(provider, model, source)) {
                continue;
            }
            deduplicatedSeeds.putIfAbsent(source + "|" + provider + "|" + model,
                new ModelOptionSeed(provider, seed.providerLabel(), model, source));
        }
        if (deduplicatedSeeds.isEmpty()) {
            return List.of();
        }

        Set<String> providers = new HashSet<>();
        deduplicatedSeeds.values().forEach(seed -> providers.add(seed.provider()));
        List<AiModelPricing> pricingRows = mapper.selectList(new LambdaQueryWrapper<AiModelPricing>()
            .in(AiModelPricing::getProvider, providers));
        Map<String, AiModelPricing> pricingByKey = new HashMap<>();
        pricingRows.forEach(pricing -> pricingByKey.put(pricingKey(pricing.getProvider(), pricing.getModelName()), pricing));

        return deduplicatedSeeds.values().stream()
            .map(seed -> toConfiguredOption(seed, pricingByKey.get(pricingKey(seed.provider(), seed.modelName()))))
            .toList();
    }

    public PricingSnapshot resolvePricing(String provider, String modelName, boolean requireEnabled) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedModel = StrUtil.nullToEmpty(modelName).trim();
        if (StrUtil.hasBlank(normalizedProvider, normalizedModel)) {
            if (requireEnabled) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请选择可用的 AI 模型");
            }
            return new PricingSnapshot(normalizedProvider, normalizedModel, DEFAULT_MULTIPLIER);
        }

        AiModelPricing pricing = mapper.selectOne(new LambdaQueryWrapper<AiModelPricing>()
            .eq(AiModelPricing::getProvider, normalizedProvider)
            .eq(AiModelPricing::getModelName, normalizedModel)
            .last("LIMIT 1"));
        if (pricing == null || !Boolean.TRUE.equals(pricing.getEnabled())) {
            if (requireEnabled) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "当前模型未开放选择，请联系管理员检查模型倍率配置");
            }
            return new PricingSnapshot(normalizedProvider, normalizedModel, DEFAULT_MULTIPLIER);
        }

        BigDecimal multiplier = pricing.getCreditMultiplier() != null && pricing.getCreditMultiplier().compareTo(BigDecimal.ZERO) > 0
            ? pricing.getCreditMultiplier()
            : DEFAULT_MULTIPLIER;
        return new PricingSnapshot(normalizedProvider, normalizedModel, multiplier);
    }

    private AiModelOptionVO toOptionVO(AiModelPricing pricing, String modelSource) {
        AiModelOptionVO vo = new AiModelOptionVO();
        vo.setProvider(pricing.getProvider());
        vo.setProviderLabel(AiProviderRegistry.get(pricing.getProvider()).getDisplayName());
        vo.setModelName(pricing.getModelName());
        vo.setDisplayName(StrUtil.blankToDefault(pricing.getDisplayName(), pricing.getModelName()));
        vo.setCreditMultiplier(pricing.getCreditMultiplier() != null ? pricing.getCreditMultiplier() : DEFAULT_MULTIPLIER);
        vo.setModelSource(modelSource);
        return vo;
    }

    private AiModelOptionVO toConfiguredOption(ModelOptionSeed seed, AiModelPricing pricing) {
        AiModelOptionVO vo = new AiModelOptionVO();
        vo.setProvider(seed.provider());
        vo.setProviderLabel(StrUtil.blankToDefault(seed.providerLabel(), AiProviderRegistry.get(seed.provider()).getDisplayName()));
        vo.setModelName(seed.modelName());
        vo.setModelSource(seed.modelSource());
        boolean customModel = AiModelSource.CUSTOM.equals(AiModelSource.normalize(seed.modelSource()));
        if (pricing != null && Boolean.TRUE.equals(pricing.getEnabled())) {
            vo.setDisplayName(StrUtil.blankToDefault(pricing.getDisplayName(), seed.modelName()));
            vo.setCreditMultiplier(customModel ? null : resolveOptionMultiplier(pricing));
        } else {
            vo.setDisplayName(seed.modelName());
            vo.setCreditMultiplier(customModel ? null : DEFAULT_MULTIPLIER);
        }
        return vo;
    }

    private BigDecimal resolveOptionMultiplier(AiModelPricing pricing) {
        return pricing.getCreditMultiplier() != null ? pricing.getCreditMultiplier() : DEFAULT_MULTIPLIER;
    }

    private Set<String> normalizeProviders(Collection<String> providers) {
        Set<String> normalized = new HashSet<>();
        if (providers == null) {
            return normalized;
        }
        for (String provider : providers) {
            String value = normalizeProvider(provider);
            if (StrUtil.isNotBlank(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizeProvider(String provider) {
        return StrUtil.nullToEmpty(provider).trim().toLowerCase(Locale.ROOT);
    }

    private String pricingKey(String provider, String modelName) {
        return normalizeProvider(provider) + "|" + StrUtil.nullToEmpty(modelName).trim();
    }

    public record PricingSnapshot(String provider, String modelName, BigDecimal creditMultiplier) {
    }

    public record ModelOptionSeed(String provider, String providerLabel, String modelName, String modelSource) {
    }
}
