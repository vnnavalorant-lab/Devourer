#!/usr/bin/env python3
"""
Converte tools/zombie_form.bbmodel (Blockbench) para o JSON "assado" que o mod lê:
    src/main/resources/assets/devourer/models/zombie_form.json
e copia as texturas do .bbmodel para assets/devourer/textures/entity/.

Uso:  python3 tools/convert_bbmodel.py [arquivo.bbmodel]

Convenções (já validadas contra o modelo do jogador do Minecraft):
  java_x = -bb_x ; java_y = 24 - bb_y ; java_z = bb_z   (rotação de 180° em Z, sem espelhar)
  rotações em graus, ordem ZYX -> java (-rx, -ry, rz)
Grupos "Head/Body/Right Arm/Left Arm/Left Leg/Right Leg" seguem o modelo vanilla do jogador.
Grupos "tentacle*" + o marcador "ik*" definem as correntes animadas por IK dentro do jogo.
"""
import base64, json, math, os, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "zombie_form.bbmodel")
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "devourer")
VANILLA = {"Head", "Body", "Right Arm", "Left Arm", "Left Leg", "Right Leg"}
# Afasta ligeiramente as caixas que coincidem com o corpo do jogador (anti Z-fighting), em pixels.
LAYER_BIAS = 0.2

j = json.load(open(SRC))
elements = {e["uuid"]: e for e in j["elements"]}
groups = {g["uuid"]: g for g in j["groups"]}
tex_info = j["textures"]

# ---------------------------------------------------------------- matemática
def rot_matrix(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    mm = lambda A, B: [[sum(A[i][k] * B[k][c] for k in range(3)) for c in range(3)] for i in range(3)]
    return mm(Rz, mm(Ry, Rx))  # Rz * Ry * Rx

def apply(M, v):
    return [sum(M[i][k] * v[k] for k in range(3)) for i in range(3)]

def to_java(p):
    return [-p[0], 24 - p[1], p[2]]

def to_java_dir(d):
    return [-d[0], -d[1], d[2]]

# corners (TL, TR, BR, BL) de cada face do Blockbench, a partir de min/max
def corners(face, a, b):
    (x0, y0, z0), (x1, y1, z1) = a, b  # min, max
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east":  [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west":  [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up":    [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down":  [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]

NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

def bake_cube(e, pivot_java, bias):
    f = list(map(float, e["from"])); t = list(map(float, e["to"]))
    inf = float(e.get("inflate") or 0)
    f = [v - inf for v in f]; t = [v + inf for v in t]
    flat = any(abs(t[i] - f[i]) < 1e-6 for i in range(3))
    if bias and not flat:
        f = [v - bias for v in f]; t = [v + bias for v in t]
    org = e.get("origin") or [0, 0, 0]
    rot = e.get("rotation") or [0, 0, 0]
    M = rot_matrix(*rot)
    quads = []
    for face, fd in e["faces"].items():
        ti = fd.get("texture")
        if ti is None or ti is False:
            continue
        uv = fd["uv"]
        cs = corners(face, f, t)
        # faces sem área são descartadas (ex.: east/west de um plano)
        pts = [apply(M, [c[i] - org[i] for i in range(3)]) for c in cs]
        pts = [[p[i] + org[i] for i in range(3)] for p in pts]
        e1 = [pts[1][i] - pts[0][i] for i in range(3)]; e2 = [pts[3][i] - pts[0][i] for i in range(3)]
        cr = [e1[1]*e2[2]-e1[2]*e2[1], e1[2]*e2[0]-e1[0]*e2[2], e1[0]*e2[1]-e1[1]*e2[0]]
        if math.sqrt(sum(c*c for c in cr)) < 1e-6:
            continue
        tw, th = tex_info[ti]["uv_width"], tex_info[ti]["uv_height"]
        us = [uv[0], uv[2], uv[2], uv[0]]; vs = [uv[1], uv[1], uv[3], uv[3]]
        n = to_java_dir(apply(M, list(NORMAL[face])))
        jp = []
        for p in pts:
            q = to_java(p)
            jp += [round(q[k] - pivot_java[k], 4) for k in range(3)]
        quads.append({"t": ti, "p": jp,
                      "uv": sum([[round(us[k] / tw, 5), round(vs[k] / th, 5)] for k in range(4)], []),
                      "n": [round(c, 4) for c in n]})
    return quads

# ---------------------------------------------------------------- hierarquia
bones = []

def walk(node, parent):
    if isinstance(node, str):
        return
    g = groups[node["uuid"]]
    name = g["name"]
    if g.get("export", True) is False:
        return
    org = g.get("origin") or [0, 0, 0]
    rot = g.get("rotation") or [0, 0, 0]
    pj = to_java(org)
    idx = len(bones)
    bone = {"name": name, "parent": parent, "pivot": [round(v, 4) for v in pj],
            "rot": [round(-rot[0], 4), round(-rot[1], 4), round(rot[2], 4)], "quads": []}
    bones.append(bone)
    bias = LAYER_BIAS if name in VANILLA else 0
    for c in node.get("children", []):
        if isinstance(c, str):
            e = elements[c]
            if e.get("export", True) is False:
                continue
            bone["quads"] += bake_cube(e, pj, bias)
        else:
            walk(c, idx)

for g in j["outliner"]:
    walk(g, -1)

# "Waist" não existe no modelo vanilla: remove e promove os filhos
wi = [i for i, b in enumerate(bones) if b["name"] == "Waist"]
for w in wi:
    assert not bones[w]["quads"]
    for b in bones:
        if b["parent"] == w:
            b["parent"] = bones[w]["parent"]
# remapeia índices ignorando Waist
keep = [i for i in range(len(bones)) if i not in wi]
remap = {old: new for new, old in enumerate(keep)}
out_bones = []
for i in keep:
    b = dict(bones[i]); p = b["parent"]
    while p in wi: p = bones[p]["parent"]
    b["parent"] = remap[p] if p != -1 else -1
    out_bones.append(b)

out = {"textures": [os.path.splitext(t["name"])[0] for t in tex_info], "bones": out_bones}
os.makedirs(os.path.join(ROOT, "models"), exist_ok=True)
json.dump(out, open(os.path.join(ROOT, "models", "zombie_form.json"), "w"), separators=(",", ":"))

# texturas embutidas no .bbmodel
os.makedirs(os.path.join(ROOT, "textures", "entity"), exist_ok=True)
for t in tex_info:
    src = t.get("source", "")
    if src.startswith("data:"):
        data = base64.b64decode(src.split(",", 1)[1])
        open(os.path.join(ROOT, "textures", "entity", t["name"]), "wb").write(data)

nq = sum(len(b["quads"]) for b in out_bones)
print(f"ok: {len(out_bones)} bones, {nq} quads, texturas: {out['textures']}")
