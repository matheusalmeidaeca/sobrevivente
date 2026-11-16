# ferramentas/hd/personagem.py
"""F8: constroi o jogador e o zumbi em ALTA DEFINICAO (malha + atlas de textura 1024x1024), 100% procedural.
Mesmos ossos/pivos do esqueleto (animacao.py). Uso: m, atlas = personagem.construir("jogador" | "zumbi")."""
import math

import numpy as np

import kit
from kit import (Campo, Malha, Tubo, remendo, elipsoide, tubo_caminho, caixa, rotx, roty, rotz, uv_atlas, fbm, ruido,
                 suave, norm)

R = {
    "cabeca": (0, 0, 384, 256), "tronco": (384, 0, 512, 320),
    "braco_esq": (0, 256, 128, 384), "braco_dir": (128, 256, 128, 384),
    "perna_esq": (256, 320, 192, 448), "perna_dir": (448, 320, 192, 448),
    "cano_esq": (640, 320, 128, 96), "cano_dir": (768, 320, 128, 96),
    "pe_esq": (640, 416, 192, 128), "pe_dir": (832, 416, 192, 128),
    "mao_esq": (384, 896, 96, 96), "mao_dir": (480, 896, 96, 96),
    "olho": (0, 896, 128, 128), "cabelo": (128, 896, 256, 128),
}
TILES = ["camisa", "jeans", "couro", "metal", "bota", "sola", "cadarco", "pele", "labio", "cabelo_t", "botao",
         "escuro", "dente", "sangue", "carne", "tecido", "unha", "bota_clara"]
