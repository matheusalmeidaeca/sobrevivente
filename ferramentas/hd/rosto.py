# ferramentas/hd/rosto.py
"""F13: CABECA REALISTA do protagonista, 100% por codigo (numpy).

O que muda em relacao a cabeca antiga (personagem.Personagem.cabeca):
  - o rosto e UMA superficie continua esculpida (nariz, labios, palpebras, queixo, macas, mandibula):
    nada de esferas e tubinhos colados;
  - as palpebras tem uma FENDA de verdade; o globo ocular fica atras dela;
  - orelhas esculpidas (helice, anti-helice, concha, tragus, lobulo);
  - cabelo = massa com volume (casca com mechas em relevo) + mechas achatadas soltas na borda e no topo;
  - a cabeca tem TEXTURA PROPRIA de 1024x1024 (`textura_rosto.png`): ~3 pixels por milimetro no rosto.

Sistema do jogo: Y para cima, frente = -Z, direita = +X. Theta do tubo: 0 = costas, pi = frente.
Grupo de pele: "cabeca" (pesos pescoco/cabeca calculados em animacao.pesos_do_vertice pela altura)."""
import math

import numpy as np

import kit
from kit import Campo, Tubo, elipsoide, tubo_caminho, uv_atlas, suave, norm, fbm, ruido

PI = math.pi
ATLAS_ROSTO = 1024
ARQUIVO_TEXTURA = "textura_rosto.png"

# regioes do atlas da cabeca (x0, y0, largura, altura) em pixels
RR = {
    "pele": (0, 0, 1024, 704),
    "cabelo": (0, 704, 512, 320),
    "orelha_esq": (512, 704, 160, 256), "orelha_dir": (672, 704, 160, 256),
    "olho": (832, 704, 192, 192),
    "cilio": (832, 896, 64, 64), "cab_t": (896, 896, 64, 64), "boca": (960, 896, 64, 64),
}

# ---------------------------------------------------------------------------------------------- medidas (metros)
Y_BASE, Y_TOPO = 1.490, 1.808
OLHO_X, OLHO_Y, OLHO_Z = 0.0318, 1.6925, -0.0655
R_GLOBO, R_PALP = 0.0122, 0.0136
NARIZ_NASION, NARIZ_PONTA = 1.6950, 1.6490
BOCA_Y, BOCA_L = 1.6100, 0.0218
COR_PELE = (208, 172, 150)
COR_CABELO = (26, 23, 24)

# aneis da cabeca + pescoco: (y, cx, cz, rx, rz, expoente). Frente = cz - rz, nuca = cz + rz.
ANEIS = [
    (1.490, 0.0, 0.0040, 0.0475, 0.0480, 2.00),
    (1.530, 0.0, 0.0045, 0.0462, 0.0505, 2.00),
    (1.553, 0.0, 0.0050, 0.0465, 0.0530, 2.00),
    (1.560, 0.0, 0.0025, 0.0468, 0.0570, 1.95),
    (1.566, 0.0, -0.0050, 0.0473, 0.0662, 1.80),
    (1.573, 0.0, -0.0115, 0.0482, 0.0750, 1.70),
    (1.580, 0.0, -0.0125, 0.0495, 0.0785, 1.70),
    (1.592, 0.0, -0.0105, 0.0525, 0.0805, 1.75),
    (1.608, 0.0, -0.0065, 0.0570, 0.0845, 1.85),
    (1.635, 0.0, -0.0015, 0.0665, 0.0895, 1.90),
    (1.665, 0.0, 0.0030, 0.0722, 0.0945, 1.94),
    (1.692, 0.0, 0.0055, 0.0745, 0.0960, 1.97),
    (1.715, 0.0, 0.0050, 0.0762, 0.0985, 2.00),
    (1.745, 0.0, 0.0050, 0.0758, 0.0950, 2.02),
    (1.772, 0.0, 0.0055, 0.0690, 0.0840, 2.02),
    (1.792, 0.0, 0.0055, 0.0535, 0.0640, 2.00),
    (1.803, 0.0, 0.0055, 0.0340, 0.0400, 2.00),
    (1.808, 0.0, 0.0055, 0.0120, 0.0140, 2.00),
]


def _g(t):
    return np.exp(-0.5 * t * t)


# ---------------------------------------------------------------------------------------------- interpolacao suave
def _pchip(y, ys, vs):
    """Interpolacao cubica monotona (sem ondulacao entre os aneis)."""
    y = np.asarray(y, float)
    h = np.diff(ys)
    d = np.diff(vs) / h
    m = np.zeros(len(ys))
    m[0], m[-1] = d[0], d[-1]
    for i in range(1, len(ys) - 1):
        if d[i - 1] * d[i] > 0:
            m[i] = 2.0 * d[i - 1] * d[i] / (d[i - 1] + d[i])
    i = np.clip(np.searchsorted(ys, y) - 1, 0, len(ys) - 2)
    t = np.clip((y - ys[i]) / h[i], 0.0, 1.0)
    t2, t3 = t * t, t * t * t
    return ((2 * t3 - 3 * t2 + 1) * vs[i] + (t3 - 2 * t2 + t) * h[i] * m[i]
            + (-2 * t3 + 3 * t2) * vs[i + 1] + (t3 - t2) * h[i] * m[i + 1])


# ---------------------------------------------------------------------------------------------- formas do rosto
def fenda(q, dy):
    """Fenda das palpebras. q = lateral a partir do centro do olho (positivo = para fora), dy = altura.
    Retorna (dentro, y_palpebra_sup, y_palpebra_inf, t) com t = 0 no canto de dentro e 1 no de fora."""
    t = (q + 0.0150) / 0.0292
    tc = np.clip(t, 0.0, 1.0)
    arco = 4.0 * tc * (1.0 - tc)
    yc = -0.0010 + 0.0020 * tc
    yu = yc + 0.0047 * arco ** 0.70 * (1.0 + 0.30 * (0.5 - tc))
    yl = yc - 0.0041 * arco ** 0.85 * (1.0 - 0.30 * (0.5 - tc))
    dentro = (t > 0.0) & (t < 1.0) & (dy < yu) & (dy > yl)
    return dentro, yu, yl, t


