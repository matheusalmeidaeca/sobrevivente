#!/usr/bin/env python3
"""Testes automaticos da animacao gerada. Le SO o .g3dj, o meta .json e o config (nao usa o gerador).
Uso: python3 teste_animacao.py [--g3dj saida/humano.g3dj] [--config config_humano.json]
Sai com codigo 0 se tudo passou, 1 se algo falhou."""
import argparse, json, math, os, sys
import numpy as np

DEG = math.pi / 180.0
SINAL_X = {"ponta": -1, "coluna1": -1, "coluna2": -1, "coluna3": -1, "pescoco": -1, "cabeca": -1, "canela": -1}


def quat_mat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def decompor(R):  # R = Rx(a) Ry(b) Rz(c)
    return (math.atan2(-R[1, 2], R[2, 2]), math.asin(max(-1.0, min(1.0, R[0, 2]))), math.atan2(-R[0, 1], R[0, 0]))


def base(n):
    return n.replace("_dir", "").replace("_esq", "")


def anatomico(nome, R):
    a, b, c = decompor(R)
    sx = SINAL_X.get(nome.replace("_dir", "").replace("_esq", ""), 1)
    sy = sz = -1 if nome.endswith("_esq") else 1
    return sx * a / DEG, sy * b / DEG, sz * c / DEG   # flexao, rotacao interna, abducao


def limite_osso(L, dist, nome):
    b = base(nome)
    if b in dist:
        g = L["pescoco"] if b in ("pescoco", "cabeca") else L["coluna"]
        d = dist[b]
        return {"flex": (-g["extensao"] * d["x"], g["flexao"] * d["x"]), "rot": (-g["rot_externa"] * d["y"], g["rot_interna"] * d["y"]),
                "abd": (-g["aducao"] * d["z"], g["abducao"] * d["z"])}
    if b in L and isinstance(L[b], dict) and "flexao" in L[b]:
        g = L[b]
        return {"flex": (-g["extensao"], g["flexao"]), "rot": (-g["rot_externa"], g["rot_interna"]), "abd": (-g["aducao"], g["abducao"])}
    return None


def carregar(caminho):
    g = json.load(open(caminho))
    no = g["nodes"][0]
    pai, trans = {}, {}

    def rec(n, p):
        trans[n["id"]] = np.array(n.get("translation", [0, 0, 0]), dtype=float)
        pai[n["id"]] = p
        for c in n.get("children", []):
            rec(c, n["id"])
    rec(no, None)
    return g, pai, trans


