# ferramentas/blender/pegada.py
"""Pose do protagonista COM ARMA: as duas maos seguram a arma de verdade.
A arma e posicionada no referencial do TORAX (coluna); a mao direita vai na empunhadura e a esquerda no apoio
da arma (ou no bolso / carregador, na recarga). Os bracos sao resolvidos por IK numerico (minimos quadrados
amortecidos) e o pulso orienta a arma. Python + numpy; usado so ao gerar os modelos (nao roda no jogo).

Animacoes geradas: parado_<arma>, andar_<arma>, correr_<arma>, atirar_<arma>, recarregar_<arma>.
"""
import math

import numpy as np

import animacao as an
import armas as ar

TAU = 2.0 * math.pi
PALMA = np.array([0.0, -0.08, 0.0])  # centro da palma, no referencial da mao (a mao aponta para -Y)
RX_MENOS_90 = np.array(an.rotacao_euler(-90.0, 0.0, 0.0))[:3, :3]  # arma -> mao (y_arma = -z_mao, z_arma = y_mao)

ANIMACOES_ARMADAS = {  # base -> (quadros, em_loop)
    "parado": (90, True), "andar": (36, True), "correr": (22, True), "atirar": (24, False), "recarregar": (60, False),
    # F5: movimento "CoD Mobile" (sem deslizar): atirar/recarregar andando, pular e agachar
    "andarmirar": (36, True), "andarrecarregar": (72, False), "pular": (12, False), "cair": (12, False),
    "pousar": (16, False),
    "agachar": (90, True), "andaragachar": (36, True),
}

# Postura da arma no referencial do torax em repouso (x direita, y cima, z tras; torax ~ (0, 1.3, 0)):
# pr = palma direita; pitch (>0 = cano sobe), yaw (>0 = cano vai para a esquerda), roll (inclinacao)
PEGADA = {
    "pistola": dict(
        pronto=dict(pr=(0.075, 1.17, -0.32), pitch=-30.0, yaw=0.0, roll=0.0),
        correndo=dict(pr=(0.085, 1.22, -0.25), pitch=-38.0, yaw=0.0, roll=0.0),
        mirando=dict(pr=(0.060, 1.37, -0.44), pitch=1.0, yaw=0.0, roll=0.0),
        recarga=dict(pr=(0.100, 1.10, -0.27), pitch=-46.0, yaw=-6.0, roll=-18.0),
        coice=(0.05, 9.0),
    ),
    "fuzil": dict(
        pronto=dict(pr=(0.150, 1.22, -0.20), pitch=-16.0, yaw=8.0, roll=0.0),
        correndo=dict(pr=(0.150, 1.19, -0.17), pitch=-26.0, yaw=8.0, roll=0.0),
        mirando=dict(pr=(0.165, 1.37, -0.22), pitch=1.0, yaw=5.0, roll=0.0),
        recarga=dict(pr=(0.130, 1.17, -0.16), pitch=-28.0, yaw=5.0, roll=-14.0),
        coice=(0.035, 4.0),
    ),
    "escopeta": dict(
        pronto=dict(pr=(0.150, 1.21, -0.20), pitch=-20.0, yaw=8.0, roll=0.0),
        correndo=dict(pr=(0.150, 1.18, -0.17), pitch=-30.0, yaw=8.0, roll=0.0),
        mirando=dict(pr=(0.165, 1.37, -0.22), pitch=1.0, yaw=5.0, roll=0.0),
        recarga=dict(pr=(0.130, 1.17, -0.16), pitch=-30.0, yaw=5.0, roll=-14.0),
        coice=(0.075, 12.0),
    ),
}
# Pontos do corpo (referencial do torax) para a mao esquerda na recarga: bolso do carregador, no quadril esquerdo
BOLSO = (-0.20, 1.00, -0.10)


def _T(p):
    m = np.eye(4)
    m[:3, 3] = p
    return m


def _M(rx, ry, rz):
    return np.array(an.rotacao_euler(rx, ry, rz))


def _np(m):
    return np.array(m)


