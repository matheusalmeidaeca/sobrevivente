# ferramentas/blender/animacao.py
"""Esqueleto, pesos de pele e animacoes do boneco. Python PURO (sem bpy):
roda dentro do Blender, no Python do sistema e em qualquer teste.

Sistema do JOGO (o mesmo do F0): Y para cima, 1 unidade = 1 m, origem nos pes,
FRENTE = -Z, esquerda = -X. Angulos em GRAUS (regra da mao direita, no sistema Y para cima):
  rx > 0: osso que pende para baixo vai para a FRENTE; osso que aponta para cima vai para TRAS
  ry > 0: gira para a ESQUERDA do personagem (anti-horario visto de cima)
  rz > 0: osso que pende para baixo vai para +X (direita do personagem)
Rotacao de cada osso = Ry * Rx * Rz, em torno do PIVO (cabeca do osso), no referencial do pai.
"""
import math

FPS = 30

# ---------------------------------------------------------------------------
# Esqueleto (13 ossos; orcamento da secao 24.3: ate 25). Pais sempre antes dos filhos.
# nome, pai, pivo (cabeca do osso), ponta (so para desenhar o osso no Blender)
# ---------------------------------------------------------------------------
OSSOS = [
    ("quadril", None, (0.0, 0.92, 0.0), (0.0, 1.00, 0.0)),
    ("coluna", "quadril", (0.0, 1.00, 0.0), (0.0, 1.48, 0.0)),
    ("cabeca", "coluna", (0.0, 1.48, 0.0), (0.0, 1.80, 0.0)),
    ("braco_esq", "coluna", (-0.255, 1.44, 0.0), (-0.255, 1.20, 0.0)),
    ("antebraco_esq", "braco_esq", (-0.255, 1.20, 0.0), (-0.255, 0.70, 0.0)),
    ("braco_dir", "coluna", (0.255, 1.44, 0.0), (0.255, 1.20, 0.0)),
    ("antebraco_dir", "braco_dir", (0.255, 1.20, 0.0), (0.255, 0.70, 0.0)),
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
    "jogador": [("parado", 60, True), ("andar", 32, True), ("correr", 20, True),
                ("atirar", 18, False), ("morrer", 48, False)],
    "zumbi": [("parado", 60, True), ("andar", 32, True), ("correr", 20, True),
              ("atacar", 30, True), ("morrer", 48, False)],
}

# Quadro mostrado na folha de preview (1 por animacao).
QUADRO_PREVIA = {
    "parado": 0, "andar": 8, "correr": 5, "atirar": 3, "atacar": 6, "morrer": 48,
}

# ---------------------------------------------------------------------------
# Parametros dos ciclos de andar/correr (graus; ver explicacao em _ciclo_pernas)
#   A = balanco da coxa; KB = flexao base do joelho; KN = flexao extra do joelho na passada;
#   PC = ponta do pe para cima na pisada; PB = ponta do pe para baixo na saida;
#   BR = balanco do braco; BR0 = braco para frente (media); CO = cotovelo; CO_V = cotovelo extra;
#   INC = inclinacao do tronco (negativo = para frente); GIRO = torcao ombros/quadril;
#   SALTO = quicada (m) quando as pernas estao bem abertas.
# ---------------------------------------------------------------------------
CICLOS = {
    ("jogador", "andar"): dict(A=26, KB=8, KN=48, PC=14, PB=24, BR=20, BR0=2, CO=14, CO_V=10,
                               INC=-3, GIRO=6, SALTO=0.0, ESQ=1.0, DIR=1.0),
    ("jogador", "correr"): dict(A=52, KB=18, KN=85, PC=8, PB=30, BR=44, BR0=12, CO=82, CO_V=10,
                                INC=-12, GIRO=8, SALTO=0.05, ESQ=1.0, DIR=1.0),
    # zumbi: passos curtos, perna esquerda arrastando, bracos para frente
    ("zumbi", "andar"): dict(A=19, KB=7, KN=34, PC=6, PB=14, BR=7, BR0=62, CO=20, CO_V=6,
                             INC=-15, GIRO=5, SALTO=0.0, ESQ=0.75, DIR=1.1),
    ("zumbi", "correr"): dict(A=44, KB=16, KN=72, PC=6, PB=24, BR=14, BR0=58, CO=26, CO_V=8,
                              INC=-24, GIRO=9, SALTO=0.04, ESQ=1.0, DIR=1.0),
}


# ---------------------------------------------------------------------------
# Matematica 4x4 (listas de 4 linhas), sem dependencias
# ---------------------------------------------------------------------------
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


