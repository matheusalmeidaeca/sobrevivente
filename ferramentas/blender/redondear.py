# ferramentas/blender/redondear.py
"""F7: deixa a malha do boneco/zumbi mais redonda SEM mexer no esqueleto nem nos UVs. Python puro (numpy).
1) subdivide cada triangulo (1 -> 4, por niveis) interpolando posicao e UV;
2) empurra os vertices de cada parte (cabeca, tronco, bracos, pernas) para uma forma arredondada (superelipse) dentro da
   sua caixa -> silhueta de tubo/elipsoide em vez de caixa;
3) recalcula normais SUAVES (media das faces que dividem a mesma posicao) -> luz sem facetas.
Uso: tris2 = redondear.redondear(tris)    (tris no formato de exportar.ler_obj)
"""
import numpy as np

# por parte: (forca_no_plano_XZ, forca_nas_pontas_Y, forca_esferica_3D)  0 = caixa, 1 = totalmente redondo
PARAMETROS = {
    "cabeca": (0.0, 0.0, 0.40),
    "tronco": (0.90, 0.50, 0.0),
    "braco_esq": (1.0, 0.50, 0.0),
    "braco_dir": (1.0, 0.50, 0.0),
    "perna_esq": (0.90, 0.30, 0.0),
    "perna_dir": (0.90, 0.30, 0.0),
}
NIVEIS = 2  # cada nivel multiplica os triangulos por 4 (1050 -> 4200 -> 16800)


def _subdividir(tris):
    out = []
    for (a, b, c) in tris:
        g = a[0]
        pa, pb, pc = np.array(a[1]), np.array(b[1]), np.array(c[1])
        ua, ub, uc = np.array(a[3]), np.array(b[3]), np.array(c[3])
        pab, pbc, pca = (pa + pb) / 2, (pb + pc) / 2, (pc + pa) / 2
        uab, ubc, uca = (ua + ub) / 2, (ub + uc) / 2, (uc + ua) / 2

        def v(p, u):
            return (g, tuple(p), (0.0, 1.0, 0.0), tuple(u))
        A, B, C = v(pa, ua), v(pb, ub), v(pc, uc)
        AB, BC, CA = v(pab, uab), v(pbc, ubc), v(pca, uca)
        out += [[A, AB, CA], [AB, B, BC], [CA, BC, C], [AB, BC, CA]]
    return out


def _forma(p, centro, meia, par):
    """Desloca um ponto para a forma arredondada da parte."""
    fxz, fy, fs = par
    q = (np.array(p) - centro) / meia          # -1..1 dentro da caixa da parte
    q0 = q.copy()
    if fxz > 0:                                # secao circular: cada ponto vai para o circulo que o contem
        r_quad = max(abs(q[0]), abs(q[2]))
        r_circ = float(np.hypot(q[0], q[2]))
        if r_circ > 1e-6:
            s = r_quad / r_circ
            q[0] = q[0] * (1 - fxz + fxz * s)
            q[2] = q[2] * (1 - fxz + fxz * s)
    if fy > 0:                                 # as pontas (topo/base) afinam, como ombro/cotovelo/pe arredondados
        t = abs(q0[1]) ** 3
        k = 1 - fy * 0.35 * t
        q[0] *= k
        q[2] *= k
    if fs > 0:                                 # elipsoide (cabeca)
        n = float(np.linalg.norm(q0))
        qmax = float(np.max(np.abs(q0)))
        if n > 1e-6:
            s = qmax / n
            q = q * (1 - fs + fs * s)
    return centro + q * meia


def _normais_suaves(tris):
    soma = {}
    for t in tris:
        p = [np.array(c[1]) for c in t]
        n = np.cross(p[1] - p[0], p[2] - p[0])
        for c in t:
            chave = (c[0], round(c[1][0], 4), round(c[1][1], 4), round(c[1][2], 4))
            soma[chave] = soma.get(chave, 0) + n
    out = []
    for t in tris:
        novo = []
        for c in t:
            chave = (c[0], round(c[1][0], 4), round(c[1][1], 4), round(c[1][2], 4))
            n = soma[chave]
            l = np.linalg.norm(n)
            n = n / l if l > 0 else np.array([0.0, 1.0, 0.0])
            novo.append((c[0], c[1], tuple(float(x) for x in n), c[3]))
        out.append(novo)
    return out


def redondear(tris, niveis=NIVEIS, parametros=PARAMETROS):
    caixas = {}
    for t in tris:
        for c in t:
            caixas.setdefault(c[0], []).append(c[1])
    centro, meia = {}, {}
    for g, pts in caixas.items():
        a = np.array(pts)
        lo, hi = a.min(axis=0), a.max(axis=0)
        centro[g] = (lo + hi) / 2
        meia[g] = np.maximum((hi - lo) / 2, 1e-6)
    for _ in range(niveis):
        tris = _subdividir(tris)
    cache = {}
    novos = []
    for t in tris:
        nt = []
        for (g, p, n, uv) in t:
            chave = (g, p)
            if chave not in cache:
                cache[chave] = tuple(float(x) for x in _forma(p, centro[g], meia[g], parametros.get(g, (0, 0, 0))))
            nt.append((g, cache[chave], n, uv))
        novos.append(nt)
    # corrige o UV de pontos que mudaram: o UV continua o do ponto original (a textura "estica" junto com a forma)
    return _normais_suaves(novos)