def fk_quadro(nomes, pai, trans, raiz_pos, quats):
    res = {}
    for n in nomes:   # ordem pai antes do filho
        R = quat_mat(quats[n])
        if pai[n] is None:
            res[n] = (np.array(raiz_pos, dtype=float), R)
        else:
            pp, Rp = res[pai[n]]
            res[n] = (pp + Rp @ trans[n], Rp @ R)
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--g3dj", default="saida/humano.g3dj")
    ap.add_argument("--config", default="config_humano.json")
    a = ap.parse_args()
    cfg = json.load(open(a.config))
    meta = json.load(open(a.g3dj.rsplit(".", 1)[0] + "_meta.json"))
    g, pai, trans = carregar(a.g3dj)
    L = cfg["limites"]
    dist = L["distribuicao"]
    tol = L.get("tolerancia_graus", 0.05) + 0.01
    nomes = meta["ossos"]
    falhas = []
    linhas = []

    BIND = np.zeros((len(nomes), 3))
    for i, n in enumerate(nomes):
        p, acc = n, np.zeros(3)
        while p is not None:
            acc = acc + trans[p]
            p = pai[p]
        BIND[i] = acc

    def v_chao(por_osso, k, meta, nome_an):
        return 0.0

    # ---------- T1: pesos ----------
    maxosso, minsoma, maxsoma, bad = 0, 9, 0, 0
    nverts = 0
    for m in g["meshes"]:
        nat = len(m["attributes"])
        ncomp = {"POSITION": 3, "NORMAL": 3, "TEXCOORD0": 2}
        stride = sum(ncomp.get(k, 2) for k in m["attributes"])
        V = np.array(m["vertices"], dtype=float).reshape(-1, stride)
        W = V[:, 8:].reshape(len(V), 4, 2)
        w = W[:, :, 1]
        soma = w.sum(1)
        minsoma, maxsoma = min(minsoma, soma.min()), max(maxsoma, soma.max())
        maxosso = max(maxosso, int((w > 1e-6).sum(1).max()))
        bad += int((np.abs(soma - 1) > 1e-3).sum()) + int(((W[:, :, 0] < 0) | (W[:, :, 0] >= len(nomes))).sum())
        nverts += len(V)
    ok = bad == 0 and maxosso <= 4
    linhas.append(("pesos", ok, "%d vertices, soma min/max = %.5f/%.5f, no maximo %d ossos por vertice" % (nverts, minsoma, maxsoma, maxosso)))

    # ---------- T2/T3: limites, quaternios ----------
    viol, nq, maxnorm = [], 0, 0.0
    rel_min = {}
    for an in g["animations"]:
        nome_an = an["id"]
        por_osso = {b["boneId"]: b["keyframes"] for b in an["bones"]}
        nk = len(por_osso["quadril"])
        for k in range(nk):
            ang = {}
            for n in nomes:
                kf = por_osso[n][k]
                q = np.array(kf["rotation"], dtype=float)
                maxnorm = max(maxnorm, abs(np.linalg.norm(q) - 1))
                nq += 1
                if n == "quadril":
                    continue
                ang[n] = anatomico(n, quat_mat(q))
                lim = limite_osso(L, dist, n)
                if lim:
                    for r, v in zip(("flex", "rot", "abd"), ang[n]):
                        lo, hi = lim[r]
                        if v < lo - tol or v > hi + tol:
                            viol.append((nome_an, n, r, v, lo, hi, por_osso[n][k]["keytime"]))
            for grupo, osso_lista, G in (("coluna", ["coluna1", "coluna2", "coluna3"], L["coluna"]), ("pescoco+cabeca", ["pescoco", "cabeca"], L["pescoco"])):
                tot = [sum(ang[o][i] for o in osso_lista) for i in range(3)]
                faixas = [(-G["extensao"], G["flexao"]), (-G["rot_externa"], G["rot_interna"]), (-G["aducao"], G["abducao"])]
                for i, (lo, hi) in enumerate(faixas):
                    if tot[i] < lo - 3 * tol or tot[i] > hi + 3 * tol:
                        viol.append((nome_an, grupo, ("flex", "rot", "abd")[i], tot[i], lo, hi, por_osso["quadril"][k]["keytime"]))
    linhas.append(("limites articulares (24 ossos, todos os quadros + somas de coluna/pescoco)", not viol,
                   "%d rotacoes checadas, %d violacoes" % (nq, len(viol))))
    for v in viol[:10]:
        linhas.append(("   violacao", False, "%s %s %s=%.1f fora de [%.0f,%.0f] em t=%.0fms" % v))
    linhas.append(("quaternios unitarios", maxnorm < 1e-3, "desvio maximo da norma = %.2e" % maxnorm))

    # ---------- T4: pe nao desliza / nao atravessa o chao ----------
    pe = meta["pe"]
    ah, hb, bf, pt = pe["tornozelo_altura_m"], pe["calcanhar_atras_m"], pe["bola_a_frente_m"], pe["ponta_a_frente_m"]
    ponta_y = ah + trans["ponta_dir"][1]      # altura (mundo) da junta do dedo no bind
    res_slide = []
    pen_min = 0.0
    pen_onde = ""
    for an in g["animations"]:
        nome_an = an["id"]
        info = meta["animacoes"][nome_an]
        v = info["velocidade_m_s"]
        por_osso = {b["boneId"]: b["keyframes"] for b in an["bones"]}
        nk = len(por_osso["quadril"])
        tempos = [por_osso["quadril"][k]["keytime"] / 1000.0 for k in range(nk)]
        pontos = {}
        for k in range(nk):
            quats = {n: por_osso[n][k]["rotation"] for n in nomes}
            fk = fk_quadro(nomes, pai, trans, por_osso["quadril"][k]["translation"], quats)
            for s in ("dir", "esq"):
                p_pe, R_pe = fk["pe_" + s]
                p_pt, R_pt = fk["ponta_" + s]
                calc = p_pe + R_pe @ np.array([0, -ah, hb])
                bola = p_pe + R_pe @ np.array([0, -ah, -bf])
                ponta = p_pt + R_pt @ np.array([0, -ponta_y, -(pt - bf)])
                for nome_pt, P in (("calcanhar", calc), ("bola", bola), ("dedo", ponta)):
                    P = P.copy()
                    P[2] -= v * tempos[k]       # o personagem anda para -Z (frente): mundo = local - v*t
                    pontos.setdefault((s, nome_pt), []).append(P)
        # penetracao (pontos do pe; a malha inteira e testada mais abaixo)
        for (s, nm), lst in pontos.items():
            if nome_an == "morrer":
                continue
            ymin = min(p[1] for p in lst)
            if ymin < pen_min:
                pen_min, pen_onde = ymin, "%s %s %s" % (nome_an, s, nm)
        # deslizamento: ponto "no chao" (y < 6 mm) em dois quadros seguidos e o ponto mais baixo do pe
        pior, cont, onde = 0.0, 0, "-"
        for s in ("dir", "esq"):
            for nm in ("calcanhar", "bola", "dedo"):
                P = np.array(pontos[(s, nm)])
                toca = P[:, 1] < 0.002
                for k in range(1, nk - 2):
                    if toca[k - 1:k + 3].all():          # contato mantido (nao e chegada nem saida do pe)
                        d = P[k + 1] - P[k]
                        desl = math.hypot(d[0], d[2])
                        cont += 1
                        if nm == "dedo":                     # ponta do dedo rola sobre o chao: aceita ate 2x
                            desl = desl / 2.0
                        if desl > pior:
                            pior, onde = desl, "%s %s t=%.2fs" % (s, nm, tempos[k])
        res_slide.append((nome_an, pior, cont, onde))
    res_slide = [r for r in res_slide if r[0] != "morrer"]     # na morte o pe nao fica plantado
    maxdesl = max(r[1] for r in res_slide)
    marcha = [r for r in res_slide if r[0] in ("andar", "correr")]
    limite_mm = 5.0
    ok_slide = all(r[1] * 1000 <= limite_mm for r in res_slide)
    linhas.append(("pe nao desliza (calcanhar/bola/dedo em contato sustentado andam <= %.0f mm por quadro (dedo conta metade, pois rola))" % limite_mm, ok_slide,
                   "; ".join("%s %.1f mm (%s)" % (r[0], r[1] * 1000, r[3]) for r in res_slide)))
    linhas.append(("pe nao atravessa o chao (> 5 mm)", pen_min > -0.005, "ponto mais baixo = %.1f mm (%s)" % (pen_min * 1000, pen_onde or "-")))
    linhas.append(("pernas nunca esticadas alem do alcance", meta.get("esticou_pernas", 1) == 0, "contagem = %d" % meta.get("esticou_pernas", -1)))


    # ---------- T5: nenhuma parte da malha atravessa o chao (pele calculada com os pesos) ----------
    pior_y, onde_y = 9.0, ""
    for an in g["animations"]:
        nome_an = an["id"]
        por_osso = {b["boneId"]: b["keyframes"] for b in an["bones"]}
        nk = len(por_osso["quadril"])
        for k in range(0, nk, 2):
            quats = {n: por_osso[n][k]["rotation"] for n in nomes}
            fk = fk_quadro(nomes, pai, trans, por_osso["quadril"][k]["translation"], quats)
            P = np.array([fk[n][0] for n in nomes])
            R = np.array([fk[n][1] for n in nomes])
            for m in g["meshes"]:
                V = np.array(m["vertices"], dtype=float).reshape(-1, 16)[::5]
                pos = V[:, 0:3]
                out = np.zeros_like(pos)
                for j in range(4):
                    ii = V[:, 8 + 2 * j].astype(int)
                    w = V[:, 9 + 2 * j]
                    rel = pos - BIND[ii]
                    out += w[:, None] * (np.einsum("nij,nj->ni", R[ii], rel) + P[ii])
                y = out[:, 1].min() - (v_chao(por_osso, k, meta, nome_an))
                if y < pior_y:
                    pior_y, onde_y = y, "%s t=%.2fs" % (nome_an, por_osso["quadril"][k]["keytime"] / 1000.0)
    linhas.append(("malha nao atravessa o chao (> 20 mm)", pior_y > -0.02, "ponto mais baixo = %.1f mm (%s)" % (pior_y * 1000, onde_y)))

    print("TESTE DA ANIMACAO  (%s)" % a.g3dj)
    tudo = True
    for nome, ok, det in linhas:
        print(" [%s] %s -- %s" % ("OK " if ok else "ERRO", nome, det))
        tudo = tudo and ok
    print("RESULTADO:", "TUDO PASSOU" if tudo else "FALHOU")
    sys.exit(0 if tudo else 1)


if __name__ == "__main__":
    main()
