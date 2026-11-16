# ferramentas/blender/animacao.py
"""Esqueleto, pesos de pele e animacoes do boneco. Python PURO (sem bpy):
roda dentro do Blender, no Python do sistema e em qualquer teste.

Sistema do JOGO (o mesmo do F0): Y para cima, 1 unidade = 1 m, origem nos pes,
FRENTE = -Z, esquerda = -X. Angulos em GRAUS (regra da mao direita, no sistema Y para cima):
  rx > 0: osso que pende para baixo vai para a FRENTE; osso que aponta para cima vai para TRAS
  ry > 0: gira para a ESQUERDA do personagem (anti-horario visto de cima)
  rz > 0: osso que pende para baixo vai para +X (direita do personagem)
Rotacao de cada osso = Ry * Rx * Rz, em torno do PIVO (cabeca do osso), no referencial do pai.

Marcha: curvas de articulacao baseadas na marcha humana (quadril, joelho, tornozelo por fase do passo),
rotacao e inclinacao da pelve, contra-rotacao dos ombros, balanco dos bracos com atraso (cotovelo e mao
seguem o ombro), cabeca estabilizada. Tiro, ataque e morte usam "sobreposicao": cada osso reage com um
pequeno atraso em relacao ao pai.
"""
import math

FPS = 30

# ---------------------------------------------------------------------------
# Esqueleto (18 ossos; orcamento da secao 24.3: ate 25). Pais sempre antes dos filhos.
# nome, pai, pivo (cabeca do osso), ponta (so para desenhar o osso no Blender)
# ---------------------------------------------------------------------------
OSSOS = [
    ("quadril", None, (0.0, 0.92, 0.0), (0.0, 1.00, 0.0)),
    ("coluna", "quadril", (0.0, 1.00, 0.0), (0.0, 1.50, 0.0)),
    ("pescoco", "coluna", (0.0, 1.50, 0.0), (0.0, 1.56, 0.0)),
    ("cabeca", "pescoco", (0.0, 1.56, 0.0), (0.0, 1.80, 0.0)),
    ("ombro_esq", "coluna", (-0.19, 1.44, 0.0), (-0.255, 1.44, 0.0)),
    ("braco_esq", "ombro_esq", (-0.255, 1.44, 0.0), (-0.255, 1.20, 0.0)),
    ("antebraco_esq", "braco_esq", (-0.255, 1.20, 0.0), (-0.255, 0.84, 0.0)),
    ("mao_esq", "antebraco_esq", (-0.255, 0.84, 0.0), (-0.255, 0.68, 0.0)),
    ("ombro_dir", "coluna", (0.19, 1.44, 0.0), (0.255, 1.44, 0.0)),
    ("braco_dir", "ombro_dir", (0.255, 1.44, 0.0), (0.255, 1.20, 0.0)),
    ("antebraco_dir", "braco_dir", (0.255, 1.20, 0.0), (0.255, 0.84, 0.0)),
    ("mao_dir", "antebraco_dir", (0.255, 0.84, 0.0), (0.255, 0.68, 0.0)),
    ("coxa_esq", "quadril", (-0.095, 0.91, 0.0), (-0.095, 0.50, 0.0)),
    ("canela_esq", "coxa_esq", (-0.095, 0.50, 0.0), (-0.095, 0.09, 0.0)),
    ("pe_esq", "canela_esq", (-0.095, 0.09, 0.0), (-0.095, 0.04, -0.13)),
    ("coxa_dir", "quadril", (0.095, 0.91, 0.0), (0.095, 0.50, 0.0)),
    ("canela_dir", "coxa_dir", (0.095, 0.50, 0.0), (0.095, 0.09, 0.0)),
    ("pe_dir", "canela_dir", (0.095, 0.09, 0.0), (0.095, 0.04, -0.13)),
]
NOMES_OSSOS = [o[0] for o in OSSOS]
PAI = {o[0]: o[1] for o in OSSOS}
PIVO = {o[0]: o[2] for o in OSSOS}

# Animacoes por personagem: (nome, quadros, em_loop). Todas gravam os quadros 0..quadros (inclusive);
# nas de loop o ultimo quadro e igual ao primeiro (fecha o ciclo sem pulo).
ANIMACOES = {
    "jogador": [("parado", 90, True), ("andar", 36, True), ("correr", 22, True),
                ("atirar", 24, False), ("morrer", 54, False)],
    "zumbi": [("parado", 180, True), ("andar", 42, True), ("correr", 26, True),
              ("atacar", 72, True), ("morrer", 60, False)],
}

# Quadros mostrados na folha de preview (1 por animacao).
QUADRO_PREVIA = {
    "parado": 0, "andar": 6, "correr": 4, "atirar": 3, "atacar": 14, "morrer": 54,
}

# Velocidade (m/s) do corpo que cada ciclo cobre com 1x de reproducao (medida pela velocidade do pe de apoio).
VELOCIDADE_REFERENCIA = {
    ("jogador", "andar"): 1.25, ("jogador", "correr"): 2.9,
    ("zumbi", "andar"): 1.0, ("zumbi", "correr"): 2.1,
}


def identidade():
    return [[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0], [0.0, 0.0, 1.0, 0.0], [0.0, 0.0, 0.0, 1.0]]