def _euler_de_matriz(r):
    """Inverso de rotacao_euler (ordem Ry * Rx * Rz). Devolve graus (rx, ry, rz)."""
    sx = -r[1][2]
    sx = max(-1.0, min(1.0, sx))
    ax = math.asin(sx)
    az = math.atan2(r[1][0], r[1][1])
    ay = math.atan2(r[0][2], r[2][2])
    return math.degrees(ax), math.degrees(ay), math.degrees(az)


def _rot_arma(p):
    """Rotacao 3x3 da arma no referencial do torax a partir de pitch / yaw / roll (graus)."""
    return _M(p["pitch"], p["yaw"], p["roll"])[:3, :3]


def _mistura(a, b, t):
    return {k: ((tuple(a[k][i] * (1 - t) + b[k][i] * t for i in range(3))) if k == "pr" else a[k] * (1 - t) + b[k] * t)
            for k in a}


def _suave(t):
    return an._suave(t)


def _rampa(q, a, b):
    return _suave((q - a) / (b - a))


# ---------------------------------------------------------------------------
# IK de um braco
# ---------------------------------------------------------------------------
def _resolver_braco(pose, lado, alvo_punho):
    """Acha (braco rx, ry, rz, antebraco rx) para o PUNHO ficar em `alvo_punho` (mundo). Devolve (angulos, erro_m)."""
    sg = 1.0 if lado == "dir" else -1.0
    w = an.matrizes_mundo(pose)
    w_ombro = _np(w["ombro_" + lado])
    pb = np.array(an.PIVO["braco_" + lado])
    pa = np.array(an.PIVO["antebraco_" + lado])
    pm = np.array(an.PIVO["mao_" + lado] + (1.0,)) if False else np.array(list(an.PIVO["mao_" + lado]) + [1.0])
    prior = np.array([45.0, 0.0, sg * 8.0, 70.0])
    lo = np.array([-70.0, -80.0, -70.0 if sg > 0 else -100.0, 0.0])
    hi = np.array([175.0, 80.0, 100.0 if sg > 0 else 70.0, 150.0])

    def fk(t):
        wb = w_ombro @ _T(pb) @ _M(t[0], t[1], t[2]) @ _T(-pb)
        wa = wb @ _T(pa) @ _M(t[3], 0.0, 0.0) @ _T(-pa)
        return (wa @ pm)[:3], wa

    t = prior.copy()
    lam = 1e-2
    reg = 2e-5  # puxa de leve para a postura relaxada (resolve a ambiguidade do cotovelo)
    for _ in range(30):
        p, _wa = fk(t)
        e = alvo_punho - p
        j = np.zeros((3, 4))
        for k in range(4):
            d = t.copy()
            d[k] += 0.5
            j[:, k] = (fk(d)[0] - p) / math.radians(0.5)
        a = j.T @ j + (lam + reg) * np.eye(4)
        b = j.T @ e - reg * math.radians(1.0) * (t - prior) / 1.0
        dt = np.degrees(np.linalg.solve(a, b))
        t = np.clip(t + dt, lo, hi)
        if np.linalg.norm(e) < 1e-4:
            break
    p, wa = fk(t)
    return t, wa, float(np.linalg.norm(alvo_punho - p))


def _aplicar_braco_e_mao(pose, lado, alvo_palma, r_mao_mundo):
    """IK do braco para a palma chegar em `alvo_palma` com a mao orientada por `r_mao_mundo` (3x3)."""
    alvo_punho = alvo_palma - r_mao_mundo @ PALMA
    t, wa, erro = _resolver_braco(pose, lado, alvo_punho)
    pose["ang"]["braco_" + lado] = (float(t[0]), float(t[1]), float(t[2]))
    pose["ang"]["antebraco_" + lado] = (float(t[3]), 0.0, 0.0)
    r_local = wa[:3, :3].T @ r_mao_mundo
    rx, ry, rz = _euler_de_matriz(r_local)
    pose["ang"]["mao_" + lado] = (max(-80.0, min(80.0, rx)), max(-70.0, min(70.0, ry)), max(-70.0, min(70.0, rz)))
    return erro


