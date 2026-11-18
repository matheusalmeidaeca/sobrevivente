#!/usr/bin/env python3
"""F18: sintetiza TODOS os sons e a trilha do jogo (sem arquivos externos, sem direitos autorais).

Uso:  python3 gerar_audio.py <pasta_saida>      (gera .ogg em <pasta_saida>)
Requer numpy, scipy e ffmpeg (libvorbis).
Tudo e deterministico (semente fixa): rodar de novo gera os mesmos arquivos.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy import signal

SR = 22050
RNG = np.random.default_rng(1812)


# ---------------------------------------------------------------- utilitarios
def t_(dur):
    return np.arange(int(dur * SR)) / SR


def ruido(dur):
    return RNG.standard_normal(int(dur * SR))


def passa_baixa(x, fc, ordem=2):
    sos = signal.butter(ordem, min(fc, SR * 0.45) / (SR / 2), 'low', output='sos')
    return signal.sosfilt(sos, x)


def passa_alta(x, fc, ordem=2):
    sos = signal.butter(ordem, fc / (SR / 2), 'high', output='sos')
    return signal.sosfilt(sos, x)


def passa_faixa(x, f1, f2, ordem=2):
    sos = signal.butter(ordem, [f1 / (SR / 2), min(f2, SR * 0.45) / (SR / 2)], 'band', output='sos')
    return signal.sosfilt(sos, x)


def env_exp(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def ataque(x, ms=2.0):
    n = max(1, int(SR * ms / 1000))
    r = np.ones(len(x))
    r[:n] = np.linspace(0, 1, n)
    return x * r


def reverb(x, tempo=0.5, mix=0.25, brilho=2500):
    n = int(tempo * SR)
    ir = ruido(tempo) * np.exp(-np.arange(n) / (tempo * SR * 0.28))
    ir = passa_baixa(ir, brilho)
    ir[0] = 0
    ir /= np.max(np.abs(ir)) + 1e-9
    molhado = signal.fftconvolve(x, ir) * 0.08
    seco = np.concatenate([x, np.zeros(len(molhado) - len(x))])
    return seco + mix * molhado


def normaliza(x, pico=0.9):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * pico


def soma(*partes):
    n = max(len(p) for p in partes)
    r = np.zeros(n)
    for p in partes:
        r[:len(p)] += p
    return r


def atraso(x, seg):
    return np.concatenate([np.zeros(int(seg * SR)), x])


def seno_varrido(dur, f0, f1, curva=3.0):
    t = t_(dur)
    f = f1 + (f0 - f1) * np.exp(-curva * t / dur)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def satura(x, g=2.0):
    return np.tanh(x * g) / np.tanh(g)


def salvar(nome, x, pasta, taxa_ogg='64k', fade=0.004):
    x = np.asarray(x, dtype=np.float64)
    n = int(fade * SR)
    if len(x) > 2 * n:
        x[-n:] *= np.linspace(1, 0, n)
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype('<i2')
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        caminho = tmp.name
    with wave.open(caminho, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    os.makedirs(pasta, exist_ok=True)
    saida = os.path.join(pasta, nome + '.ogg')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', caminho, '-c:a', 'libvorbis', '-b:a', taxa_ogg, saida], check=True)
    os.remove(caminho)
    print(f'{nome:22s} {len(x) / SR:6.2f}s  pico={np.max(np.abs(x)):.2f}  {os.path.getsize(saida) / 1024:6.1f} KB')


def formante(x, freqs, larguras=None, ganhos=None):
    """Filtro de formantes (voz de zumbi/gemido)."""
    larguras = larguras or [80] * len(freqs)
    ganhos = ganhos or [1.0] * len(freqs)
    r = np.zeros(len(x))
    for f, b, g in zip(freqs, larguras, ganhos):
        r += g * passa_faixa(x, max(40, f - b), f + b, 2)
    return r


# ---------------------------------------------------------------- efeitos
def tiro_pistola():
    n = 0.5
    estalo = ataque(passa_alta(ruido(n), 1200) * env_exp(int(n * SR), 0.012), 0.5)
    corpo = ataque(passa_baixa(ruido(n), 2200) * env_exp(int(n * SR), 0.05), 0.5)
    sub = seno_varrido(n, 190, 55, 6) * env_exp(int(n * SR), 0.07)
    x = satura(soma(estalo * 0.9, corpo * 0.8, sub * 0.9), 1.6)
    return normaliza(reverb(x, 0.45, 0.45))


def tiro_fuzil():
    n = 0.45
    estalo = ataque(passa_alta(ruido(n), 1800) * env_exp(int(n * SR), 0.009), 0.4)
    corpo = ataque(passa_baixa(ruido(n), 3500) * env_exp(int(n * SR), 0.04), 0.4)
    sub = seno_varrido(n, 240, 70, 7) * env_exp(int(n * SR), 0.05)
    x = satura(soma(estalo, corpo * 0.9, sub * 0.8), 1.9)
    return normaliza(reverb(x, 0.6, 0.55, 3200))


def tiro_escopeta():
    n = 0.9
    estalo = ataque(passa_alta(ruido(n), 800) * env_exp(int(n * SR), 0.025), 0.4)
    corpo = ataque(passa_baixa(ruido(n), 1800) * env_exp(int(n * SR), 0.14), 0.6)
    sub = seno_varrido(n, 130, 38, 5) * env_exp(int(n * SR), 0.16)
    boom = satura(soma(estalo * 0.9, corpo, sub * 1.4), 2.2)
    boom = reverb(boom, 0.8, 0.6, 2000)
    # bomba (pump) depois do tiro
    clique = ataque(passa_faixa(ruido(0.09), 900, 3500) * env_exp(int(0.09 * SR), 0.02), 0.5)
    clique2 = atraso(ataque(passa_faixa(ruido(0.07), 1500, 5000) * env_exp(int(0.07 * SR), 0.015), 0.5), 0.09)
    pump = atraso(soma(clique, clique2) * 0.55, 0.55)
    return normaliza(soma(boom, pump))


def recarga():
    def metal(freq, dur=0.25, g=1.0):
        t = t_(dur)
        ping = (np.sin(2 * np.pi * freq * t) + 0.5 * np.sin(2 * np.pi * freq * 2.76 * t)) * np.exp(-t / 0.05)
        rui = passa_faixa(ruido(dur), 1200, 6000) * env_exp(int(dur * SR), 0.012)
        return ataque(0.35 * ping + 0.9 * rui, 0.4) * g
    a = metal(1400, 0.2)
    b = atraso(metal(1900, 0.2, 0.9), 0.55)
    c = atraso(metal(900, 0.3, 1.1), 0.95)
    desliza = atraso(passa_faixa(ruido(0.22), 800, 4500) * np.linspace(0, 1, int(0.22 * SR)) * 0.25, 0.3)
    return normaliza(soma(a, b, c, desliza))


def passo(variante):
    n = 0.22
    rr = np.random.default_rng(900 + variante)
    base = passa_baixa(rr.standard_normal(int(n * SR)), 260 + 60 * variante) * env_exp(int(n * SR), 0.035)
    cascalho = passa_faixa(rr.standard_normal(int(n * SR)), 1500, 5000) * env_exp(int(n * SR), 0.03) * 0.25
    return normaliza(ataque(soma(base, cascalho), 1.5), 0.55)


def passo_corrida(variante):
    x = passo(variante + 3)
    return normaliza(x * 1.0, 0.7)


def faca_ar():
    n = 0.32
    t = t_(n)
    r = ruido(n)
    fc = 600 + 3500 * np.sin(np.pi * t / n)
    # varredura de faixa: aproximada por blocos
    saida = np.zeros(len(r))
    blocos = 16
    for i in range(blocos):
        a, b = int(i * len(r) / blocos), int((i + 1) * len(r) / blocos)
        f = fc[(a + b) // 2]
        saida[a:b] = passa_faixa(r, f * 0.6, f * 1.5)[a:b]
    saida *= np.sin(np.pi * t / n) ** 1.5
    return normaliza(saida, 0.6)


def faca_acerto():
    n = 0.3
    carne = passa_baixa(ruido(n), 900) * env_exp(int(n * SR), 0.05)
    mole = seno_varrido(n, 160, 60, 7) * env_exp(int(n * SR), 0.07)
    corte = ataque(passa_faixa(ruido(0.12), 1500, 5000) * env_exp(int(0.12 * SR), 0.03), 0.5) * 0.5
    return normaliza(satura(soma(carne, mole, corte), 1.5))


def voz_zumbi(semente, dur, f0, queda, aspereza=0.7, vogal=(520, 1050, 2400)):
    rr = np.random.default_rng(semente)
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f0 * (1 - queda * (t / dur)) * (1 + 0.04 * np.sin(2 * np.pi * (5.5 + rr.random()) * t))
    fase = 2 * np.pi * np.cumsum(f) / SR
    fonte = signal.sawtooth(fase) + 0.4 * signal.square(fase * 0.5)
    fonte += aspereza * rr.standard_normal(n) * 0.5
    fonte *= (1 + 0.5 * np.sin(2 * np.pi * 28 * t))  # rouquidao
    voz = formante(fonte, list(vogal), [110, 160, 260], [1.0, 0.8, 0.4])
    env = np.minimum(1, t / 0.08) * np.minimum(1, (dur - t) / 0.25)
    return satura(voz * env, 1.4)


def zumbi_gemido(i):
    p = [(0.95, 105, 0.35, (520, 1000, 2300)),
         (1.15, 90, 0.45, (450, 880, 2100)),
         (0.8, 120, 0.25, (600, 1300, 2500))][i]
    return normaliza(reverb(voz_zumbi(40 + i, p[0], p[1], p[2], 0.8, p[3]), 0.5, 0.25), 0.75)


def zumbi_ataque():
    v = voz_zumbi(70, 0.55, 150, 0.1, 1.0, (700, 1300, 2600))
    return normaliza(v, 0.85)


def zumbi_morre():
    v = voz_zumbi(80, 1.2, 100, 0.7, 0.9, (420, 800, 2000))
    queda = ataque(passa_baixa(ruido(0.4), 500) * env_exp(int(0.4 * SR), 0.12), 3) * 0.4
    return normaliza(reverb(soma(v, atraso(queda, 0.85)), 0.5, 0.3), 0.8)


def jogador_dor(i):
    f0 = [175, 150][i]
    v = voz_zumbi(100 + i, 0.4, f0, -0.15, 0.4, (650, 1200, 2700))
    return normaliza(v, 0.8)


def item_pegar():
    n = 0.35
    t = t_(n)
    a = np.sin(2 * np.pi * 880 * t) * np.exp(-t / 0.08)
    b = atraso(np.sin(2 * np.pi * 1320 * t[:int(0.25 * SR)]) * np.exp(-t[:int(0.25 * SR)] / 0.08), 0.07)
    return normaliza(soma(a * 0.7, b * 0.7), 0.45)


def curar():
    n = 1.0
    t = t_(n)
    r = np.zeros(len(t))
    for k, f in enumerate([392, 494, 587, 784]):
        a = int(0.09 * k * SR)
        tt = t[:len(t) - a]
        r[a:] += np.sin(2 * np.pi * f * tt) * np.exp(-tt / 0.3) * 0.5
    return normaliza(reverb(r, 0.6, 0.4), 0.5)


def clique_vazio():
    n = 0.12
    c = ataque(passa_faixa(ruido(n), 1500, 6000) * env_exp(int(n * SR), 0.01), 0.3)
    return normaliza(c, 0.6)


def laser_liga():
    n = 0.09
    t = t_(n)
    return normaliza(np.sin(2 * np.pi * 2200 * t) * np.exp(-t / 0.02) * 0.6 + ruido(n) * 0.05 * np.exp(-t / 0.01), 0.35)


def alavanca():
    n = 0.8
    clun = seno_varrido(0.3, 200, 70, 5) * env_exp(int(0.3 * SR), 0.07)
    metal = passa_faixa(ruido(0.5), 400, 3000) * env_exp(int(0.5 * SR), 0.1) * 0.5
    rangido = passa_faixa(ruido(0.4), 800, 2200) * np.sin(np.pi * t_(0.4) / 0.4) * 0.3
    return normaliza(reverb(soma(clun, atraso(metal, 0.1), rangido), 0.6, 0.3), 0.7)


def chefe_rugido():
    v = voz_zumbi(200, 2.2, 62, 0.35, 1.0, (350, 700, 1700))
    sub = seno_varrido(2.2, 70, 38, 1.5) * np.minimum(1, t_(2.2) / 0.2) * np.minimum(1, (2.2 - t_(2.2)) / 0.5) * 0.5
    return normaliza(reverb(soma(satura(v * 1.4, 2), sub), 1.0, 0.4, 1800), 0.9)


def fase_completa():
    n = 3.0
    t = t_(n)
    r = np.zeros(len(t))
    notas = [(261.6, 0.0), (329.6, 0.4), (392.0, 0.8), (523.3, 1.2)]
    for f, ini in notas:
        a = int(ini * SR)
        tt = t[:len(t) - a]
        r[a:] += (np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * 2 * f * tt)) * np.exp(-tt / 0.9) * 0.4
    return normaliza(reverb(r, 1.2, 0.5), 0.6)


def derrota():
    n = 3.5
    t = t_(n)
    acorde = sum(np.sin(2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * 4 * t)) for f in (55, 58.3, 82.4)) * np.exp(-t / 1.4)
    batida = seno_varrido(0.6, 90, 35, 4) * env_exp(int(0.6 * SR), 0.2)
    return normaliza(reverb(soma(acorde * 0.6, batida), 1.5, 0.6, 1500), 0.8)


# ---------------------------------------------------------------- musica (loops)
def laco(x, dur):
    """Torna x um loop sem emenda: mistura o rabo (alem de dur) no comeco."""
    n = int(dur * SR)
    if len(x) <= n:
        return x[:n]
    corpo = x[:n].copy()
    rabo = x[n:]
    m = min(len(rabo), n)
    janela = np.linspace(0, 1, m)
    corpo[:m] = corpo[:m] * janela + rabo[:m] * (1 - janela)
    return corpo


def vento(dur, forca=1.0, semente=1):
    rr = np.random.default_rng(semente)
    n = int(dur * SR)
    r = rr.standard_normal(n)
    # modulacao lenta do corte: varias faixas somadas
    t = np.arange(n) / SR
    saida = np.zeros(n)
    for fc, per, ph in [(260, 11.0, 0.0), (520, 7.3, 1.7), (900, 5.1, 3.1)]:
        mod = 0.5 + 0.5 * np.sin(2 * np.pi * t / per + ph)
        saida += passa_faixa(r, fc * 0.6, fc * 1.4) * (0.15 + 0.85 * mod) * (0.8 if fc < 600 else 0.35)
    return saida * forca


def pluck(freq, dur, brilho=0.5):
    """Karplus-Strong: nota dedilhada sombria."""
    n = int(dur * SR)
    p = int(SR / freq)
    buf = RNG.uniform(-1, 1, p)
    saida = np.zeros(n)
    for i in range(n):
        idx = i % p
        saida[i] = buf[idx]
        buf[idx] = 0.996 * 0.5 * (buf[idx] + buf[(i + 1) % p])
    return saida


def sino_distante(freq, dur=3.0):
    t = t_(dur)
    x = sum(a * np.sin(2 * np.pi * freq * m * t) * np.exp(-t / (dur * 0.35 / m))
            for m, a in [(1, 1.0), (2.01, 0.5), (2.76, 0.35), (5.4, 0.15)])
    return x


def musica_ambiente():
    dur = 48.0
    ext = dur + 4
    n = int(ext * SR)
    t = np.arange(n) / SR
    vent = vento(ext, 1.0, 5)
    zun = sum(np.sin(2 * np.pi * f * t + 0.5 * np.sin(2 * np.pi * 0.07 * t + f)) * a
              for f, a in [(55.0, 0.5), (55.4, 0.4), (82.5, 0.25), (110.3, 0.12)])
    zun *= 0.5 + 0.5 * np.sin(2 * np.pi * t / 16 - 1.0) ** 2
    cama = soma(vent * 0.5, zun * 0.45)
    # sinos/metal distantes, esparsos
    for ini, f in [(7.0, 233.1), (21.0, 207.7), (35.0, 261.6), (43.5, 196.0)]:
        s = sino_distante(f, 3.5) * 0.10
        s = reverb(s, 1.5, 0.7, 1500)
        a = int(ini * SR)
        cama[a:a + len(s)] += s[:max(0, min(len(s), n - a))]
    return normaliza(laco(passa_baixa(cama, 5000), dur), 0.7)


def musica_tensao():
    dur = 40.0
    ext = dur + 4
    n = int(ext * SR)
    t = np.arange(n) / SR
    # cluster dissonante crescendo e baixando
    base = sum(np.sin(2 * np.pi * f * t) * a for f, a in [(55, 0.5), (58.3, 0.45), (82.4, 0.3), (87.3, 0.28), (123.5, 0.1)])
    base *= 0.55 + 0.45 * np.sin(2 * np.pi * t / 20 + 0.5) ** 2
    # pulso de coracao
    bpm = 66
    pulso = np.zeros(n)
    passo_b = 60 / bpm
    k = 0
    while k * passo_b < ext - 1:
        for off, g in [(0, 1.0), (0.28, 0.6)]:
            a = int((k * passo_b + off) * SR)
            thump = seno_varrido(0.35, 90, 40, 6) * env_exp(int(0.35 * SR), 0.09)
            pulso[a:a + len(thump)] += thump[:max(0, min(len(thump), n - a))] * g * 0.55
        k += 1
    # notas dedilhadas (frigio em Mi), esparsas
    notas = np.zeros(n)
    escala = [82.4, 87.3, 98.0, 110.0, 123.5, 130.8, 146.8]
    rr = np.random.default_rng(31)
    ini = 3.0
    while ini < ext - 4:
        f = escala[rr.integers(0, len(escala))] * (2 if rr.random() < 0.5 else 4)
        p = pluck(f, 2.0, 0.6) * 0.18
        p = reverb(p, 1.4, 0.8, 1800)
        a = int(ini * SR)
        notas[a:a + len(p)] += p[:max(0, min(len(p), n - a))]
        ini += 3.5 + rr.random() * 3.0
    vent = vento(ext, 0.5, 9)
    mix = soma(base * 0.4, pulso, notas, vent * 0.3)
    return normaliza(laco(passa_baixa(mix, 6000), dur), 0.75)


def musica_combate():
    bpm = 132
    barras = 16
    passo_b = 60 / bpm
    dur = barras * 4 * passo_b
    ext = dur + 3
    n = int(ext * SR)
    saida = np.zeros(n)

    def colocar(x, seg, g=1.0):
        a = int(seg * SR)
        if a >= n:
            return
        m = min(len(x), n - a)
        saida[a:a + m] += x[:m] * g

    kick = seno_varrido(0.28, 150, 42, 7) * env_exp(int(0.28 * SR), 0.09)
    kick = satura(kick * 1.4, 1.6)
    chimbal = passa_alta(ruido(0.1), 6000) * env_exp(int(0.1 * SR), 0.018)
    caixa = soma(passa_faixa(ruido(0.22), 1500, 6000) * env_exp(int(0.22 * SR), 0.05),
                 seno_varrido(0.2, 260, 180, 4) * env_exp(int(0.2 * SR), 0.05) * 0.6)
    # linha de baixo (Mi frigio): colcheias
    padrao = [82.4, 82.4, 98.0, 82.4, 82.4, 87.3, 82.4, 110.0]
    total_passos = barras * 8
    for i in range(total_passos):
        seg = i * passo_b / 2
        f = padrao[i % 8]
        if (i // 32) % 2 == 1 and i % 8 in (3, 7):
            f *= 1.5 if i % 16 < 8 else 1.0
        dur_n = passo_b * 0.45
        tn = t_(dur_n)
        serra = signal.sawtooth(2 * np.pi * f * tn) * np.exp(-tn / (dur_n * 0.7))
        serra = passa_baixa(serra, 700 + 600 * (i % 4 == 0))
        colocar(serra * 0.5, seg)
    for b in range(barras * 4):
        seg = b * passo_b
        colocar(kick, seg, 0.9)
        if b % 2 == 1:
            colocar(caixa, seg, 0.55)
        colocar(chimbal, seg + passo_b / 2, 0.35)
        if b % 4 == 3:
            colocar(chimbal, seg + passo_b / 4, 0.25)
            colocar(chimbal, seg + passo_b * 3 / 4, 0.25)
    # acordes dissonantes em stabs a cada 2 compassos
    for bar in range(0, barras, 2):
        seg = bar * 4 * passo_b
        tn = t_(1.4)
        stab = sum(np.sin(2 * np.pi * f * tn) for f in (164.8, 174.6, 233.1)) * np.exp(-tn / 0.45)
        stab = passa_baixa(stab, 1800)
        colocar(reverb(stab * 0.16, 0.8, 0.4), seg)
    # cama de ruido ascendente
    t = np.arange(n) / SR
    cama = passa_faixa(RNG.standard_normal(n), 300, 1800) * (0.04 + 0.04 * np.sin(2 * np.pi * t / (dur / 2)) ** 2)
    saida += cama
    return normaliza(laco(satura(saida, 1.2), dur), 0.8)


# ---------------------------------------------------------------- principal
def main():
    pasta = sys.argv[1] if len(sys.argv) > 1 else 'saida_audio'
    sfx = os.path.join(pasta, 'sfx')
    mus = os.path.join(pasta, 'musica')
    salvar('tiro_pistola', tiro_pistola(), sfx)
    salvar('tiro_fuzil', tiro_fuzil(), sfx)
    salvar('tiro_escopeta', tiro_escopeta(), sfx)
    salvar('recarga', recarga(), sfx)
    for i in range(3):
        salvar(f'passo{i}', passo(i), sfx, '48k')
        salvar(f'corrida{i}', passo_corrida(i), sfx, '48k')
    salvar('faca_ar', faca_ar(), sfx)
    salvar('faca_acerto', faca_acerto(), sfx)
    for i in range(3):
        salvar(f'zumbi_gemido{i}', zumbi_gemido(i), sfx)
    salvar('zumbi_ataque', zumbi_ataque(), sfx)
    salvar('zumbi_morre', zumbi_morre(), sfx)
    for i in range(2):
        salvar(f'jogador_dor{i}', jogador_dor(i), sfx)
    salvar('item_pegar', item_pegar(), sfx)
    salvar('curar', curar(), sfx)
    salvar('clique_vazio', clique_vazio(), sfx)
    salvar('laser_liga', laser_liga(), sfx)
    salvar('alavanca', alavanca(), sfx)
    salvar('chefe_rugido', chefe_rugido(), sfx)
    salvar('fase_completa', fase_completa(), sfx)
    salvar('derrota', derrota(), sfx)
    salvar('musica_ambiente', musica_ambiente(), mus, '56k')
    salvar('musica_tensao', musica_tensao(), mus, '56k')
    salvar('musica_combate', musica_combate(), mus, '64k')


if __name__ == '__main__':
    main()