def multiplicar(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translacao(x, y, z):
    m = identidade()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def rotacao_euler(rx, ry, rz):
    """Rotacao 4x4 = Ry * Rx * Rz (angulos em graus)."""
    ax, ay, az = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx = math.cos(ax), math.sin(ax)
    cy, sy = math.cos(ay), math.sin(ay)
    cz, sz = math.cos(az), math.sin(az)
    mx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    my = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    mz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return multiplicar(my, multiplicar(mx, mz))


def aplicar(m, p):
    x, y, z = p
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


def quaternio_da_matriz(m):
    """Quaternio (x, y, z, w) de uma matriz de rotacao (parte 3x3 de m)."""
    t = m[0][0] + m[1][1] + m[2][2]
    if t > 0.0:
        s = math.sqrt(t + 1.0) * 2.0
        w = 0.25 * s
        x = (m[2][1] - m[1][2]) / s
        y = (m[0][2] - m[2][0]) / s
        z = (m[1][0] - m[0][1]) / s
    elif m[0][0] > m[1][1] and m[0][0] > m[2][2]:
        s = math.sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]) * 2.0
        w = (m[2][1] - m[1][2]) / s
        x = 0.25 * s
        y = (m[0][1] + m[1][0]) / s
        z = (m[0][2] + m[2][0]) / s
    elif m[1][1] > m[2][2]:
        s = math.sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]) * 2.0
        w = (m[0][2] - m[2][0]) / s
        x = (m[0][1] + m[1][0]) / s
        y = 0.25 * s
        z = (m[1][2] + m[2][1]) / s
    else:
        s = math.sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]) * 2.0
        w = (m[1][0] - m[0][1]) / s
        x = (m[0][2] + m[2][0]) / s
        y = (m[1][2] + m[2][1]) / s
        z = 0.25 * s
    n = math.sqrt(x * x + y * y + z * z + w * w)
    return (x / n, y / n, z / n, w / n)


def pose_vazia():
    return {"ang": {}, "raiz": [0.0, 0.0, 0.0], "salto": 0.0}


def rotacao_local(pose, nome):
    """Matriz 4x4 so de rotacao do osso (em torno da origem; o pivo entra em matriz_do_osso)."""
    rx, ry, rz = pose["ang"].get(nome, (0.0, 0.0, 0.0))
    return rotacao_euler(rx, ry, rz)


def matriz_relativa(pose, nome):
    """D = T(raiz se for o quadril) * T(pivo) * R * T(-pivo): movimento do osso em relacao ao pai."""
    px, py, pz = PIVO[nome]
    d = multiplicar(translacao(px, py, pz), multiplicar(rotacao_local(pose, nome), translacao(-px, -py, -pz)))
    if PAI[nome] is None:
        d = multiplicar(translacao(*pose["raiz"]), d)
    return d


def matrizes_mundo(pose):
    """W[osso] leva um ponto da pose de repouso para a pose atual (pele linear: v' = soma peso * W * v)."""
    w = {}
    for nome in NOMES_OSSOS:
        d = matriz_relativa(pose, nome)
        pai = PAI[nome]
        w[nome] = d if pai is None else multiplicar(w[pai], d)
    return w


def deformar(w, pesos_vertice, p):
    """Pele linear de um ponto."""
    x = y = z = 0.0
    for osso, peso in pesos_vertice:
        q = aplicar(w[osso], p)
        x += q[0] * peso
        y += q[1] * peso
        z += q[2] * peso
    return (x, y, z)


def _suave(t):
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    return t * t * (3.0 - 2.0 * t)


def limpar_pesos(lista):
    """Tira pesos nulos e normaliza para somar 1."""
    util = [(o, p) for o, p in lista if p > 1e-6]
    total = sum(p for _, p in util)
    return [(o, p / total) for o, p in util]


def _hermite(q, pontos):
    n = len(pontos)
    if q <= pontos[0][0]:
        return pontos[0][1]
    if q >= pontos[-1][0]:
        return pontos[-1][1]
    for i in range(n - 1):
        q0, v0 = pontos[i]
        q1, v1 = pontos[i + 1]
        if q <= q1:
            h = q1 - q0
            t = (q - q0) / h
            m0 = 0.0 if i == 0 else (v1 - pontos[i - 1][1]) / (q1 - pontos[i - 1][0])
            m1 = 0.0 if i + 1 == n - 1 else (pontos[i + 2][1] - v0) / (pontos[i + 2][0] - q0)
            t2, t3 = t * t, t * t * t
            return ((2 * t3 - 3 * t2 + 1) * v0 + (t3 - 2 * t2 + t) * h * m0
                    + (-2 * t3 + 3 * t2) * v1 + (t3 - t2) * h * m1)
    return pontos[-1][1]


def _periodico(q, pontos, periodo):
    """Hermite periodico: `pontos` sem o quadro de fechamento; repete o ciclo antes e depois."""
    estendido = ([(qq - periodo, v) for qq, v in pontos[-2:]] + list(pontos)
                 + [(qq + periodo, v) for qq, v in pontos[:2]])
    q = q % periodo
    return _hermite(q, estendido)


def aterrar(pose, pontos):
    """Ajusta a altura do quadril para o ponto mais baixo da malha ficar em y = 0 (+ quicada).
    `pontos` = lista de (x, y, z, pesos). So mexe em pose['raiz'][1]. Nunca deixa o corpo afundar no chao."""
    pose["raiz"][1] = 0.0
    w = matrizes_mundo(pose)
    menor = None
    for x, y, z, pesos in pontos:
        yy = deformar(w, pesos, (x, y, z))[1]
        if menor is None or yy < menor:
            menor = yy
    pose["raiz"][1] = -menor + pose.get("salto", 0.0)
    return pose


