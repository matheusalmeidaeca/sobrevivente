# Gerador do boneco (marco F0)

Programa JVM Kotlin, sem dependencias externas. Roda so no GitHub Actions (workflow "Gerar modelo", manual).
Nao entra no APK.

Saida (pasta `saida/`, ignorada pelo git): `boneco.obj` + `boneco.mtl` (jogador), `zumbi.obj` + `zumbi.mtl`,
`textura_jogador.png`, `textura_zumbi.png` (512x512), `preview.png` (linha 1 jogador, linha 2 zumbi; frente, lado, costas),
`relatorio.txt`.

Convencoes: Y para cima, 1 unidade = 1 m, origem nos pes, FRENTE = -Z (yaw 0 do jogo), esquerda = -X.
Pecas (grupos do OBJ): cabeca, tronco, braco_esq, braco_dir, perna_esq, perna_dir.
Numeros ajustaveis: `Parametros.kt`.
