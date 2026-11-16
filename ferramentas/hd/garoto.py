# ferramentas/hd/garoto.py
"""F9: o GAROTO (protagonista da imagem de referencia): casaco esfarrapado com capuz, bolsa a tiracolo, calca rasgada com
ferida brilhante, botas de cadarco, cabelo preto bagunçado.
PIPELINE (como na industria):  ALTA (kit.FINO=True, ~centenas de milhares de triangulos + micro-relevo analitico)
                               -> BAIXA (kit.FINO=False, ~15 mil triangulos, vai para o jogo)
                               -> BAKE (bake.py: normal map + AO + curvatura da ALTA sobre a UV da BAIXA).
O esqueleto (18 ossos) e o mesmo do jogador: as animacoes continuam valendo."""
import math

import numpy as np

import kit
import personagem
from kit import Campo, Tubo, remendo, elipsoide, tubo_caminho, caixa, rotz, uv_atlas, suave, norm
from personagem import Personagem, R, TILES, TILE, COR, _aneis_tronco, _aneis_cabeca

# ---------------------------------------------------------------------------------------------- registro no atlas
R["casaco"] = (640, 544, 384, 224)
for _n in ("casaco", "forro", "tira", "bolsa", "brilho", "fio", "sujo"):
    if _n not in TILES:
        TILES.append(_n)