# ---------------------------------------------------------------------------
# Pele: pesos por vertice (no maximo 2 ossos, somando 1), dependendo da peca do OBJ e da posicao
# ---------------------------------------------------------------------------
def pesos_do_vertice(grupo, x, y, z):
    """Pesos de pele de um vertice da peca `grupo` (nomes do OBJ do F0)."""
    if grupo == "cabeca":
        if y < 1.54:  # base do pescoco: coluna -> pescoco
            t = _suave((y - 1.49) / 0.05)
            return [("coluna", 1.0 - t), ("pescoco", t)]
        u = _suave((y - 1.54) / 0.05)  # pescoco -> cabeca
        return [("pescoco", 1.0 - u), ("cabeca", u)]
    if grupo == "tronco":
        t = _suave((y - 0.90) / 0.22)  # cintura: quadril -> coluna
        lado = "esq" if x < 0 else "dir"
        w = _suave((abs(x) - 0.10) / 0.09) * _suave((y - 1.30) / 0.10) * 0.5  # ombro acompanha a clavicula
        if w > 0.0:
            return [("coluna", 1.0 - w), ("ombro_" + lado, w)]
        return [("quadril", 1.0 - t), ("coluna", t)]
    lado = "esq" if grupo.endswith("_esq") else "dir"
    if grupo.startswith("braco"):
        if y > 1.28:  # topo: ombro (clavicula) <-> braco
            s = _suave((y - 1.34) / 0.10) * 0.55
            return [("braco_" + lado, 1.0 - s), ("ombro_" + lado, s)]
        if y > 0.90:  # cotovelo: braco acima de 1,28, antebraco abaixo de 1,12
            t = _suave((1.28 - y) / 0.16)
            return [("braco_" + lado, 1.0 - t), ("antebraco_" + lado, t)]
        u = _suave((0.90 - y) / 0.10)  # punho: antebraco acima de 0,90, mao abaixo de 0,80
        return [("antebraco_" + lado, 1.0 - u), ("mao_" + lado, u)]
    if grupo.startswith("perna"):
        if y <= 0.085 and (z < -0.045 or z > 0.045):  # caixa do pe (so ela passa de |z| = 0,045)
            return [("pe_" + lado, 1.0)]
        if y >= 0.12:
            a = _suave((y - 0.44) / 0.12)  # joelho: coxa acima de 0,56, canela abaixo de 0,44
            return [("coxa_" + lado, a), ("canela_" + lado, 1.0 - a)]
        b = _suave((y - 0.07) / 0.05)  # tornozelo: pe ate 0,07, canela a partir de 0,12
        return [("canela_" + lado, b), ("pe_" + lado, 1.0 - b)]
    raise ValueError("peca desconhecida: " + grupo)


# ---------------------------------------------------------------------------
# Auxiliares de animacao
# ---------------------------------------------------------------------------
def _somar(pose, osso, rx=0.0, ry=0.0, rz=0.0):
    a = pose["ang"].get(osso, (0.0, 0.0, 0.0))
    pose["ang"][osso] = (a[0] + rx, a[1] + ry, a[2] + rz)


def _janela(q, a, b, c, d):
    """Sobe suave de a a b, segura ate c e desce suave ate d (0..1)."""
    return _suave((q - a) / (b - a)) * (1.0 - _suave((q - c) / (d - c)))


def _pulso(q):
    """Impulso amortecido: sobe suave ate 1 em q = 1,5 e depois decai oscilando (recuo, tranco)."""
    if q <= 0.0:
        return 0.0
    if q < 1.5:
        return _suave(q / 1.5)
    d = q - 1.5
    return math.exp(-d / 3.5) * math.cos(0.30 * d)


def _avaliar(chaves, q, periodo=None, atrasos=None):
    """Pose por chaves. chaves = [(quadro, {osso: (rx, ry, rz)}, (dx, dz) da raiz em metros), ...].
    `atrasos` = {osso: quadros}: o osso segue a curva com esse atraso (sobreposicao / follow-through)."""
    atrasos = atrasos or {}
    pose = pose_vazia()
    ossos = set()
    for _, angs, _ in chaves:
        ossos.update(angs.keys())
    for osso in sorted(ossos):
        qq = q - atrasos.get(osso, 0.0)
        canais = []
        for k in range(3):
            pts = [(c[0], c[1].get(osso, (0.0, 0.0, 0.0))[k]) for c in chaves]
            canais.append(_periodico(qq, pts, periodo) if periodo else _hermite(qq, pts))
        pose["ang"][osso] = tuple(canais)
    for k, eixo in ((0, 0), (1, 2)):
        pts = [(c[0], c[2][k]) for c in chaves]
        pose["raiz"][eixo] = _periodico(q, pts, periodo) if periodo else _hermite(q, pts)
    return pose


def _aplicar_lados(d):
    """Expande {'braco': (..)} em braco_esq/braco_dir (rz espelhado) quando a chave termina em '*'."""
    saida = {}
    for nome, v in d.items():
        if nome.endswith("*"):
            base = nome[:-1]
            saida[base + "_esq"] = (v[0], -v[1], -v[2])
            saida[base + "_dir"] = (v[0], v[1], v[2])
        else:
            saida[nome] = v
    return saida


