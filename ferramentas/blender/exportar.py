# ferramentas/blender/exportar.py
"""Leitura do OBJ do F0 e escrita do modelo animado em g3dj (JSON do LibGDX). Python PURO (sem bpy)."""
import json
import os

import animacao as an

CASAS = 5  # casas decimais gravadas no JSON
CASAS_MALHA = 4  # vertices/normais/UV (F8: malha HD, arquivo menor)
PASSO_PONTOS = 6  # F8: so 1 a cada N vertices entra no ajuste de altura do pe (aterrar); basta e e rapido
# O G3dModelLoader/Model do LibGDX guarda em "bones" a pose de REPOUSO do osso (T(+pivo)) e inverte ao carregar.
# (A v1 gravava T(-pivo) e o modelo explodiu no jogo.) NAO TENHO CERTEZA: se o corpo ainda sair errado, troque para True.
GRAVAR_POSE_INVERSA = False


def ler_obj(caminho):
    """Retorna (cantos, texturas): cantos = lista de triangulos; cada triangulo = 3 tuplas
    (grupo, (x, y, z), (nx, ny, nz), (u, v)) com v do OBJ (v para cima). Texturas = {material: arquivo}."""
    pasta = os.path.dirname(caminho)
    v, vt, vn, tris = [], [], [], []
    grupo = None
    mtl = None
    with open(caminho, "r") as f:
        for linha in f:
            t = linha.split()
            if not t or t[0] == "#":
                continue
            if t[0] == "v":
                v.append((float(t[1]), float(t[2]), float(t[3])))
            elif t[0] == "vt":
                vt.append((float(t[1]), float(t[2])))
            elif t[0] == "vn":
                vn.append((float(t[1]), float(t[2]), float(t[3])))
            elif t[0] == "g":
                grupo = t[1]
            elif t[0] == "mtllib":
                mtl = os.path.join(pasta, t[1])
            elif t[0] == "f":
                cantos = []
                for c in t[1:4]:
                    i, j, k = [int(x) for x in c.split("/")]
                    cantos.append((grupo, v[i - 1], vn[k - 1], vt[j - 1]))
                if len(t) != 4:
                    raise ValueError("so triangulos sao aceitos: " + linha.strip())
                tris.append(cantos)
    texturas = {}
    if mtl and os.path.exists(mtl):
        atual = None
        with open(mtl, "r") as f:
            for linha in f:
                t = linha.split()
                if len(t) >= 2 and t[0] == "newmtl":
                    atual = t[1]
                elif len(t) >= 2 and t[0] == "map_Kd" and atual:
                    texturas[atual] = t[1]
    return tris, texturas


def montar_vertices(tris):
    """Junta cantos iguais (posicao+normal+uv) em vertices unicos.
    Retorna (vertices, indices); vertice = dict(grupo, p, n, uv, pesos)."""
    mapa, vertices, indices = {}, [], []
    for tri in tris:
        for grupo, p, n, uv in tri:
            chave = (grupo, p, n, uv)
            if chave not in mapa:
                mapa[chave] = len(vertices)
                vertices.append({"grupo": grupo, "p": p, "n": n, "uv": uv,
                                 "pesos": an.limpar_pesos(an.pesos_do_vertice(grupo, p[0], p[1], p[2]))})
            indices.append(mapa[chave])
    return vertices, indices


def _r(x):
    return round(float(x), CASAS)


def _rm(x):
    return round(float(x), CASAS_MALHA)


def _nos_do_esqueleto(poses_repouso=True):
    """Arvore de nos (so ossos) no repouso: translacao local = pivo - pivo do pai."""
    filhos = {n: [] for n in an.NOMES_OSSOS}
    raiz = None
    for nome in an.NOMES_OSSOS:
        if an.PAI[nome] is None:
            raiz = nome
        else:
            filhos[an.PAI[nome]].append(nome)

    def no(nome):
        pai = an.PAI[nome]
        px, py, pz = an.PIVO[nome]
        if pai is None:
            t = (px, py, pz)
        else:
            qx, qy, qz = an.PIVO[pai]
            t = (px - qx, py - qy, pz - qz)
        d = {"id": nome, "translation": [_r(t[0]), _r(t[1]), _r(t[2])]}
        if filhos[nome]:
            d["children"] = [no(f) for f in filhos[nome]]
        return d

    return no(raiz)


