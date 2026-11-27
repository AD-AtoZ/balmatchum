"""w5 복도 스캔에서 휴대폰 앱이 쓰는 "벽 지도"를 만든다.

사용법: python3 tools/build_w5_wallmap.py <viewer_pts_full.npz> <출력.bin> [--no-mirror] [--png 확인용.png]
입력 npz: xy(N×2, 벽에 맞춰 돌린 좌표 m), zc(바닥 높이를 0으로 맞춘 높이 m). `tools/build_w5_viewer.py`와 같은 입력이다.

좌표는 시연 경로 기준이다: forward = 출발선에서 복도 방향으로 간 거리(m), lateral = 중심선에서 오른쪽으로 벗어난 거리(m).
스캔은 실제 건물과 좌우가 뒤집혀 있어서 기본으로 좌우를 뒤집는다.

스캔의 구멍 메우기
- 벽: 벽 높이(0.4~2.0m)의 점이 있는 칸을 벽으로 보고, 30cm 이하의 틈은 이어 붙인다(모폴로지 닫기).
- 가상 벽: 스캔에 아무것도 찍히지 않아 벽이 끊긴 곳은 VIRTUAL_WALLS 선분으로 닫는다(지도 맞추기에도 벽으로 쓴다).
- 안쪽 공간: 바닥 점이 찍힌 칸, 천장 점이 찍힌 칸(위에 천장이 있으면 건물 안), 벽 칸을 합쳐 50cm 이하의 틈을 잇고,
  사방이 막힌 구멍을 채운다. 바닥이 번들거리거나 가려져 스캔이 비어 있던 곳도 천장이 찍혔으면 건물 안으로 본다.
- 경로 복도: 경로(lateral 0) 줄마다 좌우로 벽을 찾아 그 사이를 안쪽 공간으로 본다. 한쪽 벽이 없는
  교차로·출입구는 다른 줄에서 잰 복도 반폭(중앙값)을 쓴다. 바닥 스캔이 비어 있어도 경로는 끊기지 않는다.
  경로 중심선 좌우 40cm는 벽으로 찍혀 있어도(출발선 문틀 등) 항상 걸을 수 있는 곳으로 둔다.
- 걸을 수 있는 곳: 안쪽 공간에서 벽 근처 20cm를 뺀 곳 중, 시연 경로와 이어진 영역만.
  위치는 이 영역 안에서만 잡히게 한다.

파일 형식(리틀 엔디언): "BWM2", int32 forward 칸 수, int32 lateral 칸 수, float32 칸 크기(m),
float32 forward 최솟값, float32 lateral 최솟값, 이어서 칸마다 1바이트(가장 가까운 벽까지 거리, 칸 단위, 최대 255),
이어서 칸마다 1바이트(걸을 수 있으면 1). 칸 순서는 lateral 줄마다 forward 순서.
"""
import struct
import sys

import numpy as np
from scipy import ndimage

# tools/build_w5_viewer.py 의 ROUTE 와 같은 값
ROUTE_X = 14.8
START_Y = 99.0
ROUTE_LENGTH_M = 55.0

CELL_M = 0.10
FORWARD_RANGE = (-6.0, 62.0)
LATERAL_RANGE = (-13.0, 13.0)
WALL_HEIGHT = (0.4, 2.0)      # 바닥에서 이 높이 사이의 점을 벽으로 본다(바닥·천장 제외)
FLOOR_HEIGHT = (-0.15, 0.15)  # 바닥 점
CEILING_MIN = 2.3             # 이보다 높은 점은 천장으로 본다(벽 높이 범위 위)

# 스캔에 벽이 찍히지 않은 곳을 닫는 가상 벽 (forward, lateral) → (forward, lateral), 좌우 뒤집은 좌표 기준.
# 교차로 왼쪽(앞으로 43.7~48.0m): 양쪽 벽 끝(lateral 약 -1.45m)을 잇는다. 바깥에 천장·바닥 점이 거의 없다.
# 출발선 뒤(앞으로 -1.9m): 스캔이 -1.8m에서 끝나 복도 뒤가 트여 있다. 양쪽 벽(lateral -1.5 ~ +0.7m)을 가로로 막는다.
VIRTUAL_WALLS = [
    ((43.4, -1.45), (48.3, -1.45)),
    ((-1.9, -1.6), (-1.9, 0.8)),
]
VIRTUAL_WALL_HALF_CELLS = 1   # 가상 벽 두께 = 3칸(30cm)
MIN_WALL_POINTS = 2
WALL_GAP_CELLS = 3            # 벽 틈 잇기 (약 30cm)
INSIDE_GAP_CELLS = 5          # 안쪽 공간 틈 잇기 (약 50cm)
WALL_MARGIN_CELLS = 2         # 벽에서 이만큼은 걸을 수 없는 곳으로 둔다 (20cm)
CORRIDOR_SEARCH_M = 3.0       # 경로 중심선에서 좌우 벽을 찾는 최대 거리
ROUTE_MARGIN_M = 2.0          # 도착선 뒤로 복도를 더 이어 두는 길이
ROUTE_BACK_M = 1.6            # 출발선 뒤로 복도를 이어 두는 길이 (출발선 뒤 가상 벽 앞까지)
ROUTE_CORE_M = 0.4            # 경로 중심선 좌우 이 폭은 벽으로 찍혀 있어도 항상 걸을 수 있다