# ---------------------------------------------------------------------------
# Marcha (curvas por fase do passo: 0 = calcanhar do pe em contato; o outro pe esta 0,5 defasado)
# angulos em graus: quadril = flexao (+ coxa para frente), joelho = flexao (>= 0), tornozelo = dorsiflexao (+ ponta para cima)
# ---------------------------------------------------------------------------
MARCHA = {
    "andar": dict(
        quadril=[(0.0, 26), (0.12, 22), (0.32, 3), (0.50, -13), (0.60, -11), (0.74, 12), (0.88, 25)],
        joelho=[(0.0, 4), (0.12, 16), (0.30, 9), (0.45, 10), (0.58, 38), (0.72, 58), (0.85, 34), (0.95, 10)],
        tornozelo=[(0.0, 2), (0.08, -5), (0.38, 8), (0.62, -14), (0.72, 6), (0.82, 4), (0.92, 3)],
    ),
    "correr": dict(
        quadril=[(0.0, 38), (0.12, 30), (0.30, 6), (0.45, -18), (0.55, -24), (0.68, 0), (0.82, 30)],
        joelho=[(0.0, 18), (0.10, 38), (0.25, 22), (0.42, 16), (0.58, 70), (0.72, 105), (0.86, 62), (0.95, 28)],
        tornozelo=[(0.0, 5), (0.08, -4), (0.25, 8), (0.42, -5), (0.55, -20), (0.72, 2), (0.88, 8)],
    ),
}

# esc_* = escala do quadril por perna; joelho = escala do joelho; pe_* = tornozelo extra (graus);
# BR/BR0 = balanco e posicao media do ombro; CO/CV = cotovelo; HM = mao; PY = giro da pelve; SY = giro dos ombros;
# PR = queda do quadril; PX = deslocamento lateral (m); INC = inclinacao do tronco (negativo = para frente);
# SH = encolher de ombros; SALTO = quicada (m)
MARCHA_PARAM = {
    ("jogador", "andar"): dict(esc_esq=1.0, esc_dir=1.0, joelho=1.0, pe_esq=0.0, pe_dir=0.0, h0=0.0,
                               BR=17, BR0=4, CO=14, CV=10, HM=5, PY=2.2, SY=3.6, PR=2.0, PX=0.010,
                               INC=-4.0, SH=1.2, SALTO=0.0, loll=0.0, lat=0.0),
    ("jogador", "correr"): dict(esc_esq=1.0, esc_dir=1.0, joelho=1.0, pe_esq=0.0, pe_dir=0.0, h0=2.0,
                                BR=32, BR0=10, CO=76, CV=8, HM=8, PY=3.8, SY=6.0, PR=2.4, PX=0.008,
                                INC=-13.0, SH=1.5, SALTO=0.06, loll=0.0, lat=0.0),
    ("zumbi", "andar"): dict(esc_esq=0.62, esc_dir=0.95, joelho=0.8, pe_esq=-12.0, pe_dir=-2.0, h0=7.0,
                             BR=11, BR0=16, CO=20, CV=9, HM=10, PY=3.0, SY=5.0, PR=3.4, PX=0.018,
                             INC=-26.0, SH=1.2, SALTO=0.0, loll=1.0, lat=2.0),
    ("zumbi", "correr"): dict(esc_esq=0.85, esc_dir=1.0, joelho=0.85, pe_esq=-8.0, pe_dir=0.0, h0=8.0,
                              BR=13, BR0=60, CO=28, CV=8, HM=10, PY=4.0, SY=6.0, PR=3.6, PX=0.022,
                              INC=-28.0, SH=1.5, SALTO=0.03, loll=0.7, lat=2.5),
}


def _pose_marcha(estilo, tipo, q, n):
    f = (q % n) / n
    P = MARCHA_PARAM[(estilo, tipo)]
    T = MARCHA[tipo]
    p = pose_vazia()
    tau = 2.0 * math.pi
    for lado, fase in (("esq", f), ("dir", (f + 0.5) % 1.0)):
        e = P["esc_" + lado]
        h = _periodico(fase, T["quadril"], 1.0) * e + P["h0"]
        k = max(0.0, _periodico(fase, T["joelho"], 1.0)) * P["joelho"] * min(1.0, e)
        a = _periodico(fase, T["tornozelo"], 1.0) + P["pe_" + lado]
        sinal = -1.0 if lado == "esq" else 1.0
        _somar(p, "coxa_" + lado, h, 0.0, sinal * 1.6)
        _somar(p, "canela_" + lado, -k)
        _somar(p, "pe_" + lado, a)
        # braco do lado oposto da perna: avanca quando a perna oposta esta a frente
        oposta = (f + 0.5) % 1.0 if lado == "esq" else f
        bal = math.cos(tau * (oposta - 0.04))
        _somar(p, "braco_" + lado, P["BR0"] + P["BR"] * bal, 0.0, sinal * 3.0)
        flex = 0.5 + 0.5 * math.cos(tau * (oposta - 0.12))
        _somar(p, "antebraco_" + lado, P["CO"] + P["CV"] * flex)
        _somar(p, "mao_" + lado, 6.0 + P["HM"] * math.cos(tau * (oposta - 0.22)))
        shr = P["SH"] * (0.5 + 0.5 * math.cos(tau * (oposta - 0.10)))
        _somar(p, "ombro_" + lado, 0.0, sinal * 0.8 * bal, sinal * shr)
    if estilo == "jogador":  # camada da arma: braco direito segura a arma em "baixo-pronto"; so ~35% do balanco passa
        w = 0.65 if tipo == "andar" else 0.55
        pronto = {"braco_dir": (38.0, 6.0, 6.0), "antebraco_dir": (62.0, 0.0, 0.0), "mao_dir": (10.0, 0.0, 0.0)}
        for osso, alvo in pronto.items():
            a0 = p["ang"][osso]
            p["ang"][osso] = tuple(a0[k] * (1.0 - w) + alvo[k] * w for k in range(3))
        a0 = p["ang"]["braco_esq"]  # mao de apoio: acompanha a arma, balanco menor
        p["ang"]["braco_esq"] = (a0[0] * 0.7 + 14.0 * 0.3, a0[1], a0[2])
    c, s = math.cos(tau * f), math.sin(tau * f)
    c2, s2 = math.cos(2 * tau * f), math.sin(2 * tau * f)
    cl = math.cos(tau * (f - 0.04))
    _somar(p, "quadril", 3.0 + 1.5 * c2, -P["PY"] * c, -P["PR"] * s)
    p["raiz"][0] = -P["PX"] * s
    _somar(p, "coluna", P["INC"] + 0.8 * s2, (P["SY"] + P["PY"]) * cl, 0.7 * P["PR"] * s + P["lat"] * s)
    _somar(p, "pescoco", -0.45 * P["INC"], -0.35 * P["SY"] * math.cos(tau * (f - 0.07)), -0.3 * P["PR"] * s)
    _somar(p, "cabeca", -0.45 * P["INC"] - 3.0 + 1.1 * math.sin(2 * tau * (f - 0.12)),
           -0.5 * P["SY"] * math.cos(tau * (f - 0.11)), -0.25 * P["PR"] * s)
    if P["loll"] > 0.0:  # zumbi: cabeca caida e balancando
        _somar(p, "cabeca", -9.0 + 2.0 * math.sin(tau * (f - 0.2)), 4.0 * math.sin(tau * (f - 0.3)),
               8.0 + 4.0 * math.cos(tau * (f - 0.15)))
        _somar(p, "pescoco", -4.0, 0.0, 3.0)
        _somar(p, "braco_esq", 4.0 * math.sin(tau * (f + 0.3)))  # um braco desgovernado
    p["salto"] = P["SALTO"] * (0.5 + 0.5 * math.cos(2 * tau * (f - 0.05))) ** 2
    return p