# ---------------------------------------------------------------------------
# Pose, cinematica direta e pele
# ---------------------------------------------------------------------------
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


def pesos_do_vertice(grupo, x, y, z):
    """Pesos de pele (no maximo 2 ossos, somando 1) de um vertice da peca `grupo` (nomes do OBJ do F0)."""
    if grupo == "cabeca":
        t = _suave((y - 1.49) / 0.06)  # colo (1,49) acompanha a coluna; do rosto para cima, so a cabeca
        return [("coluna", 1.0 - t), ("cabeca", t)]
    if grupo == "tronco":
        t = _suave((y - 0.90) / 0.22)  # cintura: do quadril (0,90) a coluna (1,12)
        return [("quadril", 1.0 - t), ("coluna", t)]
    lado = "esq" if grupo.endswith("_esq") else "dir"
    if grupo.startswith("braco"):
        t = _suave((1.28 - y) / 0.16)  # cotovelo: braco acima de 1,28, antebraco abaixo de 1,12
        topo = _suave((y - 1.40) / 0.10) * 0.35  # topo do ombro acompanha um pouco a coluna
        return [("braco_" + lado, (1.0 - t) * (1.0 - topo)), ("antebraco_" + lado, t),
                ("coluna", (1.0 - t) * topo)]
    if grupo.startswith("perna"):
        if y <= 0.085 and (z < -0.045 or z > 0.045):  # caixa do pe (so ela passa de |z| = 0,045)
            return [("pe_" + lado, 1.0)]
        if y >= 0.12:
            a = _suave((y - 0.44) / 0.12)  # joelho: coxa acima de 0,56, canela abaixo de 0,44
            return [("coxa_" + lado, a), ("canela_" + lado, 1.0 - a)]
        b = _suave((y - 0.07) / 0.05)  # tornozelo: pe ate 0,07, canela a partir de 0,12
        return [("canela_" + lado, b), ("pe_" + lado, 1.0 - b)]
    raise ValueError("peca desconhecida: " + grupo)


def limpar_pesos(lista):
    """Tira pesos nulos e normaliza para somar 1."""
    util = [(o, p) for o, p in lista if p > 1e-6]
    total = sum(p for _, p in util)
    return [(o, p / total) for o, p in util]


# ---------------------------------------------------------------------------
# Interpolacao por chaves (Hermite com tangentes por diferenca finita)
# ---------------------------------------------------------------------------
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


def _pose_por_chaves(q, chaves, periodo=None):
    """chaves = [(quadro, {osso: (rx, ry, rz)}, raiz_y_extra), ...]; interpola canal por canal."""
    pose = pose_vazia()
    ossos = set()
    for _, angs, _ in chaves:
        ossos.update(angs.keys())
    for osso in sorted(ossos):
        canais = []
        for k in range(3):
            pontos = [(c[0], c[1].get(osso, (0.0, 0.0, 0.0))[k]) for c in chaves]
            canais.append(_periodico(q, pontos, periodo) if periodo else _hermite(q, pontos))
        pose["ang"][osso] = tuple(canais)
    return pose


# ---------------------------------------------------------------------------
# Animacoes
# ---------------------------------------------------------------------------
def _somar(pose, osso, rx=0.0, ry=0.0, rz=0.0):
    a = pose["ang"].get(osso, (0.0, 0.0, 0.0))
    pose["ang"][osso] = (a[0] + rx, a[1] + ry, a[2] + rz)


