# Devourer (Fabric 1.20.1)

- Matou uma criatura → uma parte aleatória dela (cabeça, braço, perna, tronco, cauda, olho, mandíbula) fica presa ao seu corpo (máx. 14).
- Cada espécie devorada dá 1 poder (só 1 por espécie). Poderes especiais: creeper, skeleton, zombie, enderman, blaze, ghast, spider, slime, witch, golem, wolf, phantom, guardian etc. Qualquer outra espécie dá uma "Essência" com buff passivo + pulso.
- Teclas: **V** usa o poder selecionado, **B** troca de poder (configurável).
- Visual: usa a skin normal do jogador, com cauda de escorpião, braços extras com garras e olho laranja na máscara.
- Partículas: apenas sangue com gravidade que forma poças planas no chão.

Build: `./gradlew build` → `build/libs/devourer-1.0.0.jar` (ou rode o workflow `.github/workflows/build.yml` no GitHub).
Requer Java 17 e Fabric API 0.92.2+1.20.1.