def route_core(nf, nl):
    """경로 중심선 좌우 ROUTE_CORE_M 띠. 출발선 문틀처럼 스캔이 경로를 가로막는 벽으로 찍힌 곳도 지나갈 수 있게 둔다."""
    band = np.zeros((nl, nf), bool)
    zero_l = int(round(-LATERAL_RANGE[0] / CELL_M))
    half = int(round(ROUTE_CORE_M / CELL_M))
    f0 = max(int(round((-ROUTE_BACK_M - FORWARD_RANGE[0]) / CELL_M)), 0)
    f1 = min(int(round((ROUTE_LENGTH_M + ROUTE_MARGIN_M - FORWARD_RANGE[0]) / CELL_M)), nf)
    band[zero_l - half: zero_l + half + 1, f0:f1] = True
    return band


def virtual_walls(nf, nl, mirror):
    g = np.zeros((nl, nf), bool)
    if not mirror:  # 가상 벽 좌표는 좌우 뒤집은 지도 기준
        return g
    for (f0, l0), (f1, l1) in VIRTUAL_WALLS:
        n = int(max(abs(f1 - f0), abs(l1 - l0)) / CELL_M * 2) + 1
        for t in np.linspace(0.0, 1.0, n):
            fi = int(np.floor((f0 + (f1 - f0) * t - FORWARD_RANGE[0]) / CELL_M))
            li = int(np.floor((l0 + (l1 - l0) * t - LATERAL_RANGE[0]) / CELL_M))
            h = VIRTUAL_WALL_HALF_CELLS
            g[max(li - h, 0):li + h + 1, max(fi - h, 0):fi + h + 1] = True
    return g


def corridor_band(wall, nf, nl):
    """경로 중심선 좌우 벽 사이 칸. 벽을 못 찾은 쪽은 다른 줄의 반폭 중앙값을 쓴다."""
    zero_l = int(round(-LATERAL_RANGE[0] / CELL_M))
    reach = int(round(CORRIDOR_SEARCH_M / CELL_M))
    f0 = int(round((-ROUTE_BACK_M - FORWARD_RANGE[0]) / CELL_M))
    f1 = int(round((ROUTE_LENGTH_M + ROUTE_MARGIN_M - FORWARD_RANGE[0]) / CELL_M))
    sides = {}
    for c in range(max(f0, 0), min(f1, nf)):
        left = next((k for k in range(1, reach) if wall[zero_l - k, c]), None)
        right = next((k for k in range(1, reach) if wall[zero_l + k, c]), None)
        sides[c] = (left, right)
    lefts = [v[0] for v in sides.values() if v[0] is not None]
    rights = [v[1] for v in sides.values() if v[1] is not None]
    med_l = int(np.median(lefts)) if lefts else reach // 2
    med_r = int(np.median(rights)) if rights else reach // 2
    band = np.zeros((nl, nf), bool)
    for c, (left, right) in sides.items():
        band[zero_l - (left if left is not None else med_l) + 1: zero_l + (right if right is not None else med_r), c] = True
    return band


def disk(r):
    y, x = np.ogrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


def build(src, mirror=True, forward_range=None, lateral_range=None):
    """지도 계산. forward_range·lateral_range를 주면 그 범위로 계산한다(뷰어가 스캔 전체를 메울 때). 앱 지도는 기본 범위."""
    global FORWARD_RANGE, LATERAL_RANGE
    saved = FORWARD_RANGE, LATERAL_RANGE
    FORWARD_RANGE = tuple(forward_range) if forward_range else FORWARD_RANGE
    LATERAL_RANGE = tuple(lateral_range) if lateral_range else LATERAL_RANGE
    try:
        return _build(src, mirror)
    finally:
        FORWARD_RANGE, LATERAL_RANGE = saved