def labios(x, y):
    """Retorna (relevo em metros, mascara do vermelhao 0..1, distancia assinada a linha da boca)."""
    u = np.clip(np.abs(x) / BOCA_L, 0.0, 1.0)
    env = np.sqrt(np.clip(1.0 - u * u, 0.0, 1.0))
    ys = BOCA_Y - 0.0012 * u * u + 0.0003 * _g(x / 0.007)
    hu = 0.0064 * env * (1.0 - 0.20 * _g(x / 0.0038)) + 0.0007 * _g((np.abs(x) - 0.0064) / 0.0038)
    hl = 0.0088 * env ** 1.25
    su = np.maximum(0.40 * hu, 0.0009)
    sl = np.maximum(0.40 * hl, 0.0009)
    dentro = (np.abs(x) < BOCA_L * 1.02).astype(float)
    rel = 0.0032 * env * _g((y - (ys + 0.50 * hu)) / su)
    rel += 0.0042 * env ** 0.8 * _g((y - (ys - 0.48 * hl)) / sl)
    rel -= 0.0034 * np.sqrt(env + 0.05) * _g((y - ys) / 0.0010) * _g(np.maximum(np.abs(x) - BOCA_L, 0) / 0.004)
    rel -= 0.0022 * _g((np.abs(x) - BOCA_L - 0.0015) / 0.0035) * _g((y - ys) / 0.0045)   # covinha do canto
    borda = 0.0006
    verm = suave((ys + hu - y) / borda + 0.5) * suave((y - (ys - hl)) / borda + 0.5) * dentro * (env > 0.02)
    return rel, verm, y - ys


def nariz(x, y):
    """Relevo do nariz (metros, para a frente)."""
    t = np.clip((NARIZ_NASION - y) / (NARIZ_NASION - NARIZ_PONTA), 0.0, 1.0)
    acima = y >= NARIZ_PONTA
    alt = np.where(acima, 0.0010 + 0.0125 * t ** 1.10, 0.0135 * _g((y - NARIZ_PONTA) / 0.0062))
    larg = 0.0052 + 0.0034 * t ** 1.5
    rel = alt * _g(x / larg)
    rel += 0.0088 * _g(x / 0.0088) * _g((y - 1.6508) / 0.0078)                          # ponta (bola)
    rel += 0.0094 * _g((np.abs(x) - 0.0128) / 0.0048) * _g((y - 1.6448) / 0.0056)        # asas
    rel += 0.0052 * _g(x / 0.0042) * _g((y - 1.6400) / 0.0042)                           # columela
    rel -= 0.0046 * _g((np.abs(x) - 0.0066) / 0.0030) * _g((y - 1.6395) / 0.0021)        # narinas
    rel -= 0.0016 * _g((np.abs(x) - 0.0188) / 0.0030) * _g((y - 1.6430) / 0.0070)        # sulco da asa
    return rel


def narina(x, y):
    return _g((np.abs(x) - 0.0066) / 0.0026) * _g((y - 1.6393) / 0.0016)


def linha_cabelo(th):
    """Altura (y) da linha do cabelo em funcao de theta."""
    th = np.asarray(th, float)
    a = np.abs(((th - PI + PI) % (2 * PI)) - PI)            # 0 = frente ... pi = nuca
    base = np.interp(a, [0.0, 0.55, 0.95, 1.20, 1.42, 1.62, 1.95, 2.25, 2.70, PI],
                     [1.7600, 1.7580, 1.7480, 1.7240, 1.7020, 1.7160, 1.7120, 1.6740, 1.6420, 1.6360])
    rag = 0.0035 * np.sin(7 * th + 0.6) + 0.0025 * np.sin(17 * th + 2.1) + 0.0015 * np.sin(31 * th + 4.0)
    return base + rag


def _campo_rosto():
    c = Campo()
    f = -0.090  # z aproximado do plano do rosto (as bossas da frente usam sz grande)
    c.bossa(0.0300, 1.7130, f, .0220, .0070, .03, .0050, True)       # arcada superciliar
    c.bossa(0.0, 1.7090, f, .0110, .0080, .03, .0026)                # glabela
    c.bossa(0.0270, 1.7480, f, .0240, .0180, .03, .0018, True)       # bossa frontal
    c.bossa(OLHO_X + 0.001, 1.6925, f, .0170, .0120, .03, -.0085, True)   # orbita
    c.bossa(0.0150, 1.6860, f, .0060, .0100, .03, -.0030, True)      # canto interno / parede do nariz
    c.bossa(0.0285, 1.6760, f, .0130, .0050, .03, -.0009, True)      # olheira
    c.bossa(0.0520, 1.6660, -0.068, .0170, .0120, .028, .0042, True)  # maca do rosto
    c.bossa(0.0470, 1.6260, -0.062, .0150, .0180, .028, -.0014, True)  # bochecha funda
    c.bossa(0.0, 1.6130, f, .0290, .0200, .03, .0044)                # arco dentario (focinho)
    c.bossa(0.0235, 1.6270, f, .0050, .0110, .03, -.0012, True)      # sulco nasolabial
    c.bossa(0.0, 1.5975, f, .0150, .0042, .03, -.0032)               # sulco do queixo
    c.bossa(0.0, 1.5830, -0.091, .0145, .0100, .022, .0064)
    c.bossa(0.0360, 1.5750, -0.050, .0150, .0090, .022, -.0040, True)      # vao embaixo da mandibula          # queixo
    c.bossa(0.0540, 1.6040, -0.012, .0110, .0130, .022, .0040, True)  # angulo da mandibula
    c.bossa(0.0730, 1.7250, -0.045, .0140, .0200, .030, -.0028, True)  # tempora
    c.bossa(0.0, 1.7050, 0.108, .0400, .0400, .030, .0040)           # occipital
    c.bossa(0.0, 1.5240, -0.044, .0085, .0100, .020, .0040)          # pomo de adao
    c.bossa(0.0250, 1.5200, -0.036, .0060, .0300, .020, .0026, True)  # esternocleidomastoideo
    c.bossa(0.0, 1.5050, -0.045, .0100, .0080, .020, -.0030)         # furcula
    c.bossa(0.0420, 1.5080, 0.040, .0130, .0200, .030, .0030, True)  # trapezio

    def frente(P, th, y):
        x = P[:, 0]
        w = suave((-P[:, 2] - 0.045) / 0.025)
        rel = nariz(x, y) + labios(x, y)[0]
        rel += 0.0008 * _g((np.abs(x) - 0.0046) / 0.0018) * _g((y - 1.6280) / 0.0060)   # cristas do filtro
        rel -= 0.0010 * _g(x / 0.0026) * _g((y - 1.6280) / 0.0055)                      # filtro
        return rel * w
    c.fn(frente)
    return c


