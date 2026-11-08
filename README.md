# Devourer (Fabric 1.20.1)

- Matou uma criatura → 3 membros reais dela (braço, perna, cauda, asa...) ficam presos ao seu corpo, podendo repetir o mesmo mob. Eles ocupam pontos fixos espalhados pelo corpo (ombros, laterais do tronco, costas, braços e pernas; veja `Mount.java`), até 22 no total; depois disso o mais antigo é substituído. Nunca na cabeça. Para mudar a quantidade por criatura, edite `PARTS_PER_KILL` em `DevourerMod.java`.
- Os membros são os modelos reais do mob (peças do próprio modelo vanilla dele) desenhados com a textura original do mob. Espécies sem modelo conhecido usam o braço/perna do zumbi.
- Visual: o modelo `tools/zombie_form.bbmodel` é desenhado POR CIMA da skin normal do jogador (textura `zombie_skin.png`), seguindo andar/mira/agachar. Partes vanilla escondidas: braço esquerdo e perna direita (com suas camadas). A 2ª camada da skin (chapéu, jaqueta, manga direita, calça esquerda) fica visível; para escondê-la de novo, ponha `HIDE_SECOND_LAYER = true` em `PlayerRendererMixin.java`. Os tentáculos (marcadores `ik`, `ik2`, `ik3`, `ik4`) são animados por IK: se mexem e dobram dentro do jogo.
- **Habilidades (catálogo):** tecla **K** (configurável) abre a tela com 46 habilidades em 7 categorias: origem anatômica, funcionamento, animação, efeitos, uso, evolução e mobs fornecedores. Os textos ficam em `assets/devourer/abilities.json` e podem ser editados sem recompilar código. A tela mostra "FUNCIONAL EM JOGO" e o seu nível atual nas habilidades de tentáculo.
- **Habilidades de tentáculo (funcionais):** as 8 da categoria Tentáculos + *Tentáculos Interceptores*. Veja a seção abaixo. As outras 37 continuam só como catálogo.
- Editou o modelo no Blockbench? Rode `python3 tools/convert_bbmodel.py` para regenerar `models/zombie_form.json` e as texturas.
- Partículas: apenas sangue com gravidade que forma poças planas no chão.

Build: `./gradlew build` → `build/libs/devourer-1.0.0.jar` (ou rode o workflow `.github/workflows/build.yml` no GitHub).
Requer Java 17 e Fabric API 0.92.2+1.20.1.

## Habilidades de tentáculo (em jogo)
Teclas (Opções > Controles > Devourer): **V** = próxima habilidade, *anterior* (sem tecla por padrão), **R** = usar. O HUD acima da hotbar mostra a habilidade, o nível e a recarga.
Para desbloquear, devore os mobs da lista da habilidade (tela K). **Nível = quantos mobs diferentes da lista você tem presos no corpo (1 a 3).**

| Habilidade | Como usa | O que faz |
|---|---|---|
| Agarrão Dorsal | segurar R | tentáculo 1 (e 2 no Nv2) prende o mob mais próximo (até 6 blocos) atrás de você: lentidão máxima + dano contínuo; Nv3 o aperto cresce e ignora armadura |
| Lança Peristáltica | R | dispara em linha reta (6 / 10 blocos), fura 1 / 2 mobs e os puxa em solavancos; mobs pesados só no Nv2+/Nv3; Nv3 farpa e mantém preso 3 s |
| Tentáculo-Boca | R | o tentáculo com dentes morde, cura você e arrasta a vítima até o tronco; Nv2 mastiga no caminho; Nv3 alimenta enquanto segura |
| Guindaste de Carne | segurar R | dois tentáculos erguem o mob até 3 blocos (pequeno / médio / grande por nível); ao soltar bate no chão (3 / 6 / 9 de dano, Nv3 com dano em área) |
| Colheita Múltipla | segurar R | prende 2 (Nv1) ou 4 mobs girando em volta de você, ocupando todos os tentáculos; dano contínuo e alimenta |
| Âncora de Aderência | R liga/desliga | escala paredes (avançar/pular sobe, agachar segura); Nv2 anda no teto (pule contra ele, agachar solta); Nv3 Shift+R = arrancada. Gasta fome |
| Raízes Farejadoras | R liga/desliga | passiva: tentáculos deitam no chão e agarram mobs a 3 (Nv1) / 5 blocos; Nv3 arrasta até a boca |
| Inquietação Neural | R liga/desliga | passiva: parado ou ferido, tentáculos pegam drops (Nv1), XP (Nv2) e mobs fracos (Nv3) |
| Tentáculos Interceptores | R liga/desliga | passiva: corta 1 / 2 projéteis vindos de frente; Nv3 devolve (fogo explosivo é só destruído) |

Notas: tudo é calculado no servidor; o dano é atribuído a você, então o que morrer é devorado normalmente. Pets seus, aldeões, comerciantes, Ender Dragon e Wither nunca são agarrados. Cada tentáculo só faz uma coisa por vez (a Colheita ocupa os quatro). O tentáculo se estica até 7x para alcançar o alvo.

## Comandos
- `/infectado on` / `/infectado off` — liga/desliga o modelo zombie_form que cobre o jogador (off = skin vanilla completa, sem partes escondidas).
- `/devorar limpar` — remove os membros de outros mobs colados no corpo (o modelo zombie_form não é afetado).

## Pupila
O bone `pupil` do modelo segue a câmera (só translação no plano da pupila, sem rotação). Os limites ficam em `ZombieFormFeature.java` (`PUPIL_MAX_*`) e foram calculados pela janela da casca do focinho: se você redesenhar o olho, ajuste esses valores.