TILE = {n: (i % 16 * 64, 768 + (i // 16) * 64, 64, 64) for i, n in enumerate(TILES)}

COR = {
    "jogador": dict(pele=(226, 174, 140), camisa=(70, 104, 152), jeans=(52, 80, 124), couro=(104, 66, 38),
                    bota=(96, 64, 42), cabelo=(70, 46, 30), sola=(36, 30, 28), cadarco=(205, 190, 150)),
    "zumbi": dict(pele=(150, 168, 138), camisa=(132, 126, 114), jeans=(74, 70, 62), couro=(60, 44, 32),
                  bota=(62, 50, 40), cabelo=(48, 40, 32), sola=(28, 26, 24), cadarco=(120, 112, 96)),
}


def _c(t):
    return np.array(t, np.float32) / 255.0


# ============================================================================================ feridas (zumbi)
FERIDAS = [  # (x, y, z, raio, tipo)  tipo: 0 carne exposta, 1 rasgo de pano com sangue
    (0.075, 1.335, -0.108, 0.040, 1), (-0.060, 1.150, -0.098, 0.032, 1), (0.195, 1.40, -0.02, 0.034, 0),
    (-0.090, 1.250, 0.100, 0.034, 1), (0.100, 0.500, -0.062, 0.038, 0), (-0.098, 0.700, -0.070, 0.030, 1),
    (0.260, 1.330, -0.052, 0.030, 0), (-0.255, 1.050, 0.040, 0.026, 0), (0.060, 1.610, -0.080, 0.016, 0),
]


def _campo_feridas(campo, y0, y1):
    for (x, y, z, r, t) in FERIDAS:
        if y0 - r <= y <= y1 + r:
            campo.bossa(x, y, z, r * 0.7, r * 0.7, r * 0.7, -r * 0.30)


def _dist_feridas(P):
    """Para cada ponto: menor distancia normalizada (d/r) a uma ferida e o tipo dela."""
    best = np.full(P.shape[:-1], 9.0, np.float32)
    tipo = np.zeros(P.shape[:-1], np.int32)
    for (x, y, z, r, t) in FERIDAS:
        d = np.sqrt((P[..., 0] - x) ** 2 + (P[..., 1] - y) ** 2 + (P[..., 2] - z) ** 2) / r
        m = d < best
        best = np.where(m, d, best)
        tipo = np.where(m, t, tipo)
    return best, tipo


# ============================================================================================ formas
def _aneis_cabeca(z):
    return [  # y, cx, cz, rx, rz, n
        (1.490, 0.0, 0.012, 0.050, 0.054, 2.0), (1.540, 0.0, 0.010, 0.048, 0.052, 2.0),
        (1.562, 0.0, 0.004, 0.050, 0.070, 2.2), (1.575, 0.0, 0.000, 0.054, 0.080, 2.2),
        (1.590, 0.0, 0.002, 0.061, 0.088, 2.3), (1.612, 0.0, 0.004, 0.068, 0.093, 2.3),
        (1.640, 0.0, 0.008, 0.074, 0.097, 2.2), (1.670, 0.0, 0.010, 0.077, 0.100, 2.1),
        (1.700, 0.0, 0.012, 0.078, 0.102, 2.1), (1.735, 0.0, 0.012, 0.077, 0.101, 2.1),
        (1.765, 0.0, 0.012, 0.070, 0.093, 2.1), (1.790, 0.0, 0.012, 0.052, 0.074, 2.0),
        (1.806, 0.0, 0.012, 0.022, 0.032, 2.0)]


def _aneis_tronco():
    return [(0.84, 0, 0.000, 0.146, 0.092, 2.3), (0.88, 0, 0.000, 0.158, 0.097, 2.3),
            (0.95, 0, 0.000, 0.170, 0.102, 2.3), (1.02, 0, 0.000, 0.152, 0.096, 2.3),
            (1.10, 0, -0.001, 0.150, 0.093, 2.3), (1.20, 0, -0.001, 0.160, 0.097, 2.3),
            (1.30, 0, -0.003, 0.180, 0.104, 2.4), (1.38, 0, -0.004, 0.198, 0.108, 2.4),
            (1.44, 0, -0.002, 0.204, 0.100, 2.5), (1.475, 0, 0.004, 0.150, 0.082, 2.3),
            (1.495, 0, 0.010, 0.082, 0.066, 2.1), (1.507, 0, 0.012, 0.060, 0.060, 2.0),
            (1.514, 0, 0.012, 0.056, 0.056, 2.0)]


def _aneis_braco(s):
    x = 0.246 * s
    return [(1.505, 0.214 * s, 0, 0.050, 0.050, 2), (1.475, 0.234 * s, 0, 0.064, 0.064, 2),
            (1.43, x, 0, 0.060, 0.060, 2), (1.35, x, 0, 0.052, 0.054, 2), (1.28, x, 0, 0.050, 0.052, 2),
            (1.21, x, 0, 0.047, 0.047, 2), (1.15, x, 0, 0.047, 0.046, 2), (1.08, x, 0, 0.040, 0.040, 2),
            (0.96, x, 0, 0.035, 0.034, 2), (0.86, x, 0, 0.027, 0.026, 2), (0.79, x, 0, 0.026, 0.024, 2)]


def _aneis_perna(s):
    x = 0.095 * s
    return [(0.115, x, 0.012, 0.050, 0.054, 2), (0.20, x, 0.012, 0.052, 0.057, 2), (0.34, x, 0.010, 0.056, 0.062, 2),
            (0.46, x, 0.000, 0.055, 0.060, 2), (0.52, x, 0.000, 0.058, 0.062, 2), (0.62, x, 0.000, 0.068, 0.072, 2),
            (0.74, x, 0.000, 0.078, 0.080, 2), (0.86, x, 0.000, 0.086, 0.090, 2), (0.93, x, 0.000, 0.088, 0.092, 2)]


# ============================================================================================ construcao
class Personagem:
    def __init__(self, estilo):
        self.estilo = estilo
        self.z = estilo == "zumbi"
        self.cor = COR[estilo]
        self.lod = 0.58 if self.z else 1.0
        self.m = Malha()
        self.tubos = {}
        self.atlas = np.zeros((kit.ATLAS, kit.ATLAS, 3), np.float32)

    def ns(self, n):
        return max(8, int(round(n * self.lod)))

    def rows(self, n, t0=0.0, t1=1.0):
        return np.linspace(t0, t1, max(3, int(round(n * self.lod))))

    # ------------------------------------------------------------------------------------ cabeca
    def cabeca(self):
        m, z = self.m, self.z
        c = Campo()
        # rosto esculpido (bossas/covas gaussianas; frente = -Z)
        c.bossa(0, 1.676, -0.096, .0060, .024, .022, .0070)                 # ponte do nariz
        c.bossa(0.032, 1.712, -0.090, .022, .008, .02, .0050, True)         # arcada
        c.bossa(0.033, 1.692, -0.088, .016, .0125, .02, -.0090 if not z else -.016, True)  # orbita
        c.bossa(0.054, 1.658, -0.075, .019, .015, .026, .0050 if not z else -.006, True)  # macas
        c.bossa(0.0, 1.628, -0.092, .008, .010, .02, -.0025)                # filtro
        c.bossa(0.024, 1.606, -0.088, .0065, .0065, .02, -.0030, True)      # cantos da boca
        c.bossa(0.0, 1.580, -0.087, .015, .011, .02, .0065)                 # queixo
        c.bossa(0.0, 1.596, -0.090, .013, .005, .02, -.0030)                # sulco do queixo
        c.bossa(0.062, 1.598, -0.020, .012, .02, .026, .0045, True)         # angulo da mandibula
        c.bossa(0.075, 1.730, -0.040, .015, .02, .03, -.003, True)          # tempora
        c.bossa(0.0, 1.700, 0.115, .04, .04, .03, .0045)                    # occipital
        c.bossa(0.0, 1.525, -0.052, .010, .012, .02, .0055)                 # pomo de adao
        c.bossa(0.027, 1.515, -0.044, .007, .026, .02, .0032, True)         # esternocleidomastoideo
        c.bossa(0.04, 1.508, 0.04, .012, .02, .03, .003, True)              # trapezio
        if z:
            c.bossa(0.0, 1.600, -0.092, .024, .013, .03, -.020)             # boca aberta
            c.bossa(0.058, 1.630, -0.060, .02, .03, .04, -.007, True)       # bochechas fundas
            _campo_feridas(c, 1.49, 1.82)
        t = Tubo(_aneis_cabeca(0), R["cabeca"], c)
        self.tubos["cabeca"] = t
        t.malha(m, "cabeca", self.ns(52), self.rows(40), tampa_topo=True, tampa_base=True, nome="cabeca")
        tp = TILE["pele"]
        # olhos: globo + palpebras
        for s in (-1, 1):
            cx = 0.033 * s
            elipsoide(m, "cabeca", (cx, 1.692, -0.0745), (0.0122, 0.0122, 0.0122), nu=12, nv=8, nome="olho",
                      uv_fn=lambda P, U: uv_atlas(R["olho"], 0.5 + P[..., 0] / 0.0244,
                                                  0.5 + P[..., 1] / 0.0244))
            elipsoide(m, "cabeca", (cx, 1.692, -0.0745), (0.0131, 0.0131, 0.0131), tile=tp, nu=12, nv=4,
                      lat=(10.0, 78.0), lon=(-62.0, 62.0), nome="palpebra")
            elipsoide(m, "cabeca", (cx, 1.692, -0.0745), (0.0126, 0.0126, 0.0126), tile=tp, nu=12, nv=3,
                      lat=(-58.0, -14.0), lon=(-58.0, 58.0), nome="palpebra")
            # sobrancelha
            pts = [(cx - 0.020 * 1, 1.7095, -0.0935), (cx - 0.008, 1.7145, -0.0965), (cx + 0.008, 1.7155, -0.0965),
                   (cx + 0.021, 1.7145, -0.0925)]
            if s < 0:
                pts = [(cx + 0.020, 1.7095, -0.0935), (cx + 0.008, 1.7145, -0.0965), (cx - 0.008, 1.7155, -0.0965),
                       (cx - 0.021, 1.7145, -0.0925)]
            if not z:
                tubo_caminho(m, "cabeca", pts, [0.0022, 0.0034, 0.0034, 0.0016], tile=TILE["cabelo_t"], nseg=6, nn=8,
                             nome="sobrancelha", achatar=(0.55, (0, 0, 1)))
            # orelha
            elipsoide(m, "cabeca", (0.082 * s, 1.684, 0.014), (0.0075, 0.029, 0.019), tile=tp,
                      rot=rotz(-10 * s) @ roty(-14 * s), nu=12, nv=10, nome="orelha")
            elipsoide(m, "cabeca", (0.078 * s, 1.678, 0.004), (0.0045, 0.0065, 0.005), tile=tp, nu=8, nv=6, nome="orelha")
        # nariz
        elipsoide(m, "cabeca", (0, 1.646, -0.0965), (0.0082, 0.0082, 0.0100), tile=tp, nu=12, nv=8, nome="nariz")
        for s in (-1, 1):
            elipsoide(m, "cabeca", (0.0108 * s, 1.637, -0.0925), (0.0068, 0.0074, 0.0080), tile=tp, nu=10, nv=7, nome="nariz")
            elipsoide(m, "cabeca", (0.0062 * s, 1.6355, -0.1010), (0.0028, 0.0018, 0.0032), tile=TILE["escuro"], nu=8, nv=5,
                      nome="narina")
        # labios
        tl = TILE["labio"]
        xs = np.linspace(-0.0225, 0.0225, 7)
        sup = [(x, 1.6150 + 0.0022 * (1 - abs(x) / 0.0225) * (1.0 if abs(x) > 0.004 else 0.4), -0.0905 + (x / 0.0225) ** 2 * 0.011)
               for x in xs]
        inf = [(x, 1.6035 - 0.0010 * (1 - (x / 0.0225) ** 2), -0.0915 + (x / 0.0225) ** 2 * 0.011) for x in xs]
        rl = 0.0031 if not z else 0.0022
        if not z:
            tubo_caminho(m, "cabeca", sup, [rl * 0.5, rl, rl * 1.1, rl * 1.0, rl * 1.1, rl, rl * 0.5], tile=tl, nseg=8,
                         nn=14, nome="labio")
            tubo_caminho(m, "cabeca", inf, [rl * 0.5, rl * 1.1, rl * 1.3, rl * 1.3, rl * 1.3, rl * 1.1, rl * 0.5], tile=tl,
                         nseg=8, nn=14, nome="labio")
        else:
            tubo_caminho(m, "cabeca", [(x, 1.6190, -0.0895 + (x / 0.0225) ** 2 * 0.011) for x in xs], [rl] * 7,
                         tile=tl, nseg=6, nn=12, nome="labio")
            tubo_caminho(m, "cabeca", [(x, 1.5890, -0.0895 + (x / 0.0225) ** 2 * 0.011) for x in xs], [rl] * 7,
                         tile=tl, nseg=6, nn=12, nome="labio")
            # dentes
            for k, x in enumerate(np.linspace(-0.0185, 0.0185, 8)):
                caixa(m, "cabeca", (x, 1.6135, -0.0805 + (x / 0.0225) ** 2 * 0.011), (0.0042, 0.0068, 0.0030), TILE["dente"],
                      nome="dente")
                caixa(m, "cabeca", (x, 1.5940, -0.0800 + (x / 0.0225) ** 2 * 0.011), (0.0040, 0.0058, 0.0030), TILE["dente"],
                      nome="dente")
        self.cabelo()

    def cabelo(self):
        m, z = self.m, self.z
        rs = np.random.RandomState(11 if not z else 5)
        n = 64 if not z else 22
        reg = R["cabelo"]
        # calota do couro cabeludo (tira em torno do topo)
        t = self.tubos["cabeca"]
        remendo(m, t, "cabeca", 0.0, 2 * math.pi, 1.776, 1.806, 0.0055, 44, 4, uv_modo="tile", tile=reg, saia=False,
                fecha=True, nome="cabelo")
        for (ta, tb_) in ((-0.62 * math.pi, 0.62 * math.pi),):
            remendo(m, t, "cabeca", ta + 2 * math.pi, tb_ + 2 * math.pi, 1.690, 1.780, 0.0050, 30, 7, uv_modo="tile",
                    tile=reg, saia=False, nome="cabelo")
        g = np.array([0.0, -1.0, 0.0])
        for k in range(n):
            tipo = rs.choice(["topo", "franja", "lado", "nuca"], p=[0.40, 0.22, 0.22, 0.16])
            if tipo == "topo":
                th, y0 = rs.uniform(0, 2 * math.pi), rs.uniform(1.775, 1.800)
            elif tipo == "franja":
                th, y0 = math.pi + rs.uniform(-0.55, 0.55), rs.uniform(1.775, 1.795)
            elif tipo == "lado":
                th, y0 = rs.choice([1, -1]) * rs.uniform(0.38, 0.62) * math.pi + math.pi, rs.uniform(1.70, 1.76)
            else:
                th, y0 = rs.uniform(-0.25, 0.25) % (2 * math.pi), rs.uniform(1.665, 1.70)
            P0, rad = t.ponto(th, y0, 0.004)
            P0, rad = P0.reshape(3), rad.reshape(3)
            L = rs.uniform(0.016, 0.032) * (0.7 if z else 1.0)
            if tipo == "franja":
                d1 = norm(np.array([0.0, 0.5, -1.0]) * 0.8 + rad * 0.3)
                d2 = norm(d1 * 0.7 + g * 0.55)
            elif tipo == "topo":
                d1 = norm(rad * 0.6 + np.array([0.0, 0.8, 0.0]) + rs.normal(0, 0.25, 3))
                d2 = norm(d1 * 0.5 + rad * 0.6 + g * 0.55)
            elif tipo == "lado":
                d1 = norm(rad * 0.5 + np.array([0.0, 0.2, 0.0]))
                d2 = norm(d1 * 0.4 + g * 1.1 + rad * 0.3)
            else:
                d1 = norm(rad * 0.6 + g * 0.5)
                d2 = norm(d1 * 0.5 + g * 1.0 + rad * 0.2)
            p1 = P0 + d1 * L * 0.5
            p2 = p1 + d2 * L * 0.6
            p3 = p2 + norm(d2 + g * 0.5) * L * 0.4
            tubo_caminho(m, "cabeca", [P0, p1, p2, p3], [0.0090, 0.0080, 0.0052, 0.0012], uv_regiao=reg, nseg=5, nn=7,
                         tampas=False, nome="cabelo", angulo=80.0)

    # ------------------------------------------------------------------------------------ tronco
    def tronco(self):
        m, z = self.m, self.z
        c = Campo()
        c.bossa(0.055, 1.340, -0.100, .045, .035, .04, .0080, True)   # peitoral
        c.bossa(0.0, 1.250, -0.095, .02, .12, .04, .0030)             # esterno / linha media
        c.bossa(0.05, 1.150, -0.092, .040, .040, .04, .0030, True)    # abdomen
        c.bossa(0.080, 1.330, 0.095, .05, .06, .04, .0070, True)      # omoplatas
        c.bossa(0.0, 1.250, 0.100, .012, .12, .04, -.0035)            # coluna
        c.bossa(0.065, 0.920, 0.098, .06, .05, .05, .0100, True)      # gluteos
        c.bossa(0.0, 1.500, 0.060, .06, .03, .05, .0050)              # nuca / trapezio
        c.bossa(0.0, 1.30, -0.04, .24, .24, .20, 0.0)                 # (ancora)

        def dobras(P, th, y):
            # vincos de tecido: cintura (sobra de camisa por dentro da calca), axilas, costas
            env = np.exp(-((y - 1.095) / 0.045) ** 2)
            f = 0.0070 * env * np.sin(9 * th + 18.0 * y + 0.6 * np.sin(5 * th))
            env2 = np.exp(-((y - 1.30) / 0.06) ** 2) * (np.abs(np.sin(th)) > 0.7)
            f += 0.0050 * env2 * np.sin(24.0 * y + 3 * th)
            f += 0.0040 * np.exp(-((y - 1.20) / 0.09) ** 2) * np.sin(13 * th + 30 * y)
            return f * (0.6 if not z else 1.2)
        c.fn(dobras)
        if z:
            _campo_feridas(c, 0.80, 1.54)
        t = Tubo(_aneis_tronco(), R["tronco"], c)
        self.tubos["tronco"] = t
        t.malha(m, "tronco", self.ns(46), self.rows(46), tampa_topo=True, tampa_base=True, nome="tronco")
        tb = TILE["botao"]
        if not z:
            # botoes e casa da camisa
            for y in (1.475, 1.395, 1.315, 1.235, 1.155, 1.075):
                P, rad = t.ponto(math.pi, y, 0.0035)
                elipsoide(m, "tronco", P + np.array([0, 0, -0.0012]), (0.0058, 0.0058, 0.0030), tile=tb, nu=8, nv=4,
                          nome="botao")
            # pestana da camisa (listra levantada)
            remendo(m, t, "tronco", math.pi - 0.075, math.pi + 0.075, 1.045, 1.495, 0.0028, 2, 22, nome="placa", saia=True)
            # bolsos do peito com aba
            for s in (-1, 1):
                th0 = math.pi + s * 0.52
                remendo(m, t, "tronco", th0 - 0.22, th0 + 0.22, 1.295, 1.360, 0.0034, 6, 5, nome="bolso")
                remendo(m, t, "tronco", th0 - 0.23, th0 + 0.23, 1.335, 1.372, 0.0058, 6, 2, nome="aba")
            # cinto
            remendo(m, t, "tronco", 0, 2 * math.pi, 1.000, 1.037, 0.0055, 56, 3, uv_modo="tile", tile=TILE["couro"], fecha=True,
                    nome="cinto")
            # passadores
            for th in (math.pi * 0.62, math.pi * 0.82, math.pi * 1.18, math.pi * 1.38, 0.15, 2 * math.pi - 0.15):
                remendo(m, t, "tronco", th - 0.045, th + 0.045, 0.994, 1.043, 0.0090, 3, 3, uv_modo="tile", tile=TILE["couro"],
                        nome="passador")
            # fivela
            Pf, _ = t.ponto(math.pi, 1.0185, 0.0105)
            caixa(m, "tronco", Pf + np.array([0, 0, -0.002]), (0.044, 0.034, 0.0045), TILE["metal"], nome="fivela")
            caixa(m, "tronco", Pf + np.array([0, 0, -0.0035]), (0.030, 0.021, 0.0035), TILE["couro"], nome="fivela")
            caixa(m, "tronco", Pf + np.array([0.003, 0, -0.0055]), (0.0045, 0.020, 0.0030), TILE["metal"], nome="fivela")
            # ponta do cinto
            remendo(m, t, "tronco", math.pi + 0.20, math.pi + 0.40, 0.995, 1.030, 0.0100, 4, 2, uv_modo="tile",
                    tile=TILE["couro"], nome="cinto")
            # bolsos de tras (jeans) e botao da calca
            for s in (-1, 1):
                th0 = (s * 0.40) % (2 * math.pi)
                remendo(m, t, "tronco", th0 - 0.19, th0 + 0.19, 0.860, 0.955, 0.0034, 8, 6, nome="bolso_tras")
            P, _ = t.ponto(math.pi, 0.965, 0.0040)
            elipsoide(m, "tronco", P, (0.0075, 0.0075, 0.0030), tile=TILE["metal"], nu=10, nv=6, nome="botao")
        else:
            remendo(m, t, "tronco", 0, 2 * math.pi, 1.000, 1.032, 0.0050, 56, 2, uv_modo="tile", tile=TILE["couro"],
                    fecha=True, nome="cinto")
            Pf, _ = t.ponto(math.pi, 1.016, 0.0095)
            caixa(m, "tronco", Pf + np.array([0, 0, -0.002]), (0.040, 0.030, 0.0045), TILE["metal"], nome="fivela")
            for th in (math.pi * 0.62, math.pi * 1.38, 0.15, 2 * math.pi - 0.15):
                remendo(m, t, "tronco", th - 0.045, th + 0.045, 0.994, 1.040, 0.0085, 3, 3, uv_modo="tile", tile=TILE["couro"],
                        nome="passador")
        # gola
        self.gola()

    def gola(self):
        m, z = self.m, self.z
        t = self.tubos["tronco"]
        tile = TILE["camisa"]
        nth, nr = 36, 4
        ths = np.linspace(0, 2 * math.pi, nth + 1)
        rows = np.linspace(0, 1, nr + 1)
        TH, T_ = np.meshgrid(ths, rows)
        # faixa baixa na frente (V), alta atras
        frente = np.exp(-((np.abs(((TH - math.pi) + math.pi) % (2 * math.pi) - math.pi)) / 0.55) ** 2)
        if not z:
            ybot = 1.468 - 0.040 * frente
            ytop = 1.512 - 0.006 * frente + 0.010 * (1 - frente)
        else:
            ybot = 1.470 - 0.030 * frente
            ytop = 1.505 - 0.012 * frente
        Y = ybot + T_ * (ytop - ybot)
        P, rad = t.ponto(TH, Y, 0.0)
        # a gola abre para fora e a parte de cima dobra
        flare = 0.004 + 0.012 * T_ ** 1.5 * (0.5 + 0.5 * frente) + 0.003
        P = P + rad * flare[..., None]
        # abertura frontal: some a gola onde a pestana divide
        UV = np.tile(uv_atlas(tile, 0.5, 0.5), (P.shape[0] * P.shape[1], 1))
        idx = np.arange((nr + 1) * (nth + 1)).reshape(nr + 1, nth + 1)
        T = []
        for i in range(nr):
            for j in range(nth):
                if abs(ths[j] - math.pi) < 0.07 and not z:
                    continue
                a, b, c, d = idx[i, j], idx[i, j + 1], idx[i + 1, j + 1], idx[i + 1, j]
                T += [[a, b, c], [a, c, d]]
        m.add("tronco", P.reshape(-1, 3), UV, T, rad.reshape(-1, 3), nome="gola")

    # ------------------------------------------------------------------------------------ bracos
    def braco(self, s):
        m, z = self.m, self.z
        nome = "braco_esq" if s < 0 else "braco_dir"
        c = Campo()
        x = 0.246 * s
        c.bossa(x, 1.44, 0.0, .04, .05, .06, .004)                    # deltoide
        c.bossa(x, 1.34, -0.04, .03, .07, .03, .0040)                 # biceps
        c.bossa(x, 1.34, 0.04, .03, .06, .03, .0040)                  # triceps
        c.bossa(x, 1.20, 0.05, .02, .03, .03, .0040)                  # cotovelo
        c.bossa(x, 0.99, -0.02, .03, .08, .03, .0040)                 # musculo do antebraco
        c.bossa(x, 0.84, 0.0, .02, .015, .03, .0030)                  # punho (ulna)

        def dobras(P, th, y):
            env = np.exp(-((y - 1.20) / 0.07) ** 2)
            f = 0.0060 * env * np.sin(14 * th + 9 * y)
            f += 0.0040 * np.exp(-((y - 1.33) / 0.07) ** 2) * np.sin(11 * th + 40 * y)
            f += 0.0030 * np.exp(-((y - 1.40) / 0.06) ** 2) * np.sin(10 * th + 30 * y) * (np.cos(th) > 0)
            return f
        c.fn(dobras)
        if z:
            _campo_feridas(c, 0.78, 1.52)
        t = Tubo(_aneis_braco(s), R[nome], c)
        self.tubos[nome] = t
        t.malha(m, nome, self.ns(24), self.rows(34), tampa_topo=True, tampa_base=True, nome="braco")
        if not z:  # punho arregacado (dobra de tecido)
            remendo(m, t, nome, 0, 2 * math.pi, 1.100, 1.165, 0.0100, 24, 3, uv_modo="tile", tile=TILE["camisa"], fecha=True,
                    nome="punho")
            remendo(m, t, nome, 0, 2 * math.pi, 1.165, 1.178, 0.0042, 24, 1, uv_modo="tile", tile=TILE["camisa"], fecha=True,
                    nome="punho")
        self.mao(s)

    def mao(self, s):
        m, z = self.m, self.z
        nome = "braco_esq" if s < 0 else "braco_dir"
        x = 0.246 * s
        reg = R["mao_esq" if s < 0 else "mao_dir"]
        # palma: largura em Z (rz), espessura em X (rx); a palma olha para dentro (-X para a mao direita)
        ac = [(0.830, x, 0.0, 0.0255, 0.0240, 2), (0.805, x, 0.0, 0.0240, 0.0255, 2), (0.785, x, 0.0, 0.0220, 0.0295, 2),
              (0.760, x, 0.0, 0.0205, 0.0335, 2), (0.735, x, 0.0, 0.0190, 0.0362, 2), (0.715, x, 0.0, 0.0175, 0.0372, 2.1),
              (0.700, x, 0.0, 0.0145, 0.0335, 2.1)]
        c = Campo()
        sin_ = -s
        c.bossa(x + sin_ * 0.014, 0.765, -0.030, .014, .022, .02, .0050)     # eminencia do polegar
        c.bossa(x - sin_ * 0.012, 0.745, 0.0, .014, .025, .03, .0020)        # dorso
        t = Tubo(ac, reg, c)
        t.malha(m, nome, self.ns(16), np.linspace(0, 1, 11), tampa_base=True, tampa_topo=True, nome="mao")
        tile = TILE["pele"]
        # dedos: curvam para dentro (sin_) em torno de um eixo Z (pegada de arma)
        dedos = [(-0.0285, (0.034, 0.024, 0.021), 0.0090), (-0.0095, (0.038, 0.027, 0.022), 0.0092),
                 (0.0095, (0.034, 0.025, 0.021), 0.0088), (0.0275, (0.026, 0.019, 0.017), 0.0078)]
        for zf, comp, r in dedos:
            ang = [0.0, 24.0, 24.0 + 48.0, 24.0 + 48.0 + 38.0]
            p = np.array([x + sin_ * 0.003, 0.714, zf])
            pts = [p]
            for k in range(3):
                a = math.radians((ang[k] + ang[k + 1]) / 2.0)
                d = np.array([sin_ * math.sin(a), -math.cos(a), 0.0])
                p = p + d * comp[k]
                pts.append(p)
            tubo_caminho(m, nome, pts, [r, r * 0.95, r * 0.86, r * 0.70], tile=tile, nseg=7, nn=8, nome="dedo")
            # unha na ponta (dorso do dedo)
            pe, pu = pts[-1], pts[-2]
            elipsoide(m, nome, pe + np.array([-sin_ * 0.0042, 0.0012, 0.0]) , (0.0012, 0.0058, 0.0050), tile=TILE["unha"],
                      nu=6, nv=3, nome="unha")
        # polegar
        base = np.array([x + sin_ * 0.014, 0.776, -0.030])
        d0 = norm(np.array([sin_ * 0.35, -0.55, -0.75]))
        d1 = norm(np.array([sin_ * 0.55, -0.70, -0.45]))
        d2 = norm(np.array([sin_ * 0.80, -0.50, -0.30]))
        p1 = base + d0 * 0.030
        p2 = p1 + d1 * 0.026
        p3 = p2 + d2 * 0.022
        tubo_caminho(m, nome, [base, p1, p2, p3], [0.0105, 0.0095, 0.0082, 0.0068], tile=tile, nseg=7, nn=8, nome="polegar")

    # ------------------------------------------------------------------------------------ pernas e botas
    def perna(self, s):
        m, z = self.m, self.z
        nome = "perna_esq" if s < 0 else "perna_dir"
        x = 0.095 * s
        c = Campo()
        c.bossa(x, 0.78, -0.060, .05, .08, .03, .0050)                # quadriceps
        c.bossa(x, 0.50, -0.060, .03, .03, .03, .0060)                # patela
        c.bossa(x, 0.34, 0.058, .04, .07, .03, .0070)                 # panturrilha
        c.bossa(x, 0.72, 0.075, .06, .08, .04, .0040)                 # posterior

        def dobras(P, th, y):
            f = 0.0080 * np.exp(-((y - 0.50) / 0.05) ** 2) * np.sin(12 * th + 25 * y) * (np.cos(th) > -0.2)   # dobras do joelho
            f += 0.0060 * np.exp(-((y - 0.55) / 0.06) ** 2) * (np.cos(th) > 0.3) * np.sin(30 * y)           # atras do joelho
            f += 0.0070 * np.exp(-((y - 0.19) / 0.075) ** 2) * (0.6 + 0.4 * np.sin(7 * th)) * np.sin(55 * y + 3 * th)  # barra empilhada
            f += 0.0032 * np.exp(-((y - 0.70) / 0.2) ** 2) * np.sin(9 * th + 14 * y + 1.3 * np.sin(4 * th))
            f += 0.0060 * np.exp(-((y - 0.90) / 0.05) ** 2) * np.sin(10 * th + 20 * y)                      # virilha
            return f * (0.8 if not z else 1.2)
        c.fn(dobras)
        if z:
            _campo_feridas(c, 0.10, 0.94)
        t = Tubo(_aneis_perna(s), R[nome], c)
        self.tubos[nome] = t
        t.malha(m, nome, self.ns(30), self.rows(44), tampa_topo=True, tampa_base=False, nome="perna")
        self.bota(s)

    def bota(self, s):
        m, z = self.m, self.z
        nome = "perna_esq" if s < 0 else "perna_dir"
        x = 0.095 * s
        tb = TILE["bota"]
        # cano (dentro da barra da calca)
        cano = Tubo([(0.050, x, 0.014, 0.0455, 0.0475, 2.0), (0.090, x, 0.012, 0.0470, 0.0500, 2.0),
                     (0.130, x, 0.012, 0.0460, 0.0490, 2.0), (0.150, x, 0.012, 0.0440, 0.0470, 2.0)],
                    R["cano_esq" if s < 0 else "cano_dir"])
        self.tubos["cano_" + ("esq" if s < 0 else "dir")] = cano
        cano.malha(m, nome, self.ns(20), np.linspace(0, 1, 5), tampa_topo=True, nome="cano")
        # pe (secoes ao longo de Z)
        zs = np.array([-0.140, -0.128, -0.108, -0.085, -0.060, -0.030, 0.000, 0.030, 0.052, 0.066])
        wx = np.array([0.014, 0.030, 0.042, 0.047, 0.047, 0.045, 0.044, 0.042, 0.038, 0.028])
        yt = np.array([0.026, 0.050, 0.064, 0.074, 0.088, 0.100, 0.106, 0.096, 0.078, 0.046])
        self._casca(m, nome, x, zs, wx, yt, 0.012, R["pe_esq" if s < 0 else "pe_dir"], 20, 3.0, "pe")
        # sola (laje) e salto
        zs2 = np.array([-0.146, -0.134, -0.110, -0.060, 0.000, 0.040, 0.062, 0.074])
        wx2 = np.array([0.016, 0.034, 0.046, 0.050, 0.047, 0.044, 0.040, 0.030])
        yt2 = np.array([0.018, 0.024, 0.026, 0.026, 0.026, 0.026, 0.026, 0.020])
        self._casca(m, nome, x, zs2, wx2, yt2, 0.0, TILE["sola"], 14, 4.0, "sola", tile=True)
        caixa(m, nome, (x, 0.020, 0.040), (0.074, 0.030, 0.050), TILE["sola"], nome="salto")
        # cadarcos (cruzados sobre o peito do pe) e ilhoses
        rs = np.random.RandomState(3)
        for k, zz in enumerate(np.linspace(-0.088, -0.036, 5)):
            y_top = float(np.interp(zz, zs, yt))
            ox = 0.027
            ya = self._ytop_casca(zz, zs, wx, yt, ox, 0.012) + 0.0016
            yb = y_top + 0.0030
            z2 = zz + 0.010
            yb2 = self._ytop_casca(z2, zs, wx, yt, 0.0, 0.012) + 0.0030
            ya2 = self._ytop_casca(z2, zs, wx, yt, ox, 0.012) + 0.0016
            tubo_caminho(m, nome, [(x - ox, ya, zz), (x, yb, (zz + z2) / 2), (x + ox, ya2, z2)], [0.0021] * 3, tile=TILE["cadarco"],
                         nseg=5, nn=6, nome="cadarco")
            tubo_caminho(m, nome, [(x + ox, ya, zz), (x, yb, (zz + z2) / 2), (x - ox, ya2, z2)], [0.0021] * 3, tile=TILE["cadarco"],
                         nseg=5, nn=6, nome="cadarco")
        # pontas soltas do cadarco
        tubo_caminho(m, nome, [(x + 0.002, 0.108, -0.034), (x + 0.010, 0.114, -0.040), (x + 0.020, 0.108, -0.052)], [0.0021] * 3,
                     tile=TILE["cadarco"], nseg=5, nn=6, nome="cadarco")

    def _ytop_casca(self, zz, zs, wx, yt, ox, yb):
        w = float(np.interp(zz, zs, wx))
        h = float(np.interp(zz, zs, yt))
        yc, hy = (h + yb) / 2, (h - yb) / 2
        q = min(abs(ox) / w, 0.999)
        return yc + hy * (1 - q ** 3) ** (1 / 3)

    def _casca(self, m, nome, cx, zs, wx, yt, ybot, regiao, nphi, n, rotulo, tile=False):
        """Superficie por secoes (superelipse em XY) ao longo de Z."""
        zz = np.linspace(zs[0], zs[-1], max(8, int(len(zs) * 2 * (1.0 if self.lod > 0.8 else 0.75))))
        W = np.interp(zz, zs, wx)
        H = np.interp(zz, zs, yt)
        phi = np.linspace(0, 2 * math.pi, nphi + 1)
        sp, cp = np.sin(phi), np.cos(phi)
        e = 2.0 / n
        X = cx + W[:, None] * kit._sp(sp, e)[None, :]
        yc = (H + ybot) / 2
        hy = (H - ybot) / 2
        Y = yc[:, None] + hy[:, None] * kit._sp(cp, e)[None, :]
        Z = np.tile(zz[:, None], (1, nphi + 1))
        P = np.stack([X, Y, Z], -1)
        Vo = np.stack([X - cx, Y - yc[:, None], np.zeros_like(X)], -1)
        # fecha as pontas (afunila no primeiro/ultimo anel ja pequeno): so cola um ponto central
        nr = len(zz)
        if tile:
            UV = np.tile(uv_atlas(regiao, 0.5, 0.5), (nr * (nphi + 1), 1)).reshape(nr, nphi + 1, 2)
        else:
            uu = (phi / (2 * math.pi))[None, :] + 0 * zz[:, None]
            vv = (1 - (zz - zs[0]) / (zs[-1] - zs[0]))[:, None] + 0 * phi[None, :]
            UV = uv_atlas(regiao, uu, vv)
        idx = np.arange(nr * (nphi + 1)).reshape(nr, nphi + 1)
        T = []
        for i in range(nr - 1):
            for j in range(nphi):
                a, b, c, d = idx[i, j], idx[i, j + 1], idx[i + 1, j + 1], idx[i + 1, j]
                T += [[a, b, c], [a, c, d]]
        Pf, Uf, Vf = [P.reshape(-1, 3)], [UV.reshape(-1, 2)], [Vo.reshape(-1, 3)]
        ex_ = nr * (nphi + 1)
        for i, vo in ((0, (0, 0, -1)), (nr - 1, (0, 0, 1))):
            Pf.append(np.tile(P[i].mean(0), (nphi + 1, 1)))
            Uf.append(UV[i])
            Vf.append(np.tile(np.array(vo, float), (nphi + 1, 1)))
            for j in range(nphi):
                T.append([idx[i, j], idx[i, j + 1], ex_ + j + 1])
                T.append([idx[i, j], ex_ + j + 1, ex_ + j])
            ex_ += nphi + 1
        m.add(nome, np.vstack(Pf), np.vstack(Uf), T, np.vstack(Vf), nome=rotulo)
        self._casca_geom = (zs, wx, yt, ybot)

    def tudo(self):
        self.cabeca()
        self.tronco()
        for s in (-1, 1):
            self.braco(s)
            self.perna(s)
        return self
