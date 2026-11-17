# ferramentas/hd/kit.py
"""F8: ferramentas de modelagem procedural (Python + numpy, sem Blender).
Tubo (loft ao longo de Y com secao superelipse), remendos sobre a superficie (bolsos, cinto, punho), elipsoides,
tubos por caminho (dedos, cabelo, cadarco), caixas e uma Malha que solda normais suaves com limite de angulo.
Sistema do jogo: Y para cima, frente = -Z, direita = +X. Theta do Tubo: 0 = costas (+Z), pi/2 = direita (+X), pi = frente (-Z).
"""
import math

import numpy as np

ATLAS = 1024
FINO = False   # True = ALTA POLIGONAGEM: liga os detalhes finos (trama, costuras, vincos) dos Campos


def _sp(a, p):
    return np.sign(a) * np.abs(a) ** p


def norm(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v, axis=-1, keepdims=True)
    return v / np.maximum(n, 1e-12)


def suave(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def uv_atlas(regiao, u, v):
    """(u, v) 0..1 dentro da regiao (v=1 em cima) -> uv do OBJ (v para cima) no atlas."""
    x0, y0, w, h = regiao
    return np.stack([(x0 + u * w) / ATLAS, 1.0 - (y0 + (1.0 - v) * h) / ATLAS], axis=-1)


class Campo:
    """Soma de gaussianas 3D (bossas/covas ao longo da normal radial) + funcoes extras f(P, th, y) -> metros."""

    def __init__(self):
        self.bossas = []
        self.funcoes = []
        self.finas = []

    def bossa(self, cx, cy, cz, sx, sy, sz, amp, espelho=False):
        self.bossas.append((cx, cy, cz, sx, sy, sz, amp))
        if espelho and abs(cx) > 1e-9:
            self.bossas.append((-cx, cy, cz, sx, sy, sz, amp))
        return self

    def fn(self, f):
        self.funcoes.append(f)
        return self

    def fina(self, f):
        """Detalhe fino f(P, th, y) -> metros: so existe na malha ALTA (kit.FINO=True); na baixa vira normal map."""
        self.finas.append(f)
        return self

    def __call__(self, P, th, y):
        P = P.reshape(-1, 3)
        tot = np.zeros(len(P))
        if self.bossas:
            B = np.array(self.bossas)
            d = (P[:, None, :] - B[None, :, 0:3]) / B[None, :, 3:6]
            tot += (np.exp(-0.5 * (d ** 2).sum(-1)) * B[None, :, 6]).sum(1)
        for f in self.funcoes:
            tot += f(P, np.ravel(th), np.ravel(y))
        if FINO:
            for f in self.finas:
                tot += f(P, np.ravel(th), np.ravel(y))
        return tot


class Malha:
    """Acumula primitivas; cada uma ja traz normais suaves (soldadas por posicao com limite de angulo)."""

    def __init__(self):
        self.P, self.N, self.UV, self.G, self.T = [], [], [], [], []
        self.n = 0
        self.contagem = {}

    def add(self, grupo, P, UV, T, Vout, angulo=60.0, nome=None):
        P = np.asarray(P, float).reshape(-1, 3)
        UV = np.asarray(UV, float).reshape(-1, 2)
        T = np.asarray(T, int).reshape(-1, 3).copy()
        Vout = np.asarray(Vout, float).reshape(-1, 3)
        # tira triangulos degenerados e orienta cada face para fora
        a, b, c = P[T[:, 0]], P[T[:, 1]], P[T[:, 2]]
        fn = np.cross(b - a, c - a)
        area = np.linalg.norm(fn, axis=1)
        ok = area > 1e-12
        T, fn, area = T[ok], fn[ok], area[ok]
        fo = Vout[T].mean(axis=1)
        flip = (fn * fo).sum(1) < 0
        T[flip] = T[flip][:, [0, 2, 1]]
        fn[flip] *= -1
        fnn = fn / area[:, None]
        # normais suaves por posicao soldada
        chave = np.round(P * 20000).astype(np.int64)
        _, inv = np.unique(chave, axis=0, return_inverse=True)
        inv = inv.ravel()
        cos_lim = math.cos(math.radians(angulo))
        grupos = {}
        for fi, tri in enumerate(T):
            for k in range(3):
                grupos.setdefault(inv[tri[k]], []).append(fi)
        # vertices usados por faces de angulos diferentes: duplica o vertice por face em arestas duras
        # (simplificacao: normal por canto) -> expande para cantos
        cantos_p, cantos_n, cantos_uv = [], [], []
        for fi, tri in enumerate(T):
            for k in range(3):
                lst = np.array(grupos[inv[tri[k]]])
                m = (fnn[lst] @ fnn[fi]) > cos_lim
                s = fn[lst][m].sum(0)
                l = np.linalg.norm(s)
                cantos_n.append(s / l if l > 0 else fnn[fi])
                cantos_p.append(P[tri[k]])
                cantos_uv.append(UV[tri[k]])
        base = self.n
        self.P.append(np.array(cantos_p))
        self.N.append(np.array(cantos_n))
        self.UV.append(np.array(cantos_uv))
        self.G += [grupo] * len(cantos_p)
        self.n += len(cantos_p)
        if nome:
            self.contagem[nome] = self.contagem.get(nome, 0) + len(T)

    def triangulos(self):
        P = np.vstack(self.P)
        N = np.vstack(self.N)
        UV = np.vstack(self.UV)
        out = []
        for i in range(0, self.n, 3):
            out.append([(self.G[i + k], tuple(float(x) for x in P[i + k]), tuple(float(x) for x in N[i + k]),
                         tuple(float(x) for x in UV[i + k])) for k in range(3)])
        return out


# ---------------------------------------------------------------------------------------------- Tubo
class Tubo:
    """Loft ao longo de Y. aneis = [(y, cx, cz, rx, rz, expoente)]. Colunas theta, linhas t (0 = base, 1 = topo).
    ybase/ytop: funcoes de theta (bordas irregulares, ex.: barra rasgada)."""

    def __init__(self, aneis, regiao, campo=None, ybase=None, ytop=None):
        a = np.array(aneis, float)
        self.regiao = regiao
        self.campo = campo
        self.y0 = float(a[0, 0])
        self.y1 = float(a[-1, 0])
        # F13: np.interp exige y crescente. Os bracos listam os aneis de cima para baixo e por isso saiam como
        # cilindros de raio constante (sem ombro, sem afinar no punho). y0/y1 continuam sendo o 1o e o ultimo anel.
        a = a[np.argsort(a[:, 0], kind="stable")]
        self.ys = a[:, 0]
        self.a = a
        self.ybase = ybase or (lambda th: np.full_like(np.asarray(th, float), self.y0))
        self.ytop = ytop or (lambda th: np.full_like(np.asarray(th, float), self.y1))

    def y_de(self, th, t):
        yb, yt = self.ybase(th), self.ytop(th)
        return yb + t * (yt - yb)

    def _interp(self, y, k):
        return np.interp(y, self.ys, self.a[:, k])

    def base(self, th, y):
        """Ponto da superficie SEM campo + direcao radial."""
        cx, cz = self._interp(y, 1), self._interp(y, 2)
        rx, rz = self._interp(y, 3), self._interp(y, 4)
        n = self._interp(y, 5)
        s, c = np.sin(th), np.cos(th)
        e = 2.0 / n
        x = cx + rx * _sp(s, e)
        z = cz + rz * _sp(c, e)
        P = np.stack([x, y, z], axis=-1)
        rad = np.stack([(x - cx) / rx ** 2, np.zeros_like(x), (z - cz) / rz ** 2], axis=-1)
        return P, norm(rad)

    def ponto(self, th, y, alt=0.0):
        th, y = np.broadcast_arrays(np.asarray(th, float), np.asarray(y, float))
        P, rad = self.base(th, y)
        d = np.zeros(th.shape)
        if self.campo is not None:
            d = self.campo(P, th, y).reshape(th.shape)
        P = P + rad * (d + alt)[..., None]
        return P, rad

    def uv(self, th, t):
        u = np.asarray(th) / (2 * math.pi)
        return uv_atlas(self.regiao, u, np.asarray(t) + 0 * u)

    def sombra(self, th, y):
        """Deslocamento radial (para pintar o 'ambient occlusion' na textura)."""
        if self.campo is None:
            return np.zeros(np.shape(th))
        th, y = np.broadcast_arrays(np.asarray(th, float), np.asarray(y, float))
        P, _ = self.base(th, y)
        return self.campo(P, th, y).reshape(th.shape)

    def malha(self, m, grupo, nseg, trows, tampa_topo=False, tampa_base=False, nome=None):
        th = np.linspace(0, 2 * math.pi, nseg + 1)
        t = np.asarray(trows, float)
        TH, TT = np.meshgrid(th, t)
        Y = self.y_de(TH, TT)
        P, rad = self.ponto(TH, Y)
        UV = self.uv(TH, TT)
        nr = len(t)
        Pf, Uf, Vf = [P.reshape(-1, 3)], [UV.reshape(-1, 2)], [rad.reshape(-1, 3)]
        idx = np.arange(nr * (nseg + 1)).reshape(nr, nseg + 1)
        quads = []
        for i in range(nr - 1):
            for j in range(nseg):
                a, b, c, d = idx[i, j], idx[i, j + 1], idx[i + 1, j + 1], idx[i + 1, j]
                quads += [[a, b, c], [a, c, d]]
        extra = nr * (nseg + 1)
        for tampa, i, vo in ((tampa_base, 0, (0, -1, 0)), (tampa_topo, nr - 1, (0, 1, 0))):
            if not tampa:
                continue
            ring = P[i]
            centro = ring[:-1].mean(0)
            Pf.append(np.tile(centro, (nseg + 1, 1)))
            Uf.append(UV[i])
            Vf.append(np.tile(np.array(vo, float), (nseg + 1, 1)))
            for j in range(nseg):
                quads.append([idx[i, j], idx[i, j + 1], extra + j + 1])
                quads.append([idx[i, j], extra + j + 1, extra + j])
            extra += nseg + 1
        m.add(grupo, np.vstack(Pf), np.vstack(Uf), quads, np.vstack(Vf), nome=nome)


def remendo(m, tubo, grupo, th0, th1, y0, y1, alt, nth, ny, uv_modo="sup", tile=None, saia=True, nome=None,
            fecha=False):
    """Painel levantado `alt` metros sobre a superficie do tubo (bolsos, cinto, punho, passadores).
    uv_modo 'sup': continua a textura do tubo; 'tile': usa um tile (x0, y0, w, h) esticado uma vez."""
    th = np.linspace(th0, th1, nth + 1)
    y = np.linspace(y0, y1, ny + 1)
    TH, Y = np.meshgrid(th, y)
    P, rad = tubo.ponto(TH, Y, alt)
    if uv_modo == "sup":
        t = (Y - tubo.y0) / (tubo.y1 - tubo.y0)
        UV = tubo.uv(TH, t)
    else:
        u = (TH - th0) / (th1 - th0 + 1e-12) * 0.8 + 0.1
        v = (Y - y0) / (y1 - y0 + 1e-12) * 0.8 + 0.1
        UV = uv_atlas(tile, u, v)
    idx = np.arange((ny + 1) * (nth + 1)).reshape(ny + 1, nth + 1)
    Pf, Uf, Vf = [P.reshape(-1, 3)], [UV.reshape(-1, 2)], [rad.reshape(-1, 3)]
    T = []
    for i in range(ny):
        for j in range(nth):
            a, b, c, d = idx[i, j], idx[i, j + 1], idx[i + 1, j + 1], idx[i + 1, j]
            T += [[a, b, c], [a, c, d]]
    if saia:  # paredes laterais descendo ate a superficie (sem alt)
        P0, _ = tubo.ponto(TH, Y, 0.0)
        base = len(np.vstack(Pf))
        off = base
        Pf.append(P0.reshape(-1, 3))
        Uf.append(UV.reshape(-1, 2))
        Vf.append(rad.reshape(-1, 3))
        idx0 = idx + off
        bordas = [[(0, j) for j in range(nth + 1)], [(ny, j) for j in range(nth + 1)],
                  [(i, 0) for i in range(ny + 1)], [(i, nth) for i in range(ny + 1)]]
        if fecha:  # painel em anel completo (cinto): sem laterais em theta
            bordas = bordas[:2]
        for bd in bordas:
            for k in range(len(bd) - 1):
                (i0, j0), (i1, j1) = bd[k], bd[k + 1]
                T += [[idx[i0, j0], idx[i1, j1], idx0[i1, j1]], [idx[i0, j0], idx0[i1, j1], idx0[i0, j0]]]
    m.add(grupo, np.vstack(Pf), np.vstack(Uf), T, np.vstack(Vf), nome=nome)


# ---------------------------------------------------------------------------------------------- primitivas soltas
def _uv_tile(tile, n, jitter=0.0):
    x0, y0, w, h = tile
    u, v = 0.5, 0.5
    return np.tile(uv_atlas(tile, u, v), (n, 1))


def elipsoide(m, grupo, centro, raios, tile=None, rot=None, nu=12, nv=8, nome=None, uv_fn=None,
              lat=(-90.0, 90.0), lon=(0.0, 360.0), angulo=70.0):
    """Elipsoide (ou pedaco: lat/lon em graus; lon 0 = frente -Z, cresce para a direita +X)."""
    la = np.radians(np.linspace(lat[0], lat[1], nv + 1))
    lo = np.radians(np.linspace(lon[0], lon[1], nu + 1))
    LA, LO = np.meshgrid(la, lo, indexing="ij")
    x = np.cos(LA) * np.sin(LO)
    y = np.sin(LA)
    z = -np.cos(LA) * np.cos(LO)
    U = np.stack([x, y, z], -1)
    r = np.asarray(raios, float)
    P = U * r
    Vout = norm(U / r)
    if rot is not None:
        R = np.asarray(rot, float)
        P = P @ R.T
        Vout = Vout @ R.T
    P = P + np.asarray(centro, float)
    if uv_fn is not None:
        UV = uv_fn(P - np.asarray(centro, float), U)
    else:
        UV = _uv_tile(tile, P.shape[0] * P.shape[1])
    idx = np.arange((nv + 1) * (nu + 1)).reshape(nv + 1, nu + 1)
    T = []
    for i in range(nv):
        for j in range(nu):
            a, b, c, d = idx[i, j], idx[i, j + 1], idx[i + 1, j + 1], idx[i + 1, j]
            T += [[a, b, c], [a, c, d]]
    m.add(grupo, P.reshape(-1, 3), UV.reshape(-1, 2), T, Vout.reshape(-1, 3), angulo=angulo, nome=nome)


def rotx(g):
    a = math.radians(g)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def roty(g):
    a = math.radians(g)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rotz(g):
    a = math.radians(g)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def _spline(pts, n):
    """Catmull-Rom por pontos de controle, n pontos no total."""
    pts = np.asarray(pts, float)
    if len(pts) < 3:
        return np.array([pts[0] + (pts[-1] - pts[0]) * t for t in np.linspace(0, 1, n)])
    p = np.vstack([pts[0] * 2 - pts[1], pts, pts[-1] * 2 - pts[-2]])
    out = []
    seg = len(pts) - 1
    for k in range(n):
        s = k / (n - 1) * seg
        i = min(int(s), seg - 1)
        t = s - i
        p0, p1, p2, p3 = p[i], p[i + 1], p[i + 2], p[i + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                          + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    return np.array(out)


def tubo_caminho(m, grupo, pontos, raios, tile=None, nseg=8, nn=10, tampas=True, nome=None, uv_regiao=None,
                 achatar=None, angulo=70.0):
    """Tubo de secao circular ao longo de uma curva suave (dedos, cabelo, cadarco, sobrancelha).
    raios: lista com um raio por ponto de controle. achatar = (fator, eixo_vetor): achata a secao nesse eixo."""
    C = _spline(pontos, nn)
    R = np.interp(np.linspace(0, 1, nn), np.linspace(0, 1, len(raios)), raios)
    T_ = np.gradient(C, axis=0)
    T_ = norm(T_)
    ref = np.array([0.0, 0.0, 1.0]) if abs(T_[0][2]) < 0.9 else np.array([1.0, 0.0, 0.0])
    u = norm(np.cross(T_[0], ref))
    anel = []
    for k in range(nn):
        if k > 0:
            u = u - T_[k] * np.dot(u, T_[k])
            if np.linalg.norm(u) < 1e-9:
                u = norm(np.cross(T_[k], ref))
            u = norm(u)
        v = np.cross(T_[k], u)
        anel.append((u, v))
    ang = np.linspace(0, 2 * math.pi, nseg + 1)
    P = np.zeros((nn, nseg + 1, 3))
    V = np.zeros_like(P)
    for k in range(nn):
        u, v = anel[k]
        for j, a in enumerate(ang):
            d = u * math.cos(a) + v * math.sin(a)
            rr = R[k]
            q = d * rr
            if achatar is not None:
                f, eje = achatar
                eje = norm(eje)
                q = q - eje * np.dot(q, eje) * (1 - f)
            P[k, j] = C[k] + q
            V[k, j] = d
    if uv_regiao is not None:
        uu = ang[None, :] / (2 * math.pi) + 0 * np.linspace(0, 1, nn)[:, None]
        vv = np.linspace(1, 0, nn)[:, None] + 0 * ang[None, :]
        UV = uv_atlas(uv_regiao, uu * 0.9 + 0.05, vv * 0.9 + 0.05)
    else:
        UV = np.tile(uv_atlas(tile, 0.5, 0.5), (nn * (nseg + 1), 1)).reshape(nn, nseg + 1, 2)
    idx = np.arange(nn * (nseg + 1)).reshape(nn, nseg + 1)
    T = []
    for k in range(nn - 1):
        for j in range(nseg):
            a, b, c, d = idx[k, j], idx[k, j + 1], idx[k + 1, j + 1], idx[k + 1, j]
            T += [[a, b, c], [a, c, d]]
    Pf, Uf, Vf = [P.reshape(-1, 3)], [UV.reshape(-1, 2)], [V.reshape(-1, 3)]
    extra = nn * (nseg + 1)
    for k, sg in ((0, -1), (nn - 1, 1)):
        if not tampas:
            continue
        Pf.append(np.tile(C[k] + T_[k] * R[k] * 0.55 * sg * (1 if sg > 0 else 1), (nseg + 1, 1)))
        Uf.append(UV[k])
        Vf.append(np.tile(T_[k] * sg, (nseg + 1, 1)))
        for j in range(nseg):
            T.append([idx[k, j], idx[k, j + 1], extra + j + 1])
            T.append([idx[k, j], extra + j + 1, extra + j])
        extra += nseg + 1
    m.add(grupo, np.vstack(Pf), np.vstack(Uf), T, np.vstack(Vf), angulo=angulo, nome=nome)


def caixa(m, grupo, centro, dim, tile, rot=None, nome=None):
    """Caixa com cantos levemente suavizados (angulo 40)."""
    d = np.asarray(dim, float) / 2
    V = np.array([[sx, sy, sz] for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)], float) * d
    F = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    P, T, Vo = [], [], []
    for f in F:
        b = len(P)
        for k in f:
            P.append(V[k])
            Vo.append(V[k])
        T += [[b, b + 1, b + 2], [b, b + 2, b + 3]]
    P = np.array(P)
    Vo = np.array(Vo)
    if rot is not None:
        P = P @ np.asarray(rot).T
        Vo = Vo @ np.asarray(rot).T
    P = P + np.asarray(centro, float)
    UV = np.tile(uv_atlas(tile, 0.5, 0.5), (len(P), 1))
    m.add(grupo, P, UV, T, Vo, angulo=35.0, nome=nome)


# ---------------------------------------------------------------------------------------------- texturas
def ruido(h, w, escala, sem):
    from PIL import Image
    r = np.random.RandomState(sem)
    g = r.rand(max(2, h // escala + 2), max(2, w // escala + 2)).astype(np.float32)
    im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(im).astype(np.float32) / 255.0


def fbm(h, w, escala, sem, oit=3):
    t = np.zeros((h, w), np.float32)
    a, s = 1.0, 0.0
    for o in range(oit):
        t += ruido(h, w, max(1, escala >> o), sem + o * 17) * a
        s += a
        a *= 0.5
    return t / s


def rgb(r, g, b):
    return np.array([r, g, b], np.float32) / 255.0
