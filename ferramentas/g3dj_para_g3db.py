#!/usr/bin/env python3
"""Converte .g3dj (JSON) em .g3db (UBJSON binario do LibGDX): carregamento bem mais rapido (sem ler texto JSON).
Uso: python3 ferramentas/g3dj_para_g3db.py android/assets/modelos/*.g3dj
O jogo usa o .g3db quando existe e cai para o .g3dj quando nao existe."""
import json, struct, sys, os

def chave(b, k):
    d = k.encode('utf-8'); b += enc_len(len(d)) + d

def enc_len(n):
    if n < 128: return b'i' + struct.pack('>b', n)
    if n < 32768: return b'I' + struct.pack('>h', n)
    return b'l' + struct.pack('>i', n)

def escrever(b, v):
    if v is None: b += b'Z'
    elif v is True: b += b'T'
    elif v is False: b += b'F'
    elif isinstance(v, int):
        # no leitor do LibGDX: B = byte sem sinal, i = int16, l = int32 (tamanhos/chaves usam i/I/l de 1/2/4 bytes)
        if 0 <= v < 256: b += b'B' + struct.pack('>B', v)
        elif -32768 <= v < 32768: b += b'i' + struct.pack('>h', v)
        else: b += b'l' + struct.pack('>i', v)
    elif isinstance(v, float): b += b'd' + struct.pack('>f', v)
    elif isinstance(v, str):
        d = v.encode('utf-8'); b += b'S' + enc_len(len(d)) + d
    elif isinstance(v, list):
        if True:
            b += b'['
            for x in v: escrever(b, x)
            b += b']'
    elif isinstance(v, dict):
        b += b'{'
        for k, x in v.items():
            chave(b, k); escrever(b, x)
        b += b'}'
    else: raise TypeError(type(v))

for caminho in sys.argv[1:]:
    d = json.load(open(caminho))
    b = bytearray(); escrever(b, d)
    saida = os.path.splitext(caminho)[0] + '.g3db'
    open(saida, 'wb').write(b)
    print(caminho, os.path.getsize(caminho), '->', saida, len(b))
