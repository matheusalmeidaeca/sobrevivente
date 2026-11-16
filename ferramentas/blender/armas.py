# ferramentas/blender/armas.py
"""Armas do protagonista: geometria (caixas coloridas), pontos de pegada e boca do cano. Python PURO.

Referencial da ARMA: origem = centro da palma da mao direita na empunhadura; x = direita, y = cima, z = para TRAS
(o cano aponta para -Z, como a frente do personagem). Unidades em metros.
"""
import json
import math
import os

# Paleta (uma textura de faixas; cada material usa o centro da sua faixa -> sem sangrar cor)
PALETA = [
    ("aco_escuro", (0.15, 0.16, 0.18)),
    ("aco_claro", (0.52, 0.54, 0.57)),
    ("preto", (0.05, 0.05, 0.06)),
    ("madeira", (0.43, 0.28, 0.15)),
    ("oliva", (0.22, 0.26, 0.17)),
    ("latao", (0.70, 0.55, 0.20)),
]
INDICE_MATERIAL = {n: i for i, (n, _) in enumerate(PALETA)}

# caixa = (material, (cx, cy, cz), (sx, sy, sz), rx_graus)  (rx gira a caixa em torno do eixo X dela)
ARMAS = {
    "pistola": dict(
        nome="Pistola",
        caixas=[
            ("preto", (0.0, -0.040, 0.012), (0.034, 0.112, 0.046), -12.0),   # empunhadura inclinada
            ("preto", (0.0, 0.012, -0.045), (0.030, 0.026, 0.170), 0.0),      # armacao
            ("aco_escuro", (0.0, 0.043, -0.050), (0.030, 0.032, 0.200), 0.0),  # ferrolho
            ("aco_claro", (0.0, 0.040, -0.158), (0.014, 0.014, 0.030), 0.0),   # ponta do cano
            ("preto", (0.0, -0.012, -0.040), (0.010, 0.012, 0.050), 0.0),      # guarda-mato
            ("aco_claro", (0.0, 0.062, 0.040), (0.008, 0.010, 0.020), 0.0),    # alça de mira
        ],
        boca=(0.0, 0.040, -0.175),
        apoio=(-0.040, -0.030, 0.000),   # mao esquerda ao lado da empunhadura
        carregador=(0.0, -0.100, 0.010),  # onde o pente entra
    ),
    "fuzil": dict(
        nome="Fuzil",
        caixas=[
            ("aco_escuro", (0.0, 0.075, -0.105), (0.050, 0.075, 0.300), 0.0),  # receptor
            ("preto", (0.0, 0.060, -0.360), (0.056, 0.062, 0.215), 0.0),       # protetor de mao
            ("aco_claro", (0.0, 0.075, -0.560), (0.018, 0.018, 0.190), 0.0),   # cano
            ("aco_escuro", (0.0, 0.075, -0.665), (0.030, 0.030, 0.040), 0.0),  # quebra-chama
            ("preto", (0.0, 0.020, 0.0), (0.034, 0.112, 0.046), -14.0),        # empunhadura
            ("aco_claro", (0.0, -0.045, -0.165), (0.034, 0.150, 0.066), 10.0),  # carregador
            ("preto", (0.0, 0.080, 0.200), (0.046, 0.100, 0.170), 0.0),        # coronha
            ("aco_escuro", (0.0, 0.070, 0.300), (0.050, 0.120, 0.030), 0.0),   # soleira
            ("aco_claro", (0.0, 0.130, -0.130), (0.014, 0.022, 0.300), 0.0),   # trilho de mira
            ("preto", (0.0, 0.148, -0.560), (0.010, 0.040, 0.012), 0.0),       # massa de mira
        ],
        boca=(0.0, 0.075, -0.690),
        apoio=(0.0, 0.010, -0.200),
        carregador=(0.0, -0.040, -0.165),
    ),
    "escopeta": dict(
        nome="Escopeta",
        caixas=[
            ("aco_escuro", (0.0, 0.070, -0.095), (0.052, 0.080, 0.250), 0.0),  # receptor
            ("aco_escuro", (0.0, 0.090, -0.520), (0.032, 0.032, 0.650), 0.0),  # cano
            ("aco_claro", (0.0, 0.050, -0.470), (0.026, 0.026, 0.520), 0.0),   # tubo do carregador
            ("madeira", (0.0, 0.040, -0.260), (0.056, 0.058, 0.170), 0.0),     # bomba (pump)
            ("madeira", (0.0, 0.015, 0.0), (0.036, 0.112, 0.048), -14.0),      # empunhadura
            ("madeira", (0.0, 0.055, 0.200), (0.050, 0.110, 0.170), 0.0),      # coronha
            ("preto", (0.0, 0.050, 0.300), (0.052, 0.116, 0.026), 0.0),        # soleira
            ("aco_claro", (0.0, 0.118, -0.820), (0.010, 0.016, 0.012), 0.0),   # massa de mira
        ],
        boca=(0.0, 0.090, -0.850),
        apoio=(0.0, 0.000, -0.200),
        carregador=(0.0, 0.020, -0.100),
    ),
}
ORDEM = ["pistola", "fuzil", "escopeta"]


