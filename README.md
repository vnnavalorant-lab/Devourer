# Devourer (Fabric 1.20.1)

- Matou uma criatura → 3 membros reais dela (braço, perna, cauda, asa...) ficam presos ao seu corpo, podendo repetir o mesmo mob. Eles ocupam pontos fixos espalhados pelo corpo (ombros, laterais do tronco, costas, braços e pernas; veja `Mount.java`), até 22 no total; depois disso o mais antigo é substituído. Nunca na cabeça. Para mudar a quantidade por criatura, edite `PARTS_PER_KILL` em `DevourerMod.java`.
- Os membros são os modelos reais do mob (peças do próprio modelo vanilla dele) desenhados com a textura original do mob. Espécies sem modelo conhecido usam o braço/perna do zumbi.
- Visual: o modelo `tools/zombie_form.bbmodel` é desenhado POR CIMA da skin normal do jogador (textura `zombie_skin.png`), seguindo andar/mira/agachar. Partes vanilla escondidas: braço esquerdo e perna direita (com suas camadas). A 2ª camada da skin (chapéu, jaqueta, manga direita, calça esquerda) fica visível; para escondê-la de novo, ponha `HIDE_SECOND_LAYER = true` em `PlayerRendererMixin.java`. Os tentáculos (marcadores `ik`, `ik2`, `ik3`, `ik4`) são animados por IK: se mexem e dobram dentro do jogo.
- **Habilidades (catálogo):** tecla **K** (configurável) abre a tela com 46 habilidades em 7 categorias: origem anatômica, funcionamento, animação, efeitos, uso, evolução e mobs fornecedores. Os textos ficam em `assets/devourer/abilities.json` e podem ser editados sem recompilar código. Por enquanto é só o catálogo: as habilidades ainda não são executadas em jogo. Todas usam apenas as peças que já existem (tentáculos IK, boca do tronco, braço/perna mutantes e membros anexados).
- Editou o modelo no Blockbench? Rode `python3 tools/convert_bbmodel.py` para regenerar `models/zombie_form.json` e as texturas.
- Partículas: apenas sangue com gravidade que forma poças planas no chão.

Build: `./gradlew build` → `build/libs/devourer-1.0.0.jar` (ou rode o workflow `.github/workflows/build.yml` no GitHub).
Requer Java 17 e Fabric API 0.92.2+1.20.1.

## Comandos
- `/infectado on` / `/infectado off` — liga/desliga o modelo zombie_form que cobre o jogador (off = skin vanilla completa, sem partes escondidas).
- `/devorar limpar` — remove os membros de outros mobs colados no corpo (o modelo zombie_form não é afetado).

## Pupila
O bone `pupil` do modelo segue a câmera (só translação no plano da pupila, sem rotação). Os limites ficam em `ZombieFormFeature.java` (`PUPIL_MAX_*`) e foram calculados pela janela da casca do focinho: se você redesenhar o olho, ajuste esses valores.