TILE.clear()
TILE.update({n: (i % 16 * 64, 768 + (i // 16) * 64, 64, 64) for i, n in enumerate(TILES)})
COR["garoto"] = dict(pele=(208, 178, 156), camisa=(88, 86, 80), jeans=(104, 94, 78), couro=(86, 58, 38),
                     bota=(94, 80, 62), cabelo=(24, 22, 24), sola=(34, 30, 28), cadarco=(176, 160, 128),
                     casaco=(126, 112, 94))

# geometria do casaco (compartilhada com o pintor de textura)
CASACO_Y0, CASACO_Y1 = 0.45, 1.495
NSTRIP = 12


def gap_frente(y):
    """Meia-abertura (rad) do casaco aberto na frente, em torno de theta = pi."""
    return 0.13 + 0.24 * suave((np.asarray(y, float) - 1.0) / 0.45)


def _tiras():
    rs = np.random.RandomState(404)
    L = rs.uniform(0.0, 0.15, NSTRIP)
    L[::4] *= 0.3
    return L


_L = _tiras()


def barra_u(u):
    """Altura da barra rasgada em funcao de u (0..1 em torno do casaco): tiras triangulares."""
    u = np.asarray(u, float)
    k = np.clip(np.floor(u * NSTRIP).astype(int), 0, NSTRIP - 1)
    f = u * NSTRIP - k
    ponta = (1.0 - np.abs(2 * f - 1.0) ** 1.25)
    return 0.66 - _L[k] * (0.35 + 0.65 * ponta) - 0.05 * ponta * (k % 3 == 0)


# ---------------------------------------------------------------------------------------------- detalhes finos (so na ALTA)
def f_tecido(amp=0.0005, per=0.0065, raio=0.17):
    def f(P, th, y):
        return amp * np.sin(2 * math.pi * y / per) * np.sin(2 * math.pi * th * raio / per)
    return f


def f_vincos(amp, sem, nf=8):
    rs = np.random.RandomState(sem)
    par = [(rs.uniform(3, 30), rs.uniform(12, 90), rs.uniform(0, 6.28), rs.uniform(0.4, 1.0)) for _ in range(nf)]

    def f(P, th, y):
        s = np.zeros_like(y)
        for a, b, ph, w in par:
            s += w * np.sin(a * th + b * y + ph + 1.5 * np.sin(2.1 * th + 7 * y))
        return amp * s / nf * 2.4
    return f


def f_costura(th0, y0, y1, amp=0.0018, raio=0.17, larg=0.0024):
    def f(P, th, y):
        d = ((th - th0 + math.pi) % (2 * math.pi)) - math.pi
        dash = (np.mod(y / 0.0045, 1.0) < 0.7)
        return amp * np.exp(-((d * raio / larg) ** 2)) * ((y > y0) & (y < y1)) * (0.5 + 0.5 * dash)
    return f


def f_barra_gasta(ybarra, amp=0.003):
    def f(P, th, y):
        return amp * np.exp(-((y - ybarra) / 0.012) ** 2) * (0.6 + 0.4 * np.sin(40 * th))
    return f


TEARS = [(1.10, 0.90, 0.10, 0.060), (5.20, 1.05, 0.14, 0.070), (3.60, 0.72, 0.12, 0.050), (0.30, 0.80, 0.08, 0.050),
         (2.60, 1.20, 0.09, 0.040), (4.20, 1.30, 0.07, 0.035)]


def f_rasgos():
    def f(P, th, y):
        tot = np.zeros_like(y)
        for (tc, yc, a, b) in TEARS:
            dd = np.sqrt(((th - tc) / a) ** 2 + ((y - yc) / b) ** 2)
            tot += -0.0045 * suave((1.0 - dd) / 0.3) + 0.0030 * np.exp(-((dd - 1.05) / 0.12) ** 2)
        return tot
    return f


def grade_malha(m, grupo, P, UV, Vout, nome):
    nr, nc = P.shape[:2]
    idx = np.arange(nr * nc).reshape(nr, nc)
    a, b, c, d = idx[:-1, :-1].ravel(), idx[:-1, 1:].ravel(), idx[1:, 1:].ravel(), idx[1:, :-1].ravel()
    T = np.concatenate([np.stack([a, b, c], 1), np.stack([a, c, d], 1)])
    m.add(grupo, P.reshape(-1, 3), UV.reshape(-1, 2), T, Vout.reshape(-1, 3), nome=nome)


class TuboRodado(Tubo):
    """Tubo com theta' = theta - pi: a frente (aberta do casaco) fica na costura da UV (theta' = 0 / 2pi)."""

    def base(self, th, y):
        return Tubo.base(self, np.asarray(th, float) + math.pi, y)


class Garoto(Personagem):
    def __init__(self, alta=False):
        super().__init__("garoto")
        self.alta = alta
        self.lod = 3.0 if alta else 0.62

    # ------------------------------------------------------------------------------------ cabeca e cabelo
    def cabeca(self):
        l = self.lod
        self.lod = max(0.85, l)
        super().cabeca()
        self.lod = l

    def cabelo(self):
        m = self.m
        rs = np.random.RandomState(21)
        n = 440 if self.alta else 46
        reg = R["cabelo"]
        t = self.tubos["cabeca"]
        remendo(m, t, "cabeca", 0.0, 2 * math.pi, 1.770, 1.806, 0.0070, 44, 4, uv_modo="tile", tile=reg, saia=False,
                fecha=True, nome="cabelo")
        remendo(m, t, "cabeca", -0.64 * math.pi + 2 * math.pi, 0.64 * math.pi + 2 * math.pi, 1.675, 1.775, 0.0065, 30, 7,
                uv_modo="tile", tile=reg, saia=False, nome="cabelo")
        g = np.array([0.0, -1.0, 0.0])
        for k in range(n):
            tipo = rs.choice(["topo", "franja", "lado", "nuca"], p=[0.34, 0.30, 0.20, 0.16])
            if tipo == "topo":
                th, y0 = rs.uniform(0, 2 * math.pi), rs.uniform(1.770, 1.800)
            elif tipo == "franja":
                th, y0 = math.pi + rs.uniform(-0.75, 0.75), rs.uniform(1.770, 1.796)
            elif tipo == "lado":
                th, y0 = rs.choice([1, -1]) * rs.uniform(0.36, 0.64) * math.pi + math.pi, rs.uniform(1.69, 1.76)
            else:
                th, y0 = rs.uniform(-0.3, 0.3) % (2 * math.pi), rs.uniform(1.655, 1.70)
            P0, rad = t.ponto(th, y0, 0.005)
            P0, rad = P0.reshape(3), rad.reshape(3)
            L = rs.uniform(0.028, 0.058)
            tw = rs.normal(0, 0.35, 3)
            if tipo == "franja":
                d1 = norm(np.array([0.0, 0.45, -1.0]) * 0.8 + rad * 0.3 + tw * 0.3)
                d2 = norm(d1 * 0.6 + g * 0.65 + tw * 0.2)
            elif tipo == "topo":
                d1 = norm(rad * 0.7 + np.array([0.0, 0.9, 0.0]) + tw)
                d2 = norm(d1 * 0.4 + rad * 0.7 + g * 0.5 + tw * 0.3)
            elif tipo == "lado":
                d1 = norm(rad * 0.6 + np.array([0.0, 0.25, 0.0]) + tw * 0.3)
                d2 = norm(d1 * 0.3 + g * 1.1 + rad * 0.3)
            else:
                d1 = norm(rad * 0.7 + g * 0.5)
                d2 = norm(d1 * 0.4 + g * 1.0 + rad * 0.2)
            p1 = P0 + d1 * L * 0.5
            p2 = p1 + d2 * L * 0.6
            p3 = p2 + norm(d2 + g * 0.4 + tw * 0.2) * L * 0.45
            tubo_caminho(m, "cabeca", [P0, p1, p2, p3], [0.0085, 0.0078, 0.0050, 0.0010],
                         uv_regiao=reg, nseg=4 if not self.alta else 6, nn=5 if not self.alta else 9, tampas=False,
                         nome="cabelo", angulo=80.0)

    # ------------------------------------------------------------------------------------ tronco (camisa por baixo)
    def tronco(self):
        m = self.m
        c = Campo()
        c.bossa(0.055, 1.340, -0.100, .045, .035, .04, .0060, True)
        c.bossa(0.0, 1.250, -0.095, .02, .12, .04, .0020)
        c.bossa(0.080, 1.330, 0.095, .05, .06, .04, .0050, True)
        c.bossa(0.0, 1.30, -0.04, .24, .24, .20, 0.0)
        c.fina(f_tecido(0.0004, 0.005, 0.15))
        c.fina(f_vincos(0.0012, 5))
        t = Tubo(_aneis_tronco(), R["tronco"], c)
        self.tubos["tronco"] = t
        t.malha(m, "tronco", self.ns(40), self.rows(40), tampa_topo=True, tampa_base=True, nome="tronco")
        # cinto de couro e fivela (aparece pela abertura do casaco)
        remendo(m, t, "tronco", 0, 2 * math.pi, 1.000, 1.034, 0.0050, 48, 2, uv_modo="tile", tile=TILE["couro"], fecha=True,
                nome="cinto")
        Pf, _ = t.ponto(math.pi, 1.017, 0.0100)
        caixa(m, "tronco", Pf + np.array([0, 0, -0.002]), (0.036, 0.028, 0.0045), TILE["metal"], nome="fivela")
        caixa(m, "tronco", Pf + np.array([0, 0, -0.0035]), (0.022, 0.016, 0.0035), TILE["couro"], nome="fivela")

    # ------------------------------------------------------------------------------------ casaco, capuz, bolsa
    def _aneis_casaco(self):
        return [(0.45, 0, 0.0, 0.232, 0.158, 2.2), (0.60, 0, 0.0, 0.222, 0.150, 2.2), (0.75, 0, 0.0, 0.208, 0.138, 2.2),
                (0.90, 0, 0.0, 0.196, 0.128, 2.2), (1.02, 0, 0.0, 0.186, 0.120, 2.3), (1.12, 0, 0.0, 0.180, 0.116, 2.3),
                (1.22, 0, 0.0, 0.188, 0.118, 2.3), (1.32, 0, -0.002, 0.212, 0.121, 2.4), (1.40, 0, -0.002, 0.238, 0.121, 2.5),
                (1.450, 0, 0.004, 0.228, 0.106, 2.4), (1.482, 0, 0.008, 0.165, 0.094, 2.2), (1.495, 0, 0.010, 0.115, 0.086, 2.1)]

    def casaco(self):
        m = self.m
        c = Campo()

        def dobras(P, th, y):
            h = np.clip((1.15 - y) / 0.6, 0, 1)
            f = 0.012 * h * np.sin(11 * th + 3 * np.sin(3 * th) + 4 * y) + 0.006 * h * np.sin(23 * th + 7 * y)
            f += 0.006 * np.exp(-((y - 1.12) / 0.05) ** 2) * np.sin(6 * th)
            f += 0.004 * np.exp(-((y - 1.38) / 0.07) ** 2) * np.sin(9 * th + 2)
            return f
        c.fn(dobras)
        c.fina(f_tecido(0.0006, 0.0065, 0.19))
        c.fina(f_vincos(0.0030, 9))
        c.fina(f_costura(math.pi, 0.5, 1.45))
        c.fina(f_costura(math.pi / 2, 0.55, 1.40))
        c.fina(f_costura(3 * math.pi / 2, 0.55, 1.40))
        c.fina(f_barra_gasta(0.80, 0.003))
        c.fina(f_rasgos())
        t = TuboRodado(self._aneis_casaco(), R["casaco"], c)
        self.tubos["casaco"] = t
        nth, nrow = self.ns(104), max(10, int(round(32 * self.lod)))
        u = np.linspace(0, 1, nth + 1)
        tt = np.linspace(0, 1, nrow)
        U, T_ = np.meshgrid(u, tt)
        Y = barra_u(U) + T_ * (CASACO_Y1 - barra_u(U))
        g = gap_frente(Y)
        TH = g + U * (2 * math.pi - 2 * g)
        P, rad = t.ponto(TH, Y, 0.0)
        UV = uv_atlas(R["casaco"], TH / (2 * math.pi), (Y - CASACO_Y0) / (CASACO_Y1 - CASACO_Y0))
        grade_malha(m, "tronco", P, UV, rad, "casaco")
        # forro interno (escuro) visivel pela abertura e pela barra
        sl = slice(None, None, 1 if self.alta else 2)   # o forro tem metade da resolucao (so aparece pela abertura)
        P2, _ = t.ponto(TH[sl, sl], Y[sl, sl], -0.009)
        UV2 = np.tile(uv_atlas(TILE["forro"], 0.5, 0.5), (P2.shape[0] * P2.shape[1], 1)).reshape(P2.shape[:2] + (2,))
        grade_malha(m, "tronco", P2, UV2, -rad[sl, sl], "forro")
        P2, _ = t.ponto(TH, Y, -0.009)
        # bordas da abertura frontal (espessura do tecido)
        for lado in (0, -1):
            a, b = P[:, lado], P2[:, lado]
            Q = np.stack([a, b], 1)
            UVq = np.tile(uv_atlas(TILE["forro"], 0.5, 0.5), (Q.shape[0] * 2, 1)).reshape(Q.shape[0], 2, 2)
            grade_malha(m, "tronco", Q, UVq, rad[:, [lado, lado]], "borda")

    def capuz(self):
        m = self.m
        # rolo do capuz em volta do pescoco (aberto na frente), mais grosso atras
        ths = np.linspace(-(math.pi - 0.95), (math.pi - 0.95), 30)
        pts, rr = [], []
        for th in ths:
            y = 1.488 + 0.055 * math.cos(th * 0.75) ** 2
            rx, rz = 0.108 + 0.016 * math.cos(th), 0.098 + 0.020 * math.cos(th)
            pts.append((rx * math.sin(th), y, rz * math.cos(th) + 0.008))
            rr.append(0.017 + 0.020 * max(0.0, math.cos(th)) ** 0.8)
        tubo_caminho(m, "tronco", pts, rr, tile=TILE["casaco"], nseg=10 if not self.alta else 14, nn=34 if not self.alta else 80,
                     tampas=True, nome="capuz")
        # capuz caido nas costas (bolsa de tecido com dobras)
        t = self.tubos["casaco"]
        nth, nrow = 18 if not self.alta else 60, 10 if not self.alta else 36
        TH, Y = np.meshgrid(np.linspace(-0.85, 0.85, nth + 1), np.linspace(1.47, 1.27, nrow + 1))
        sy = (1.47 - Y) / 0.20
        alt = 0.014 + 0.036 * np.sin(math.pi * sy) ** 0.7 * np.cos(TH / 0.85 * math.pi / 2) ** 0.6
        alt = alt + 0.004 * np.sin(13 * TH + 20 * Y)
        TH = TH + math.pi
        P, rad = t.ponto(TH, Y, alt)
        UV = np.tile(uv_atlas(TILE["casaco"], 0.5, 0.5), (P.shape[0] * P.shape[1], 1)).reshape(P.shape[:2] + (2,))
        grade_malha(m, "tronco", P, UV, rad, "capuz")

    def bolsa(self):
        m = self.m
        t = self.tubos["casaco"]
        # tira a tiracolo: ombro esquerdo -> quadril direito, sobre o casaco
        s = np.linspace(0, 1, 9)
        TH = 0.95 - s * 2.15
        Y = 1.47 - s * 0.62
        P, _ = t.ponto(TH, Y, 0.022)
        pts = [tuple(p) for p in P]
        tubo_caminho(m, "tronco", pts, [0.0150] * len(pts), tile=TILE["tira"], nseg=6 if not self.alta else 10,
                     nn=20 if not self.alta else 48, achatar=(0.35, np.array([0.0, 0.0, 1.0])), nome="tira")
        cx, cy, cz = 0.212, 0.760, -0.090
        rot = rotz(math.radians(-5))
        caixa(m, "tronco", (cx, cy, cz), (0.085, 0.185, 0.165), TILE["bolsa"], rot=rot, nome="bolsa")
        caixa(m, "tronco", (cx + 0.004, cy + 0.060, cz), (0.092, 0.076, 0.172), TILE["bolsa"], rot=rot, nome="aba")
        caixa(m, "tronco", (cx + 0.047, cy + 0.020, cz), (0.007, 0.024, 0.030), TILE["metal"], rot=rot, nome="fivela")
        caixa(m, "tronco", (cx + 0.040, cy - 0.030, cz - 0.040), (0.010, 0.056, 0.050), TILE["bolsa"], rot=rot, nome="bolso")
        # costura da bolsa (tubinhos claros) e argola da tira
        for dz in (-0.060, 0.060):
            tubo_caminho(m, "tronco", [(cx + 0.040, cy + 0.075, cz + dz), (cx + 0.041, cy + 0.0, cz + dz),
                                       (cx + 0.040, cy - 0.074, cz + dz)], [0.0014] * 3, tile=TILE["fio"], nseg=4, nn=6,
                         nome="costura")

    # ------------------------------------------------------------------------------------ bracos e pernas
    def _aneis_braco_g(self, s):
        x = 0.246 * s
        return [(1.462, 0.200 * s, 0, 0.040, 0.040, 2), (1.442, 0.228 * s, 0, 0.062, 0.062, 2),
                (1.43, x, 0, 0.068, 0.068, 2), (1.35, x, 0, 0.062, 0.062, 2), (1.28, x, 0, 0.060, 0.060, 2),
                (1.21, x, 0, 0.058, 0.058, 2), (1.15, x, 0, 0.057, 0.057, 2), (1.08, x, 0, 0.054, 0.054, 2),
                (0.96, x, 0, 0.048, 0.048, 2), (0.90, x, 0, 0.040, 0.040, 2), (0.86, x, 0, 0.027, 0.026, 2),
                (0.79, x, 0, 0.026, 0.024, 2)]

    def braco(self, s):
        m = self.m
        nome = "braco_esq" if s < 0 else "braco_dir"
        x = 0.246 * s
        c = Campo()
        c.bossa(x, 1.44, 0.0, .04, .05, .06, .004)
        c.bossa(x, 1.20, 0.05, .02, .03, .03, .006)

        def dobras(P, th, y):
            f = 0.009 * np.exp(-((y - 1.20) / 0.08) ** 2) * np.sin(10 * th + 9 * y)
            f += 0.006 * np.exp(-((y - 0.99) / 0.10) ** 2) * np.sin(8 * th + 30 * y)
            return f
        c.fn(dobras)
        c.fina(f_tecido(0.0006, 0.0065, 0.058))
        c.fina(f_vincos(0.0030, 13 + int(s)))
        c.fina(f_costura(math.pi / 2, 0.95, 1.40, raio=0.058))
        t = Tubo(self._aneis_braco_g(s), R[nome], c)
        self.tubos[nome] = t
        t.malha(m, nome, self.ns(24), self.rows(34), tampa_topo=True, tampa_base=True, nome="braco")
        remendo(m, t, nome, 0, 2 * math.pi, 0.885, 0.940, 0.0070, 22, 2, uv_modo="sup", fecha=True, nome="punho")
        rs = np.random.RandomState(70 + int(s))
        for k in range(5):  # tiras de pano pendendo da manga rasgada
            th = rs.uniform(0, 2 * math.pi)
            P0, rad = t.ponto(th, 0.895, 0.006)
            P0, rad = P0.reshape(3), rad.reshape(3)
            L = rs.uniform(0.030, 0.062)
            d = norm(np.array([rs.normal(0, 0.3), -1.0, rs.normal(0, 0.3)]) + rad * 0.35)
            tubo_caminho(m, nome, [P0, P0 + d * L * 0.5, P0 + d * L * 0.5 + norm(d + rad * 0.2) * L * 0.5],
                         [0.0060, 0.0050, 0.0015], tile=TILE["casaco"], nseg=4, nn=5, tampas=False,
                         achatar=(0.30, rad), nome="tira_manga")
        self.mao(s)

    def perna(self, s):
        m = self.m
        nome = "perna_esq" if s < 0 else "perna_dir"
        x = 0.095 * s
        rings = [(0.115, x, 0.012, 0.062, 0.066, 2), (0.20, x, 0.012, 0.068, 0.072, 2), (0.34, x, 0.010, 0.074, 0.080, 2),
                 (0.46, x, 0.000, 0.076, 0.082, 2), (0.52, x, 0.000, 0.078, 0.082, 2), (0.62, x, 0.000, 0.082, 0.086, 2),
                 (0.74, x, 0.000, 0.086, 0.090, 2), (0.86, x, 0.000, 0.088, 0.092, 2), (0.93, x, 0.000, 0.088, 0.092, 2)]
        c = Campo()
        c.bossa(x, 0.78, -0.060, .05, .08, .03, .0040)
        c.bossa(x, 0.34, 0.058, .04, .07, .03, .0050)

        def dobras(P, th, y):
            f = 0.010 * np.exp(-((y - 0.50) / 0.06) ** 2) * np.sin(10 * th + 25 * y) * (np.cos(th) > -0.2)
            f += 0.008 * np.exp(-((y - 0.20) / 0.08) ** 2) * np.sin(7 * th + 60 * y)   # calca amontoada na bota
            f += 0.005 * np.exp(-((y - 0.70) / 0.2) ** 2) * np.sin(9 * th + 14 * y)
            return f
        c.fn(dobras)
        if s < 0:   # ferida: o pano se abre e a carne fica numa cova rasa
            c.bossa(x, 0.60, -0.080, 0.032, 0.105, 0.03, -0.012)

            def rim(P, th, y):
                d = np.sqrt(((th - math.pi) / 0.42) ** 2 + ((y - 0.60) / 0.14) ** 2)
                return 0.0045 * np.exp(-((d - 1.0) / 0.10) ** 2) * (0.6 + 0.4 * np.sin(31 * th + 40 * y))
            c.fina(rim)
        c.fina(f_tecido(0.0006, 0.0065, 0.08))
        c.fina(f_vincos(0.0028, 31 + int(s)))
        c.fina(f_costura(math.pi / 2, 0.2, 0.9, raio=0.08))
        c.fina(f_costura(3 * math.pi / 2, 0.2, 0.9, raio=0.08))
        t = Tubo(rings, R[nome], c)
        self.tubos[nome] = t
        t.malha(m, nome, self.ns(30), self.rows(44), tampa_topo=True, tampa_base=False, nome="perna")
        # bolso cargo na coxa (lado de fora) e bainha amontoada
        th0 = math.pi / 2 if s > 0 else 3 * math.pi / 2
        remendo(m, t, nome, th0 - 0.38, th0 + 0.38, 0.60, 0.74, 0.0075, 7, 5, nome="bolso_cargo")
        remendo(m, t, nome, th0 - 0.40, th0 + 0.40, 0.715, 0.752, 0.0105, 7, 2, nome="aba_cargo")
        remendo(m, t, nome, 0, 2 * math.pi, 0.150, 0.215, 0.0085, 26, 3, uv_modo="sup", fecha=True, nome="bainha")
        self.bota(s)

    def tudo(self):
        self.cabeca()
        self.tronco()
        for s in (-1, 1):
            self.braco(s)
            self.perna(s)
        self.casaco()
        self.capuz()
        self.bolsa()
        return self