def _caixa(material, c, s, rx):
    """24 vertices (6 faces planas) de uma caixa; devolve (vertices, indices). vertice = (pos, normal, uv)."""
    cx, cy, cz = c
    hx, hy, hz = s[0] / 2.0, s[1] / 2.0, s[2] / 2.0
    ar = math.radians(rx)
    co, si = math.cos(ar), math.sin(ar)

    def girar(p):  # em torno de X (pelo centro da caixa)
        return (p[0], p[1] * co - p[2] * si, p[1] * si + p[2] * co)

    u = (INDICE_MATERIAL[material] + 0.5) / len(PALETA)
    faces = [
        ((1, 0, 0), [(hx, -hy, hz), (hx, -hy, -hz), (hx, hy, -hz), (hx, hy, hz)]),
        ((-1, 0, 0), [(-hx, -hy, -hz), (-hx, -hy, hz), (-hx, hy, hz), (-hx, hy, -hz)]),
        ((0, 1, 0), [(-hx, hy, hz), (hx, hy, hz), (hx, hy, -hz), (-hx, hy, -hz)]),
        ((0, -1, 0), [(-hx, -hy, -hz), (hx, -hy, -hz), (hx, -hy, hz), (-hx, -hy, hz)]),
        ((0, 0, 1), [(-hx, -hy, hz), (hx, -hy, hz), (hx, hy, hz), (-hx, hy, hz)]),
        ((0, 0, -1), [(hx, -hy, -hz), (-hx, -hy, -hz), (-hx, hy, -hz), (hx, hy, -hz)]),
    ]
    verts, idx = [], []
    for n, quad in faces:
        base = len(verts)
        for p in quad:
            q = girar(p)
            verts.append(((q[0] + cx, q[1] + cy, q[2] + cz), girar(n), (u, 0.5)))
        idx += [base, base + 1, base + 2, base, base + 2, base + 3]
    return verts, idx


def triangulos(arma):
    """Lista de triangulos [(pos, normal, uv) x3] da arma (referencial da arma)."""
    tris = []
    for material, c, s, rx in ARMAS[arma]["caixas"]:
        v, idx = _caixa(material, c, s, rx)
        for i in range(0, len(idx), 3):
            tris.append([v[idx[i]], v[idx[i + 1]], v[idx[i + 2]]])
    return tris


def montar_g3dj(arma, textura="textura_armas.png"):
    """Modelo g3dj ESTATICO (sem ossos) da arma. Quem usa o prende ao osso mao_dir (ver Jogador.kt)."""
    dados, indices = [], []
    for material, c, s, rx in ARMAS[arma]["caixas"]:
        v, idx = _caixa(material, c, s, rx)
        base = len(dados) // 8
        for p, n, uv in v:
            dados += [round(p[0], 5), round(p[1], 5), round(p[2], 5), round(n[0], 4), round(n[1], 4), round(n[2], 4),
                      uv[0], uv[1]]
        indices += [base + i for i in idx]
    return {
        "version": [0, 1], "id": "arma_" + arma,
        "meshes": [{"attributes": ["POSITION", "NORMAL", "TEXCOORD0"], "vertices": dados,
                    "parts": [{"id": "parte", "type": "TRIANGLES", "indices": indices}]}],
        "materials": [{"id": "material", "diffuse": [1.0, 1.0, 1.0],
                       "textures": [{"id": "textura", "filename": textura, "type": "DIFFUSE"}]}],
        "nodes": [{"id": "arma", "parts": [{"meshpartid": "parte", "materialid": "material"}]}],
        "animations": [],
    }


def escrever_modelos(pasta):
    os.makedirs(pasta, exist_ok=True)
    for a in ORDEM:
        with open(os.path.join(pasta, "arma_%s.g3dj" % a), "w") as f:
            json.dump(montar_g3dj(a), f, separators=(",", ":"))
    from PIL import Image
    img = Image.new("RGB", (len(PALETA) * 8, 8))
    for i, (_, cor) in enumerate(PALETA):
        for x in range(8):
            for y in range(8):
                img.putpixel((i * 8 + x, y), tuple(int(c * 255) for c in cor))
    img.save(os.path.join(pasta, "textura_armas.png"))
