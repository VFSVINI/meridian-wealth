package dev.meridianwealth.domain.composition;

import java.math.BigDecimal;

/** Setor de atuação dos inquilinos que respondem por mais de 10% da receita de um imóvel (o informe não nomeia inquilinos). */
public record TenantSector(String sector, BigDecimal percentOfPropertyRevenue) {}