def _transformacao_no(pose, nome):
    """Translacao e quaternio LOCAIS do no `nome` na pose (ver explicacao em animacao.py / LEIAME)."""
    pai = an.PAI[nome]
    px, py, pz = an.PIVO[nome]
    if pai is None:
        r = pose["raiz"]
        t = (px + r[0], py + r[1], pz + r[2])
    else:
        qx, qy, qz = an.PIVO[pai]
        t = (px - qx, py - qy, pz - qz)
    q = an.quaternio_da_matriz(an.rotacao_local(pose, nome))
    return t, q


def montar_g3dj(id_modelo, tris, textura, estilo, passo_chaves=1):
    """Dicionario g3dj completo (malha + material + esqueleto + animacoes)."""
    vertices, indices = montar_vertices(tris)
    indice_osso = {n: i for i, n in enumerate(an.NOMES_OSSOS)}
    dados = []
    for vtx in vertices:
        pes = sorted(vtx["pesos"], key=lambda a: -a[1])
        if len(pes) > 2:
            raise ValueError("vertice com mais de 2 influencias")
        while len(pes) < 2:
            pes.append((pes[0][0], 0.0))
        dados += [_rm(vtx["p"][0]), _rm(vtx["p"][1]), _rm(vtx["p"][2]),
                  _rm(vtx["n"][0]), _rm(vtx["n"][1]), _rm(vtx["n"][2]),
                  _rm(vtx["uv"][0]), _rm(1.0 - vtx["uv"][1]),  # LibGDX: origem da textura no topo
                  float(indice_osso[pes[0][0]]), _rm(pes[0][1]),
                  float(indice_osso[pes[1][0]]), _rm(pes[1][1])]
    modelo = {
        "version": [0, 1], "id": id_modelo,
        "meshes": [{
            "attributes": ["POSITION", "NORMAL", "TEXCOORD0", "BLENDWEIGHT0", "BLENDWEIGHT1"],
            "vertices": dados,
            "parts": [{"id": "malha_parte", "type": "TRIANGLES", "indices": indices}],
        }],
        "materials": [{"id": "material", "diffuse": [1.0, 1.0, 1.0],
                       "textures": [{"id": "textura", "filename": textura, "type": "DIFFUSE"}]}],
    }
    bones = []
    for nome in an.NOMES_OSSOS:  # pose de repouso T(+pivo); o LibGDX inverte ao carregar
        px, py, pz = an.PIVO[nome]
        sinal = -1.0 if GRAVAR_POSE_INVERSA else 1.0
        bones.append({"node": nome, "translation": [_r(sinal * px), _r(sinal * py), _r(sinal * pz)],
                      "rotation": [0.0, 0.0, 0.0, 1.0], "scale": [1.0, 1.0, 1.0]})
    no_malha = {"id": "malha", "parts": [{"meshpartid": "malha_parte", "materialid": "material", "bones": bones}]}
    modelo["nodes"] = [_nos_do_esqueleto(), no_malha]

    pontos = [(v["p"][0], v["p"][1], v["p"][2], v["pesos"]) for v in vertices][::PASSO_PONTOS]
    animacoes = []
    for nome_anim, n_quadros, _loop in an.listar_animacoes_exportacao(estilo):
        quadros = [(q, an.pose_final(estilo, nome_anim, float(q), pontos))
                   for q in range(0, n_quadros + 1, passo_chaves)]
        if quadros[-1][0] != n_quadros:
            quadros.append((n_quadros, an.pose_final(estilo, nome_anim, float(n_quadros), pontos)))
        ossos = []
        for osso in an.NOMES_OSSOS:
            chaves = []
            for q, pose in quadros:
                t, rot = _transformacao_no(pose, osso)
                chaves.append({"keytime": _r(q * 1000.0 / an.FPS),
                               "translation": [_r(t[0]), _r(t[1]), _r(t[2])],
                               "rotation": [_r(rot[0]), _r(rot[1]), _r(rot[2]), _r(rot[3])]})
            ossos.append({"boneId": osso, "keyframes": chaves})
        animacoes.append({"id": nome_anim, "bones": ossos})
    modelo["animations"] = animacoes
    return modelo


def escrever_g3dj(caminho, modelo):
    with open(caminho, "w") as f:
        json.dump(modelo, f, separators=(",", ":"))
    return os.path.getsize(caminho)