def _build(src, mirror):
    d = np.load(src)
    xy, zc = d["xy"], d["zc"]
    forward = START_Y - xy[:, 1]
    lateral = (xy[:, 0] - ROUTE_X) if mirror else (ROUTE_X - xy[:, 0])

    nf = int(round((FORWARD_RANGE[1] - FORWARD_RANGE[0]) / CELL_M))
    nl = int(round((LATERAL_RANGE[1] - LATERAL_RANGE[0]) / CELL_M))
    fi = np.floor((forward - FORWARD_RANGE[0]) / CELL_M).astype(int)
    li = np.floor((lateral - LATERAL_RANGE[0]) / CELL_M).astype(int)
    inside_grid = (fi >= 0) & (fi < nf) & (li >= 0) & (li < nl)

    def count(mask):
        g = np.zeros((nl, nf), np.int32)
        m = inside_grid & mask
        np.add.at(g, (li[m], fi[m]), 1)
        return g

    wall_raw = count((zc >= WALL_HEIGHT[0]) & (zc <= WALL_HEIGHT[1])) >= MIN_WALL_POINTS
    floor_raw = count((zc >= FLOOR_HEIGHT[0]) & (zc <= FLOOR_HEIGHT[1])) > 0
    ceiling_raw = count(zc >= CEILING_MIN) > 0

    wall_closed = ndimage.binary_closing(wall_raw, structure=disk(WALL_GAP_CELLS)) | wall_raw
    virtual = virtual_walls(nf, nl, mirror)
    wall = wall_closed | virtual
    seen = floor_raw | ceiling_raw | wall
    joined = ndimage.binary_closing(seen, structure=disk(INSIDE_GAP_CELLS)) | seen
    holes_filled = ndimage.binary_fill_holes(joined)
    band = corridor_band(wall, nf, nl)
    inside = holes_filled | band
    walkable = inside & ~ndimage.binary_dilation(wall, structure=disk(WALL_MARGIN_CELLS))
    walkable |= route_core(nf, nl)

    # 시연 경로와 이어진 영역만 남긴다. 경로가 지나는 칸이 하나라도 있는 덩어리.
    labels, _ = ndimage.label(walkable)
    zero_l = int(round(-LATERAL_RANGE[0] / CELL_M))
    route_cells = [int(round((f - FORWARD_RANGE[0]) / CELL_M)) for f in np.arange(0.0, ROUTE_LENGTH_M, 0.5)]
    keep = {labels[zero_l + dl, c] for c in route_cells for dl in range(-8, 9) if labels[zero_l + dl, c] > 0}
    walkable = np.isin(labels, list(keep))

    distance = np.minimum(np.round(ndimage.distance_transform_edt(~wall)), 255).astype(np.uint8)
    stats = {
        "wall_raw": int(wall_raw.sum()), "wall": int(wall.sum()),
        "floor_raw": int(floor_raw.sum()), "ceiling_raw": int(ceiling_raw.sum()), "walkable": int(walkable.sum()),
    }
    # 무엇을 어떻게 메웠는지(뷰어에서 보여 줄 층). 바닥 메움은 바닥 점이 없는 안쪽 칸을 이유별로 나눈다.
    floor_gap = inside & ~wall & ~floor_raw
    layers = {
        "wall_gap": wall_closed & ~wall_raw,               # 30cm 이하 벽 틈 잇기
        "virtual_wall": virtual & ~wall_raw,               # 가상 벽
        "floor_ceiling": floor_gap & ceiling_raw,          # 위에 천장이 찍혀 있어 안쪽으로 본 곳
        "floor_join": floor_gap & ~ceiling_raw & joined,   # 50cm 이하 틈 잇기
        "floor_hole": floor_gap & ~ceiling_raw & ~joined & holes_filled,  # 사방이 막힌 구멍 채우기
        "floor_band": floor_gap & ~ceiling_raw & ~holes_filled,           # 경로 복도 띠
        "walkable": walkable,
        "inside": inside,          # 건물 안(벽 포함). 뷰어가 바닥·천장·벽 빈틈을 메울 범위
        "wall": wall_closed,       # 벽(가상 벽 제외)
    }
    return nf, nl, distance, walkable.astype(np.uint8), wall_raw, wall, floor_raw | ceiling_raw, stats, layers


def main(src, out, mirror=True, png=None):
    nf, nl, distance, walkable, wall_raw, wall, floor_raw, stats, _ = build(src, mirror)
    with open(out, "wb") as f:
        f.write(b"BWM2")
        f.write(struct.pack("<iifff", nf, nl, CELL_M, FORWARD_RANGE[0], LATERAL_RANGE[0]))
        f.write(distance.tobytes())
        f.write(walkable.tobytes())
    print(f"cells={nf}x{nl} mirror={mirror} {stats} -> {out}")

    if png:
        from PIL import Image
        img = np.full((nl, nf, 3), 255, np.uint8)
        img[walkable == 1] = (190, 225, 190)                 # 걸을 수 있는 곳
        img[floor_raw & (walkable == 1)] = (150, 205, 150)    # 그중 바닥·천장이 실제로 찍힌 곳
        img[wall & ~wall_raw] = (230, 120, 40)                # 메운 벽 틈
        img[wall_raw] = (30, 30, 30)                          # 원래 벽
        Image.fromarray(img).transpose(Image.ROTATE_90).resize((nl * 2, nf * 2), Image.NEAREST).save(png)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    png = sys.argv[sys.argv.index("--png") + 1] if "--png" in sys.argv else None
    if png:
        args = [a for a in args if a != png]
    main(args[0], args[1], mirror="--no-mirror" not in sys.argv, png=png)