class TuboCabeca(Tubo):
    """Tubo com aneis interpolados por cubica monotona; na frente o relevo empurra em -Z (nao no raio),
    para que nariz, boca e olhos fiquem no x em que foram desenhados."""

    def _interp(self, y, k):
        return _pchip(y, self.ys, self.a[:, k])

    def direcao(self, P, rad):
        wf = suave((-P[..., 2] - 0.040) / 0.030)[..., None]
        return norm(rad * (1.0 - wf) + np.array([0.0, 0.0, -1.0]) * wf)

    def ponto(self, th, y, alt=0.0):
        th, y = np.broadcast_arrays(np.asarray(th, float), np.asarray(y, float))
        P, rad = self.base(th, y)
        d = self.campo(P, th, y).reshape(th.shape)
        P2 = P + self.direcao(P, rad) * d[..., None]
        if np.any(np.asarray(alt) != 0.0):
            P2 = P2 + self.fora(P, rad) * (np.zeros(th.shape) + alt)[..., None]
        return P2, rad

    def fora(self, P, rad):
        """Normal aproximada do cranio: radial nos lados, esferica no topo."""
        esf = norm(P - np.array([0.0, 1.700, 0.005]))
        w = suave((P[..., 1] - 1.690) / 0.060)[..., None]
        return norm(rad * (1.0 - w) + esf * w)


# ---------------------------------------------------------------------------------------------- UV da pele
def _w(s):
    a = np.abs(s)
    return np.sign(s) * (0.72 * (1.0 - (1.0 - a) ** 2) + 0.28 * a)


_S = np.linspace(-1.0, 1.0, 4001)
_WS = _w(_S)
_YK = [Y_BASE, 1.565, 1.750, Y_TOPO]
_VK = [0.0, 0.09, 0.90, 1.0]


def u_de_th(th):
    return 0.5 + 0.5 * _w((np.asarray(th, float) - PI) / PI)


def th_de_u(u):
    return PI + PI * np.interp(2.0 * np.asarray(u, float) - 1.0, _WS, _S)


def v_de_y(y):
    return np.interp(y, _YK, _VK)


def y_de_v(v):
    return np.interp(v, _VK, _YK)


def uv_pele(th, y):
    return uv_atlas(RR["pele"], u_de_th(th), v_de_y(y))


# ---------------------------------------------------------------------------------------------- olhos
def _palpebra_z(r):
    """Superficie da palpebra: esfera em volta do globo, continuada em rampa suave fora de r0."""
    r0 = 0.0095
    k = math.sqrt(R_PALP ** 2 - r0 ** 2)
    return np.where(r < r0, OLHO_Z - np.sqrt(np.maximum(R_PALP ** 2 - r * r, 1e-10)), OLHO_Z - k + (r - r0) * r0 / k)


def encaixar_olhos(P, frente):
    """Molda as palpebras em volta do globo e marca os vertices dentro da fenda (que viram a borda)."""
    P = P.copy()
    furo = np.zeros(P.shape[:-1], bool)
    for s in (-1, 1):
        dx = P[..., 0] - s * OLHO_X
        dy = P[..., 1] - OLHO_Y
        q = dx * s
        dentro, yu, yl, t = fenda(q, dy)
        dentro = dentro & frente
        dy2 = np.where(dentro, np.where(dy > 0.5 * (yu + yl), yu, yl), dy)
        r = np.sqrt(dx * dx + dy2 * dy2)
        dist = np.sqrt((q + 0.0005) ** 2 + (dy2 * 1.30) ** 2)
        w = (1.0 - suave((dist - 0.0120) / 0.0080)) * frente
        z = P[..., 2] * (1.0 - w) + _palpebra_z(r) * w
        meio = suave(t / 0.12) * suave((1.0 - t) / 0.12)
        z = z + 0.0012 * _g((dy2 - (yu + 0.0036)) / 0.0011) * meio * w            # sulco da palpebra superior
        z = z - 0.0006 * _g((dy2 - (yl - 0.0016)) / 0.0012) * meio * w            # rolinho da palpebra inferior
        zb = OLHO_Z - np.sqrt(np.maximum((R_GLOBO + 0.0003) ** 2 - r * r, 1e-10))
        P[..., 1] = np.where(dentro, OLHO_Y + dy2, P[..., 1])
        P[..., 2] = np.where(dentro, zb, z)
        furo |= dentro
    return P, furo


# ---------------------------------------------------------------------------------------------- malha da pele
def _amostras(dens, a0, a1, n):
    x = np.linspace(a0, a1, 6001)
    c = np.cumsum(dens(x))
    c = (c - c[0]) / (c[-1] - c[0])
    return np.interp(np.linspace(0.0, 1.0, n), c, x)


