#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gerador de esqueleto humano + pesos + animacoes (.g3dj para LibGDX) a partir de um .obj.

Uso:
    python3 gerar_g3dj.py                       # usa config_humano.json
    python3 gerar_g3dj.py --config meu.json --obj meu_boneco.obj --saida saida/humano.g3dj

Precisa so de Python 3 e numpy (Termux: pkg install python-numpy).
Convencao do resultado: Y para cima, o boneco olha para -Z, +X e a DIREITA do boneco (padrao do jogo).

Tudo e feito em 5 blocos:
  1) matematica de rotacoes        4) animacoes (marcha com pe no chao, IK de perna)
  2) esqueleto + limites rigidos   5) escrita do .g3dj
  3) malha .obj + pesos (skinning)
Qualquer pose fora dos limites articulares levanta PoseInvalida (nada e "cortado" em silencio).
"""
import argparse
import json
import math
import os
import sys

import numpy as np

# =====================================================================================
# 1) MATEMATICA
# =====================================================================================
DEG = math.pi / 180.0


def Rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def Ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def Rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def compor_xyz(a, b, c):
    """R = Rx(a) Ry(b) Rz(c). a = giro em X, b = em Y, c = em Z (radianos)."""
    return Rx(a) @ Ry(b) @ Rz(c)


def decompor_xyz(R):
    """Inverso de compor_xyz (b em [-90,90] graus)."""
    b = math.asin(max(-1.0, min(1.0, R[0, 2])))
    a = math.atan2(-R[1, 2], R[2, 2])
    c = math.atan2(-R[0, 1], R[0, 0])
    return a, b, c


def mat_para_quat(R):
    """Matriz 3x3 -> quaternion (x, y, z, w), w >= 0."""
    t = R[0, 0] + R[1, 1] + R[2, 2]
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        w = 0.25 * s
        x = (R[2, 1] - R[1, 2]) / s
        y = (R[0, 2] - R[2, 0]) / s
        z = (R[1, 0] - R[0, 1]) / s
    elif R[0, 0] > R[1, 1] and R[0, 0] > R[2, 2]:
        s = math.sqrt(1.0 + R[0, 0] - R[1, 1] - R[2, 2]) * 2
        w = (R[2, 1] - R[1, 2]) / s
        x = 0.25 * s
        y = (R[0, 1] + R[1, 0]) / s
        z = (R[0, 2] + R[2, 0]) / s
    elif R[1, 1] > R[2, 2]:
        s = math.sqrt(1.0 + R[1, 1] - R[0, 0] - R[2, 2]) * 2
        w = (R[0, 2] - R[2, 0]) / s
        x = (R[0, 1] + R[1, 0]) / s
        y = 0.25 * s
        z = (R[1, 2] + R[2, 1]) / s
    else:
        s = math.sqrt(1.0 + R[2, 2] - R[0, 0] - R[1, 1]) * 2
        w = (R[1, 0] - R[0, 1]) / s
        x = (R[0, 2] + R[2, 0]) / s
        y = (R[1, 2] + R[2, 1]) / s
        z = 0.25 * s
    q = np.array([x, y, z, w])
    q /= np.linalg.norm(q)
    return q if q[3] >= 0 else -q


def quat_para_mat(q):
    x, y, z, w = q / np.linalg.norm(q)
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def unit(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def suave(x):
    """smootherstep: 0..1 com velocidade e aceleracao zero nas pontas (ease-in/ease-out)."""
    x = min(1.0, max(0.0, x))
    return x * x * x * (x * (x * 6 - 15) + 10)


def interp_chaves(t, chaves):
    """chaves = [(tempo, valor), ...] ordenadas. Entre duas chaves usa ease-in/out (nunca linear seca)."""
    if t <= chaves[0][0]:
        return chaves[0][1]
    for i in range(1, len(chaves)):
        if t <= chaves[i][0]:
            t0, v0 = chaves[i - 1]
            t1, v1 = chaves[i]
            k = suave((t - t0) / (t1 - t0)) if t1 > t0 else 1.0
            return v0 + (v1 - v0) * k
    return chaves[-1][1]


# =====================================================================================
# 2) ESQUELETO E LIMITES
# =====================================================================================
class PoseInvalida(Exception):
    pass


# (nome, pai) — 24 ossos
OSSOS = [
    ("quadril", None),
    ("coluna1", "quadril"), ("coluna2", "coluna1"), ("coluna3", "coluna2"),
    ("pescoco", "coluna3"), ("cabeca", "pescoco"),
    ("clavicula_dir", "coluna3"), ("braco_dir", "clavicula_dir"), ("antebraco_dir", "braco_dir"),
    ("mao_dir", "antebraco_dir"), ("dedos_dir", "mao_dir"),
    ("clavicula_esq", "coluna3"), ("braco_esq", "clavicula_esq"), ("antebraco_esq", "braco_esq"),
    ("mao_esq", "antebraco_esq"), ("dedos_esq", "mao_esq"),
    ("coxa_dir", "quadril"), ("canela_dir", "coxa_dir"), ("pe_dir", "canela_dir"), ("ponta_dir", "pe_dir"),
    ("coxa_esq", "quadril"), ("canela_esq", "coxa_esq"), ("pe_esq", "canela_esq"), ("ponta_esq", "pe_esq"),
]
NOMES = [o[0] for o in OSSOS]
PAI = {o[0]: o[1] for o in OSSOS}
INDICE = {n: i for i, n in enumerate(NOMES)}
FILHO_PRINCIPAL = {  # osso -> osso cujo ponto inicial e o fim deste segmento
    "quadril": "coluna1", "coluna1": "coluna2", "coluna2": "coluna3", "coluna3": "pescoco", "pescoco": "cabeca",
    "clavicula_dir": "braco_dir", "braco_dir": "antebraco_dir", "antebraco_dir": "mao_dir", "mao_dir": "dedos_dir",
    "clavicula_esq": "braco_esq", "braco_esq": "antebraco_esq", "antebraco_esq": "mao_esq", "mao_esq": "dedos_esq",
    "coxa_dir": "canela_dir", "canela_dir": "pe_dir", "pe_dir": "ponta_dir",
    "coxa_esq": "canela_esq", "canela_esq": "pe_esq", "pe_esq": "ponta_esq",
}
# sinal da flexao no eixo X local (flexao positiva anatomica)
SINAL_X = {"ponta_dir": -1, "ponta_esq": -1, "coluna1": -1, "coluna2": -1, "coluna3": -1, "pescoco": -1, "cabeca": -1, "canela_dir": -1, "canela_esq": -1}


def base(nome):
    return nome.replace("_dir", "").replace("_esq", "")


def lado(nome):
    return "dir" if nome.endswith("_dir") else ("esq" if nome.endswith("_esq") else None)


def sinais(nome):
    """(sx, sy, sz): anatomico (flex, rot_interna, abducao) -> angulos locais (X, Y, Z)."""
    sx = SINAL_X.get(nome, 1)
    sy = sz = -1 if lado(nome) == "esq" else 1
    return sx, sy, sz


def anat_para_local(nome, flex, rot, abd):
    sx, sy, sz = sinais(nome)
    return sx * flex, sy * rot, sz * abd


def local_para_anat(nome, a, b, c):
    sx, sy, sz = sinais(nome)
    return sx * a, sy * b, sz * c


class Limites:
    """Limites rigidos por osso (graus anatomicos: flexao+, rotacao interna+, abducao+)."""

    def __init__(self, cfg):
        L = cfg["limites"]
        self.tol = L.get("tolerancia_graus", 0.05)
        self.coletar = None  # se for uma lista, em vez de levantar erro apenas anota (modo diagnostico)
        self.tab = {}
        dist = L["distribuicao"]
        for nome in NOMES:
            b = base(nome)
            if b in dist:
                g = L["pescoco"] if b in ("pescoco", "cabeca") else L["coluna"]
                d = dist[b]
                self.tab[nome] = {
                    "flex": (-g["extensao"] * d["x"], g["flexao"] * d["x"]),
                    "rot": (-g["rot_externa"] * d["y"], g["rot_interna"] * d["y"]),
                    "abd": (-g["aducao"] * d["z"], g["abducao"] * d["z"]),
                }
            elif b in L:
                g = L[b]
                self.tab[nome] = {
                    "flex": (-g["extensao"], g["flexao"]),
                    "rot": (-g["rot_externa"], g["rot_interna"]),
                    "abd": (-g["aducao"], g["abducao"]),
                }
        # o quadril (raiz) nao tem limite: e o corpo todo

    def checar(self, nome, flex_g, rot_g, abd_g, contexto=""):
        """Valores em GRAUS anatomicos. Levanta PoseInvalida se algum passar do limite."""
        if nome not in self.tab:
            return
        t = self.tab[nome]
        for rotulo, val in (("flex", flex_g), ("rot", rot_g), ("abd", abd_g)):
            lo, hi = t[rotulo]
            if val < lo - self.tol or val > hi + self.tol:
                if self.coletar is not None:
                    self.coletar.append((nome, rotulo, val, lo, hi, contexto))
                    continue
                raise PoseInvalida("%s %s: %s=%.2f fora de [%.1f, %.1f] %s" % (nome, rotulo, rotulo, val, lo, hi, contexto))


class Quadro:
    """Uma pose completa: raiz (posicao + rotacao) e angulos anatomicos (radianos) de cada osso."""

    def __init__(self, pos, Rraiz):
        self.pos = np.array(pos, dtype=float)
        self.R = np.array(Rraiz, dtype=float)
        self.ang = {}  # nome -> (flex, rot, abd) em RADIANOS anatomicos

    def set(self, nome, flex=0.0, rot=0.0, abd=0.0):
        """Aceita GRAUS anatomicos."""
        self.ang[nome] = (flex * DEG, rot * DEG, abd * DEG)

    def get(self, nome):
        return self.ang.get(nome, (0.0, 0.0, 0.0))

    def dedo_do_pe(self, s, para_cima_graus):
        """Articulacao da bola do pe: positivo = dedos para CIMA (extensao)."""
        self.set("ponta_" + s, -para_cima_graus, 0.0, 0.0)


class Esqueleto:
    def __init__(self, mundo, fins, lim):
        self.mundo = mundo          # nome -> posicao de bind (mundo)
        self.fins = fins            # pontas dos ossos folha (para pesos)
        self.local = {}
        for n in NOMES:
            p = PAI[n]
            self.local[n] = mundo[n] - (mundo[p] if p else 0 * mundo[n])
        self.lim = lim

    def R_local(self, nome, quadro):
        f, r, a = quadro.get(nome)
        x, y, z = anat_para_local(nome, f, r, a)
        return compor_xyz(x, y, z)

    contexto = ""

    def validar(self, quadro, contexto=""):
        contexto = contexto or self.contexto
        for n in NOMES:
            f, r, a = quadro.get(n)
            self.lim.checar(n, f / DEG, r / DEG, a / DEG, contexto)

    def fk(self, quadro):
        """Devolve nome -> (posicao mundo, rotacao mundo)."""
        res = {"quadril": (quadro.pos.copy(), quadro.R.copy())}
        for n in NOMES[1:]:
            pp, Rp = res[PAI[n]]
            R = Rp @ self.R_local(n, quadro)
            res[n] = (pp + Rp @ self.local[n], R)
        return res

    def quats(self, quadro):
        """nome -> (translacao ou None, quaternion local) para gravar no g3dj."""
        self.validar(quadro)
        out = {"quadril": (quadro.pos.copy(), mat_para_quat(quadro.R))}
        for n in NOMES[1:]:
            out[n] = (None, mat_para_quat(self.R_local(n, quadro)))
        return out


# =====================================================================================
# 3) MALHA .OBJ E PESOS
# =====================================================================================
def ler_obj(caminho):
    """Le v/vt/vn/f/usemtl/mtllib. Devolve (V, VT, VN, grupos{mat: [(i,j,k) triplas de indices de vertice]}, mtl_texturas)."""
    V, VT, VN = [], [], []
    grupos = {}
    mat = "material"
    mtl_arq = None
    with open(caminho, "r", errors="ignore") as f:
        for linha in f:
            if linha.startswith("v "):
                V.append([float(x) for x in linha.split()[1:4]])
            elif linha.startswith("vt "):
                p = linha.split()
                VT.append([float(p[1]), float(p[2])])
            elif linha.startswith("vn "):
                VN.append([float(x) for x in linha.split()[1:4]])
            elif linha.startswith("usemtl"):
                mat = linha.split(None, 1)[1].strip()
            elif linha.startswith("mtllib"):
                mtl_arq = linha.split(None, 1)[1].strip()
            elif linha.startswith("f "):
                ids = []
                for tok in linha.split()[1:]:
                    p = tok.split("/")
                    vi = int(p[0])
                    ti = int(p[1]) if len(p) > 1 and p[1] else 0
                    ni = int(p[2]) if len(p) > 2 and p[2] else 0
                    nv, nt, nn = len(V), len(VT), len(VN)
                    vi = vi - 1 if vi > 0 else nv + vi
                    ti = (ti - 1 if ti > 0 else nt + ti) if ti else -1
                    ni = (ni - 1 if ni > 0 else nn + ni) if ni else -1
                    ids.append((vi, ti, ni))
                for k in range(1, len(ids) - 1):  # leque: triangula quadrilateros e poligonos
                    grupos.setdefault(mat, []).append((ids[0], ids[k], ids[k + 1]))
    texturas = {}
    if mtl_arq:
        pm = os.path.join(os.path.dirname(caminho), mtl_arq)
        if os.path.exists(pm):
            atual = None
            for linha in open(pm, errors="ignore"):
                if linha.startswith("newmtl"):
                    atual = linha.split(None, 1)[1].strip()
                elif linha.startswith("map_Kd") and atual:
                    texturas[atual] = os.path.basename(linha.split(None, 1)[1].strip())
    return np.array(V), np.array(VT) if VT else np.zeros((0, 2)), np.array(VN) if VN else np.zeros((0, 3)), grupos, texturas


def orientar(V, VN, cfg):
    """Gira a malha para frente = -Z e cima = +Y, aplica escala. Poe os pes em y=0."""
    m = cfg["malha"]
    cima, frente = m.get("eixo_cima", "+Y"), m.get("eixo_frente", "-Z")
    R = np.eye(3)
    if cima == "+Z":
        R = Rx(-90 * DEG)       # +Z -> +Y (e a frente -Y vira -Z)
        frente = {"-Y": "-Z", "+Y": "+Z", "+X": "+X", "-X": "-X"}.get(frente, frente)
    elif cima == "-Y":
        R = Rx(180 * DEG)
    # leva o vetor "frente" do .obj para -Z girando em Y
    alvo = {"-Z": (0, 0, -1), "+Z": (0, 0, 1), "-X": (-1, 0, 0), "+X": (1, 0, 0)}[frente]
    th = math.atan2(alvo[0], -alvo[2])      # angulo medido a partir de -Z, positivo para +X
    R = Ry(th) @ R
    V = (V @ R.T) * m.get("unidade_para_metros", 1.0)
    VN = VN @ R.T if len(VN) else VN
    alt = m.get("altura_alvo_m")
    V = V - np.array([0, V[:, 1].min(), 0])
    if alt:
        V = V * (alt / V[:, 1].max())
    return V, VN


def medir_malha(V, cfg):
    """Mede, na malha, os centros de cada regiao para colocar as juntas."""
    P = cfg["proporcoes"]
    H = V[:, 1].max()

    def fatia(a, b):
        return V[(V[:, 1] >= a * H) & (V[:, 1] <= b * H)]

    # pernas (so ha pernas entre 12% e 26% da altura): centro lateral e profundidade
    perna = fatia(0.12, 0.26)
    pd = perna[perna[:, 0] > 0]
    leg_x = float(pd[:, 0].mean())
    mid = float((V[:, 0].max() + V[:, 0].min()) / 2)
    z_leg = float(fatia(0.12, 0.44)[:, 2].mean())
    # pe: calcanhar (z maior), ponta (z menor)
    pe = V[(V[:, 1] < 0.045 * H) & (V[:, 0] > 0)]
    zmax, zmin = float(pe[:, 2].max()), float(pe[:, 2].min())
    comp = zmax - zmin
    pm = cfg.get("pe", {})
    hb = pm.get("calcanhar_atras_m") or (zmax - z_leg)
    z_bola = zmax - 0.72 * comp
    bf = pm.get("bola_a_frente_m") or (z_leg - z_bola)
    ponta = pm.get("ponta_a_frente_m") or (z_leg - zmin)
    # tronco e bracos
    tronco = fatia(0.62, 0.72)
    z_t = float(tronco[:, 2].mean())
    x_out = float(tronco[:, 0].max())
    arm_x = x_out - P["meia_espessura_braco"] * H
    z_a = float(tronco[tronco[:, 0] > arm_x - P["meia_espessura_braco"] * H][:, 2].mean())
    cab = fatia(0.90, 0.99)
    z_h = float(cab[:, 2].mean())
    return dict(H=H, leg_x=leg_x - 0 * mid, z_leg=z_leg, hb=hb, bf=bf, ponta=ponta,
                z_t=z_t, arm_x=arm_x, z_a=z_a, z_h=z_h, comprimento_pe=comp)


def montar_juntas(med, cfg):
    """Posicoes de bind (mundo) de cada osso e pontas das folhas, a partir das medidas da malha."""
    P = cfg["proporcoes"]
    H = med["H"]
    zt, zl, za, zh = med["z_t"], med["z_leg"], med["z_a"], med["z_h"]
    mundo, fins = {}, {}
    mundo["quadril"] = np.array([0, P["quadril"] * H, zt])
    mundo["coluna1"] = np.array([0, P["coluna1"] * H, zt])
    mundo["coluna2"] = np.array([0, P["coluna2"] * H, zt])
    mundo["coluna3"] = np.array([0, P["coluna3"] * H, zt])
    mundo["pescoco"] = np.array([0, P["pescoco"] * H, zh])
    mundo["cabeca"] = np.array([0, P["cabeca_junta"] * H, zh])
    fins["cabeca"] = np.array([0, H, zh])
    for s, sg in (("dir", 1), ("esq", -1)):
        mundo["clavicula_" + s] = np.array([sg * P["clavicula_x"] * H, P["clavicula"] * H, zt])
        mundo["braco_" + s] = np.array([sg * med["arm_x"], P["ombro"] * H, za])
        mundo["antebraco_" + s] = np.array([sg * med["arm_x"], P["cotovelo"] * H, za])
        mundo["mao_" + s] = np.array([sg * med["arm_x"], P["punho"] * H, za])
        mundo["dedos_" + s] = np.array([sg * med["arm_x"], P["dedos"] * H, za])
        fins["dedos_" + s] = np.array([sg * med["arm_x"], P["ponta_dedos"] * H, za])
        mundo["coxa_" + s] = np.array([sg * med["leg_x"], P["quadril"] * H, zl])
        mundo["canela_" + s] = np.array([sg * med["leg_x"], P["joelho"] * H, zl])
        mundo["pe_" + s] = np.array([sg * med["leg_x"], P["tornozelo"] * H, zl])
        mundo["ponta_" + s] = np.array([sg * med["leg_x"], P["tornozelo"] * H * 0.30, zl - med["bf"]])
        fins["ponta_" + s] = np.array([sg * med["leg_x"], P["tornozelo"] * H * 0.30, zl - med["ponta"]])
    # raiz do braco: a clavicula comeca no esterno e termina no ombro (ja definido)
    return mundo, fins


def dist_segmento(Pts, a, b):
    ab = b - a
    n2 = float(ab @ ab) + 1e-12
    t = np.clip(((Pts - a) @ ab) / n2, 0, 1)
    proj = a + t[:, None] * ab
    return np.linalg.norm(Pts - proj, axis=1)


def normais_vertice(V, grupos):
    """Normal por vertice (media das normais das faces, ponderada pela area). Corrige o sentido se a malha vier invertida."""
    N = np.zeros_like(V)
    for tris in grupos.values():
        T = np.array([[t[0][0], t[1][0], t[2][0]] for t in tris])
        fn = np.cross(V[T[:, 1]] - V[T[:, 0]], V[T[:, 2]] - V[T[:, 0]])
        for k in range(3):
            np.add.at(N, T[:, k], fn)
    N = N / (np.linalg.norm(N, axis=1, keepdims=True) + 1e-12)
    centro = V.mean(axis=0)
    if float(np.sum(N * (V - centro))) < 0:
        N = -N
    return N


LIMBOS_TUBO = ("braco", "antebraco", "mao", "dedos", "coxa", "canela")


def calcular_pesos(V, esk, cfg, med, NV=None):
    """Pesos suaves (ate 4 ossos). Para cada vertice: distancia a cada osso normalizada pelo raio dele,
    peso = exp(-(d/raio/nitidez)^2). Raios maiores = transicao mais longa. Normaliza para soma = 1."""
    H = med["H"]
    R = cfg["raios_pesos"]
    nit = R["nitidez"]
    N = len(V)
    S = np.zeros((N, len(NOMES)))
    for i, n in enumerate(NOMES):
        a = esk.mundo[n]
        fp = FILHO_PRINCIPAL.get(n)
        b = esk.mundo[fp] if fp else esk.fins[n]
        # osso "quadril": segmento do quadril ate coluna1, mais um pouco para baixo (pelve)
        if n == "quadril":
            a = a - np.array([0, 0.03 * H, 0])
        raio = R[base(n)] * H
        d = dist_segmento(V, a, b)
        S[:, i] = d / raio
        if NV is not None and base(n) in LIMBOS_TUBO and R.get("filtro_superficie", True):
            # so a superficie "de tubo" do membro segue o membro: a normal aponta para longe do eixo do osso.
            # Isso separa a manga do casaco (e uma perna da outra), que se encostam mas tem normais opostas.
            ab = b - a
            t = np.clip(((V - a) @ ab) / (float(ab @ ab) + 1e-12), 0, 1)
            u = V - (a + t[:, None] * ab)
            un = u / (np.linalg.norm(u, axis=1, keepdims=True) + 1e-9)
            c = np.sum(NV * un, axis=1)
            lo, hi = R.get("filtro_min", -0.25), R.get("filtro_max", 0.25)
            g = np.clip((c - lo) / (hi - lo), 0, 1)
            g = g * g * (3 - 2 * g)
            perto = d < 0.5 * raio      # junto do eixo (pontas/tampas) nao filtra
            g = np.where(perto, 1.0, g)
            S[:, i] = S[:, i] / np.maximum(g, 0.05) ** 0.5
    W = np.exp(-(S / nit) ** 2)
    # mantem so os 4 maiores
    idx = np.argsort(-W, axis=1)[:, :4]
    Wt = np.take_along_axis(W, idx, axis=1)
    Wt[Wt < 0.02 * Wt[:, :1]] = 0.0
    soma = Wt.sum(axis=1, keepdims=True)
    semforca = soma[:, 0] < 1e-6
    if semforca.any():  # vertice longe de tudo: usa o osso mais proximo
        mais_perto = np.argmin(S[semforca], axis=1)
        Wt[semforca] = 0
        idx[semforca, 0] = mais_perto
        Wt[semforca, 0] = 1
        soma = Wt.sum(axis=1, keepdims=True)
    Wt = Wt / soma
    return idx, Wt


# =====================================================================================
# 4) ANIMACAO
# =====================================================================================
class Perna:
    """Dados geometricos de uma perna (do esqueleto)."""

    def __init__(self, esk, s, med):
        self.l1 = float(np.linalg.norm(esk.local["canela_" + s]))
        self.l2 = float(np.linalg.norm(esk.local["pe_" + s]))
        self.ah = float(esk.mundo["pe_" + s][1])          # altura do tornozelo
        self.hb = med["hb"]                                # tornozelo -> calcanhar (atras)
        self.bf = med["bf"]                                # tornozelo -> bola do pe (frente)
        self.x = float(esk.mundo["pe_" + s][0])


def perna_ik(esk, quadro, s, tornozelo, R_pe, yaw_pole, kmin_graus, perna, relatorio, rel_deg=None):
    """IK analitica de 2 ossos. Preenche coxa/canela/pe (angulos anatomicos) em `quadro`.
    tornozelo: posicao mundo do osso 'pe'; R_pe: rotacao mundo desejada do pe; joelho aponta para onde o pe aponta."""
    hip = quadro.pos + quadro.R @ esk.local["coxa_" + s]
    l1, l2 = perna.l1, perna.l2
    d = tornozelo - hip
    D = float(np.linalg.norm(d))
    Dmax = math.sqrt(l1 * l1 + l2 * l2 - 2 * l1 * l2 * math.cos(math.pi - kmin_graus * DEG))
    if D > Dmax:
        relatorio["esticou"] = relatorio.get("esticou", 0) + 1
        relatorio["maior_excesso_m"] = max(relatorio.get("maior_excesso_m", 0.0), D - Dmax)
        d = d * (Dmax / D)
        D = Dmax
    dh = d / D
    cos_int = (l1 * l1 + l2 * l2 - D * D) / (2 * l1 * l2)
    knee_flex = math.pi - math.acos(max(-1.0, min(1.0, cos_int)))
    beta = math.acos(max(-1.0, min(1.0, (l1 * l1 + D * D - l2 * l2) / (2 * l1 * D))))
    pole = np.array([-math.sin(yaw_pole), 0.0, -math.cos(yaw_pole)])
    pp = pole - (pole @ dh) * dh
    if np.linalg.norm(pp) < 1e-6:
        pp = np.array([0.0, 0.0, -1.0])
    pp = unit(pp)
    u = math.cos(beta) * dh + math.sin(beta) * pp
    K = hip + l1 * u
    sdir = unit(tornozelo_corrigido(hip, d) - K)
    Xt = unit(np.cross(dh, pp))
    Yt = -u
    Zt = np.cross(Xt, Yt)
    Rt = np.stack([Xt, Yt, Zt], axis=1)
    Ys = -sdir
    Zs = np.cross(Xt, Ys)
    Rs = np.stack([Xt, Ys, Zs], axis=1)
    if R_pe is None:  # pe definido pelo angulo do tornozelo (graus, + = dorsiflexao) relativo a canela
        R_pe = Rs @ Rx(rel_deg * DEG)
    Rhip_local = quadro.R.T @ Rt
    Rknee_local = Rt.T @ Rs
    Rank_local = Rs.T @ R_pe
    for nome, Rl in (("coxa_" + s, Rhip_local), ("canela_" + s, Rknee_local), ("pe_" + s, Rank_local)):
        a, b, c = decompor_xyz(Rl)
        quadro.ang[nome] = local_para_anat(nome, a, b, c)
    relatorio["joelho_max"] = max(relatorio.get("joelho_max", 0.0), knee_flex / DEG)


def limitar_alcance(esk, quadro, s, alvo, perna, kmin_graus=4.0):
    """Se o alvo do tornozelo passa do alcance da perna, sobe o pe (chute de calcanhar) em vez de esticar a perna."""
    hip = quadro.pos + quadro.R @ esk.local["coxa_" + s]
    l1, l2 = perna.l1, perna.l2
    Dmax = math.sqrt(l1 * l1 + l2 * l2 - 2 * l1 * l2 * math.cos(math.pi - kmin_graus * DEG)) * 0.998
    d = alvo - hip
    if np.linalg.norm(d) <= Dmax:
        return alvo
    h2 = d[0] ** 2 + d[2] ** 2
    novo = np.array(alvo, dtype=float)
    if h2 < Dmax ** 2:
        novo[1] = hip[1] - math.sqrt(Dmax ** 2 - h2)
    else:
        f = Dmax / math.sqrt(h2)
        novo[0] = hip[0] + d[0] * f
        novo[2] = hip[2] + d[2] * f
        novo[1] = hip[1]
    return novo


def tornozelo_corrigido(hip, d):
    return hip + d


def R_pe_mundo(pitch_graus, yaw_graus):
    """Pe com pitch (positivo = ponta para cima / dorsiflexao) e yaw (positivo = ponta para -X)."""
    return Ry(yaw_graus * DEG) @ Rx(pitch_graus * DEG)


class Animador:
    def __init__(self, esk, cfg, med):
        self.esk = esk
        self.cfg = cfg
        self.med = med
        self.pernas = {"dir": Perna(esk, "dir", med), "esq": Perna(esk, "esq", med)}
        self.quad0 = esk.mundo["quadril"].copy()
        self.relatorio = {}
        self.fps = cfg["animacao"]["fps"]

    # ---- utilidades de pose --------------------------------------------------------
    def novo_quadro(self, dx=0.0, dy=0.0, dz_frente=0.0, yaw=0.0, roll=0.0, pitch_frente=0.0):
        """Raiz: dy sobe, dz_frente avanca (frente = -Z). pitch_frente > 0 inclina o tronco para frente."""
        pos = self.quad0 + np.array([dx, dy, -dz_frente])
        R = Ry(yaw * DEG) @ Rx(-pitch_frente * DEG) @ Rz(roll * DEG)
        return Quadro(pos, R)

    def cabeca_estavel(self, q, q_lag, pitch_alvo=0.0, yaw_alvo=0.0):
        """Pescoco+cabeca compensam o tronco para a cabeca ficar estavel no mundo. A compensacao usa a
        pose do tronco um instante antes (q_lag) = atraso natural da cabeca (movimento secundario)."""
        Rc3 = q_lag.R @ self.esk.R_local("coluna1", q_lag) @ self.esk.R_local("coluna2", q_lag) @ self.esk.R_local("coluna3", q_lag)
        alvo = Ry(yaw_alvo * DEG) @ Rx(-pitch_alvo * DEG)
        Rtot = Rc3.T @ alvo
        a, b, c = decompor_xyz(Rtot)
        # divide meio a meio entre pescoco e cabeca
        for nome in ("pescoco", "cabeca"):
            q.ang[nome] = local_para_anat(nome, a * 0.5, b * 0.5, c * 0.5)

    def espalhar_coluna(self, q, flex=0.0, yaw=0.0, tilt=0.0):
        """Reparte flexao/rotacao/inclinacao TOTAIS da coluna entre os 3 segmentos (usa 'distribuicao')."""
        dist = self.cfg["limites"]["distribuicao"]
        for n in ("coluna1", "coluna2", "coluna3"):
            d = dist[n]
            q.ang[n] = (flex * d["x"] * DEG, yaw * d["y"] * DEG, tilt * d["z"] * DEG)

    def braco(self, q, s, flex=0.0, abd=0.0, rot=0.0, cotovelo=0.0, pronacao=0.0, punho=0.0, dedos=0.0, punho_desvio=0.0):
        """Braco com ritmo escapulo-umeral: acima de 90 graus de elevacao a clavicula acompanha (1 para 2,5)."""
        elev = max(abs(flex), abs(abd))
        clav_z = max(0.0, elev - 90.0) * 0.38
        clav_y = max(0.0, flex) * 0.10
        q.set("clavicula_" + s, 0.0, clav_y, clav_z)
        q.set("braco_" + s, flex, rot, abd)
        q.set("antebraco_" + s, cotovelo, pronacao, 0.0)
        q.set("mao_" + s, punho, 0.0, punho_desvio)
        q.set("dedos_" + s, dedos, 0.0, 0.0)

    def pes_parados(self, q, alvo_dir, alvo_esq, kmin=3.0, pe_pitch=(0.0, 0.0), yaw_pe=7.0):
        """Pernas por IK com os tornozelos em alvos do mundo (listas [x,y,z]) e pitch do pe no mundo."""
        for s, alvo, pitch in (("dir", alvo_dir, pe_pitch[0]), ("esq", alvo_esq, pe_pitch[1])):
            yaw = -yaw_pe if s == "dir" else yaw_pe
            Rf = R_pe_mundo(pitch, yaw)
            perna_ik(self.esk, q, s, np.array(alvo, float), Rf, yaw * DEG, kmin, self.pernas[s], self.relatorio)

    def alvo_sobre_bola(self, s, x, z_flat, psi_graus):
        """Posicao do tornozelo quando o pe gira (calcanhar sobe psi graus) em torno da bola, sem sair do lugar."""
        p = self.pernas[s]
        psi = psi_graus * DEG
        y = p.bf * math.sin(psi) + p.ah * math.cos(psi)
        z = z_flat - (p.bf * (1 - math.cos(psi)) + p.ah * math.sin(psi))
        return np.array([x, y, z])

    def apoiar(self, q, s, x, z_flat, kmin=3.0, dorsi_max=17.0, yaw_pe=7.0, psi_min=0.0, plantar_max=45.0):
        """Pe no chao em (x, z) com o pe plano; se a dorsiflexao passar de dorsi_max, levanta o calcanhar (e o dedo
        dobra para o pe ficar apoiado na bola). Devolve o angulo do calcanhar usado."""
        yaw = -yaw_pe if s == "dir" else yaw_pe
        psi = psi_min
        while True:
            tmp = {}
            alvo = self.alvo_sobre_bola(s, x, z_flat, psi)
            perna_ik(self.esk, q, s, alvo, R_pe_mundo(-psi, yaw), yaw * DEG, kmin, self.pernas[s], tmp)
            dors = q.ang["pe_" + s][0] / DEG
            if dors <= dorsi_max or psi >= 40:
                break
            psi += 1.0
        for k, v in tmp.items():
            if k == "esticou":
                self.relatorio[k] = self.relatorio.get(k, 0) + v
            else:
                self.relatorio[k] = max(self.relatorio.get(k, 0.0), v)
        q.dedo_do_pe(s, psi)
        return psi

    def altura_minima(self, q):
        """Altura (y) do ponto mais baixo da malha (amostra) na pose q."""
        Vs, idxs, Ws = self.amostra
        fk = self.esk.fk(q)
        P = np.array([fk[n][0] for n in NOMES])
        R = np.array([fk[n][1] for n in NOMES])
        B = np.array([self.esk.mundo[n] for n in NOMES])
        out = np.zeros_like(Vs)
        for j in range(4):
            i = idxs[:, j]
            rel = Vs - B[i]
            out += Ws[:, j:j + 1] * (np.einsum("nij,nj->ni", R[i], rel) + P[i])
        return float(out[:, 1].min())

    def elevar_do_chao(self, quadros, folga=0.0):
        """Se alguma parte da malha atravessa o chao, sobe a raiz (suavizado). Nunca desce."""
        subir = np.array([max(0.0, folga - self.altura_minima(q)) for q in quadros])
        k = 2
        pad = np.concatenate([subir[:1].repeat(k), subir, subir[-1:].repeat(k)])
        suav = np.convolve(pad, np.ones(2 * k + 1) / (2 * k + 1), mode="valid")
        subir = np.maximum(subir, suav)
        for q, d in zip(quadros, subir):
            q.pos[1] += d
        return float(subir.max())

    def base_pes(self):
        x = self.pernas["dir"].x
        z = self.esk.mundo["pe_dir"][2]
        y = self.pernas["dir"].ah
        return x, y, z

    # ---- geracao -------------------------------------------------------------------
    def gerar(self):
        """Devolve dict nome -> {"quadros": [...], "duracao": s, "loop": bool, "velocidade": m/s ou 0}."""
        anims = {}
        self.rel_anim = {}
        for nome, fn in (("parado", self.anim_parado), ("andar", lambda: self.anim_marcha("andar")),
                         ("correr", lambda: self.anim_marcha("correr")), ("agachar", self.anim_agachar),
                         ("pular", self.anim_pular), ("atacar", self.anim_atacar), ("dano", self.anim_dano),
                         ("morrer", self.anim_morrer), ("interagir", self.anim_interagir)):
            self.relatorio = {}
            anims[nome] = fn()
            self.rel_anim[nome] = dict(self.relatorio)
        return anims

    def amostras(self, duracao, loop):
        n = max(2, int(round(duracao * self.fps)))
        return [i * duracao / n for i in range(n)] + ([duracao] if True else [])

    # ---------------------------------------------------------------------------------
    # PARADO: respiracao, balanco de peso, microajustes
    # ---------------------------------------------------------------------------------
    def pose_parado(self, t, T=4.0, agachado=0.0):
        w = 2 * math.pi * t / T
        resp = math.sin(2 * w)                      # 2 respiracoes a cada 4 s (15/min)
        resp_lag = math.sin(2 * w - 0.5)
        sway = math.sin(w)                           # balanco de peso
        x0, y0, z0 = self.base_pes()
        q = self.novo_quadro(dx=0.010 * sway, dy=-0.009 - 0.004 * resp - agachado, dz_frente=0.0, roll=0.7 * sway, yaw=0.8 * math.sin(w * 0.5))
        self.espalhar_coluna(q, flex=0.8 + 0.4 * resp_lag, yaw=-0.8 * math.sin(w * 0.5), tilt=-0.5 * sway)
        ql = self.novo_quadro(dx=0.010 * math.sin(w - 0.25), roll=0.7 * math.sin(w - 0.25))
        self.espalhar_coluna(ql, flex=0.8, yaw=0, tilt=-0.5 * math.sin(w - 0.25))
        self.cabeca_estavel(q, ql, pitch_alvo=2.0 + 0.8 * math.sin(w * 0.5 - 0.6), yaw_alvo=2.0 * math.sin(w * 0.25))
        for s, sg in (("dir", 1), ("esq", -1)):
            self.braco(q, s, flex=4 + 1.2 * resp_lag * 0 + 1.5 * math.sin(w * 0.5 + sg), abd=7 + 0.8 * resp_lag, rot=8, cotovelo=14 + 1.5 * resp_lag, pronacao=18, punho=2, dedos=18)
            q.ang["clavicula_" + s] = (0.0, 0.0, 1.2 * resp_lag * DEG)
        return q

    def anim_parado(self):
        T = 4.0
        quadros = []
        x0, y0, z0 = self.base_pes()
        for t in self.amostras(T, True):
            q = self.pose_parado(t, T)
            sway = math.sin(2 * math.pi * t / T)
            # pes fixos no chao; o peso passa de um pe para o outro
            self.apoiar(q, "dir", x0, z0, kmin=1.0)
            self.apoiar(q, "esq", -x0, z0, kmin=1.0)
            quadros.append(q)
        return {"quadros": quadros, "duracao": T, "loop": True, "velocidade": 0.0}

    # ---------------------------------------------------------------------------------
    # AGACHAR: pose de agachamento parcial (calcanhar no chao: dorsiflexao <= 20 graus)
    # ---------------------------------------------------------------------------------
    def anim_agachar(self):
        T = 4.0
        quadros = []
        x0, y0, z0 = self.base_pes()
        for t in self.amostras(T, True):
            w = 2 * math.pi * t / T
            resp = math.sin(2 * w)
            q = self.novo_quadro(dx=0.006 * math.sin(w), dy=-0.30 - 0.003 * resp, dz_frente=-0.17, pitch_frente=17.0, roll=0.4 * math.sin(w))
            self.espalhar_coluna(q, flex=22.0 + 0.8 * math.sin(2 * w - 0.5), yaw=0.0, tilt=0.0)
            ql = self.novo_quadro(dz_frente=-0.17, pitch_frente=17.0)
            self.espalhar_coluna(ql, flex=22.0)
            self.cabeca_estavel(q, ql, pitch_alvo=-8.0 + 1.5 * math.sin(w * 0.5), yaw_alvo=2.0 * math.sin(w * 0.25))
            for s, sg in (("dir", 1), ("esq", -1)):
                # bracos para frente, em guarda
                self.braco(q, s, flex=38, abd=8, rot=10, cotovelo=70 + 2 * resp, pronacao=25, punho=0, dedos=30)
            self.apoiar(q, "dir", x0 + 0.02, z0 - 0.02, kmin=1.0)
            self.apoiar(q, "esq", -x0 - 0.02, z0 - 0.02, kmin=1.0)
            quadros.append(q)
        return {"quadros": quadros, "duracao": T, "loop": True, "velocidade": 0.0}

    # ---------------------------------------------------------------------------------
    # MARCHA / CORRIDA com pe sem deslizar
    # ---------------------------------------------------------------------------------
    def estado_pe(self, s, p, t, T, v, perna):
        """Estado do pe 's' no instante t (periodico). Devolve (s_mundo, y, pitch_deg|None, toe_deg, em_apoio, u_balanco).
        No apoio o pe nao desliza: calcanhar fixo (toque), pe plano, depois bola fixa (impulso). No balanco o tornozelo
        vai do ponto de saida ao ponto do proximo toque em curva suave (pitch None = angulo do tornozelo e dado pela curva)."""
        d = p["apoio"]
        deslocamento = 0.0 if s == "dir" else 0.5
        ciclo = t / T - deslocamento
        n = math.floor(ciclo)
        fase = ciclo - n                         # 0..1: 0 = toque do calcanhar
        t0 = (n + deslocamento) * T              # instante do toque deste apoio
        ah, hb, bf = perna.ah, perna.hb, perna.bf
        th0 = p["calcanhar_graus"] * DEG
        psi = p["impulso_graus"] * DEG
        w1, w2 = p.get("fim_calcanhar", 0.12), p.get("inicio_impulso", 0.40)   # fases do apoio (fracao do apoio)
        sA = v * (t0 + d * T / 2.0) + p.get("avanco_pe_m", 0.0)   # tornozelo com o pe plano, no meio do apoio
        S = v * T

        def tornozelo_calc(sA_, th):             # calcanhar fixo
            return (sA_ - hb) + hb * math.cos(th) - ah * math.sin(th), ah * math.cos(th) + hb * math.sin(th)

        def tornozelo_bola(sA_, ps):             # bola fixa
            bs = sA_ + bf
            return bs + (-bf * math.cos(ps) + ah * math.sin(ps)), bf * math.sin(ps) + ah * math.cos(ps)

        if fase < d:
            w = fase / d
            if w < w1:
                th = th0 * (1 - suave(w / w1))
                sx, sy = tornozelo_calc(sA, th)
                return sx, sy, th / DEG, 0.0, True, 0.0
            if w < w2:
                return sA, ah, 0.0, 0.0, True, 0.0
            ps = psi * suave((w - w2) / (1 - w2))
            sx, sy = tornozelo_bola(sA, ps)
            return sx, sy, -ps / DEG, ps / DEG, True, 0.0
        u = (fase - d) / (1 - d)
        s0, y0 = tornozelo_bola(sA, psi)
        s1, y1 = tornozelo_calc(sA + S, th0)
        m = suave(u)
        sx = s0 + (s1 - s0) * m
        sy = y0 + (y1 - y0) * m + p["folga_pe_m"] * math.sin(math.pi * (u ** p.get("pico_balanco", 0.85))) ** 2
        toe = interp_chaves(u, [(0.0, psi / DEG), (0.30, 0.0), (1.0, 0.0)])
        return sx, sy, None, toe, False, u

    def anim_marcha(self, nome):
        p = self.cfg["marcha"][nome]
        v = p["velocidade"]
        T = 120.0 / p["passos_por_min"]
        N = int(round(T * self.fps))
        d = p["apoio"]
        corrida = nome == "correr"
        x0, y0, z0 = self.base_pes()
        lat = p["largura_pe_m"]
        tempos = [i * T / N for i in range(N)] + [T]
        kb = (8.0, 6.0) if not corrida else (14.0, 45.0)     # flexao do joelho no apoio: base + amplitude*sin

        # ---- 1) altura da pelve: a que mantem os joelhos de apoio com a flexao pedida ----
        fino = 240
        ypelve = []
        for i in range(fino):
            t = i * T / fino
            cand = []
            for s in ("dir", "esq"):
                ps = self.pernas[s]
                sx, sy, pitch, toe, apoio, u_b = self.estado_pe(s, p, t, T, v, ps)
                if apoio or not corrida:
                    desl = 0.0 if s == "dir" else 0.5
                    u = min(1.0, ((t / T - desl) % 1.0) / d)
                    kappa = kb[0] + kb[1] * math.sin(math.pi * u)
                    xlat = lat if s == "dir" else -lat
                    dh = math.hypot(sx - v * t, xlat - self.esk.mundo["coxa_" + s][0])
                    Dalvo = math.sqrt(ps.l1 ** 2 + ps.l2 ** 2 - 2 * ps.l1 * ps.l2 * math.cos(math.pi - kappa * DEG))
                    if Dalvo > dh:
                        cand.append(sy + math.sqrt(Dalvo ** 2 - dh ** 2))
                    else:
                        cand.append(None)
            cand = [c for c in cand if c is not None]
            ypelve.append(min(cand) if cand else None)
        vals = np.array([np.nan if y is None else y for y in ypelve])
        if np.isnan(vals).any():                       # voo (corrida): arco suave entre o fim de um apoio e o toque seguinte
            ok = ~np.isnan(vals)
            xs = np.arange(fino)
            ext = np.concatenate([xs - fino, xs, xs + fino])
            vv = np.concatenate([vals, vals, vals])
            oke = ~np.isnan(vv)
            novo_v = np.interp(xs, ext[oke], vv[oke])
            nanidx = np.where(~ok)[0]
            grupos = np.split(nanidx, np.where(np.diff(nanidx) > 1)[0] + 1)
            for g in grupos:
                for j, kk in enumerate(g):
                    uu = (j + 1) / (len(g) + 1)
                    novo_v[kk] += (9.81 * (len(g) * T / fino) ** 2 / 8.0) * 4 * uu * (1 - uu)
            vals = novo_v
        kk = 3
        pad = np.concatenate([vals[-kk:], vals, vals[:kk]])
        vals = np.convolve(pad, np.ones(2 * kk + 1) / (2 * kk + 1), mode="same")[kk:-kk]
        relax = 0.003 if not corrida else 0.002

        def pelve_y(t):
            x = (t / T % 1.0) * fino
            i0 = int(x) % fino
            f = x - int(x)
            return vals[i0] * (1 - f) + vals[(i0 + 1) % fino] * f - relax

        # ---- 2) tronco, bracos e cabeca em funcao da fase ----
        def montar(tt):
            ph = (tt / T) % 1.0
            yaw_p = p["bacia_yaw"] * math.cos(2 * math.pi * ph)
            roll = p["bacia_roll"] * math.cos(2 * math.pi * (ph - d / 2))
            lean = p["bacia_inclina"] + (1.5 if corrida else 0.6) * math.cos(4 * math.pi * ph)
            sway = p["balanco_lateral_m"] * math.cos(2 * math.pi * (ph - d / 2))
            qq = self.novo_quadro(dx=sway, dy=pelve_y(tt) - self.quad0[1], yaw=yaw_p, roll=roll, pitch_frente=lean)
            shy = -p["ombro_yaw"] * math.cos(2 * math.pi * (ph - 0.04))
            self.espalhar_coluna(qq, flex=(3.0 if not corrida else 4.0) + 0.8 * math.cos(4 * math.pi * ph), yaw=shy - yaw_p, tilt=-0.8 * roll)
            return qq

        # ---- 3) angulo do tornozelo (relativo a canela) nas pontas do balanco, para a transicao ser continua ----
        def tornozelo_rel(s, tt, pitch_mundo):
            qq = montar(tt)
            sx, sy, _, toe, apoio, _ = self.estado_pe(s, p, tt, T, v, self.pernas[s])
            yaw = -7.0 if s == "dir" else 7.0
            alvo = [lat if s == "dir" else -lat, sy, z0 - (sx - v * tt)]
            perna_ik(self.esk, qq, s, np.array(alvo), R_pe_mundo(pitch_mundo, yaw), yaw * DEG, 3.0, self.pernas[s], {})
            return qq.ang["pe_" + s][0] / DEG

        rel_ponta = {}
        for s in ("dir", "esq"):
            desl = 0.0 if s == "dir" else 0.5
            t_sai = ((desl + d) * T) - 1e-4
            t_toque = (desl + 1.0) * T
            a0 = tornozelo_rel(s, t_sai, -p["impulso_graus"])
            a1 = tornozelo_rel(s, t_toque, p["calcanhar_graus"])
            rel_ponta[s] = (a0, a1)

        quadros = []
        for t in tempos:
            ph = (t / T) % 1.0
            q = montar(t)
            self.cabeca_estavel(q, montar(t - 0.05), pitch_alvo=(0.0 if not corrida else -3.0), yaw_alvo=0.0)
            A = p["braco_graus"]
            for s in ("dir", "esq"):
                f = ph - (0.0 if s == "dir" else 0.5)
                balanco = -math.cos(2 * math.pi * (f - 0.03))        # braco oposto a perna do mesmo lado
                bal_cot = -math.cos(2 * math.pi * (f - 0.10))        # cotovelo atrasa mais (sobreposicao)
                flex = A * balanco + (3.0 if not corrida else 10.0)
                cot = p["cotovelo_base"] + (10.0 if not corrida else 14.0) * (0.5 + 0.5 * bal_cot)
                self.braco(q, s, flex=flex, abd=(5.0 if not corrida else 12.0), rot=(10.0 if not corrida else 22.0), cotovelo=cot,
                           pronacao=(22.0 if not corrida else 35.0), punho=(3.0 if not corrida else 8.0) * bal_cot, dedos=(22.0 if not corrida else 55.0))
            for s in ("dir", "esq"):
                sx, sy, pitch, toe, apoio, u_b = self.estado_pe(s, p, t, T, v, self.pernas[s])
                xl = lat if s == "dir" else -lat
                alvo = np.array([xl, sy, z0 - (sx - v * t)])
                yaw = (-7.0 if s == "dir" else 7.0) if not corrida else (-3.0 if s == "dir" else 3.0)
                if pitch is not None:
                    perna_ik(self.esk, q, s, alvo, R_pe_mundo(pitch, yaw), yaw * DEG, 3.0, self.pernas[s], self.relatorio)
                else:
                    alvo = limitar_alcance(self.esk, q, s, alvo, self.pernas[s])
                    a0, a1 = rel_ponta[s]
                    rel = interp_chaves(u_b, [(0.0, a0), (0.28, 0.2 * a0 + 2.0), (0.62, 4.0), (1.0, a1)])
                    perna_ik(self.esk, q, s, alvo, None, yaw * DEG, 3.0, self.pernas[s], self.relatorio, rel_deg=rel)
                q.dedo_do_pe(s, toe)
            quadros.append(q)
        return {"quadros": quadros, "duracao": T, "loop": True, "velocidade": v, "tempos": tempos}

    # ---------------------------------------------------------------------------------
    # Animacoes de uma passada (pose por chaves, pes por IK)
    # ---------------------------------------------------------------------------------
    def anim_por_chaves(self, T, chaves_fn, loop=False, voo=None):
        quadros = []
        x0, y0, z0 = self.base_pes()
        for t in self.amostras(T, loop):
            quadros.append(chaves_fn(t, x0, y0, z0))
        return quadros

    def anim_pular(self):
        """Pulo: agacha (pes no chao), impulso na ponta dos pes, voo balistico (joelhos recolhem), aterrissagem amortecida."""
        T = 1.30
        T_voo = 0.46
        t_sai = 0.44
        t_toq = t_sai + T_voo
        g = 9.81
        h = g * T_voo ** 2 / 8.0           # altura do pulo (balistica)
        x0, y0, z0 = self.base_pes()

        def dy_pelve(t):
            if t < t_sai:
                return interp_chaves(t, [(0.0, 0.0), (0.28, -0.24), (t_sai, 0.0)])
            if t <= t_toq:
                u = (t - t_sai) / T_voo
                return h * 4 * u * (1 - u)
            return interp_chaves(t, [(t_toq, 0.0), (t_toq + 0.10, -0.22), (T, 0.0)])

        def psi_calc(t):  # calcanhar sobe no impulso, desce na aterrissagem
            if t < 0.28:
                return 0.0
            if t < t_sai:
                return 30.0 * suave((t - 0.28) / (t_sai - 0.28))
            return 0.0

        def tronco(t, q):
            lean_k = [(0.0, 4.0), (0.28, 16.0), (t_sai, 6.0), (t_toq, 6.0), (t_toq + 0.10, 15.0), (T, 4.0)]
            flex_k = [(0.0, 3.0), (0.28, 16.0), (t_sai, -3.0), (t_toq, 5.0), (t_toq + 0.10, 13.0), (T, 3.0)]
            dz_k = [(0.0, 0.0), (0.28, -0.12), (t_sai, 0.0), (t_toq, 0.0), (t_toq + 0.10, -0.10), (T, 0.0)]
            return lean_k, flex_k, dz_k

        quadros = []
        # angulo do tornozelo (relativo) na saida e no toque, para a transicao do voo ser continua
        def pose_base(t):
            lean_k, flex_k, dz_k = tronco(t, None)
            q = self.novo_quadro(dy=dy_pelve(t), dz_frente=interp_chaves(t, dz_k), pitch_frente=interp_chaves(t, lean_k))
            self.espalhar_coluna(q, flex=interp_chaves(t, flex_k))
            ql = self.novo_quadro(dz_frente=interp_chaves(t - 0.04, dz_k), pitch_frente=interp_chaves(t - 0.04, lean_k))
            self.espalhar_coluna(ql, flex=interp_chaves(t - 0.04, flex_k))
            self.cabeca_estavel(q, ql, pitch_alvo=interp_chaves(t, [(0.0, 2.0), (0.3, -6.0), (0.55, 4.0), (T, 2.0)]))
            bf = interp_chaves(t, [(0.0, 6.0), (0.28, -35.0), (t_sai, 115.0), (t_sai + 0.22, 85.0), (t_toq, 40.0), (t_toq + 0.10, 15.0), (T, 6.0)])
            ba = interp_chaves(t, [(0.0, 7.0), (0.28, 12.0), (t_sai, 30.0), (t_sai + 0.22, 55.0), (t_toq, 25.0), (T, 7.0)])
            cot = interp_chaves(t, [(0.0, 15.0), (0.28, 25.0), (t_sai, 50.0), (t_sai + 0.22, 70.0), (t_toq + 0.05, 45.0), (T, 15.0)])
            for s in ("dir", "esq"):
                self.braco(q, s, flex=bf, abd=ba, rot=10, cotovelo=cot, pronacao=20, punho=0, dedos=25)
            return q

        def apoio(t, q):
            psi = psi_calc(t)
            for s, sg in (("dir", 1), ("esq", -1)):
                self.apoiar(q, s, sg * x0, z0, kmin=3.0, psi_min=psi)

        q_s = pose_base(t_sai)
        apoio(t_sai, q_s)
        q_t = pose_base(t_toq + 0.02)
        apoio(t_toq + 0.02, q_t)
        rel_sai = {s: q_s.ang["pe_" + s][0] / DEG for s in ("dir", "esq")}
        rel_toq = {s: q_t.ang["pe_" + s][0] / DEG for s in ("dir", "esq")}
        for t in self.amostras(T, False):
            q = pose_base(t)
            if t < t_sai or t >= t_toq - 1e-6:
                apoio(t, q)
            else:
                u = (t - t_sai) / T_voo
                tuck = math.sin(math.pi * u) ** 0.9
                for s, sg in (("dir", 1), ("esq", -1)):
                    yaw = -7.0 if s == "dir" else 7.0
                    alvo = np.array([sg * x0, y0 + 0.04 * (1 - u) + dy_pelve(t) + 0.20 * tuck, z0 - 0.05 * tuck])
                    rel = interp_chaves(u, [(0.0, rel_sai[s]), (0.5, 8.0), (1.0, rel_toq[s])])
                    perna_ik(self.esk, q, s, alvo, None, yaw * DEG, 3.0, self.pernas[s], self.relatorio, rel_deg=rel)
                    q.dedo_do_pe(s, 0.0)
            quadros.append(q)
        return {"quadros": quadros, "duracao": T, "loop": False, "velocidade": 0.0}

    def anim_atacar(self):
        T = 0.95
        x0, y0, z0 = self.base_pes()

        def fn(t, x0, y0, z0):
            # chaves: 0 guarda -> 0.30 preparo (gira para tras) -> 0.46 golpe -> 0.62 passa -> 0.95 volta
            giro = interp_chaves(t, [(0.0, 0.0), (0.30, 32.0), (0.46, -34.0), (0.62, -30.0), (0.95, 0.0)])
            giro_lag = interp_chaves(t - 0.05, [(0.0, 0.0), (0.30, 32.0), (0.46, -34.0), (0.62, -30.0), (0.95, 0.0)])
            avanco = interp_chaves(t, [(0.0, 0.0), (0.30, -0.04), (0.46, 0.10), (0.62, 0.09), (0.95, 0.0)])
            dy = interp_chaves(t, [(0.0, -0.014), (0.30, -0.05), (0.46, -0.07), (0.62, -0.05), (0.95, -0.014)])
            lean = interp_chaves(t, [(0.0, 4.0), (0.30, 2.0), (0.46, 14.0), (0.62, 12.0), (0.95, 4.0)])
            q = self.novo_quadro(dy=dy, dz_frente=avanco, yaw=giro * 0.35, pitch_frente=lean)
            self.espalhar_coluna(q, flex=interp_chaves(t, [(0.0, 3.0), (0.30, 0.0), (0.46, 12.0), (0.62, 10.0), (0.95, 3.0)]), yaw=giro * 0.65)
            ql = self.novo_quadro(dy=dy, dz_frente=avanco, yaw=giro_lag * 0.35, pitch_frente=lean)
            self.espalhar_coluna(ql, flex=3.0, yaw=giro_lag * 0.65)
            self.cabeca_estavel(q, ql, pitch_alvo=interp_chaves(t, [(0.0, 2.0), (0.46, -4.0), (0.95, 2.0)]), yaw_alvo=interp_chaves(t, [(0.0, 0.0), (0.46, -12.0), (0.95, 0.0)]))
            # braco direito: golpe diagonal; esquerdo: guarda
            f_b = interp_chaves(t, [(0.0, 30.0), (0.30, 95.0), (0.46, 70.0), (0.62, 55.0), (0.95, 30.0)])
            a_b = interp_chaves(t, [(0.0, 10.0), (0.30, 70.0), (0.46, 5.0), (0.62, -10.0), (0.95, 10.0)])
            r_b = interp_chaves(t, [(0.0, 10.0), (0.30, -50.0), (0.46, 40.0), (0.62, 55.0), (0.95, 10.0)])
            c_b = interp_chaves(t - 0.04, [(0.0, 80.0), (0.30, 120.0), (0.46, 15.0), (0.62, 25.0), (0.95, 80.0)])
            self.braco(q, "dir", flex=f_b, abd=a_b, rot=r_b, cotovelo=c_b, pronacao=interp_chaves(t, [(0.0, 20.0), (0.46, -30.0), (0.95, 20.0)]), punho=interp_chaves(t, [(0.0, 0.0), (0.30, -20.0), (0.46, 25.0), (0.95, 0.0)]), dedos=60)
            self.braco(q, "esq", flex=interp_chaves(t, [(0.0, 40.0), (0.30, 25.0), (0.46, 55.0), (0.95, 40.0)]), abd=12, rot=10, cotovelo=interp_chaves(t, [(0.0, 85.0), (0.46, 100.0), (0.95, 85.0)]), pronacao=25, punho=0, dedos=40)
            # pes: direito a frente, esquerdo atras, fixos no chao
            pdz = 0.14
            self.apoiar(q, "dir", x0, z0 - pdz, kmin=1.0)
            self.apoiar(q, "esq", -x0, z0 + pdz, kmin=1.0)
            return q

        quadros = self.anim_por_chaves(T, fn)
        return {"quadros": quadros, "duracao": T, "loop": False, "velocidade": 0.0}

    def anim_dano(self):
        T = 0.65
        x0, y0, z0 = self.base_pes()

        def fn(t, x0, y0, z0):
            imp = interp_chaves(t, [(0.0, 0.0), (0.08, 1.0), (0.30, 0.35), (0.65, 0.0)])
            imp_lag = interp_chaves(t - 0.04, [(0.0, 0.0), (0.08, 1.0), (0.30, 0.35), (0.65, 0.0)])
            q = self.novo_quadro(dy=-0.05 * imp, dz_frente=-0.07 * imp, pitch_frente=4.0 - 8.0 * imp, yaw=-6.0 * imp, roll=3.0 * imp)
            self.espalhar_coluna(q, flex=3.0 - 14.0 * imp_lag, yaw=7.0 * imp_lag, tilt=6.0 * imp_lag)
            ql = self.novo_quadro(dy=-0.05 * imp_lag, dz_frente=-0.07 * imp_lag, pitch_frente=4.0 - 8.0 * imp_lag, yaw=-6.0 * imp_lag, roll=3.0 * imp_lag)
            self.espalhar_coluna(ql, flex=3.0 - 14.0 * imp_lag, yaw=7.0 * imp_lag, tilt=6.0 * imp_lag)
            # cabeca "chicoteia" para tras (nao compensa o tronco)
            q.ang["pescoco"] = local_para_anat("pescoco", -16.0 * imp_lag * DEG, 12.0 * imp_lag * DEG, 8.0 * imp_lag * DEG)
            q.ang["cabeca"] = local_para_anat("cabeca", -14.0 * imp_lag * DEG, 6.0 * imp_lag * DEG, 4.0 * imp_lag * DEG)
            for s, sg in (("dir", 1), ("esq", -1)):
                self.braco(q, s, flex=6.0 + 40.0 * imp, abd=7.0 + 25.0 * imp, rot=8, cotovelo=14.0 + 55.0 * imp, pronacao=18, punho=-8.0 * imp, dedos=18 + 40 * imp)
            self.apoiar(q, "dir", x0, z0, kmin=1.0)
            self.apoiar(q, "esq", -x0, z0, kmin=1.0)
            return q

        quadros = self.anim_por_chaves(T, fn)
        return {"quadros": quadros, "duracao": T, "loop": False, "velocidade": 0.0}

    def anim_morrer(self):
        T = 1.9
        x0, y0, z0 = self.base_pes()

        def fn(t, x0, y0, z0):
            # 0-0.35 joelhos dobram e tronco recua; 0.35-1.25 cai de costas; 1.25-1.9 acomoda
            k_fall = suave((t - 0.25) / 1.05)       # 0 em pe -> 1 deitado
            pitch = 90.0 * k_fall                    # giro de costas (raiz gira para tras)
            dy = interp_chaves(t, [(0.0, 0.0), (0.30, -0.18), (0.70, -0.45), (1.25, -(self.quad0[1] - 0.15)), (1.9, -(self.quad0[1] - 0.14))])
            dz_back = interp_chaves(t, [(0.0, 0.0), (0.30, -0.06), (0.70, -0.25), (1.25, -0.50), (1.9, -0.52)])
            q = Quadro(self.quad0 + np.array([0.0, dy, -dz_back]), Rx(pitch * DEG) @ Ry(-8.0 * k_fall * DEG))
            fl = interp_chaves(t, [(0.0, 3.0), (0.30, -8.0), (1.25, 6.0), (1.9, 3.0)])
            self.espalhar_coluna(q, flex=fl, yaw=6.0 * k_fall, tilt=4.0 * k_fall)
            q.ang["pescoco"] = local_para_anat("pescoco", -interp_chaves(t, [(0.0, 0.0), (0.35, -8.0), (1.0, 10.0), (1.9, 6.0)]) * DEG, 8 * k_fall * DEG, 0.0)
            q.ang["cabeca"] = local_para_anat("cabeca", -interp_chaves(t, [(0.0, 0.0), (0.35, -6.0), (1.0, 8.0), (1.9, 5.0)]) * DEG, 10 * k_fall * DEG, 3 * k_fall * DEG)
            # bracos se abrem
            ab = interp_chaves(t, [(0.0, 7.0), (0.5, 40.0), (1.25, 28.0), (1.9, 22.0)])
            fx = interp_chaves(t, [(0.0, 4.0), (0.5, 60.0), (0.9, 20.0), (1.25, -10.0), (1.9, -8.0)])
            for s, sg in (("dir", 1), ("esq", -1)):
                self.braco(q, s, flex=fx * (1.0 if s == "dir" else 0.75), abd=ab, rot=10, cotovelo=interp_chaves(t, [(0.0, 15.0), (0.5, 40.0), (1.9, 25.0)]), pronacao=20, punho=0, dedos=30)
            # pernas: dobram e soltam
            for s, sg in (("dir", 1), ("esq", -1)):
                hip = interp_chaves(t, [(0.0, 3.0), (0.35, 40.0), (0.9, 70.0), (1.25, 12.0), (1.9, 8.0)]) * (1.0 if s == "dir" else 0.6)
                joe = interp_chaves(t, [(0.0, 5.0), (0.35, 60.0), (0.9, 85.0), (1.25, 25.0), (1.9, 15.0)]) * (1.0 if s == "dir" else 0.7)
                q.set("coxa_" + s, hip, 0.0, 8.0 + 4 * k_fall)
                q.set("canela_" + s, joe, 0, 0)
                q.set("pe_" + s, -interp_chaves(t, [(0.0, 0.0), (0.6, 12.0), (1.9, 18.0)]), 0, 0)
                q.set("ponta_" + s, 0, 0, 0)
            return q

        quadros = self.anim_por_chaves(T, fn)
        self.relatorio["subiu_do_chao_m"] = self.elevar_do_chao(quadros)
        return {"quadros": quadros, "duracao": T, "loop": False, "velocidade": 0.0}

    def anim_interagir(self):
        T = 1.5
        x0, y0, z0 = self.base_pes()

        def fn(t, x0, y0, z0):
            alc = interp_chaves(t, [(0.0, 0.0), (0.45, 1.0), (0.85, 1.0), (1.5, 0.0)])
            alc_lag = interp_chaves(t - 0.05, [(0.0, 0.0), (0.45, 1.0), (0.85, 1.0), (1.5, 0.0)])
            pres = interp_chaves(t, [(0.0, 0.0), (0.55, 0.0), (0.68, 1.0), (0.80, 0.0), (1.5, 0.0)])
            q = self.novo_quadro(dy=-0.05 * alc, dz_frente=0.06 * alc, pitch_frente=4.0 + 9.0 * alc, yaw=4.0 * alc)
            self.espalhar_coluna(q, flex=3.0 + 10.0 * alc_lag, yaw=-6.0 * alc_lag)
            ql = self.novo_quadro(dy=-0.05 * alc_lag, dz_frente=0.06 * alc_lag, pitch_frente=4.0 + 9.0 * alc_lag, yaw=4.0 * alc_lag)
            self.espalhar_coluna(ql, flex=3.0 + 10.0 * alc_lag, yaw=-6.0 * alc_lag)
            self.cabeca_estavel(q, ql, pitch_alvo=-5.0 * alc, yaw_alvo=-6.0 * alc)
            self.braco(q, "dir", flex=8.0 + 78.0 * alc - 6.0 * pres, abd=8.0 + 6.0 * alc, rot=10.0 - 6.0 * alc, cotovelo=16.0 + 48.0 * alc - 12.0 * pres, pronacao=18 + 40.0 * alc, punho=-6.0 * alc + 12.0 * pres, dedos=18 + 40.0 * alc - 30.0 * pres)
            self.braco(q, "esq", flex=6.0 + 6.0 * alc, abd=7.0, rot=8, cotovelo=14.0 + 6.0 * alc, pronacao=18, punho=2, dedos=18)
            self.apoiar(q, "dir", x0, z0, kmin=1.0)
            self.apoiar(q, "esq", -x0, z0, kmin=1.0)
            return q

        quadros = self.anim_por_chaves(T, fn)
        return {"quadros": quadros, "duracao": T, "loop": False, "velocidade": 0.0}


# =====================================================================================
# 5) ESCRITA DO .G3DJ
# =====================================================================================
def r5(v):
    return round(float(v), 5)


def montar_g3dj(esk, V, VN, VT, grupos, texturas, idx, Wt, cfg, anims):
    """Monta o dicionario g3dj. Uma malha por material; todas com os 24 ossos."""
    cfgm = cfg["malha"]
    tex_cfg = cfgm.get("materiais_textura", {})
    meshes, materiais, partes_no = [], [], []
    for mat, tris in grupos.items():
        mapa = {}
        verts, indices = [], []
        for tri in tris:
            for (vi, ti, ni) in tri:
                chave = (vi, ti, ni)
                if chave not in mapa:
                    mapa[chave] = len(verts)
                    p = V[vi]
                    n = VN[ni] if ni >= 0 and len(VN) else np.array([0.0, 1.0, 0.0])
                    uv = VT[ti] if ti >= 0 and len(VT) else np.array([0.0, 0.0])
                    verts.append((vi, p, n, uv))
                indices.append(mapa[chave])
        flat = []
        for (vi, p, n, uv) in verts:
            flat += [r5(p[0]), r5(p[1]), r5(p[2]), r5(n[0]), r5(n[1]), r5(n[2]), r5(uv[0]), r5(uv[1])]
            for k in range(4):
                flat += [float(idx[vi, k]), r5(Wt[vi, k])]
        pid = "parte_" + mat
        meshes.append({
            "attributes": ["POSITION", "NORMAL", "TEXCOORD0", "BLENDWEIGHT0", "BLENDWEIGHT1", "BLENDWEIGHT2", "BLENDWEIGHT3"],
            "vertices": flat,
            "parts": [{"id": pid, "type": "TRIANGLES", "indices": indices}]})
        tex = tex_cfg.get(mat) or texturas.get(mat)
        m = {"id": mat, "diffuse": [1.0, 1.0, 1.0]}
        if tex:
            m["textures"] = [{"id": "tex_" + mat, "filename": tex, "type": "DIFFUSE"}]
        materiais.append(m)
        partes_no.append({
            "meshpartid": pid, "materialid": mat,
            "bones": [{"node": n, "translation": [r5(c) for c in esk.mundo[n]], "rotation": [0.0, 0.0, 0.0, 1.0], "scale": [1.0, 1.0, 1.0]} for n in NOMES]})

    def no(nome):
        d = {"id": nome, "translation": [r5(c) for c in esk.local[nome]]}
        filhos = [no(n) for n in NOMES if PAI[n] == nome]
        if filhos:
            d["children"] = filhos
        return d

    raiz = no("quadril")
    raiz["translation"] = [r5(c) for c in esk.mundo["quadril"]]
    nodes = [raiz, {"id": "malha", "parts": partes_no}]
    animacoes = []
    for nome, a in anims.items():
        tempos = a.get("tempos") or [i * a["duracao"] / (len(a["quadros"]) - 1) for i in range(len(a["quadros"]))]
        bones = {n: [] for n in NOMES}
        for t, q in zip(tempos, a["quadros"]):
            try:
                esk.contexto = "[animacao %s, t=%.3fs]" % (nome, t)
                dados = esk.quats(q)
            except PoseInvalida as e:
                raise PoseInvalida("[animacao %s, t=%.3fs] %s" % (nome, t, e))
            for n in NOMES:
                tr, qt = dados[n]
                kf = {"keytime": r5(t * 1000.0), "rotation": [r5(c) for c in qt]}
                if tr is not None:
                    kf["translation"] = [r5(c) for c in tr]
                bones[n].append(kf)
        animacoes.append({"id": nome, "bones": [{"boneId": n, "keyframes": bones[n]} for n in NOMES]})
    return {"version": [0, 1], "id": "humano", "meshes": meshes, "materials": materiais, "nodes": nodes, "animations": animacoes}


def gerar(cfg, caminho_obj, saida, silencioso=False):
    log = (lambda *a: None) if silencioso else print
    V, VT, VN, grupos, texturas = ler_obj(caminho_obj)
    V, VN = orientar(V, VN, cfg)
    med = medir_malha(V, cfg)
    log("Malha: %d vertices, altura %.3f m, perna x=%.3f, braco x=%.3f" % (len(V), med["H"], med["leg_x"], med["arm_x"]))
    mundo, fins = montar_juntas(med, cfg)
    lim = Limites(cfg)
    esk = Esqueleto(mundo, fins, lim)
    NV = normais_vertice(V, grupos)
    idx, Wt = calcular_pesos(V, esk, cfg, med, NV)
    log("Pesos: soma min=%.6f max=%.6f, ossos por vertice (media) = %.2f" % (Wt.sum(1).min(), Wt.sum(1).max(), (Wt > 0).sum(1).mean()))
    anim = Animador(esk, cfg, med)
    sel = np.arange(0, len(V), 6)
    anim.amostra = (V[sel], idx[sel], Wt[sel])
    anims = anim.gerar()
    log("Animacoes:", ", ".join("%s(%.2fs)" % (n, a["duracao"]) for n, a in anims.items()))
    esticou = sum(r.get("esticou", 0) for r in anim.rel_anim.values())
    for n, r in anim.rel_anim.items():
        log("   %-10s perna esticada alem do alcance: %3d (excesso max %.1f mm) | joelho max %.0f graus" % (n, r.get("esticou", 0), 1000 * r.get("maior_excesso_m", 0), r.get("joelho_max", 0)))
    if os.environ.get("DIAG"):
        lim.coletar = []
    g = montar_g3dj(esk, V, VN, VT, grupos, texturas, idx, Wt, cfg, anims)
    if lim.coletar:
        piores = {}
        for (n, r, v, lo, hi, ctx) in lim.coletar:
            k = (ctx.split(",")[0], n, r)
            ex = max(lo - v, v - hi)
            if k not in piores or ex > piores[k][0]:
                piores[k] = (ex, v, lo, hi, ctx)
        for k, (ex, v, lo, hi, cx) in sorted(piores.items()):
            print("  VIOLACAO %-22s %-14s %-4s valor %.1f limite [%.0f, %.0f] (excesso %.1f) %s" % (k[0], k[1], k[2], v, lo, hi, ex, cx))
    os.makedirs(os.path.dirname(saida) or ".", exist_ok=True)
    with open(saida, "w") as f:
        json.dump(g, f, separators=(",", ":"))
    meta = {
        "altura_m": med["H"], "fps": cfg["animacao"]["fps"], "ossos": NOMES,
        "blend_s": [cfg["animacao"]["blend_min_s"], cfg["animacao"]["blend_max_s"]],
        "animacoes": {n: {"duracao_s": a["duracao"], "loop": a["loop"], "velocidade_m_s": a["velocidade"]} for n, a in anims.items()},
        "pe": {"ponta_a_frente_m": med["ponta"], "calcanhar_atras_m": med["hb"], "bola_a_frente_m": med["bf"], "tornozelo_altura_m": float(mundo["pe_dir"][1]), "x_perna_m": med["leg_x"]},
        "esticou_pernas": esticou,
    }
    with open(saida.rsplit(".", 1)[0] + "_meta.json", "w") as f:
        json.dump(meta, f, indent=1)
    log("OK ->", saida, "(%.1f KB)" % (os.path.getsize(saida) / 1024))
    return esk, med, idx, Wt, V, anims


def main():
    aqui = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", default=os.path.join(aqui, "config_humano.json"))
    ap.add_argument("--obj", default=None)
    ap.add_argument("--saida", default=None)
    a = ap.parse_args()
    cfg = json.load(open(a.config))
    obj = a.obj or os.path.join(os.path.dirname(os.path.abspath(a.config)), cfg["malha"]["arquivo_obj"])
    saida = a.saida or os.path.join(os.path.dirname(os.path.abspath(a.config)), cfg["saida"]["arquivo_g3dj"])
    cfg["saida"]["arquivo_g3dj"] = saida
    gerar(cfg, obj, saida)


if __name__ == "__main__":
    try:
        main()
    except PoseInvalida as e:
        print("POSE REJEITADA:", e)
        sys.exit(2)
