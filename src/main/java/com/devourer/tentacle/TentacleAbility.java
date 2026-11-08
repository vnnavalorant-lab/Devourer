package com.devourer.tentacle;

/**
 * As 9 habilidades de tentáculo. O id é o mesmo de assets/devourer/abilities.json (de lá vêm os mobs fornecedores).
 *
 * HOLD   = ativa ao apertar a tecla de uso e termina ao soltar.
 * PRESS  = um disparo por aperto.
 * TOGGLE = liga/desliga a cada aperto (Âncora e as passivas/involuntárias).
 */
public enum TentacleAbility {
    AGARRAO("tentaculos_00", "Agarrão Dorsal", Mode.HOLD),
    LANCA("tentaculos_01", "Lança Peristáltica", Mode.PRESS),
    BOCA("tentaculos_02", "Tentáculo-Boca", Mode.PRESS),
    GUINDASTE("tentaculos_03", "Guindaste de Carne", Mode.HOLD),
    COLHEITA("tentaculos_04", "Colheita Múltipla", Mode.HOLD),
    ANCORA("tentaculos_05", "Âncora de Aderência", Mode.TOGGLE),
    RAIZES("tentaculos_06", "Raízes Farejadoras", Mode.TOGGLE),
    INQUIETACAO("tentaculos_07", "Inquietação Neural", Mode.TOGGLE),
    INTERCEPTORES("defesas_26", "Tentáculos Interceptores", Mode.TOGGLE);

    public enum Mode {HOLD, PRESS, TOGGLE}

    public final String id;
    public final String displayName;
    public final Mode mode;

    TentacleAbility(String id, String displayName, Mode mode) {
        this.id = id;
        this.displayName = displayName;
        this.mode = mode;
    }

    /** Passivas: ficam ligadas em segundo plano e agem sozinhas. */
    public boolean passive() {
        return this == RAIZES || this == INQUIETACAO || this == INTERCEPTORES;
    }

    public static final TentacleAbility[] ALL = values();
}