# ---------------------------------------------------------------------------
# Alvos por animacao
# ---------------------------------------------------------------------------
def _alvo(arma, base, q):
    """Devolve (postura, balanco, extras). postura = dict pr/pitch/yaw/roll no torax; balanco = (dx, dy, dz, dpitch, droll)."""
    P = PEGADA[arma]
    ex = {"apoio_dz": 0.0, "pesos": (1.0, 0.0, 0.0), "empurra": 0.0}
    bal = [0.0, 0.0, 0.0, 0.0, 0.0]
    if base == "parado":
        f = (q % 90.0) / 90.0
        post = P["pronto"]
        r = math.sin(2 * TAU * f)
        bal = [0.004 * math.sin(TAU * f), 0.004 * r, 0.0, 0.8 * r, 0.6 * math.sin(TAU * f)]
    elif base in ("andar", "correr"):
        n = 36.0 if base == "andar" else 22.0
        f = (q % n) / n
        post = P["pronto"] if base == "andar" else P["correndo"]
        a_y, a_x, a_p = (0.008, 0.006, 1.2) if base == "andar" else (0.020, 0.012, 3.0)
        bal = [a_x * math.sin(TAU * (f - 0.05)), a_y * math.cos(2 * TAU * (f - 0.04)),
               0.4 * a_y * math.sin(2 * TAU * (f - 0.04)), a_p * math.cos(2 * TAU * (f - 0.1)),
               1.5 * a_p * math.sin(TAU * f)]
    elif base == "andarmirar":  # andando e mirando: pernas da caminhada, arma na mira, balanco pequeno
        f = (q % 36.0) / 36.0
        post = P["mirando"]
        bal = [0.003 * math.sin(TAU * (f - 0.05)), 0.005 * math.cos(2 * TAU * (f - 0.04)), 0.0,
               0.8 * math.cos(2 * TAU * (f - 0.1)), 0.6 * math.sin(TAU * f)]
    elif base == "andarrecarregar":
        return _alvo(arma, "recarregar", (q % 72.0) * 60.0 / 72.0)
    elif base in ("pular", "cair"):
        post = _mistura(P["pronto"], P["correndo"], _rampa(q, 0.0, 4.0)) if base == "pular" else P["correndo"]
        bal = [0.0, 0.0, 0.0, 0.0, 0.0]
    elif base == "pousar":
        post = _mistura(P["correndo"], P["pronto"], _rampa(q, 0.0, 8.0))
        bal = [0.0, 0.0, 0.0, 0.0, 0.0]
    elif base in ("agachar", "andaragachar"):
        n = 90.0 if base == "agachar" else 36.0
        f = (q % n) / n
        post = P["pronto"]
        r = math.sin(2 * TAU * f)
        bal = [0.003 * math.sin(TAU * f), 0.004 * r + (0.006 * math.cos(2 * TAU * f) if base == "andaragachar" else 0.0),
               0.0, 0.6 * r, 0.0]
    elif base == "atirar":
        subida = _rampa(q, 0.0, 3.0)
        descida = _rampa(q, 17.0, 24.0)
        post = _mistura(_mistura(P["pronto"], P["mirando"], subida), P["pronto"], descida)
        k = ar_coice = P["coice"]
        pulso = an._pulso(q - 2.0)
        bal = [0.0, 0.004 * pulso, k[0] * pulso, k[1] * pulso, 0.0]
        if arma == "escopeta":
            ex["apoio_dz"] = 0.11 * an._janela(q, 8.0, 11.0, 13.0, 18.0)
            bal[3] += -5.0 * an._janela(q, 8.0, 11.0, 14.0, 19.0)
    else:  # recarregar
        sobe = _rampa(q, 0.0, 9.0) * (1.0 - _rampa(q, 50.0, 60.0))
        post = _mistura(P["pronto"], P["recarga"], sobe)
        chaves = [(0.0, (1, 0, 0)), (8.0, (1, 0, 0)), (22.0, (0, 1, 0)), (28.0, (0, 1, 0)), (38.0, (0, 0, 1)),
                  (44.0, (0, 0, 1)), (52.0, (1, 0, 0)), (60.0, (1, 0, 0))]
        pes = chaves[0][1]
        for i in range(len(chaves) - 1):
            if chaves[i][0] <= q <= chaves[i + 1][0]:
                t = _rampa(q, chaves[i][0], chaves[i + 1][0])
                pes = tuple(chaves[i][1][k] * (1 - t) + chaves[i + 1][1][k] * t for k in range(3))
                break
        ex["pesos"] = pes
        ex["empurra"] = 0.035 * an._janela(q, 38.0, 41.0, 43.0, 47.0)
        bal = [0.0, 0.0, 0.0, 3.0 * an._janela(q, 38.0, 41.0, 43.0, 47.0), 0.0]
    return post, bal, ex