def _pose_parado(estilo, q):
    f = 2.0 * math.pi * q / 60.0
    s = math.sin(f)
    c = math.cos(f)
    p = pose_vazia()
    if estilo == "jogador":
        _somar(p, "coluna", -1.5 + 1.0 * s, 1.0 * s, 0.8 * c)
        _somar(p, "cabeca", 1.0 - 0.8 * s, -1.0 * s, 0.0)
        _somar(p, "braco_esq", 3.0 + 1.0 * s, 0.0, -5.0)
        _somar(p, "braco_dir", 3.0 - 1.0 * s, 0.0, 5.0)
        _somar(p, "antebraco_esq", 10.0 + 1.5 * s, 0.0, 0.0)
        _somar(p, "antebraco_dir", 10.0 - 1.5 * s, 0.0, 0.0)
        _somar(p, "coxa_esq", 4.0, 0.0, -2.0)
        _somar(p, "canela_esq", -6.0)
        _somar(p, "pe_esq", 2.0)
        _somar(p, "coxa_dir", -3.0, 0.0, 2.0)
        _somar(p, "canela_dir", -4.0)
        _somar(p, "pe_dir", 7.0)
    else:  # zumbi: curvado, bracos pendurados a frente, balanco lento
        _somar(p, "coluna", -16.0 + 1.5 * s, 2.0 * s, 3.0 + 1.5 * c)
        _somar(p, "cabeca", -10.0 + 2.0 * s, 6.0 * c, 9.0)
        _somar(p, "braco_esq", 14.0 + 3.0 * s, 0.0, -6.0)
        _somar(p, "braco_dir", 10.0 - 3.0 * s, 0.0, 8.0)
        _somar(p, "antebraco_esq", 30.0 + 3.0 * s, 0.0, 0.0)
        _somar(p, "antebraco_dir", 38.0 - 3.0 * s, 0.0, 0.0)
        _somar(p, "coxa_esq", 6.0, 0.0, -3.0)
        _somar(p, "canela_esq", -12.0)
        _somar(p, "pe_esq", 6.0)
        _somar(p, "coxa_dir", 6.0, 0.0, 3.0)
        _somar(p, "canela_dir", -12.0)
        _somar(p, "pe_dir", 6.0)
    return p


def _ciclo_pernas(p, g, lado, cfg):
    """Uma perna do ciclo. g = fase (rad); coxa para frente quando sen(g) > 0."""
    esc = cfg["ESQ"] if lado == "esq" else cfg["DIR"]
    coxa = cfg["A"] * esc * math.sin(g)
    flex = cfg["KB"] + cfg["KN"] * esc * max(0.0, math.cos(g))  # joelho dobra na fase de balanco
    canela = -flex
    # pe: inclinacao desejada no MUNDO (ponta para baixo na saida, para cima na pisada)
    alvo = -cfg["PB"] * max(0.0, -math.sin(g)) + cfg["PC"] * max(0.0, math.sin(g)) * max(0.0, -math.cos(g))
    pe = alvo - coxa - canela  # compensa a soma das rotacoes em X da cadeia
    _somar(p, "coxa_" + lado, coxa, 0.0, -2.0 if lado == "esq" else 2.0)
    _somar(p, "canela_" + lado, canela)
    _somar(p, "pe_" + lado, pe)


def _pose_ciclo(estilo, nome, q, n):
    cfg = CICLOS[(estilo, nome)]
    f = 2.0 * math.pi * q / n
    s = math.sin(f)
    c = math.cos(f)
    p = pose_vazia()
    _ciclo_pernas(p, f, "esq", cfg)
    _ciclo_pernas(p, f + math.pi, "dir", cfg)
    # bracos: contrarios as pernas
    _somar(p, "braco_esq", cfg["BR0"] - cfg["BR"] * s, 0.0, -5.0)
    _somar(p, "braco_dir", cfg["BR0"] + cfg["BR"] * s, 0.0, 5.0)
    _somar(p, "antebraco_esq", cfg["CO"] + cfg["CO_V"] * max(0.0, -s))
    _somar(p, "antebraco_dir", cfg["CO"] + cfg["CO_V"] * max(0.0, s))
    # tronco: inclinacao, torcao contraria entre quadril e ombros, balanco lateral
    _somar(p, "quadril", 0.0, -cfg["GIRO"] * 0.6 * s, 0.0)
    _somar(p, "coluna", cfg["INC"], cfg["GIRO"] * s, 1.5 * c)
    _somar(p, "cabeca", -cfg["INC"] * 0.6, -cfg["GIRO"] * 0.5 * s, 0.0)
    if estilo == "zumbi":
        _somar(p, "coluna", 0.0, 0.0, 4.0 * s)
        _somar(p, "cabeca", -8.0, 0.0, 10.0 * c)
    p["salto"] = cfg["SALTO"] * s * s
    return p


