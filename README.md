# Devourer (Fabric 1.20.1)

- Matou uma criatura → uma parte aleatória dela (cabeça, braço, perna, tronco, cauda, olho, mandíbula) fica presa ao seu corpo (máx. 14).
- Cada espécie devorada dá 1 poder (só 1 por espécie). Poderes especiais: creeper, skeleton, zombie, enderman, blaze, ghast, spider, slime, witch, golem, wolf, phantom, guardian etc. Qualquer outra espécie dá uma "Essência" com buff passivo + pulso.
- Teclas: **V** usa o poder selecionado, **B** troca de poder (configurável).
- Visual: o modelo `tools/zombie_form.bbmodel` é desenhado POR CIMA da skin normal do jogador (textura `zombie_skin.png`), seguindo andar/mira/agachar. Partes vanilla escondidas: braço esquerdo e perna direita (com suas camadas). A 2ª camada da skin (chapéu, jaqueta, manga direita, calça esquerda) fica visível; para escondê-la de novo, ponha `HIDE_SECOND_LAYER = true` em `PlayerRendererMixin.java`. Os tentáculos (marcadores `ik`, `ik2`, `ik3`, `ik4`) são animados por IK: se mexem e dobram dentro do jogo.
- Editou o modelo no Blockbench? Rode `python3 tools/convert_bbmodel.py` para regenerar `models/zombie_form.json` e as texturas.
- Partículas: apenas sangue com gravidade que forma poças planas no chão.

Build: `./gradlew build` → `build/libs/devourer-1.0.0.jar` (ou rode o workflow `.github/workflows/build.yml` no GitHub).
Requer Java 17 e Fabric API 0.92.2+1.20.1.

## Comandos
- `/infectado on` / `/infectado off` — liga/desliga o modelo zombie_form que cobre o jogador (off = skin vanilla completa, sem partes escondidas).
- `/devorar limpar` — remove as partes de outros mobs coladas no corpo e todos os poderes adquiridos (o modelo zombie_form não é afetado).

## Pupila
O bone `pupil` do modelo segue a câmera (só translação no plano da pupila, sem rotação). Os limites ficam em `ZombieFormFeature.java` (`PUPIL_MAX_*`) e foram calculados pela janela da casca do focinho: se você redesenhar o olho, ajuste esses valores.