# ---------------------------------------------------------------------------
# Parado (respiracao, troca de peso, olhar). Periodo de 90 quadros (3 s).
# ---------------------------------------------------------------------------
def _pose_parado(estilo, q):
    f = (q % 90.0) / 90.0
    tau = 2.0 * math.pi
    s1, c1 = math.sin(tau * f), math.cos(tau * f)
    resp = math.sin(2 * tau * f)  # 2 respiracoes
    respl = math.sin(2 * tau * (f - 0.06))
    p = pose_vazia()
    if estilo == "jogador":
        p["raiz"][0] = 0.012 * s1
        _somar(p, "quadril", 1.5, 1.0 * math.sin(tau * f + 1.1), -1.6 * s1)
        _somar(p, "coluna", -1.5 + 0.9 * resp, -1.0 * math.sin(tau * f + 1.1), 1.2 * s1)
        _somar(p, "pescoco", 0.8, 1.5 * math.sin(tau * (f - 0.08)), -0.6 * s1)
        _somar(p, "cabeca", 1.0 + 1.0 * math.sin(2 * tau * f + 1.0), 5.0 * math.sin(tau * f + 0.5), -0.5 * s1)
        for lado, sg in (("esq", -1.0), ("dir", 1.0)):
            _somar(p, "ombro_" + lado, 0.0, 0.0, sg * (1.0 + 0.9 * resp))
            _somar(p, "braco_" + lado, 3.0 + 1.5 * math.sin(tau * (f - 0.1) + (0.0 if lado == "esq" else 1.4)),
                   0.0, sg * 5.0)
            _somar(p, "antebraco_" + lado, 12.0 + 2.0 * respl)
            _somar(p, "mao_" + lado, 8.0 + 3.0 * math.sin(tau * (f - 0.2)))
        carga_esq = max(0.0, s1)  # perna de apoio esticada, a outra relaxada
        carga_dir = max(0.0, -s1)
        _somar(p, "coxa_esq", 2.0 + 3.0 * carga_dir, 0.0, -1.5)
        _somar(p, "canela_esq", -(3.0 + 7.0 * carga_dir))
        _somar(p, "pe_esq", 1.0 + 4.0 * carga_dir)
        _somar(p, "coxa_dir", 2.0 + 3.0 * carga_esq, 0.0, 1.5)
        _somar(p, "canela_dir", -(3.0 + 7.0 * carga_esq))
        _somar(p, "pe_dir", 1.0 + 4.0 * carga_esq)
    else:  # zumbi: curvado, bracos caidos a frente, cabeca pendendo, tremores
        tw = _pulso(q - 62.0) * 1.0  # espasmo
        p["raiz"][0] = 0.02 * s1
        _somar(p, "quadril", 4.0, 2.0 * s1, -2.0 * s1)
        _somar(p, "coluna", -15.0 + 1.6 * resp, 3.0 * math.sin(tau * f + 0.4), 3.0 + 2.0 * s1)
        _somar(p, "pescoco", -6.0 + 1.0 * respl, 3.0 * math.sin(tau * (f - 0.1)), 3.0)
        _somar(p, "cabeca", -9.0 + 3.0 * math.sin(2 * tau * f + 0.8), 9.0 * math.sin(tau * f - 0.6) + 12.0 * tw,
               10.0 + 5.0 * c1)
        _somar(p, "ombro_esq", 0.0, 0.0, -4.5 + 1.0 * resp)
        _somar(p, "ombro_dir", 0.0, 0.0, 1.5 + 1.0 * resp)
        _somar(p, "braco_esq", 13.0 + 3.0 * math.sin(tau * (f - 0.1)), 0.0, -6.0)
        _somar(p, "braco_dir", 9.0 + 3.0 * math.sin(tau * (f - 0.3)) + 10.0 * tw, 0.0, 8.0)
        _somar(p, "antebraco_esq", 32.0 + 4.0 * math.sin(tau * (f - 0.2)))
        _somar(p, "antebraco_dir", 42.0 + 4.0 * math.sin(tau * (f - 0.4)))
        _somar(p, "mao_esq", 22.0 + 8.0 * math.sin(tau * (f - 0.3)))
        _somar(p, "mao_dir", 28.0 + 8.0 * math.sin(tau * (f - 0.5)))
        _somar(p, "coxa_esq", 9.0, 0.0, -3.0)
        _somar(p, "canela_esq", -17.0)
        _somar(p, "pe_esq", 7.0)
        _somar(p, "coxa_dir", 6.0, 0.0, 3.0)
        _somar(p, "canela_dir", -11.0)
        _somar(p, "pe_dir", 5.0)
        # cacoes (periodo de 180 quadros = 6 s): olhar para um lado e voltar, ombro sobe, braco cai, cabeca estala
        j1 = _janela(q % 180.0, 28.0, 36.0, 52.0, 64.0)
        j2 = _janela(q % 180.0, 96.0, 104.0, 124.0, 136.0)
        j3 = _janela(q % 180.0, 150.0, 153.0, 158.0, 168.0)
        _somar(p, "cabeca", 4.0 * j1, 26.0 * j1, 8.0 * j1)
        _somar(p, "pescoco", 0.0, 10.0 * j1, 0.0)
        _somar(p, "ombro_dir", 0.0, 0.0, 5.0 * j2)
        _somar(p, "coluna", 2.0 * j2, -6.0 * j2, 0.0)
        _somar(p, "braco_esq", -14.0 * j2, 0.0, -4.0 * j2)
        _somar(p, "cabeca", 0.0, -18.0 * j3, -10.0 * j3)
    return p


