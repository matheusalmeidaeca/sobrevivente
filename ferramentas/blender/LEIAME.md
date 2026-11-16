# Ferramentas Blender (sem tela)

Python so de bastidor: roda no GitHub Actions (workflow "Gerar modelo", etapa `f1`), nunca dentro do APK.

- `teste_f1.py` (marco F1): cria um cubo, renderiza `preview.png` (Cycles, CPU) e grava `versao_blender.txt`.
- `gerar_boneco.py` e `exportar.py`: virao nos marcos F2 e F3.

Saida somente em `saida/` (ignorada pelo git). Blender fixado em 4.5.11 LTS no workflow.
