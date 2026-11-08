package com.devourer;

/**
 * Pontos fixos do corpo onde um membro devorado pode ficar preso (nunca na cabeça).
 * A ordem da lista é a ordem de preenchimento: intercalada (lado direito/esquerdo, ombro/perna/braço/costas)
 * para que, mesmo com poucos membros, eles já fiquem espalhados pelo corpo todo.
 *
 * Coordenadas em pixels no espaço da peça a que o ponto está preso. +x = lado esquerdo do jogador, -x = direito,
 * +z = atrás, +y = para baixo. O membro nasce no ponto e aponta para baixo, girado por {@code rz} (para fora/para cima)
 * e {@code rx} (para trás).
 */
public enum Mount {
    SIDE_HI_R(Attach.BODY, -1, 4.6f, 3.0f, 0.6f, 85, 10),
    SIDE_HI_L(Attach.BODY, 1, 4.6f, 3.0f, 0.6f, 85, 10),
    LEG_OUT_R(Attach.LEG_R, -1, 3.8f, 0.0f, 0.0f, 15, 0),
    LEG_OUT_L(Attach.LEG_L, 1, 3.8f, 0.0f, 0.0f, 15, 0),
    UP_R(Attach.BODY, -1, 3.2f, 0.5f, 1.6f, 150, 25),
    UP_L(Attach.BODY, 1, 3.2f, 0.5f, 1.6f, 150, 25),
    ARM_OUT_R(Attach.ARM_R, -1, 4.2f, 1.0f, 0.0f, 25, -5),
    ARM_OUT_L(Attach.ARM_L, 1, 4.2f, 1.0f, 0.0f, 25, -5),
    SIDE_MID_R(Attach.BODY, -1, 4.4f, 6.0f, 0.4f, 60, 10),
    SIDE_MID_L(Attach.BODY, 1, 4.4f, 6.0f, 0.4f, 60, 10),
    BACK_HI_R(Attach.BODY, -1, 2.4f, 1.5f, 2.4f, 145, 35),
    BACK_HI_L(Attach.BODY, 1, 2.4f, 1.5f, 2.4f, 145, 35),
    UP_OUT_R(Attach.BODY, -1, 4.2f, 1.5f, 1.0f, 118, 15),
    UP_OUT_L(Attach.BODY, 1, 4.2f, 1.5f, 1.0f, 118, 15),
    SIDE_LOW_R(Attach.BODY, -1, 4.2f, 9.0f, 0.4f, 38, 10),
    SIDE_LOW_L(Attach.BODY, 1, 4.2f, 9.0f, 0.4f, 38, 10),
    ARM_LOW_R(Attach.ARM_R, -1, 4.0f, 5.0f, -0.5f, 45, 15),
    ARM_LOW_L(Attach.ARM_L, 1, 4.0f, 5.0f, -0.5f, 45, 15),
    LEG_BACK_R(Attach.LEG_R, -1, 1.5f, 0.0f, 2.0f, 10, 30),
    LEG_BACK_L(Attach.LEG_L, 1, 1.5f, 0.0f, 2.0f, 10, 30),
    BACK_LOW_R(Attach.BODY, -1, 1.8f, 8.0f, 2.4f, 20, 60),
    BACK_LOW_L(Attach.BODY, 1, 1.8f, 8.0f, 2.4f, 20, 60);

    /** A que peça do jogador o ponto está preso (segue o movimento dela). */
    public enum Attach {BODY, ARM_R, ARM_L, LEG_R, LEG_L}

    public final Attach attach;
    public final float x, y, z;
    /** Rotação em Z (rad): afasta o membro do corpo, ou o ergue acima do ombro. */
    public final float rz;
    /** Rotação em X (rad): inclina o membro para trás. */
    public final float rx;

    Mount(Attach attach, int side, float x, float y, float z, float outwardDeg, float backDeg) {
        this.attach = attach;
        this.x = side * x;
        this.y = y;
        this.z = z;
        this.rz = -side * (float) Math.toRadians(outwardDeg);
        this.rx = (float) Math.toRadians(backDeg);
    }
}