def _pose_atirar(estilo, q):
    # Comeca ja na mira (da para encadear tiros); o recuo e o pico em q = 2.
    mira = {
        "coluna": (-5.0, 8.0, 0.0), "cabeca": (4.0, -6.0, 0.0),
        "braco_dir": (68.0, 17.0, 6.0), "antebraco_dir": (10.0, 0.0, 0.0),
        "braco_esq": (62.0, -16.0, -6.0), "antebraco_esq": (16.0, 0.0, 0.0),
        "coxa_esq": (12.0, 0.0, -3.0), "canela_esq": (-10.0, 0.0, 0.0), "pe_esq": (-2.0, 0.0, 0.0),
        "coxa_dir": (-12.0, 0.0, 3.0), "canela_dir": (-16.0, 0.0, 0.0), "pe_dir": (28.0, 0.0, 0.0),
    }
    recuo = {
        "coluna": (-1.0, 8.0, 0.0), "cabeca": (1.0, -6.0, 0.0),
        "braco_dir": (78.0, 17.0, 6.0), "antebraco_dir": (24.0, 0.0, 0.0),
        "braco_esq": (72.0, -16.0, -6.0), "antebraco_esq": (30.0, 0.0, 0.0),
        "coxa_esq": (12.0, 0.0, -3.0), "canela_esq": (-10.0, 0.0, 0.0), "pe_esq": (-2.0, 0.0, 0.0),
        "coxa_dir": (-12.0, 0.0, 3.0), "canela_dir": (-16.0, 0.0, 0.0), "pe_dir": (28.0, 0.0, 0.0),
    }
    chaves = [(0.0, mira, 0.0), (2.0, recuo, 0.0), (8.0, mira, 0.0), (18.0, mira, 0.0)]
    return _pose_por_chaves(q, chaves)


def _pose_atacar(estilo, q):
    # Zumbi: levanta os bracos, desce com forca (golpe em q = 10) e volta. Ciclo de 30 quadros = 1 s.
    pronto = {
        "coluna": (-16.0, 0.0, 3.0), "cabeca": (-10.0, 0.0, 8.0),
        "braco_esq": (62.0, 6.0, -8.0), "antebraco_esq": (22.0, 0.0, 0.0),
        "braco_dir": (66.0, -6.0, 8.0), "antebraco_dir": (20.0, 0.0, 0.0),
        "coxa_esq": (8.0, 0.0, -3.0), "canela_esq": (-14.0, 0.0, 0.0), "pe_esq": (6.0, 0.0, 0.0),
        "coxa_dir": (-6.0, 0.0, 3.0), "canela_dir": (-8.0, 0.0, 0.0), "pe_dir": (14.0, 0.0, 0.0),
    }
    levanta = {
        "coluna": (6.0, 0.0, 0.0), "cabeca": (10.0, 0.0, 0.0),
        "braco_esq": (128.0, 8.0, -14.0), "antebraco_esq": (20.0, 0.0, 0.0),
        "braco_dir": (130.0, -8.0, 14.0), "antebraco_dir": (18.0, 0.0, 0.0),
        "coxa_esq": (4.0, 0.0, -3.0), "canela_esq": (-8.0, 0.0, 0.0), "pe_esq": (4.0, 0.0, 0.0),
        "coxa_dir": (-8.0, 0.0, 3.0), "canela_dir": (-10.0, 0.0, 0.0), "pe_dir": (18.0, 0.0, 0.0),
    }
    golpe = {
        "coluna": (-32.0, 0.0, 0.0), "cabeca": (-14.0, 0.0, 0.0),
        "braco_esq": (38.0, 6.0, -8.0), "antebraco_esq": (14.0, 0.0, 0.0),
        "braco_dir": (40.0, -6.0, 8.0), "antebraco_dir": (12.0, 0.0, 0.0),
        "coxa_esq": (24.0, 0.0, -3.0), "canela_esq": (-30.0, 0.0, 0.0), "pe_esq": (6.0, 0.0, 0.0),
        "coxa_dir": (-14.0, 0.0, 3.0), "canela_dir": (-12.0, 0.0, 0.0), "pe_dir": (26.0, 0.0, 0.0),
    }
    chaves = [(0.0, pronto, 0.0), (6.0, levanta, 0.0), (10.0, golpe, 0.0), (18.0, pronto, 0.0)]
    return _pose_por_chaves(q, chaves, periodo=30.0)