# ---------------------------------------------------------------------------
# Atirar: mira + recuo com sobreposicao (cada osso reage com atraso)
# ---------------------------------------------------------------------------
def _pose_atirar(estilo, q):
    p = pose_vazia()
    base = _aplicar_lados({
        "coluna": (-5.0, 9.0, 0.0), "pescoco": (2.0, -3.0, 0.0), "cabeca": (3.0, -7.0, 0.0),
        "quadril": (1.0, 4.0, 0.0),
        "ombro_dir": (0.0, 6.0, 0.0), "ombro_esq": (0.0, -6.0, 0.0),
        "braco_dir": (68.0, 17.0, 6.0), "antebraco_dir": (10.0, 0.0, 0.0), "mao_dir": (4.0, 0.0, 0.0),
        "braco_esq": (62.0, -16.0, -6.0), "antebraco_esq": (16.0, 0.0, 0.0), "mao_esq": (6.0, 0.0, 0.0),
        "coxa_esq": (14.0, 0.0, -3.0), "canela_esq": (-12.0, 0.0, 0.0), "pe_esq": (-1.0, 0.0, 0.0),
        "coxa_dir": (-13.0, 0.0, 3.0), "canela_dir": (-16.0, 0.0, 0.0), "pe_dir": (28.0, 0.0, 0.0),
    })
    for osso, v in base.items():
        _somar(p, osso, v[0], v[1], v[2])
    # respiracao curta de mira
    _somar(p, "coluna", 0.4 * math.sin(2 * math.pi * q / 24.0))
    # recuo: (osso, ganho rx, atraso em quadros)
    recuo = [
        ("braco_dir", 11.0, 0.0), ("braco_esq", 6.0, 0.5), ("antebraco_dir", 17.0, 1.0), ("antebraco_esq", 9.0, 1.5),
        ("mao_dir", 15.0, 2.0), ("mao_esq", 8.0, 2.5), ("ombro_dir", -2.0, 0.5), ("coluna", 5.0, 0.8),
        ("pescoco", 3.0, 1.4), ("cabeca", 5.0, 2.0), ("quadril", -2.5, 0.2),
        ("coxa_esq", 3.0, 1.0), ("canela_esq", -6.0, 1.2), ("coxa_dir", -2.0, 1.0), ("canela_dir", -5.0, 1.2),
        ("pe_dir", -3.0, 1.5),
    ]
    for osso, ganho, atraso in recuo:
        _somar(p, osso, ganho * _pulso(q - atraso))
    _somar(p, "coluna", 0.0, 1.5 * _pulso(q - 1.0), 0.0)
    p["raiz"][1] = 0.0
    return p