def pele(m, t, f=1.0):
    nc, nr = int(round(200 * f)) // 2 * 2, int(round(128 * f))
    meio = _amostras(lambda a: 1.0 + 4.0 * (1.0 - suave((a - 0.80) / 0.75)), 0.0, PI, nc // 2 + 1)
    th = np.concatenate([PI - meio[::-1], PI + meio[1:]])
    ys = _amostras(lambda y: 1.0 + 3.2 * suave((y - 1.552) / 0.010) * (1.0 - suave((y - 1.722) / 0.02)), Y_BASE, 1.800, nr)
    TH, Y = np.meshgrid(th, ys)
    P, rad = t.ponto(TH, Y)
    Pb, _ = t.base(TH, Y)
    frente = Pb[..., 2] < -0.045
    P, furo = encaixar_olhos(P, frente)
    UV = uv_pele(TH, Y)
    # normal da grade (orienta as faces mesmo embaixo do queixo e do nariz)
    dth = np.gradient(P, axis=1)
    dy = np.gradient(P, axis=0)
    nrm = norm(np.cross(dy, dth))
    if (nrm * rad).sum(-1).mean() < 0:
        nrm = -nrm
    coberto = Y > linha_cabelo(TH) + 0.014
    idx = np.arange(nr * len(th)).reshape(nr, len(th))
    a, b, c, d = idx[:-1, :-1].ravel(), idx[:-1, 1:].ravel(), idx[1:, 1:].ravel(), idx[1:, :-1].ravel()
    T = np.concatenate([np.stack([a, b, c], 1), np.stack([a, c, d], 1)])
    fu, co = furo.ravel(), coberto.ravel()
    fica = ~(fu[T].all(1)) & ~(co[T].all(1))
    m.add("cabeca", P.reshape(-1, 3), UV.reshape(-1, 2), T[fica], nrm.reshape(-1, 3), angulo=80.0, nome="pele")


def globos(m):
    reg = RR["olho"]
    for s in (-1, 1):
        elipsoide(m, "cabeca", (s * OLHO_X, OLHO_Y, OLHO_Z), (R_GLOBO, R_GLOBO, R_GLOBO), nu=16, nv=12, nome="olho",
                  lat=(-70.0, 70.0), lon=(-80.0, 80.0),
                  uv_fn=lambda P, U: uv_atlas(reg, 0.5 + P[..., 0] / (2.0 * R_GLOBO) * 0.96,
                                              0.5 + P[..., 1] / (2.0 * R_GLOBO) * 0.96))


# ---------------------------------------------------------------------------------------------- orelhas
ORELHA_A, ORELHA_B = 0.0158, 0.0295


def _orelha_relevo(an, bn):
    """Relevo da face externa da orelha. an, bn em -1..1 (an: -1 = raiz na frente, +1 = borda de tras)."""
    rho = np.sqrt(an * an + bn * bn)
    phi = np.arctan2(bn, an)
    m_hel = suave((np.sin(phi) + 0.55) / 0.5)                          # some no lobulo
    h = 0.0042 * _g((rho - 0.88) / 0.085) * m_hel                       # helice
    h -= 0.0012 * _g((rho - 0.69) / 0.07) * m_hel                       # escafa
    m_ant = suave((phi + 1.5) / 0.5) * suave((2.2 - phi) / 0.5)
    h += 0.0034 * _g((rho - 0.50) / 0.085) * m_ant                      # anti-helice
    h -= 0.0080 * np.exp(-(((an + 0.16) / 0.36) ** 2 + ((bn + 0.10) / 0.29) ** 2))   # concha
    h += 0.0046 * np.exp(-(((an + 0.80) / 0.17) ** 2 + ((bn + 0.08) / 0.15) ** 2))   # tragus
    h += 0.0022 * np.exp(-(((an + 0.05) / 0.18) ** 2 + ((bn + 0.50) / 0.10) ** 2))   # antitragus
    return h


def _orelha_contorno(phi):
    """Raio normalizado do contorno: lobulo mais estreito, frente mais reta."""
    s, c = np.sin(phi), np.cos(phi)
    ra = 1.0 - 0.20 * suave((-s - 0.30) / 0.6) - 0.10 * suave(-c)
    return ra


def orelhas(m):
    nro, nphi = 11, 44
    rho = np.linspace(0.0, 1.0, nro) ** 0.85
    phi = np.linspace(0.0, 2.0 * PI, nphi + 1)
    RHO, PHI = np.meshgrid(rho, phi, indexing="ij")
    an = RHO * _orelha_contorno(PHI) * np.cos(PHI)
    bn = RHO * np.sin(PHI)
    h = _orelha_relevo(an, bn)
    idx = np.arange(nro * (nphi + 1)).reshape(nro, nphi + 1)
    a, b, c, d = idx[:-1, :-1].ravel(), idx[:-1, 1:].ravel(), idx[1:, 1:].ravel(), idx[1:, :-1].ravel()
    T = np.concatenate([np.stack([a, b, c], 1), np.stack([a, c, d], 1)])
    for s in (-1, 1):
        O = np.array([s * 0.0715, 1.6760, -0.0040])
        A = norm(np.array([s * 0.33, 0.0, 0.94]))                      # para tras e para fora (~19 graus)
        B = norm(np.array([0.0, 0.990, 0.140]))                        # para cima, levemente inclinada para tras
        B = norm(B - A * np.dot(A, B))
        C = np.cross(B, A)
        if C[0] * s < 0:
            C = -C
        reg = RR["orelha_esq" if s < 0 else "orelha_dir"]
        pa = (an + 1.0) * ORELHA_A
        pb = bn * ORELHA_B
        fecha = 1.0 - RHO ** 10
        frente = (0.0022 + h) * fecha
        tras = -(0.0030 + 0.0030 * _g(RHO / 0.5)) * np.sqrt(np.clip(1.0 - RHO ** 2, 0.0, 1.0))
        UV = uv_atlas(reg, 0.08 + 0.84 * (an + 1.0) / 2.0, 0.06 + 0.88 * (bn + 1.0) / 2.0)
        for lado, cc, sinal in ((0, frente, 1.0), (1, tras, -1.0)):
            P = O + pa[..., None] * A + pb[..., None] * B + cc[..., None] * C
            m.add("cabeca", P.reshape(-1, 3), UV.reshape(-1, 2), T, np.tile(C * sinal, (P.shape[0] * P.shape[1], 1)),
                  angulo=80.0, nome="orelha")


# ---------------------------------------------------------------------------------------------- cabelo
def _ruido_per(th, y, nth, ny, sem):
    """Ruido de valor periodico em theta (0..1)."""
    rs = np.random.RandomState(sem)
    g = rs.rand(ny + 2, nth)
    fx = (np.asarray(th, float) / (2 * PI) * nth) % nth
    fy = np.clip((np.asarray(y, float) - 1.60) / 0.21, 0.0, 1.0) * ny
    ix, iy = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    ix1 = (ix + 1) % nth
    return ((g[iy, ix] * (1 - tx) + g[iy, ix1] * tx) * (1 - ty) + (g[iy + 1, ix] * (1 - tx) + g[iy + 1, ix1] * tx) * ty)


def _espessura(th, y):
    """Espessura do cabelo sobre o cranio (metros), com mechas em relevo."""
    d = y - linha_cabelo(th)
    topo = suave((y - 1.72) / 0.07)
    base = 0.0100 + 0.0110 * topo
    r1 = 1.0 - np.abs(2.0 * _ruido_per(th + 0.9 * np.sin(38.0 * y), y, 30, 4, 7) - 1.0)
    r2 = _ruido_per(th, y, 64, 9, 8)
    r3 = _ruido_per(th, y, 11, 3, 9)
    tot = base + 0.0056 * (r1 - 0.5) + 0.0026 * (r2 - 0.5) + 0.0070 * (r3 - 0.5) * topo
    return 0.0012 + np.maximum(tot, 0.003) * suave(d / 0.012)


def cabelo(m, t, f=1.0, n_mechas=1.0):
    reg = RR["cabelo"]
    nth, nrow = int(round(120 * f)), int(round(30 * f))
    th = np.linspace(0.0, 2 * PI, nth + 1)
    tt = np.linspace(0.0, 1.0, nrow) ** 1.25
    TH, TT = np.meshgrid(th, tt)
    yh = linha_cabelo(TH) - 0.002
    Y = yh + (1.8065 - yh) * TT
    P, rad = t.ponto(TH, Y, _espessura(TH, Y))
    UV = uv_atlas(reg, (TH / (2 * PI) * 2.0) % 1.0 * 0.98 + 0.01, 0.03 + 0.94 * TT)
    idx = np.arange(nrow * (nth + 1)).reshape(nrow, nth + 1)
    a, b, c, d = idx[:-1, :-1].ravel(), idx[:-1, 1:].ravel(), idx[1:, 1:].ravel(), idx[1:, :-1].ravel()
    T = np.concatenate([np.stack([a, b, c], 1), np.stack([a, c, d], 1)])
    Pf, Uf, Vf = P.reshape(-1, 3), UV.reshape(-1, 2), rad.reshape(-1, 3) + np.array([0.0, 0.6, 0.0])
    # tampa do topo
    centro = P[-1, :-1].mean(0) + np.array([0.0, 0.002, 0.0])
    k = len(Pf)
    Pf = np.vstack([Pf, centro])
    Uf = np.vstack([Uf, uv_atlas(reg, 0.5, 0.99)])
    Vf = np.vstack([Vf, [0.0, 1.0, 0.0]])
    T = np.concatenate([T, np.array([[idx[-1, j], idx[-1, j + 1], k] for j in range(nth)])])
    m.add("cabeca", Pf, Uf, T, Vf, angulo=85.0, nome="cabelo")

    # ---- mechas achatadas (borda e topo): quebram a silhueta sem virar espinhos
    rs = np.random.RandomState(33)

    def mecha(th0, y0, comp, dth, alt0, lev, larg, nn=9, ponta=0.004):
        pts, r0 = [], None
        for k_ in range(5):
            s = k_ / 4.0
            thk, yk = th0 + dth * s * s, max(y0 - comp * s, 1.555)
            alt = alt0 + lev * math.sin(PI * min(s * 1.15, 1.0)) ** 1.2 + (ponta - alt0) * s ** 3
            Pk, rk = t.ponto(thk, yk, alt)
            pts.append(Pk.reshape(3))
            if r0 is None:
                r0 = rk.reshape(3)
        tubo_caminho(m, "cabeca", pts, [larg * 0.80, larg, larg * 0.96, larg * 0.74, larg * 0.26], uv_regiao=reg,
                     nseg=6, nn=nn, tampas=False, nome="mecha", achatar=(0.42, r0), angulo=85.0)

    def qtd(n):
        return max(3, int(round(n * n_mechas)))

    for k_ in range(qtd(30)):                                            # franja (duas camadas)
        th0 = PI + rs.uniform(-0.85, 0.85)
        y0 = float(linha_cabelo(th0)) + rs.uniform(0.006, 0.024)
        curto = k_ % 3 == 0
        mecha(th0, y0, rs.uniform(0.016, 0.028) if curto else rs.uniform(0.028, 0.048), rs.normal(0.06, 0.20), 0.0105,
              rs.uniform(0.002, 0.007), rs.uniform(0.0085, 0.0125), ponta=rs.uniform(0.004, 0.009))
    for lado in (-1, 1):                                                 # tempora, costeleta e por cima da orelha
        for _ in range(qtd(11)):
            th0 = PI + lado * rs.uniform(0.85, 1.95)
            y0 = float(linha_cabelo(th0)) + rs.uniform(0.008, 0.022)
            mecha(th0, y0, rs.uniform(0.022, 0.044), lado * rs.normal(0.05, 0.10), 0.0100, rs.uniform(0.003, 0.009),
                  rs.uniform(0.0070, 0.0105), ponta=rs.uniform(0.006, 0.012))
    for _ in range(qtd(18)):                                             # nuca
        th0 = rs.uniform(-1.20, 1.20) % (2 * PI)
        y0 = float(linha_cabelo(th0)) + rs.uniform(0.008, 0.025)
        mecha(th0, y0, rs.uniform(0.028, 0.055), rs.normal(0, 0.10), 0.0100, rs.uniform(0.002, 0.008),
              rs.uniform(0.0075, 0.0110), ponta=rs.uniform(0.004, 0.010))
    for _ in range(qtd(34)):                                             # topo baguncado
        th0 = rs.uniform(0, 2 * PI)
        y0 = rs.uniform(1.752, 1.803)
        mecha(th0, y0, rs.uniform(0.020, 0.040), rs.normal(0, 0.30), 0.0150, rs.uniform(0.006, 0.014),
              rs.uniform(0.0075, 0.0110), nn=8, ponta=rs.uniform(0.016, 0.030))


# ---------------------------------------------------------------------------------------------- construcao
def construir(p, f=1.0, n_mechas=1.0):
    """Monta a cabeca na malha do personagem `p` (p.m) e guarda o tubo em p.tubos['cabeca_real']."""
    t = TuboCabeca(ANEIS, RR["pele"], _campo_rosto())
    p.tubos["cabeca_real"] = t
    pele(p.m, t, f)
    globos(p.m)
    orelhas(p.m)
    cabelo(p.m, t, f, n_mechas)
    return t


# ============================================================================================== TEXTURA
def _c(t):
    return np.array(t, np.float32) / 255.0


def _box(a, r):
    """Media movel separavel (borda repetida)."""
    k = 2 * r + 1
    for ax in (0, 1):
        pad = [(0, 0)] * a.ndim
        pad[ax] = (r + 1, r)
        c = np.cumsum(np.pad(a, pad, mode="edge"), axis=ax, dtype=np.float64)
        sl_a = [slice(None)] * a.ndim
        sl_b = [slice(None)] * a.ndim
        sl_a[ax] = slice(k, None)
        sl_b[ax] = slice(0, -k)
        a = ((c[tuple(sl_a)] - c[tuple(sl_b)]) / k).astype(np.float32)
    return a


def _mix(cor, alvo, m):
    return cor * (1.0 - m[..., None]) + np.asarray(alvo, np.float32) * m[..., None]


def _pintar_pele(t, A):
    x0, y0, w, h = RR["pele"]
    u = (np.arange(w) + 0.5) / w
    v = 1.0 - (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    TH, Y = th_de_u(U), y_de_v(V)
    P, rad = t.base(TH, Y)
    d = t.campo(P, TH, Y).reshape(TH.shape)
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    ax = np.abs(x)
    fr = suave((-z - 0.030) / 0.040)
    pele_c = _c(COR_PELE)
    # ---- base: variacao larga de tom, poros
    n1 = fbm(h, w, 26, 1)
    n2 = fbm(h, w, 7, 2, 3)
    poro = ruido(h, w, 1, 3)
    cor = pele_c[None, None, :] * (0.975 + 0.05 * n1[..., None]) * (0.985 + 0.03 * n2[..., None])
    cor = cor * (1.0 - 0.045 * (poro[..., None] - 0.5))
    # ---- zonas de cor: bochechas/nariz/queixo mais rosados, testa mais amarelada, pescoco mais palido
    rosa = np.array([0.050, -0.022, -0.018], np.float32)
    boch = np.exp(-(((ax - 0.046) / 0.020) ** 2 + ((y - 1.650) / 0.022) ** 2)) * fr
    nz = np.exp(-((x / 0.014) ** 2 + ((y - 1.652) / 0.013) ** 2)) * fr
    qx = np.exp(-((x / 0.020) ** 2 + ((y - 1.586) / 0.012) ** 2)) * fr
    cor = cor + (0.85 * boch + 0.95 * nz + 0.35 * qx)[..., None] * rosa
    testa = suave((y - 1.722) / 0.02) * fr
    cor = cor + testa[..., None] * np.array([0.010, 0.004, -0.014], np.float32)
    # ---- sardas (referencia do garoto): pintinhas castanhas em nariz e bochechas
    rs = np.random.RandomState(77)
    sard = np.zeros((h, w), np.float32)
    for _ in range(170):
        sx = rs.normal(0, 0.034)
        sy = 1.656 + rs.normal(0, 0.016)
        if abs(sx) > 0.066 or not (1.628 < sy < 1.700):
            continue
        if abs(abs(sx) - OLHO_X) < 0.017 and abs(sy - OLHO_Y) < 0.011:
            continue
        rr = rs.uniform(0.00045, 0.00105)
        sard = np.maximum(sard, rs.uniform(0.35, 0.9) * np.exp(-(((x - sx) ** 2 + (y - sy) ** 2) / rr ** 2)) * (z < -0.03))
    cor = _mix(cor, cor * _c((178, 128, 96)), sard * 0.80)
    # ---- olhos: olheira, palpebra, cilios, linha d'agua
    for s in (-1, 1):
        q = (x - s * OLHO_X) * s
        dy = y - OLHO_Y
        dentro, yu, yl, tq = fenda(q, dy)
        meio = suave(tq / 0.10) * suave((1.0 - tq) / 0.10)
        perto = (np.abs(q) < 0.030) & (np.abs(dy) < 0.030) & (z < -0.04)
        soq = np.exp(-(((q + 0.001) / 0.019) ** 2 + ((dy - 0.002) / 0.013) ** 2)) * perto
        cor = cor * (1.0 - 0.15 * soq[..., None]) + soq[..., None] * 0.030 * np.array([0.55, 0.20, 0.45], np.float32)
        olh = np.exp(-(((q + 0.002) / 0.012) ** 2 + ((dy + 0.0095) / 0.0042) ** 2)) * perto      # olheira
        cor = cor * (1.0 - 0.07 * olh[..., None]) + olh[..., None] * 0.018 * np.array([0.45, 0.18, 0.55], np.float32)
        sul = _g((dy - (yu + 0.0036)) / 0.0010) * meio * perto                                  # sulco da palpebra
        cor = cor * (1.0 - 0.22 * sul[..., None])
        # cilios: linha escura na borda de cima + fiozinhos; linha fina na de baixo
        fio = ruido(h, w, 1, 40 + s) * 0.6 + fbm(h, w, 2, 41 + s) * 0.4
        cs = suave((yu + 0.0006 + 0.0016 * fio * (0.4 + 0.6 * tq) - dy) / 0.0005) * suave((dy - yu + 0.0020) / 0.0008)
        cs = cs * suave((tq + 0.02) / 0.08) * suave((1.03 - tq) / 0.06) * perto
        cor = _mix(cor, _c((30, 22, 20)), np.clip(cs, 0, 1) * 0.92)
        ci = suave((dy - yl + 0.0007) / 0.0005) * suave((yl + 0.0018 - dy) / 0.0010) * meio * perto
        cor = _mix(cor, _c((120, 78, 70)), np.clip(ci, 0, 1) * 0.55)
        cor = np.where((dentro & perto & (dy > 0.5 * (yu + yl)))[..., None], _c((34, 24, 22)), cor)
        cor = np.where((dentro & perto & (dy <= 0.5 * (yu + yl)))[..., None], _c((170, 116, 106)), cor)
        # sobrancelha: sombra de base + fios individuais (cada fio e um risquinho inclinado)
        ii, jj = np.where(perto & (np.abs(dy - 0.018) < 0.012) & (q > -0.022) & (q < 0.030))
        i0, i1, j0, j1 = ii.min(), ii.max() + 1, jj.min(), jj.max() + 1
        qs, ds = q[i0:i1, j0:j1], dy[i0:i1, j0:j1]

        def centro(tb_):
            return 0.0152 + 0.0052 * np.sin(PI * tb_ * 0.62) - 0.0034 * tb_ ** 2.2

        def meia(tb_):
            return 0.0036 * (1.0 - 0.74 * tb_ ** 1.5)
        tb = np.clip((qs + 0.0165) / 0.0420, 0.0, 1.0)
        fora_ = suave((qs + 0.0165) / 0.004) * suave((0.0255 - qs) / 0.006)
        alfa = 0.34 * suave((meia(tb) - np.abs(ds - centro(tb))) / 0.0022 + 0.35) * fora_
        rb = np.random.RandomState(120)          # mesma semente nos dois lados: sobrancelhas simetricas
        for _ in range(420):
            tb0 = rb.uniform(0.0, 1.0) ** 0.85
            hq = -0.0165 + 0.0420 * tb0
            hy = centro(tb0) + rb.uniform(-1.0, 1.0) * meia(tb0)
            ang = math.radians(62.0 - 62.0 * tb0 ** 0.7 + rb.normal(0, 9.0)) if tb0 < 0.92 else math.radians(-12.0)
            comp = rb.uniform(0.0022, 0.0042) * (1.0 - 0.35 * tb0)
            ca, sa = math.cos(ang), math.sin(ang)
            al = (qs - hq) * ca + (ds - hy) * sa
            tr = -(qs - hq) * sa + (ds - hy) * ca
            fio_ = (1.0 - suave(np.abs(tr) / 0.00030)) * suave(al / 0.0004 + 0.5) * suave((comp - al) / 0.0012)
            alfa = np.maximum(alfa, fio_ * rb.uniform(0.60, 0.95))
        sub = cor[i0:i1, j0:j1]
        cor[i0:i1, j0:j1] = _mix(sub, _c((40, 30, 26)), np.clip(alfa, 0, 1))
    # ---- nariz: narinas escuras
    cor = _mix(cor, _c((48, 26, 24)), np.clip(narina(x, y) * 1.30, 0, 1) * fr)
    tn = np.clip((NARIZ_NASION - y) / (NARIZ_NASION - NARIZ_PONTA), 0.0, 1.0)
    lado_n = _g((ax - (0.0075 + 0.0045 * tn)) / 0.0030) * suave((y - 1.650) / 0.006) * suave((1.700 - y) / 0.008) * fr
    cor = cor * (1.0 - 0.085 * lado_n[..., None] * np.array([0.75, 1.0, 1.05], np.float32))        # paredes do nariz
    da = np.sqrt((ax - 0.0128) ** 2 + ((y - 1.6448) * 0.9) ** 2)
    sulco = _g((da - 0.0066) / 0.0014) * suave((ax - 0.0125) / 0.004) * suave((y - 1.6400) / 0.004) * fr
    cor = cor * (1.0 - 0.13 * sulco[..., None] * np.array([0.72, 1.0, 1.05], np.float32))          # sulco da asa
    sob = _g(x / 0.0100) * _g((y - 1.6350) / 0.0024) * fr
    cor = cor * (1.0 - 0.10 * sob[..., None] * np.array([0.75, 1.0, 1.05], np.float32))            # sombra embaixo do nariz
    dorso = _g(x / 0.0030) * suave((y - 1.650) / 0.004) * suave((1.692 - y) / 0.010) * fr
    cor = cor * (1.0 + 0.045 * dorso[..., None])                                                   # luz no dorso
    pt = _g(x / 0.0045) * _g((y - 1.6520) / 0.0040) * fr
    cor = cor * (1.0 + 0.060 * pt[..., None])
    # ---- labios
    rel, verm, dl = labios(x, y)
    verm = verm * fr
    rugas = 0.5 + 0.5 * np.sin(x / 0.00062 + 4.0 * fbm(h, w, 4, 60))
    labio = _c((180, 120, 110))[None, None, :] * (0.93 + 0.10 * rugas[..., None]) * (0.96 + 0.08 * n2[..., None])
    labio = labio * (1.0 + 0.10 * _g((dl + 0.0048) / 0.0022)[..., None])          # brilho do labio inferior
    cor = _mix(cor, labio, verm * 0.80)
    linha = _g(dl / 0.00075) * (ax < BOCA_L + 0.0010) * fr
    cor = _mix(cor, _c((84, 40, 40)), np.clip(linha, 0, 1) * 0.90)
    # ---- sujeira de sobrevivente (leve) e sombra do queixo no pescoco
    suj = suave((fbm(h, w, 40, 70, 4) - 0.50) / 0.30) * (0.3 + 0.7 * suave((1.66 - y) / 0.08)) * suave((y - 1.56) / 0.02)
    suj = suj * (1.0 - verm) * (1.0 - np.clip(soq * 2, 0, 1))
    cor = cor * (1.0 - 0.085 * suj[..., None] * np.array([0.85, 1.0, 1.15], np.float32))
    pesc = suave((1.580 - y) / 0.012) * suave((-z + 0.02) / 0.05) * (0.30 + 0.70 * np.exp(-np.clip(1.566 - y, 0, 1) / 0.028))
    cor = cor * (1.0 - 0.30 * pesc[..., None] * np.array([0.80, 1.0, 1.05], np.float32))
    # ---- relevo assado: cavidades escurecem puxando para o vermelho (a pele nao sombreia em cinza)
    df = d.astype(np.float32)
    cav = (df - _box(df, 3)) * 1.4 + (df - _box(df, 12)) * 0.45
    oc = np.clip(1.0 + 44.0 * np.minimum(cav, 0.0) + 14.0 * np.maximum(cav, 0.0), 0.70, 1.08)
    cor = cor * (1.0 - (1.0 - oc)[..., None] * np.array([0.72, 1.0, 1.06], np.float32))
    # ---- couro cabeludo: cabelo pintado por baixo da casca (com borda de fiozinhos)
    dh = y - linha_cabelo(TH)
    fn_ = fbm(h, w, 3, 80, 3)
    mh = suave((dh + 0.006 + 0.010 * (fn_ - 0.5)) / 0.008)
    cab = _c(COR_CABELO)[None, None, :] * (0.75 + 0.5 * fbm(h, w, 2, 81)[..., None])
    cor = _mix(cor, cab, mh)
    A[y0:y0 + h, x0:x0 + w] = np.clip(cor, 0, 1)


def _pintar_cabelo(A):
    x0, y0, w, h = RR["cabelo"]
    rs = np.random.RandomState(90)
    fios = np.repeat(rs.rand(1, w), h, axis=0)
    fios = 0.6 * fios + 0.4 * np.repeat(rs.rand(1, w // 4 + 1), 4, axis=1)[:, :w]
    grossos = fbm(h, w, 14, 91, 2)
    longo = fbm(h, w, 40, 92, 2)
    v = (np.arange(h) + 0.5)[:, None] / h
    brilho = 0.30 * _g((v - 0.42) / 0.16) + 0.12 * _g((v - 0.80) / 0.10)
    tom = 0.62 + 0.55 * fios + 0.35 * (grossos - 0.5) + 0.25 * (longo - 0.5)
    cor = _c(COR_CABELO)[None, None, :] * tom[..., None]
    cor = cor + (brilho * (0.4 + 0.8 * fios))[..., None] * np.array([0.085, 0.090, 0.110], np.float32)
    A[y0:y0 + h, x0:x0 + w] = np.clip(cor, 0, 1)
    for nome, c in (("cab_t", COR_CABELO), ("cilio", (30, 22, 20)), ("boca", (60, 26, 28))):
        tx, ty, tw, th_ = RR[nome]
        A[ty:ty + th_, tx:tx + tw] = _c(c)


def _pintar_olho(A):
    x0, y0, w, h = RR["olho"]
    i, j = np.mgrid[0:h, 0:w]
    px = ((j + 0.5) / w - 0.5) / 0.96
    py = (0.5 - (i + 0.5) / h) / 0.96
    r = np.hypot(px, py)
    cor = np.tile(_c((232, 226, 220)), (h, w, 1))
    veias = suave((fbm(h, w, 6, 95, 3) - 0.60) / 0.08) * suave((r - 0.27) / 0.10)
    cor = _mix(cor, _c((206, 150, 140)), veias * 0.35)
    cor = cor * (1.0 - 0.22 * suave((r - 0.30) / 0.18)[..., None])
    ir_r = 0.242
    ang = np.arctan2(py, px)
    t = np.clip(r / ir_r, 0, 1)
    estr = 0.5 + 0.5 * np.sin(ang * 34 + 5.0 * fbm(h, w, 5, 96))
    ir = _c((122, 84, 48))[None, None, :] * (1 - t[..., None]) + _c((66, 44, 28))[None, None, :] * t[..., None]
    ir = ir * (0.78 + 0.36 * estr[..., None]) * (1.0 - 0.25 * _g((t - 0.42) / 0.08)[..., None])
    cor = np.where((r < ir_r)[..., None], ir, cor)
    cor = cor * (1.0 - 0.62 * (_g((r - ir_r) / 0.016) * (r < ir_r + 0.02))[..., None])        # anel do limbo
    cor = _mix(cor, _c((10, 9, 9)), 1.0 - suave((r - 0.092) / 0.012))
    # sombra da palpebra de cima sobre o globo (assada: o olho nao gira)
    cor = cor * (1.0 - 0.42 * suave((py - 0.02) / 0.22)[..., None])
    cor = cor * (1.0 - 0.18 * suave((-py - 0.10) / 0.15)[..., None])
    br = _g(np.hypot(px + 0.060, py - 0.050) / 0.020)
    cor = _mix(cor, np.array([1.0, 1.0, 1.0], np.float32), np.clip(br * 1.4, 0, 1) * 0.92)
    A[y0:y0 + h, x0:x0 + w] = np.clip(cor, 0, 1)


def _pintar_orelha(A, lado):
    x0, y0, w, h = RR["orelha_esq" if lado < 0 else "orelha_dir"]
    u = (np.arange(w) + 0.5) / w
    v = 1.0 - (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    an = (U - 0.08) / 0.84 * 2.0 - 1.0
    bn = (V - 0.06) / 0.88 * 2.0 - 1.0
    rel = _orelha_relevo(an, bn)
    cor = _c(COR_PELE)[None, None, :] * (0.96 + 0.08 * fbm(h, w, 12, 97 + lado)[..., None])
    cor = cor + np.array([0.045, -0.020, -0.016], np.float32)                                   # orelha mais corada
    cav = rel - _box(rel.astype(np.float32), 6)
    oc = np.clip(1.0 + 85.0 * np.minimum(rel, 0.0) + 60.0 * cav, 0.42, 1.10)
    cor = cor * (1.0 - (1.0 - oc)[..., None] * np.array([0.70, 1.0, 1.06], np.float32))
    A[y0:y0 + h, x0:x0 + w] = np.clip(cor, 0, 1)


def pintar(t):
    """Atlas 1024x1024 (float 0..1) da cabeca."""
    A = np.zeros((ATLAS_ROSTO, ATLAS_ROSTO, 3), np.float32)
    A[:] = _c(COR_PELE)
    _pintar_pele(t, A)
    _pintar_cabelo(A)
    _pintar_olho(A)
    for lado in (-1, 1):
        _pintar_orelha(A, lado)
    return A
