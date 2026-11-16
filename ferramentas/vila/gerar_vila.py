# ferramentas/vila/gerar_vila.py
"""Gera o mapa da VILA (inspirada na vila do Resident Evil 4): paredes, casas, igreja, celeiro, cercas, arvores,
rampa/andar alto da casa da escopeta, itens, pontos de nascimento dos zumbis.
Saida: (1) texto Kotlin para colar em Constantes.kt  (2) previa PNG vista de cima  (3) testes (portas, itens, nascimentos).
Uso: python3 gerar_vila.py <pasta_saida>
Coordenadas: x direita, z para SUL (o norte e -Z). Paredes = (minX, minZ, maxX, maxZ).
"""
import heapq
import math
import os
import sys

T = 0.4  # espessura das paredes
TAM = 160.0
LIM = TAM / 2.0 - 0.4

# tipo de parede: 0 casa, 1 pedra, 2 cerca/caixa, 3 celeiro, 4 guarda-corpo, 5 tronco de arvore
paredes = []  # (minX, minZ, maxX, maxZ, base, altura, tipo)
pisos = []    # piso interno das casas (minX, minZ, maxX, maxZ)
altos = []    # andar alto: (minX, minZ, maxX, maxZ, altura)
rampas = []   # (minX, minZ, maxX, maxZ, h0, h1, eixo)  eixo 0: sobe para -Z; 1: +Z; 2: +X; 3: -X
bloq_inimigo = []
itens = []    # (x, z, y, tipo) tipo: 0 cura, 1 municao, 2 fuzil, 3 escopeta
spawns = []
inimigos = []
cercas_seg = []   # pecas de cerca: ('h'|'v', a, b, fixo)
caixotes = []     # (x, z, base)
objetos = []      # (tipo, x, z): poco e fardos de feno (colisao + desenho proprio)
casas_info = []  # (x0, z0, w, d, alt, tipo, lado, pos, larg) para a decoracao
decor = []       # caixas so de desenho: (cx, cy, cz, sx, sy, sz, rotY, r, g, b)
telhados = []    # (x0, z0, x1, z1, yBase, altura, forma, r, g, b, oculta) forma 0 cumeeira em X, 1 em Z, 2 piramide


def caixa(x0, z0, x1, z1, tipo=0, alt=3.0, base=0.0):
    paredes.append((x0, z0, x1, z1, base, alt, tipo))


def casa(x0, z0, w, d, lado, pos=0.5, larg=3.0, tipo=0, alt=3.0, janela=None):
    """Quatro paredes com UMA porta no `lado` ('N','S','L','O'); pos = posicao do centro da porta (0..1)."""
    x1, z1 = x0 + w, z0 + d
    casas_info.append((x0, z0, w, d, alt, tipo, lado, pos, larg))
    def horiz(z, porta):  # parede horizontal (norte/sul)
        if not porta:
            caixa(x0, z, x1, z + T, tipo, alt)
        else:
            c = x0 + w * pos
            caixa(x0, z, c - larg / 2, z + T, tipo, alt)
            caixa(c + larg / 2, z, x1, z + T, tipo, alt)
    def vert(x, porta):
        if not porta:
            caixa(x, z0 + T, x + T, z1 - T, tipo, alt)
        else:
            c = z0 + d * pos
            caixa(x, z0 + T, x + T, c - larg / 2, tipo, alt)
            caixa(x, c + larg / 2, x + T, z1 - T, tipo, alt)
    horiz(z0, lado == 'N')
    horiz(z1 - T, lado == 'S')
    vert(x0, lado == 'O')
    vert(x1 - T, lado == 'L')
    pisos.append((x0 + T, z0 + T, x1 - T, z1 - T))


def cerca(x0, z0, x1, z1, vaos=()):
    """Cerca reta (horizontal ou vertical) em pedacos de 3 m, com vaos [(a, b)] (coordenada ao longo da cerca)."""
    horizontal = abs(x1 - x0) >= abs(z1 - z0)
    a0, a1 = (x0, x1) if horizontal else (z0, z1)
    a = a0
    while a < a1 - 0.01:
        b = min(a + 3.0, a1)
        if not any(not (b <= v0 or a >= v1) for v0, v1 in vaos):
            if horizontal:
                caixa(a, z0 - 0.1, b, z0 + 0.1, 2, 1.0)
                cercas_seg.append(('h', a, b, z0))
            else:
                caixa(x0 - 0.1, a, x0 + 0.1, b, 2, 1.0)
                cercas_seg.append(('v', a, b, x0))
        a = b


def arvore(x, z):
    caixa(x - 0.3, z - 0.3, x + 0.3, z + 0.3, 5, 6.0)