# ---------------------------------------------------------------------------
# Atacar (zumbi): golpe com UM braco por vez (alterna direita/esquerda). Ciclo de 72 quadros = 2 golpes de 36.
# O braco livre fica recolhido na guarda; a pose "pronto" e simetrica, entao a troca de lado nao da pulo.
# ---------------------------------------------------------------------------
def _golpe_um_braco(q, ativo):
    """q em 0..36 (periodico); `ativo` = 'dir' ou 'esq' (braco que bate)."""
    outro = "esq" if ativo == "dir" else "dir"
    sg = 1.0 if ativo == "dir" else -1.0  # lado do braco ativo (+X = direita)
    pr = {"quadril": (4.0, 0.0, 0.0), "coluna": (-16.0, 0.0, 3.0), "pescoco": (-6.0, 0.0, 3.0), "cabeca": (-9.0, 0.0, 9.0),
          "coxa_esq": (8.0, 0.0, -3.0), "canela_esq": (-14.0, 0.0, 0.0), "pe_esq": (6.0, 0.0, 0.0),
          "coxa_dir": (8.0, 0.0, 3.0), "canela_dir": (-14.0, 0.0, 0.0), "pe_dir": (6.0, 0.0, 0.0),
          "braco_esq": (60.0, -6.0, -8.0), "antebraco_esq": (24.0, 0.0, 0.0), "mao_esq": (18.0, 0.0, 0.0),
          "braco_dir": (60.0, 6.0, 8.0), "antebraco_dir": (24.0, 0.0, 0.0), "mao_dir": (18.0, 0.0, 0.0),
          "ombro_esq": (0.0, 0.0, 0.0), "ombro_dir": (0.0, 0.0, 0.0)}

    def variante(mudar):
        d = dict(pr)
        d.update(mudar)
        return d

    a, o = "_" + ativo, "_" + outro
    ergue = variante({
        "quadril": (-2.0, -sg * 5.0, 0.0), "coluna": (6.0, -sg * 12.0, 0.0), "pescoco": (4.0, sg * 4.0, 0.0),
        "cabeca": (18.0, sg * 10.0, 12.0),
        "braco" + a: (104.0, sg * 6.0, sg * 18.0), "antebraco" + a: (30.0, 0.0, 0.0), "mao" + a: (-8.0, 0.0, 0.0),
        "ombro" + a: (0.0, 0.0, sg * 3.0),
        "braco" + o: (52.0, -sg * 8.0, -sg * 12.0), "antebraco" + o: (58.0, 0.0, 0.0), "mao" + o: (14.0, 0.0, 0.0),
        "coxa_esq": (6.0, 0.0, -3.0), "coxa_dir": (6.0, 0.0, 3.0)})
    golpe = variante({
        "quadril": (8.0, sg * 6.0, 0.0), "coluna": (-30.0, sg * 14.0, 2.0), "pescoco": (-10.0, -sg * 4.0, 0.0),
        "cabeca": (-14.0, -sg * 6.0, 6.0),
        "braco" + a: (26.0, -sg * 10.0, -sg * 4.0), "antebraco" + a: (10.0, 0.0, 0.0), "mao" + a: (30.0, 0.0, 0.0),
        "ombro" + a: (0.0, 0.0, -sg * 2.0),
        "braco" + o: (46.0, -sg * 8.0, -sg * 14.0), "antebraco" + o: (66.0, 0.0, 0.0), "mao" + o: (16.0, 0.0, 0.0),
        "coxa" + a: (-12.0, 0.0, sg * 3.0), "canela" + a: (-12.0, 0.0, 0.0),
        "coxa" + o: (28.0, 0.0, -sg * 3.0), "canela" + o: (-32.0, 0.0, 0.0)})
    chaves = [(0.0, pr, (0.0, 0.0)), (11.0, ergue, (0.0, 0.06)), (17.0, golpe, (0.0, -0.18)),
              (27.0, pr, (0.0, -0.02))]
    atrasos = {"coluna": 0.0, "pescoco": 1.5, "cabeca": 2.5, "braco" + a: 1.0, "antebraco" + a: 2.2,
               "mao" + a: 3.4, "braco" + o: 1.8, "antebraco" + o: 2.6, "mao" + o: 3.4}
    return _avaliar(chaves, q, 36.0, atrasos)


def _pose_atacar(estilo, q):
    f = q % 72.0
    if f < 36.0:
        p = _golpe_um_braco(f, "dir")
    else:
        p = _golpe_um_braco(f - 36.0, "esq")
    _somar(p, "cabeca", 1.5 * math.sin(2 * math.pi * q / 36.0 * 3))
    return p


