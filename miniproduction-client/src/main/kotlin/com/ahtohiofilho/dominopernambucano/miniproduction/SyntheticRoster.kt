package com.ahtohiofilho.dominopernambucano.miniproduction

internal data class SyntheticProfile(
    val index: Int,
    val publicDisplayName: String,
    val tableCode: String,
)

internal val syntheticRoster: List<SyntheticProfile> = listOf(
    SyntheticProfile(1, "João Alves", "JAL"),
    SyntheticProfile(2, "Marina Costa", "MCO"),
    SyntheticProfile(3, "Rafael Lima", "RLI"),
    SyntheticProfile(4, "Camila Rocha", "CRO"),
    SyntheticProfile(5, "Bruno Melo", "BME"),
    SyntheticProfile(6, "Larissa Nunes", "LNU"),
    SyntheticProfile(7, "Diego Ramos", "DRA"),
    SyntheticProfile(8, "Paula Freitas", "PFR"),
    SyntheticProfile(9, "Tiago Barros", "TBA"),
    SyntheticProfile(10, "Renata Moura", "RMO"),
    SyntheticProfile(11, "André Sales", "ASA"),
    SyntheticProfile(12, "Bianca Torres", "BTO"),
    SyntheticProfile(13, "Felipe Castro", "FCA"),
    SyntheticProfile(14, "Aline Gomes", "AGO"),
    SyntheticProfile(15, "Lucas Dantas", "LDA"),
    SyntheticProfile(16, "Natália Pires", "NPI"),
    SyntheticProfile(17, "Márcio Tavares", "MTA"),
    SyntheticProfile(18, "Sofia Correia", "SCO"),
    SyntheticProfile(19, "Gustavo Leal", "GLE"),
    SyntheticProfile(20, "Helena Braga", "HBR"),
)