# ------------------------------------------------------------------ casas
# CASA DO FAZENDEIRO: 2 andares, a escopeta esta no andar de cima
FZ = (-34.0, -32.0, 16.0, 14.0)
casa(FZ[0], FZ[1], FZ[2], FZ[3], 'S', 0.5, 3.0, 0, 6.4)
fx0, fz0, fx1, fz1 = FZ[0] + T, FZ[1] + T, FZ[0] + FZ[2] - T, FZ[1] + FZ[3] - T
ALTURA_ANDAR = 3.2
altos.append((fx0, fz0, fx1, -24.6, ALTURA_ANDAR))
rampas.append((fx0, -24.6, fx0 + 2.2, -19.0, 0.0, ALTURA_ANDAR, 0))
bloq_inimigo.append((fx0, -24.6, fx0 + 2.2, -19.0))
caixa(fx0 + 2.2, -24.75, fx1, -24.55, 4, 1.0, ALTURA_ANDAR)       # guarda-corpo (a escada fica aberta)
caixa(fx0, fz0, fx0 + 2.2, fz0 + 0.2, 4, 1.0, ALTURA_ANDAR) if False else None
itens.append((-21.0, -28.5, ALTURA_ANDAR, 3))   # ESCOPETA
itens.append((-31.0, -29.0, ALTURA_ANDAR, 1))
itens.append((-24.0, -22.0, 0.0, 0))
caixa(-28.0, -28.0, -27.0, -27.0, 9, 1.0, ALTURA_ANDAR)  # caixote em cima
caixotes.append((-27.5, -27.5, ALTURA_ANDAR))

casa(14, -32, 14, 10, 'S', 0.5, 3.0, 6)
casa(-32, 10, 12, 10, 'N', 0.5, 3.0, 7)
casa(18, 12, 12, 12, 'O', 0.5, 3.0, 8)
casa(-56, -6, 9, 8, 'L', 0.5)
casa(40, -8, 10, 9, 'O', 0.5, 3.0, 6)
casa(-10, 40, 14, 10, 'N', 0.5, 3.0, 7)
casa(30, 36, 10, 10, 'O', 0.5, 3.0, 8)
casa(-48, 36, 11, 9, 'L', 0.5)
casa(55, 30, 9, 8, 'O', 0.5, 3.0, 6)
casa(-60, -48, 10, 9, 'S', 0.5, 3.0, 7)
casa(58, -30, 9, 9, 'O', 0.5)
# IGREJA (pedra, alta) e CELEIRO
casa(-12, -62, 24, 16, 'S', 0.5, 4.0, 1, 5.0)
casa(38, -52, 20, 14, 'O', 0.5, 4.0, 3, 4.5)
caixa(-19.0, -50.0, -14.0, -45.0, 1, 11.0)   # torre do sino (pedra)
# poco e caixotes
caixa(-1.2, -7.2, 1.2, -4.8, 10, 1.2)
objetos.append((10, 0.0, -6.0))
for (hx, hz) in [(62.0, -40.0), (62.0, -37.0), (34.0, -36.0), (31.0, -39.0)]:
    caixa(hx - 0.7, hz - 0.7, hx + 0.7, hz + 0.7, 11, 1.4)
    objetos.append((11, hx, hz))
for (cx, cz) in [(8, 6), (-9, 7), (12, -10), (-14, -8), (25, 28), (-25, 28), (-38, -2), (47, 8), (-4, 28), (9, 30)]:
    caixa(cx - 0.5, cz - 0.5, cx + 0.5, cz + 0.5, 9, 1.0)
    caixotes.append((cx, cz, 0.0))
# cercas de quintal
cerca(-38, -12, -10, -12, [(-28, -22)])
cerca(-38, 2, -34, 2)
cerca(10, 24, 36, 24, [(18, 24)])
cerca(-20, 22, -20, 36, [(26, 29)])
cerca(32, -14, 32, 2, [(-8, -4)])
cerca(-62, 8, -40, 8, [(-52, -48)])
cerca(60, -8, 60, 12, [(0, 4)])
cerca(-30, 52, -12, 52, [(-22, -19)])
cerca(46, 50, 64, 50, [(54, 57)])
cerca(-8, -44, 14, -44, [(-2, 2)])
# arvores
arvs = [(-45, -22), (-48, -20), (-70, -10), (-72, 20), (-66, 42), (-60, 60), (-30, 66), (-12, 70), (10, 68), (30, 62),
        (50, 70), (68, 60), (72, 40), (74, 10), (70, -14), (66, -44), (50, -70), (30, -72), (6, -76), (-24, -72),
        (-50, -70), (-70, -56), (-76, -34), (20, -20), (24, -16), (-8, 52), (4, 14), (-14, 18), (62, 4), (-44, 60)]
for x, z in arvs:
    arvore(x, z)