def _pose_morrer(estilo, q):
    zumbi = estilo == "zumbi"
    parado = {
        "coluna": (-1.0, 0.0, 0.0), "braco_esq": (3.0, 0.0, -5.0), "braco_dir": (3.0, 0.0, 5.0),
        "antebraco_esq": (10.0, 0.0, 0.0), "antebraco_dir": (10.0, 0.0, 0.0),
        "coxa_esq": (4.0, 0.0, -2.0), "canela_esq": (-6.0, 0.0, 0.0), "pe_esq": (2.0, 0.0, 0.0),
        "coxa_dir": (-3.0, 0.0, 2.0), "canela_dir": (-4.0, 0.0, 0.0), "pe_dir": (7.0, 0.0, 0.0),
    }
    if zumbi:
        parado = _pose_parado("zumbi", 0.0)["ang"]
    # impacto: tronco e cabeca para tras, bracos abertos
    impacto = {
        "coluna": (20.0, 5.0, 0.0), "cabeca": (16.0, 0.0, 0.0), "quadril": (4.0, 0.0, 0.0),
        "braco_esq": (24.0, 0.0, -40.0), "antebraco_esq": (28.0, 0.0, 0.0),
        "braco_dir": (20.0, 0.0, 44.0), "antebraco_dir": (34.0, 0.0, 0.0),
        "coxa_esq": (-14.0, 0.0, -3.0), "canela_esq": (-12.0, 0.0, 0.0), "pe_esq": (20.0, 0.0, 0.0),
        "coxa_dir": (6.0, 0.0, 3.0), "canela_dir": (-10.0, 0.0, 0.0), "pe_dir": (6.0, 0.0, 0.0),
    }
    # joelhos cedem
    ajoelha = {
        "coluna": (16.0, 4.0, 0.0), "cabeca": (14.0, 0.0, 0.0), "quadril": (10.0, 0.0, 0.0),
        "braco_esq": (30.0, 0.0, -36.0), "antebraco_esq": (20.0, 0.0, 0.0),
        "braco_dir": (26.0, 0.0, 40.0), "antebraco_dir": (26.0, 0.0, 0.0),
        "coxa_esq": (46.0, 0.0, -4.0), "canela_esq": (-92.0, 0.0, 0.0), "pe_esq": (30.0, 0.0, 0.0),
        "coxa_dir": (40.0, 0.0, 4.0), "canela_dir": (-86.0, 0.0, 0.0), "pe_dir": (30.0, 0.0, 0.0),
    }
    # tomba para tras
    tomba = {
        "coluna": (10.0, 6.0, 0.0), "cabeca": (8.0, 0.0, 0.0), "quadril": (58.0, 0.0, 0.0),
        "braco_esq": (10.0, 0.0, -50.0), "antebraco_esq": (14.0, 0.0, 0.0),
        "braco_dir": (6.0, 0.0, 54.0), "antebraco_dir": (18.0, 0.0, 0.0),
        "coxa_esq": (30.0, 0.0, -5.0), "canela_esq": (-50.0, 0.0, 0.0), "pe_esq": (20.0, 0.0, 0.0),
        "coxa_dir": (24.0, 0.0, 5.0), "canela_dir": (-44.0, 0.0, 0.0), "pe_dir": (20.0, 0.0, 0.0),
    }
    # deitado de costas
    chao = {
        "coluna": (2.0, 6.0, 3.0), "cabeca": (-8.0, 0.0, 10.0), "quadril": (90.0, 0.0, 0.0),
        "braco_esq": (-8.0, 0.0, -46.0), "antebraco_esq": (10.0, 0.0, 0.0),
        "braco_dir": (-12.0, 0.0, 58.0), "antebraco_dir": (16.0, 0.0, 0.0),
        "coxa_esq": (6.0, 0.0, -10.0), "canela_esq": (-14.0, 0.0, 0.0), "pe_esq": (18.0, 0.0, 0.0),
        "coxa_dir": (-4.0, 0.0, 14.0), "canela_dir": (-22.0, 0.0, 0.0), "pe_dir": (10.0, 0.0, 0.0),
    }
    if zumbi:  # o zumbi cai mais duro, com os bracos menos abertos
        for d in (impacto, ajoelha, tomba, chao):
            d["braco_esq"] = (d["braco_esq"][0], 0.0, d["braco_esq"][2] * 0.6)
            d["braco_dir"] = (d["braco_dir"][0], 0.0, d["braco_dir"][2] * 0.6)
    chaves = [(0.0, parado, 0.0), (5.0, impacto, 0.0), (16.0, ajoelha, 0.0),
              (34.0, tomba, 0.0), (48.0, chao, 0.0)]
    return _pose_por_chaves(q, chaves)


def pose_sem_chao(estilo, animacao, q):
    """Pose da animacao no quadro q (pode ser fracionario), SEM o ajuste de altura (ver aterrar)."""
    if animacao == "parado":
        return _pose_parado(estilo, q)
    if animacao in ("andar", "correr"):
        return _pose_ciclo(estilo, animacao, q, 32.0 if animacao == "andar" else 20.0)
    if animacao == "atirar":
        return _pose_atirar(estilo, q)
    if animacao == "atacar":
        return _pose_atacar(estilo, q)
    if animacao == "morrer":
        return _pose_morrer(estilo, q)
    raise ValueError("animacao desconhecida: " + animacao)


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