def _base_pose(base, q):
    """Pernas, tronco e cabeca vem das animacoes sem arma; os bracos sao refeitos aqui."""
    if base == "parado":
        p = an._pose_parado("jogador", q)
    elif base in ("andar", "correr"):
        n = float([c for c in an.ANIMACOES["jogador"] if c[0] == base][0][1])
        p = an._pose_marcha("jogador", base, q, n)
    elif base == "atirar":
        p = an._pose_atirar("jogador", q)
        for osso, f in (("coluna", 0.45), ("quadril", 0.5), ("cabeca", 0.6)):  # menos giro: a arma ja esta alinhada
            a0 = p["ang"][osso]
            p["ang"][osso] = (a0[0], a0[1] * f, a0[2])
    elif base == "andarmirar":
        p = an._pose_marcha("jogador", "andar", q, 36.0)
        a0 = p["ang"]["coluna"]
        p["ang"]["coluna"] = (a0[0] * 0.5, a0[1] * 0.4 + 3.0, a0[2])  # tronco mais firme, ombro direito a frente
    elif base == "andarrecarregar":
        p = an._pose_marcha("jogador", "andar", q % 36.0, 36.0)
        ab = _rampa(q * 60.0 / 72.0, 0.0, 9.0) * (1.0 - _rampa(q * 60.0 / 72.0, 50.0, 60.0))
        an._somar(p, "coluna", 3.0 * ab)
        an._somar(p, "cabeca", 5.0 * ab)
    elif base in ("pular", "cair", "pousar"):
        p = an._pose_salto(base, q)
    elif base in ("agachar", "andaragachar"):
        if base == "agachar":
            p = an._pose_parado("jogador", q)
        else:
            p = an._pose_marcha("jogador", "andar", q, 36.0)
            for lado in ("esq", "dir"):  # passada menor agachado
                for osso in ("coxa_", "canela_", "pe_"):
                    a0 = p["ang"][osso + lado]
                    p["ang"][osso + lado] = (a0[0] * 0.55, a0[1], a0[2])
        _agachar(p, 0.85)
    else:  # recarregar: pe firme, tronco levemente inclinado sobre a arma, olhando para ela
        p = an._pose_parado("jogador", 0.0)
        ab = an._rampa_recarga(q) if hasattr(an, "_rampa_recarga") else _rampa(q, 0.0, 9.0) * (1.0 - _rampa(q, 50.0, 60.0))
        an._somar(p, "coluna", 5.0 * ab, 3.0 * ab, 0.0)
        an._somar(p, "pescoco", 4.0 * ab)
        an._somar(p, "cabeca", 6.0 * ab, -4.0 * ab * math.sin(TAU * q / 60.0 * 0.5), 0.0)
        for lado, sg in (("esq", -1.0), ("dir", 1.0)):
            an._somar(p, "coxa_" + lado, 6.0 * ab)
            an._somar(p, "canela_" + lado, -10.0 * ab)
    for osso in ("braco_esq", "braco_dir", "antebraco_esq", "antebraco_dir", "mao_esq", "mao_dir", "ombro_esq", "ombro_dir"):
        p["ang"].pop(osso, None)
    return p


def _agachar(p, k):
    """Agacha: quadril e joelhos dobrados, pe plano, tronco inclinado. A altura vem do aterrar()."""
    for lado in ("esq", "dir"):
        an._somar(p, "coxa_" + lado, 62.0 * k)
        an._somar(p, "canela_" + lado, -118.0 * k)
        an._somar(p, "pe_" + lado, 56.0 * k)
    an._somar(p, "quadril", -8.0 * k)
    an._somar(p, "coluna", -14.0 * k)
    an._somar(p, "pescoco", 8.0 * k)
    an._somar(p, "cabeca", 6.0 * k)