# itens (cura 0, municao 1, fuzil 2)
itens += [
    (0.0, 3.0, 0.0, 0), (3.0, 3.0, 0.0, 1), (-3.0, 3.0, 0.0, 1),
    (21.0, -26.0, 0.0, 0), (24.0, -24.0, 0.0, 1),
    (-26.0, 16.0, 0.0, 0), (24.0, 18.0, 0.0, 1), (-52.0, -2.0, 0.0, 0),
    (44.0, -3.0, 0.0, 1), (-3.0, 46.0, 0.0, 0), (34.0, 42.0, 0.0, 1), (-43.0, 40.0, 0.0, 0),
    (0.0, -56.0, 0.0, 2),        # FUZIL no altar da igreja
    (6.0, -57.0, 0.0, 0), (-6.0, -57.0, 0.0, 1),
    (48.0, -46.0, 0.0, 1), (44.0, -44.0, 0.0, 0), (-56.0, -44.0, 0.0, 0), (62.0, -26.0, 0.0, 1),
]
# zumbis iniciais (perto das casas) e pontos de nascimento (longe, nas bordas)
inimigos += [(-10.0, -14.0), (12.0, 10.0), (-22.0, 22.0), (26.0, -20.0), (4.0, 34.0), (-40.0, -6.0), (48.0, 20.0)]
spawns += [(-74, -74), (74, -74), (-74, 74), (74, 74), (0, -76), (0, 76), (-76, 0), (76, 0), (-60, -30), (60, 20),
           (40, 72), (-40, 72), (-70, 30), (70, -50), (-30, -72), (30, 74)]


# ------------------------------------------------------------------ DECORACAO (so desenho, sem colisao)
import random
rnd = random.Random(7)
PAL_TELHA = [(0.52, 0.24, 0.17), (0.32, 0.34, 0.38), (0.40, 0.27, 0.18), (0.36, 0.40, 0.30)]
MADEIRA = (0.20, 0.13, 0.08)
PEDRA = (0.50, 0.50, 0.48)
MOLDURA = (0.62, 0.56, 0.46)
VIDRO = (0.10, 0.13, 0.17)
TERRA = (0.43, 0.35, 0.25)


def dec(cx, cy, cz, sx, sy, sz, cor, rot=0.0, forma=0):
    """forma: 0 caixa, 1 esfera/elipsoide, 2 cilindro, 3 cone (sx, sy, sz = diametro X, altura, diametro Z)."""
    decor.append((cx, cy, cz, sx, sy, sz, rot, cor[0], cor[1], cor[2], forma))


def janela(cx, cy, cz, eixo, lado):
    """Janela na parede: moldura clara + vidro escuro + travessas + peitoril. eixo 'x': parede ao longo de X."""
    if eixo == 'x':
        dec(cx, cy, cz + lado * 0.03, 1.5, 1.2, 0.08, MOLDURA)
        dec(cx, cy, cz + lado * 0.07, 1.2, 0.9, 0.08, VIDRO)
        dec(cx, cy, cz + lado * 0.11, 0.08, 0.9, 0.06, MOLDURA)
        dec(cx, cy, cz + lado * 0.11, 1.2, 0.08, 0.06, MOLDURA)
        dec(cx, cy - 0.68, cz + lado * 0.12, 1.7, 0.1, 0.22, (0.30, 0.20, 0.12))
    else:
        dec(cx + lado * 0.03, cy, cz, 0.08, 1.2, 1.5, MOLDURA)
        dec(cx + lado * 0.07, cy, cz, 0.08, 0.9, 1.2, VIDRO)
        dec(cx + lado * 0.11, cy, cz, 0.06, 0.9, 0.08, MOLDURA)
        dec(cx + lado * 0.11, cy, cz, 0.06, 0.08, 1.2, MOLDURA)
        dec(cx + lado * 0.12, cy - 0.68, cz, 0.22, 0.1, 1.7, (0.30, 0.20, 0.12))


