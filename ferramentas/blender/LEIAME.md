# ferramentas/blender (marcos F1 e F2)

Python so para ferramentas de ativos (nunca entra no APK). Tudo que o Blender gera vai para `saida/` (fora do git).

## Arquivos
| Arquivo | Papel |
|---|---|
| `teste_f1.py` | F1: prova que o Blender roda sem tela no CI |
| `animacao.py` | F2: esqueleto (13 ossos), pesos de pele, cinematica e as animacoes. Python puro, sem `bpy` |
| `exportar.py` | F2: le o OBJ do F0 e escreve o modelo animado em `.g3dj` (formato JSON do LibGDX). Python puro |
| `gerar_boneco.py` | F2: roda no Blender; monta malha + armadura + pesos + acoes, confere a pele, renderiza quadros e chama o exportador |
| `previa_python.py` | F2: folha de previa (1 quadro por animacao) sem Blender, com Pillow + numpy |

## Como rodar (pelo GitHub, no celular)
Actions > "Gerar modelo" > Run workflow > etapa `f2`. Baixe o artefato `boneco-f2`.

## O que sai em `saida/`
- `jogador.g3dj`, `zumbi.g3dj` + `textura_jogador.png`, `textura_zumbi.png` (juntos, na mesma pasta)
- `previa_python.png` (a folha de aceite: 2 linhas x 5 colunas; linha de cima = jogador, de baixo = zumbi)
- `f2_quadros/*.png` (os mesmos 10 quadros renderizados pelo Blender)
- `jogador_animado.blend`, `zumbi_animado.blend`
- `f2_relatorio.txt` (orcamento da secao 24.3 e conferencia da pele)

## Convencoes
- Sistema do jogo: Y para cima, 1 unidade = 1 m, frente = -Z, origem nos pes.
- 13 ossos: `quadril`, `coluna`, `cabeca`, `braco_esq/dir`, `antebraco_esq/dir`, `coxa_esq/dir`, `canela_esq/dir`, `pe_esq/dir`.
- Animacoes (30 fps): `parado`, `andar`, `correr` (loop), `atirar`, `morrer` (sem loop); o zumbi troca `atirar` por `atacar` (loop).
- Cada vertice tem no maximo 2 ossos (BLENDWEIGHT0 e BLENDWEIGHT1 do g3dj).
- A altura do quadril e ajustada quadro a quadro para o ponto mais baixo da malha ficar em y = 0 (ninguem afunda nem flutua).

## Para o F3 (integracao)
- O `DefaultShader` do LibGDX usa 12 ossos por padrao; o boneco tem 13. No F3: `DefaultShader.Config` com `numBones = 16` (ou mais).
- Carregar com `G3dModelLoader(JsonReader())`. Nenhuma dependencia extra.