def _pernas(modo, agacha):
    """Angulos das pernas para o pulo: modo 0 em pe, 1 agachado, -0.3 impulso (esticada, ponta do pe), 2 encolhida, 3 pouso."""
    d = {}
    for lado in ("esq", "dir"):
        if modo == 2.0:
            c, k, pe = 52.0, -88.0, 12.0
        elif modo == 3.0:
            c, k, pe = 22.0, -22.0, -6.0
        elif modo == -0.3:
            c, k, pe = -10.0, -6.0, 28.0
        else:
            c, k, pe = 0.0, 0.0, 0.0
        if agacha > 0.0:
            c, k, pe = c + 62.0 * agacha, k - 118.0 * agacha, pe + 56.0 * agacha
        d["coxa_" + lado] = (c, 0.0, 0.0)
        d["canela_" + lado] = (k, 0.0, 0.0)
        d["pe_" + lado] = (pe, 0.0, 0.0)
    return d


def pose_armada(arma, base, q):
    """Pose completa (sem altura do chao) do jogador com `arma`. Devolve a pose; o erro de alcance fica em pose['erro']."""
    p = _base_pose(base, q)
    # ombros um pouco para a frente e para baixo ajudam a alcancar a arma
    an._somar(p, "ombro_dir", 0.0, -4.0, 1.0)
    an._somar(p, "ombro_esq", 0.0, 4.0, -1.0)
    post, bal, ex = _alvo(arma, base, q)
    w = an.matrizes_mundo(p)
    wc = _np(w["coluna"])
    rc = wc[:3, :3]
    pr = np.array(post["pr"]) + np.array([bal[0], bal[1], bal[2]])
    post2 = dict(post)
    post2["pitch"] += bal[3]
    post2["roll"] += bal[4]
    r_arma_torax = _rot_arma(post2)
    r_arma = rc @ r_arma_torax
    palma_dir = (wc @ np.append(pr, 1.0))[:3]
    r_mao = r_arma @ RX_MENOS_90.T
    erro_d = _aplicar_braco_e_mao(p, "dir", palma_dir, r_mao)

    # referencial da arma no mundo, com a mao direita ja posicionada
    w2 = an.matrizes_mundo(p)
    w_mao = _np(w2["mao_dir"])
    h = _T(np.array(an.PIVO["mao_dir"]) + PALMA) @ np.vstack([np.hstack([RX_MENOS_90, np.zeros((3, 1))]), [0, 0, 0, 1]])
    w_arma = w_mao @ h
    dados = ar.ARMAS[arma]
    apoio_local = np.array(dados["apoio"]) + np.array([0.0, 0.0, ex["apoio_dz"]])
    p_apoio = (w_arma @ np.append(apoio_local, 1.0))[:3]
    mag_local = np.array(dados["carregador"]) + np.array([0.0, ex["empurra"], 0.0])
    p_mag = (w_arma @ np.append(mag_local, 1.0))[:3]
    p_bolso = (wc @ np.append(np.array(BOLSO), 1.0))[:3]
    pa, pb, pm = ex["pesos"]
    alvo_esq = pa * p_apoio + pb * p_bolso + pm * p_mag
    # mao esquerda: mesma orientacao da arma, girada para a palma "abracar" o apoio por baixo/lado
    r_esq = r_arma @ RX_MENOS_90.T @ _M(0.0, 0.0, -30.0)[:3, :3]
    erro_e = _aplicar_braco_e_mao(p, "esq", alvo_esq, r_esq)
    p["erro"] = max(erro_d, erro_e)
    return p


def listar():
    """[(nome, quadros, loop)] das animacoes armadas, na ordem."""
    saida = []
    for a in ar.ORDEM:
        for b, (n, loop) in ANIMACOES_ARMADAS.items():
            saida.append(("%s_%s" % (b, a), n, loop))
    return saida


def separar(nome):
    base, arma = nome.split("_", 1)
    return base, arma