for idx, (x0, z0, w, d, alt, tipo, lado, pos, larg) in enumerate(casas_info):
    x1, z1 = x0 + w, z0 + d
    pedra = tipo == 1
    for (ax, az, bx, bz) in [(x0, z0, x1, z0 + T), (x0, z1 - T, x1, z1), (x0, z0, x0 + T, z1), (x1 - T, z0, x1, z1)]:
        dec((ax + bx) / 2, 0.3, (az + bz) / 2, bx - ax + 0.12, 0.6, bz - az + 0.12, PEDRA)
        if not pedra:
            dec((ax + bx) / 2, alt - 0.12, (az + bz) / 2, bx - ax + 0.16, 0.24, bz - az + 0.16, MADEIRA)
    if not pedra:
        for (cx, cz) in [(x0 + T / 2, z0 + T / 2), (x1 - T / 2, z0 + T / 2), (x0 + T / 2, z1 - T / 2), (x1 - T / 2, z1 - T / 2)]:
            dec(cx, alt / 2, cz, 0.36, alt, 0.36, MADEIRA)
    fileiras = [1.7, 4.9] if alt > 6 else [1.7 if alt < 4 else 2.6]
    for yy in fileiras:
        for (sentido, comp, fixo, ladodir) in [('x', (x0, x1), z0, -1), ('x', (x0, x1), z1, 1), ('z', (z0, z1), x0, -1), ('z', (z0, z1), x1, 1)]:
            ehporta = (lado == 'N' and sentido == 'x' and fixo == z0) or (lado == 'S' and sentido == 'x' and fixo == z1) \
                or (lado == 'O' and sentido == 'z' and fixo == x0) or (lado == 'L' and sentido == 'z' and fixo == x1)
            if ehporta:
                continue
            ext = comp[1] - comp[0]
            n = max(1, int((ext - 2.0) // 4.0))
            for k in range(n):
                c = comp[0] + ext * (k + 1) / (n + 1)
                if sentido == 'x':
                    janela(c, yy, fixo, 'x', ladodir)
                else:
                    janela(fixo, yy, c, 'z', ladodir)
    altp = min(alt - 0.5, 3.4)
    corv = (0.45, 0.33, 0.22) if not pedra else PEDRA
    if lado in ('N', 'S'):
        zc = z0 if lado == 'N' else z1
        cx = x0 + w * pos
        sg = -1 if lado == 'N' else 1
        dec(cx - larg / 2 - 0.1, altp / 2, zc, 0.22, altp, 0.7, MADEIRA)
        dec(cx + larg / 2 + 0.1, altp / 2, zc, 0.22, altp, 0.7, MADEIRA)
        dec(cx, altp + (alt - altp) / 2, zc, larg + 0.4, alt - altp, 0.5, corv)
        dec(cx, 0.06, zc + sg * 0.9, larg + 0.6, 0.12, 1.4, PEDRA)
    else:
        xc = x0 if lado == 'O' else x1
        cz = z0 + d * pos
        sg = -1 if lado == 'O' else 1
        dec(xc, altp / 2, cz - larg / 2 - 0.1, 0.7, altp, 0.22, MADEIRA)
        dec(xc, altp / 2, cz + larg / 2 + 0.1, 0.7, altp, 0.22, MADEIRA)
        dec(xc, altp + (alt - altp) / 2, cz, 0.5, alt - altp, larg + 0.4, corv)
        dec(xc + sg * 0.9, 0.06, cz, 1.4, 0.12, larg + 0.6, PEDRA)
    o = 0.6
    forma = 0 if w >= d else 1
    corr = PAL_TELHA[idx % len(PAL_TELHA)] if tipo != 3 else (0.50, 0.20, 0.15)
    vao = d if forma == 0 else w
    alturaT = max(1.4, vao * 0.38)
    telhados.append((x0 - o, z0 - o, x1 + o, z1 + o, alt, alturaT, forma, corr[0], corr[1], corr[2], 1))
    if tipo in (0, 6, 7, 8) and alt < 4 and lado != 'L':
        dec(x1 + 0.3, (alt + alturaT * 0.5 + 0.8) / 2, z0 + d * 0.25, 0.9, alt + alturaT * 0.5 + 0.8, 0.9, (0.38, 0.31, 0.27))
        dec(x1 + 0.3, alt + alturaT * 0.5 + 0.9, z0 + d * 0.25, 1.1, 0.2, 1.1, (0.28, 0.24, 0.22))

telhados.append((-19.5, -50.5, -13.5, -44.5, 11.0, 4.5, 2, 0.30, 0.32, 0.36, 0))
dec(-16.5, 16.1, -47.5, 0.12, 1.2, 0.12, (0.80, 0.72, 0.30))
dec(-16.5, 16.4, -47.5, 0.6, 0.12, 0.12, (0.80, 0.72, 0.30))
for yy in (6.0, 9.0):
    dec(-16.5, yy, -44.95, 0.8, 1.4, 0.08, (0.08, 0.09, 0.12))
    dec(-13.95, yy, -47.5, 0.08, 1.4, 0.8, (0.08, 0.09, 0.12))

dec(0.0, 0.0175, 0.0, 14.0, 0.035, 14.0, (0.46, 0.45, 0.42))
dec(0.0, 0.0200, 0.0, 10.0, 0.04, 10.0, (0.40, 0.39, 0.37), 45.0)
dec(0.0, 0.0125, 0.0, 5.0, 0.025, 76.0, TERRA)
dec(0.0, 0.0125, 0.0, 76.0, 0.025, 5.0, TERRA)
for (x0, z0, w, d, alt, tipo, lado, pos, larg) in casas_info:
    cx, cz = x0 + w * pos, z0 + d * pos
    if lado == 'N':
        dec(cx, 0.0125, z0 - 4.5, larg + 0.4, 0.025, 9.0, TERRA)
    elif lado == 'S':
        dec(cx, 0.0125, z0 + d + 4.5, larg + 0.4, 0.025, 9.0, TERRA)
    elif lado == 'O':
        dec(x0 - 4.5, 0.0125, cz, 9.0, 0.025, larg + 0.4, TERRA)
    else:
        dec(x0 + w + 4.5, 0.0125, cz, 9.0, 0.025, larg + 0.4, TERRA)
dec(-31.0, 0.0125, -16.0, 2.4, 0.025, 12.0, TERRA)

# arvores: tronco redondo com raiz alargada; folhosa (esferas) ou pinheiro (cones empilhados)
for k, (x, z) in enumerate(arvs):
    g = rnd.uniform(0.0, 1.0)
    cor = (0.14 + 0.07 * g, 0.30 + 0.14 * g, 0.12 + 0.05 * g)
    escuro = (cor[0] * 0.8, cor[1] * 0.8, cor[2] * 0.8)
    casca = (0.27, 0.19, 0.11)
    dec(x, 3.0, z, 0.55, 6.0, 0.55, casca, 0, 2)
    dec(x, 0.45, z, 1.3, 0.9, 1.3, casca, 0, 3)
    if k % 3 == 0:  # pinheiro
        for (yy, dd, hh, f) in [(2.6, 4.0, 2.6, 0.85), (4.3, 3.2, 2.4, 0.95), (5.9, 2.3, 2.2, 1.05), (7.3, 1.4, 1.8, 1.12)]:
            dec(x, yy + hh / 2 - 0.4, z, dd, hh, dd, (cor[0] * f * 0.8, cor[1] * f * 0.95, cor[2] * f * 0.8), rnd.uniform(0, 90), 3)
    else:  # folhosa: um grupo de elipsoides
        dec(x, 5.2, z, 4.2, 3.0, 4.2, escuro, rnd.uniform(0, 90), 1)
        for (ox, oy, oz, dd) in [(1.1, 5.6, 0.4, 2.8), (-1.0, 5.7, -0.6, 2.9), (0.2, 6.6, 0.9, 2.6), (-0.4, 6.3, -1.2, 2.5)]:
            dec(x + ox, oy, z + oz, dd, dd * 0.85, dd, cor, rnd.uniform(0, 90), 1)
# pedras (elipsoides baixos)
for _ in range(60):
    px, pz = rnd.uniform(-74, 74), rnd.uniform(-74, 74)
    if abs(px) < 8 and abs(pz) < 8:
        continue
    t = rnd.uniform(0.3, 0.8)
    tom = rnd.uniform(0.36, 0.5)
    dec(px, t * 0.3, pz, t * 1.4, t * 0.7, t * 1.1, (tom, tom, tom - 0.02), rnd.uniform(0, 180), 1)
# capim: tufos de cones finos
for _ in range(110):
    px, pz = rnd.uniform(-76, 76), rnd.uniform(-76, 76)
    gg = rnd.uniform(0.0, 1.0)
    cor = (0.22 + 0.10 * gg, 0.38 + 0.10 * gg, 0.14)
    for (ox, oz, hh) in [(0.0, 0.0, 0.7), (0.18, 0.1, 0.5), (-0.15, 0.12, 0.55)]:
        dec(px + ox, hh / 2, pz + oz, 0.18, hh, 0.18, cor, 0, 3)

# cercas: moirões redondos + duas travessas por peca
MADEIRA_VELHA = (0.36, 0.27, 0.17)
for (dirc, a, b, fixo) in cercas_seg:
    if dirc == 'h':
        dec(a, 0.6, fixo, 0.2, 1.2, 0.2, MADEIRA_VELHA, 0, 2)
        dec(b, 0.6, fixo, 0.2, 1.2, 0.2, MADEIRA_VELHA, 0, 2)
        for yy in (0.45, 0.85):
            dec((a + b) / 2, yy, fixo, b - a, 0.09, 0.07, (0.42, 0.32, 0.20))
    else:
        dec(fixo, 0.6, a, 0.2, 1.2, 0.2, MADEIRA_VELHA, 0, 2)
        dec(fixo, 0.6, b, 0.2, 1.2, 0.2, MADEIRA_VELHA, 0, 2)
        for yy in (0.45, 0.85):
            dec(fixo, yy, (a + b) / 2, 0.07, 0.09, b - a, (0.42, 0.32, 0.20))

# caixotes: barris (cilindro + aros) e caixas de tabuas
for k, (cx, cz, base) in enumerate(caixotes):
    if k % 2 == 0:
        dec(cx, base + 0.5, cz, 0.92, 1.0, 0.92, (0.40, 0.26, 0.14), 0, 2)
        for yy in (0.22, 0.78):
            dec(cx, base + yy, cz, 0.97, 0.07, 0.97, (0.18, 0.18, 0.20), 0, 2)
        dec(cx, base + 1.0, cz, 0.8, 0.03, 0.8, (0.30, 0.20, 0.10), 0, 2)
    else:
        dec(cx, base + 0.5, cz, 0.96, 0.96, 0.96, (0.50, 0.37, 0.21), rnd.uniform(-8, 8))
        for sx_ in (-0.4, 0.4):
            dec(cx + sx_, base + 0.5, cz, 0.1, 0.98, 0.98, (0.30, 0.21, 0.12), 0)
        dec(cx, base + 0.5, cz, 0.98, 0.1, 0.98, (0.30, 0.21, 0.12), 0)

# poco (anel de pedra, agua escura, dois postes e telhadinho) e fardos de feno
for (tipo, ox, oz) in objetos:
    if tipo == 10:
        dec(ox, 0.6, oz, 2.5, 1.2, 2.5, (0.48, 0.47, 0.44), 0, 2)
        dec(ox, 1.0, oz, 1.9, 0.05, 1.9, (0.08, 0.14, 0.22), 0, 2)
        dec(ox, 1.25, oz, 2.6, 0.12, 2.6, (0.56, 0.55, 0.52), 0, 2)
        for sx_ in (-1.0, 1.0):
            dec(ox + sx_, 2.0, oz, 0.16, 1.6, 0.16, MADEIRA, 0, 2)
        telhados.append((ox - 1.6, oz - 1.6, ox + 1.6, oz + 1.6, 2.8, 0.9, 1, 0.40, 0.27, 0.18, 0))
    else:
        dec(ox, 0.6, oz, 1.4, 1.2, 1.4, (0.78, 0.64, 0.28), 0, 2)
        dec(ox, 1.3, oz, 1.2, 0.5, 1.2, (0.82, 0.68, 0.30), 0, 3)
        for yy in (0.35, 0.85):
            dec(ox, yy, oz, 1.44, 0.06, 1.44, (0.55, 0.42, 0.18), 0, 2)

# ------------------------------------------------------------------ teste de navegacao (mesma grade/folga do Navegacao.kt)
CEL = 0.5
FOLGA = 0.4 + 0.08
L = int(TAM / CEL)


def centro(g):
    return -TAM / 2 + (g + 0.5) * CEL


def bloqueado_grade():
    b = [[False] * L for _ in range(L)]
    for (x0, z0, x1, z1, base, alt, tipo) in paredes:
        if base > 0.5:
            continue  # so paredes do chao bloqueiam inimigos
        gx0 = max(0, int((x0 - FOLGA + TAM / 2) / CEL) - 1)
        gx1 = min(L - 1, int((x1 + FOLGA + TAM / 2) / CEL) + 1)
        gz0 = max(0, int((z0 - FOLGA + TAM / 2) / CEL) - 1)
        gz1 = min(L - 1, int((z1 + FOLGA + TAM / 2) / CEL) + 1)
        for gz in range(gz0, gz1 + 1):
            for gx in range(gx0, gx1 + 1):
                x, z = centro(gx), centro(gz)
                if x0 - FOLGA < x < x1 + FOLGA and z0 - FOLGA < z < z1 + FOLGA:
                    b[gz][gx] = True
    for (x0, z0, x1, z1) in bloq_inimigo:
        for gz in range(L):
            for gx in range(L):
                if x0 - FOLGA < centro(gx) < x1 + FOLGA and z0 - FOLGA < centro(gz) < z1 + FOLGA:
                    b[gz][gx] = True
    return b


def cel(v):
    return max(0, min(L - 1, int((v + TAM / 2) // CEL)))


def alcancaveis(b, ini):
    s = (cel(ini[0]), cel(ini[1]))
    visto = {s}
    fila = [s]
    while fila:
        c = fila.pop()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (c[0] + dx, c[1] + dz)
            if 0 <= n[0] < L and 0 <= n[1] < L and not b[n[1]][n[0]] and n not in visto:
                visto.add(n)
                fila.append(n)
    return visto


def dentro_parede(x, z, y=0.0, folga=0.35):
    for (x0, z0, x1, z1, base, alt, tipo) in paredes:
        if x0 - folga < x < x1 + folga and z0 - folga < z < z1 + folga and y < base + alt - 0.2 and y + 1.8 > base + 0.2:
            return True
    return False


def testar():
    erros = []
    b = bloqueado_grade()
    vis = alcancaveis(b, (0.0, 0.0))
    for (x, z, y, t) in itens:
        if dentro_parede(x, z, y):
            erros.append("item dentro de parede: %s" % ((x, z, y, t),))
        elif y == 0.0 and (cel(x), cel(z)) not in vis:
            erros.append("item inalcancavel no chao: %s" % ((x, z, t),))
    for (x, z) in inimigos:
        if dentro_parede(x, z) or (cel(x), cel(z)) not in vis:
            erros.append("zumbi inicial preso/dentro: %s" % ((x, z),))
    for (x, z) in spawns:
        if dentro_parede(x, z) or (cel(x), cel(z)) not in vis:
            erros.append("spawn preso/dentro: %s" % ((x, z),))
    if dentro_parede(0, 0):
        erros.append("jogador nasce dentro de parede")
    # portas: todas as casas tem de ser alcancaveis por dentro
    for (x0, z0, x1, z1) in pisos:
        cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
        if (cel(cx), cel(cz)) not in vis and not (x1 - x0 < 1 or z1 - z0 < 1):
            erros.append("interior de casa inalcancavel: %s" % ((x0, z0, x1, z1),))
    return erros, b, vis


# ------------------------------------------------------------------ saida Kotlin
def f(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return (s if s not in ("", "-0") else "0") + "f"


def kotlin():
    o = []
    o.append("    // ---- VILA (gerada por ferramentas/vila/gerar_vila.py; NAO editar a mao) ----")
    o.append("    /** Paredes e obstaculos (minX, minZ, maxX, maxZ). Ver PAREDES_BASE / PAREDES_ALTURA / PAREDES_TIPO. */")
    o.append("    val PAREDES = floatArrayOf(")
    o.append(",\n".join("        " + ", ".join(f(v) for v in p[:4]) for p in paredes))
    o.append("    )")
    o.append("    /** Altura da base de cada parede (0 = chao; 3,2 = guarda-corpo do andar alto). */")
    o.append("    val PAREDES_BASE = floatArrayOf(" + ", ".join(f(p[4]) for p in paredes) + ")")
    o.append("    val PAREDES_ALTURA = floatArrayOf(" + ", ".join(f(p[5]) for p in paredes) + ")")
    o.append("    /** 0 casa, 1 pedra, 2 cerca, 3 celeiro, 4 guarda-corpo, 5 tronco, 6-8 reboco, 9 caixote/barril, 10 poco, 11 fardo (2, 5, 9, 10, 11 so colisao: o desenho vem da decoracao). */")
    o.append("    val PAREDES_TIPO = intArrayOf(" + ", ".join(str(p[6]) for p in paredes) + ")")
    o.append("    /** Piso interno das casas (minX, minZ, maxX, maxZ). */")
    o.append("    val PISOS = floatArrayOf(" + ", ".join(f(v) for p in pisos for v in p) + ")")
    o.append("    /** Andares altos: (minX, minZ, maxX, maxZ) e altura. */")
    o.append("    val ALTOS = floatArrayOf(" + ", ".join(f(v) for p in altos for v in p[:4]) + ")")
    o.append("    val ALTOS_ALTURA = floatArrayOf(" + ", ".join(f(p[4]) for p in altos) + ")")
    o.append("    /** Rampas/escadas: (minX, minZ, maxX, maxZ), altura em baixo e em cima, e para onde sobem (0=-Z, 1=+Z, 2=+X, 3=-X). */")
    o.append("    val RAMPAS = floatArrayOf(" + ", ".join(f(v) for p in rampas for v in p[:4]) + ")")
    o.append("    val RAMPAS_ALTURAS = floatArrayOf(" + ", ".join(f(v) for p in rampas for v in p[4:6]) + ")")
    o.append("    val RAMPAS_EIXO = intArrayOf(" + ", ".join(str(p[6]) for p in rampas) + ")")
    o.append("    /** Area que os zumbis nao pisam (escada). */")
    o.append("    val BLOQUEIOS_INIMIGO = floatArrayOf(" + ", ".join(f(v) for p in bloq_inimigo for v in p) + ")")
    o.append("    val ITENS_XZ = floatArrayOf(" + ", ".join(f(v) for (x, z, y, t) in itens for v in (x, z)) + ")")
    o.append("    val ITENS_Y = floatArrayOf(" + ", ".join(f(y) for (x, z, y, t) in itens) + ")")
    o.append("    /** 0 cura, 1 municao, 2 fuzil, 3 escopeta. */")
    o.append("    val ITENS_TIPO = intArrayOf(" + ", ".join(str(t) for (x, z, y, t) in itens) + ")")
    o.append("    val INIMIGOS_XZ = floatArrayOf(" + ", ".join(f(v) for p in inimigos for v in p) + ")")
    o.append("    /** Pontos onde zumbis novos podem nascer (longe do jogador e fora da vista). */")
    o.append("    val SPAWNS_XZ = floatArrayOf(" + ", ".join(f(v) for p in spawns for v in p) + ")")
    return "\n".join(o) + "\n"


def kotlin_decor():
    """Arquivo VilaDecoracao.kt: os numeros vao em TEXTO (um array grande demais estoura o limite de 64 KB do metodo da JVM)."""
    def txt(linhas):
        return ";".join(",".join(f(v)[:-1] for v in l) for l in linhas)
    o = ["// core/src/main/kotlin/com/sobrevivente/jogo/mundo/VilaDecoracao.kt",
         "// GERADO por ferramentas/vila/gerar_vila.py - NAO editar a mao.",
         "package com.sobrevivente.jogo.mundo", "",
         "/** Decoracao so de desenho (sem colisao) e telhados da vila. Numeros em texto, lidos uma vez ao abrir o jogo. */",
         "object VilaDecoracao {",
         "    private fun ler(s: String): FloatArray {",
         "        val linhas = s.split(';')",
         "        val out = ArrayList<Float>()",
         "        for (l in linhas) for (n in l.split(',')) if (n.isNotEmpty()) out.add(n.toFloat())",
         "        return out.toFloatArray()",
         "    }", "",
         "    /** Por forma: cx, cy, cz, sx, sy, sz, rotY(graus), r, g, b, forma (0 caixa, 1 esfera, 2 cilindro, 3 cone)  (11 numeros). */",
         "    val CAIXAS: FloatArray by lazy { ler(CAIXAS_TXT) }",
         "    /** Por telhado: x0, z0, x1, z1, yBase, altura, forma (0 cumeeira em X, 1 em Z, 2 piramide), r, g, b, oculta (1 = some com o jogador dentro) (11 numeros). */",
         "    val TELHADOS: FloatArray by lazy { ler(TELHADOS_TXT) }", ""]
    # blocos de texto < 60 KB cada: divide em pedacos concatenados em tempo de execucao
    def bloco(nome, linhas):
        partes, atual = [], []
        tam = 0
        for l in linhas:
            t = ",".join(f(v)[:-1] for v in l)
            if tam + len(t) > 30000:
                partes.append(atual); atual = []; tam = 0
            atual.append(t); tam += len(t) + 1
        partes.append(atual)
        out = ["    private val %s: String = listOf(" % nome]
        out.append(",\n".join('        "' + ";".join(p) + '"' for p in partes))
        out.append("    ).joinToString(\";\")")
        return out
    o += bloco("CAIXAS_TXT", decor) + [""] + bloco("TELHADOS_TXT", telhados) + ["}"]
    return "\n".join(o) + "\n"


# ------------------------------------------------------------------ previa
def previa(caminho, b):
    from PIL import Image, ImageDraw
    E = 6
    im = Image.new("RGB", (int(TAM * E), int(TAM * E)), (86, 102, 62))
    d = ImageDraw.Draw(im)

    def px(x, z):
        return ((x + TAM / 2) * E, (z + TAM / 2) * E)

    for (x0, z0, x1, z1) in pisos:
        d.rectangle([px(x0, z0), px(x1, z1)], fill=(70, 56, 44))
    for (x0, z0, x1, z1, h0) in altos:
        d.rectangle([px(x0, z0), px(x1, z1)], fill=(120, 96, 66))
    for (x0, z0, x1, z1, a, c, e) in rampas:
        d.rectangle([px(x0, z0), px(x1, z1)], fill=(170, 140, 90))
    cor = {0: (120, 84, 56), 1: (150, 150, 150), 2: (140, 110, 70), 3: (150, 60, 50), 4: (80, 60, 40), 5: (40, 70, 30), 6: (190, 170, 130), 7: (180, 140, 90), 8: (150, 160, 160), 9: (140, 110, 70), 10: (120, 120, 120), 11: (200, 170, 70)}
    for (x0, z0, x1, z1, base, alt, tipo) in paredes:
        d.rectangle([px(x0, z0), px(x1, z1)], fill=cor[tipo])
    for (x, z, y, t) in itens:
        c = [(60, 220, 90), (70, 120, 240), (240, 220, 40), (240, 140, 30)][t]
        d.ellipse([px(x - .6, z - .6), px(x + .6, z + .6)], fill=c, outline=(0, 0, 0))
    for (x, z) in spawns:
        d.rectangle([px(x - 1, z - 1), px(x + 1, z + 1)], outline=(255, 0, 0), width=2)
    for (x, z) in inimigos:
        d.ellipse([px(x - .7, z - .7), px(x + .7, z + .7)], fill=(200, 30, 30))
    d.ellipse([px(-.8, -.8), px(.8, .8)], fill=(255, 255, 255))
    im.save(caminho)


if __name__ == "__main__":
    saida = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(saida, exist_ok=True)
    erros, grade, vis = testar()
    for e in erros:
        print("ERRO:", e)
    print("paredes:", len(paredes), "itens:", len(itens), "spawns:", len(spawns), "erros:", len(erros))
    with open(os.path.join(saida, "vila_constantes.kt.txt"), "w") as fh:
        fh.write(kotlin())
    with open(os.path.join(saida, "VilaDecoracao.kt"), "w") as fh:
        fh.write(kotlin_decor())
    previa(os.path.join(saida, "vila_previa.png"), grade)
