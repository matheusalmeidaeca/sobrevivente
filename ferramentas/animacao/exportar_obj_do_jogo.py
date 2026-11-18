#!/usr/bin/env python3
"""Converte o jogador.g3dj atual do jogo num .obj (so a malha, sem esqueleto), para servir de malha de teste.
Uso: python3 exportar_obj_do_jogo.py ../../android/assets/modelos/jogador.g3dj exemplo/boneco.obj"""
import json, sys
d = json.load(open(sys.argv[1]))
mats = {m['id']: m for m in d['materials']}
partmat = {}
for n in d['nodes']:
    for p in n.get('parts', []):
        partmat[p['meshpartid']] = p['materialid']
out = open(sys.argv[2], 'w')
mtl = open(sys.argv[2].rsplit('.', 1)[0] + '.mtl', 'w')
out.write('mtllib ' + sys.argv[2].rsplit('/', 1)[-1].rsplit('.', 1)[0] + '.mtl\n')
base = 1
for m in d['meshes']:
    nat = 0
    for a in m['attributes']:
        nat += {'POSITION': 3, 'NORMAL': 3, 'TEXCOORD0': 2}.get(a, 2)
    nv = len(m['vertices']) // nat
    vs = m['vertices']
    for i in range(nv):
        o = i * nat
        out.write('v %.5f %.5f %.5f\n' % (vs[o], vs[o+1], vs[o+2]))
    for i in range(nv):
        o = i * nat
        out.write('vn %.4f %.4f %.4f\n' % (vs[o+3], vs[o+4], vs[o+5]))
    for i in range(nv):
        o = i * nat
        out.write('vt %.5f %.5f\n' % (vs[o+6], 1 - vs[o+7]))
    for p in m['parts']:
        mid = partmat[p['id']]
        out.write('g %s\nusemtl %s\n' % (p['id'], mid))
        ix = p['indices']
        for k in range(0, len(ix), 3):
            a, b, c = (ix[k] + base, ix[k+1] + base, ix[k+2] + base)
            out.write('f %d/%d/%d %d/%d/%d %d/%d/%d\n' % (a, a, a, b, b, b, c, c, c))
    base += nv
for k, m in mats.items():
    tex = m['textures'][0]['filename'] if m.get('textures') else ''
    mtl.write('newmtl %s\nKd 1 1 1\n' % k)
    if tex:
        mtl.write('map_Kd %s\n' % tex)