# ---------------------------------------------------------------------------
# Morrer: impacto, cambaleio, joelhos cedem, tomba para tras, quica no chao e fica solto.
# ---------------------------------------------------------------------------
def _pose_morrer(estilo, q):
    zumbi = estilo == "zumbi"
    esc = 1.12 if zumbi else 1.0  # o zumbi cai mais devagar
    abre = 0.55 if zumbi else 1.0  # e com os bracos menos abertos
    inicio = _pose_parado(estilo, 0.0)["ang"]
    inicio_t = {k: v for k, v in inicio.items()}
    impacto = _aplicar_lados({
        "quadril": (-7.0, 2.0, 0.0), "coluna": (17.0, 6.0, 3.0), "pescoco": (10.0, 0.0, 0.0), "cabeca": (22.0, 6.0, 4.0),
        "ombro*": (0.0, 0.0, -3.0),
        "braco*": (-18.0, 0.0, 30.0 * abre), "antebraco*": (26.0, 0.0, 0.0), "mao*": (10.0, 0.0, 0.0),
        "coxa_esq": (10.0, 0.0, -3.0), "canela_esq": (-12.0, 0.0, 0.0), "pe_esq": (4.0, 0.0, 0.0),
        "coxa_dir": (-6.0, 0.0, 3.0), "canela_dir": (-14.0, 0.0, 0.0), "pe_dir": (8.0, 0.0, 0.0),
    })
    cambaleio = _aplicar_lados({
        "quadril": (12.0, -6.0, 3.0), "coluna": (9.0, 8.0, 0.0), "pescoco": (14.0, 0.0, 0.0), "cabeca": (18.0, 14.0, 5.0),
        "ombro*": (0.0, 0.0, -2.0),
        "braco*": (-6.0, 0.0, 38.0 * abre), "antebraco*": (22.0, 0.0, 0.0), "mao*": (14.0, 0.0, 0.0),
        "coxa_esq": (30.0, 0.0, -4.0), "canela_esq": (-56.0, 0.0, 0.0), "pe_esq": (10.0, 0.0, 0.0),
        "coxa_dir": (-8.0, 0.0, 3.0), "canela_dir": (-32.0, 0.0, 0.0), "pe_dir": (12.0, 0.0, 0.0),
    })
    joelhos = _aplicar_lados({
        "quadril": (20.0, 0.0, 4.0), "coluna": (22.0, 4.0, 0.0), "pescoco": (10.0, 0.0, 0.0), "cabeca": (8.0, 10.0, 8.0),
        "ombro*": (0.0, 0.0, -2.0),
        "braco*": (-8.0, 0.0, 36.0 * abre), "antebraco*": (18.0, 0.0, 0.0), "mao*": (20.0, 0.0, 0.0),
        "coxa_esq": (62.0, 0.0, -4.0), "canela_esq": (-110.0, 0.0, 0.0), "pe_esq": (30.0, 0.0, 0.0),
        "coxa_dir": (54.0, 0.0, 4.0), "canela_dir": (-100.0, 0.0, 0.0), "pe_dir": (30.0, 0.0, 0.0),
    })
    tomba = _aplicar_lados({
        "quadril": (56.0, 0.0, 6.0), "coluna": (8.0, 6.0, 0.0), "pescoco": (4.0, 0.0, 0.0), "cabeca": (6.0, 0.0, 8.0),
        "ombro*": (0.0, 0.0, -3.0),
        "braco*": (8.0, 0.0, 62.0 * abre), "antebraco*": (14.0, 0.0, 0.0), "mao*": (22.0, 0.0, 0.0),
        "coxa_esq": (36.0, 0.0, -6.0), "canela_esq": (-62.0, 0.0, 0.0), "pe_esq": (20.0, 0.0, 0.0),
        "coxa_dir": (22.0, 0.0, 6.0), "canela_dir": (-46.0, 0.0, 0.0), "pe_dir": (18.0, 0.0, 0.0),
    })
    chao = _aplicar_lados({
        "quadril": (92.0, 0.0, 3.0), "coluna": (3.0, 8.0, 4.0), "pescoco": (-4.0, 0.0, 4.0), "cabeca": (-6.0, 0.0, 15.0),
        "ombro*": (0.0, 0.0, -2.0),
        "braco_esq": (-6.0, 0.0, -56.0 * abre), "braco_dir": (-14.0, 0.0, 62.0 * abre),
        "antebraco*": (10.0, 0.0, 0.0), "mao*": (16.0, 0.0, 0.0),
        "coxa_esq": (8.0, 0.0, -12.0), "canela_esq": (-18.0, 0.0, 0.0), "pe_esq": (16.0, 0.0, 0.0),
        "coxa_dir": (-6.0, 0.0, 16.0), "canela_dir": (-30.0, 0.0, 0.0), "pe_dir": (8.0, 0.0, 0.0),
    })
    quica = {k: (v[0] - (4.0 if k == "quadril" else 0.0), v[1], v[2]) for k, v in chao.items()}
    quica["cabeca"] = (-12.0, 0.0, 17.0)
    quica["pescoco"] = (-8.0, 0.0, 5.0)

    def t(x):
        return x * esc

    chaves = [
        (0.0, inicio_t, (0.0, 0.0)),
        (t(4.0), impacto, (0.0, 0.06)),
        (t(12.0), cambaleio, (0.0, 0.18)),
        (t(22.0), joelhos, (0.0, 0.34)),
        (t(34.0), tomba, (0.0, 0.50)),
        (t(44.0), chao, (0.0, 0.56)),
        (t(48.0), quica, (0.0, 0.56)),
        (t(54.0), chao, (0.0, 0.56)),
        (t(54.0) + 10.0, chao, (0.0, 0.56)),
    ]
    f = 2.0 if zumbi else 1.0
    atrasos = {"pescoco": 1.0 * f, "cabeca": 2.0 * f, "braco_esq": 1.0 * f, "braco_dir": 1.5 * f,
               "antebraco_esq": 2.0 * f, "antebraco_dir": 2.5 * f, "mao_esq": 3.0 * f, "mao_dir": 3.5 * f,
               "ombro_esq": 0.5 * f, "ombro_dir": 0.8 * f}
    return _avaliar(chaves, q, None, atrasos)


# ---------------------------------------------------------------------------
# Interface do modulo
# ---------------------------------------------------------------------------
def pose_sem_chao(estilo, animacao, q):
    """Pose da animacao no quadro q (pode ser fracionario), SEM o ajuste de altura (ver aterrar)."""
    if animacao == "parado":
        return _pose_parado(estilo, q)
    if animacao in ("andar", "correr"):
        n = [c for c in ANIMACOES[estilo] if c[0] == animacao][0][1]
        return _pose_marcha(estilo, animacao, q, float(n))
    if animacao == "atirar":
        return _pose_atirar(estilo, q)
    if animacao == "atacar":
        return _pose_atacar(estilo, q)
    if animacao == "morrer":
        return _pose_morrer(estilo, q)
    raise ValueError("animacao desconhecida: " + animacao)


def pose_final(estilo, animacao, q, pontos):
    return aterrar(pose_sem_chao(estilo, animacao, q), pontos)


def listar_animacoes(estilo):
    return ANIMACOES[estilo]


def quadros_da_animacao(estilo, animacao, pontos):
    """[(quadro, pose)] para o quadro 0..N inclusive."""
    for nome, n, _ in ANIMACOES[estilo]:
        if nome == animacao:
            return [(q, pose_final(estilo, nome, float(q), pontos)) for q in range(n + 1)]
    raise ValueError("animacao desconhecida: " + animacao)
