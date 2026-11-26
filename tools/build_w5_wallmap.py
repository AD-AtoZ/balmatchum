"""w5 복도 스캔에서 "벽 지도"를 만든다. 휴대폰이 카메라로 본 점들을 이 지도에 대 보고 위치를 바로잡는 데 쓴다.

사용법: python3 tools/build_w5_wallmap.py <viewer_pts_full.npz> <출력.bin> [--no-mirror] [--png 확인용.png]

좌표는 시연 경로 기준이다: forward = 출발선에서 복도 방향으로 간 거리(m), lateral = 중심선에서 오른쪽으로 벗어난 거리(m).
스캔은 실제 복도와 좌우가 뒤집혀 있다고 보고(뷰어에서 사용자가 뒤집어 달라고 함) 기본으로 좌우를 뒤집는다.

파일 형식(리틀 엔디언): "BWM1", int32 forward 칸 수, int32 lateral 칸 수, float32 칸 크기(m),
float32 forward 최솟값, float32 lateral 최솟값, 이어서 칸마다 1바이트(가장 가까운 벽까지 거리, 칸 단위, 최대 255).
칸 순서는 lateral 줄마다 forward 순서.
"""
import struct
import sys

import numpy as np
from scipy import ndimage

# tools/build_w5_viewer.py 의 ROUTE 와 같은 값
ROUTE_X = 14.8
START_Y = 99.0

CELL_M = 0.10
FORWARD_RANGE = (-6.0, 62.0)
LATERAL_RANGE = (-13.0, 13.0)
# 바닥에서 이 높이 사이의 점만 벽으로 본다(바닥·천장 제외). 가슴 높이의 카메라가 주로 보는 범위.
HEIGHT_RANGE = (0.4, 2.0)
MIN_POINTS_PER_CELL = 2


def main(src, out, mirror=True, png=None):
    d = np.load(src)
    xy, zc = d["xy"], d["zc"]
    keep = (zc >= HEIGHT_RANGE[0]) & (zc <= HEIGHT_RANGE[1])
    x, y = xy[keep, 0], xy[keep, 1]
    forward = START_Y - y
    lateral = (x - ROUTE_X) if mirror else (ROUTE_X - x)

    nf = int(round((FORWARD_RANGE[1] - FORWARD_RANGE[0]) / CELL_M))
    nl = int(round((LATERAL_RANGE[1] - LATERAL_RANGE[0]) / CELL_M))
    fi = np.floor((forward - FORWARD_RANGE[0]) / CELL_M).astype(int)
    li = np.floor((lateral - LATERAL_RANGE[0]) / CELL_M).astype(int)
    inside = (fi >= 0) & (fi < nf) & (li >= 0) & (li < nl)
    count = np.zeros((nl, nf), np.int32)
    np.add.at(count, (li[inside], fi[inside]), 1)
    wall = count >= MIN_POINTS_PER_CELL

    distance = np.minimum(np.round(ndimage.distance_transform_edt(~wall)), 255).astype(np.uint8)
    with open(out, "wb") as f:
        f.write(b"BWM1")
        f.write(struct.pack("<iifff", nf, nl, CELL_M, FORWARD_RANGE[0], LATERAL_RANGE[0]))
        f.write(distance.tobytes())
    print(f"cells={nf}x{nl} wall={int(wall.sum())} mirror={mirror} size={4 + 20 + distance.size} -> {out}")

    if png:
        from PIL import Image
        img = np.full((nl, nf, 3), 255, np.uint8)
        img[distance <= 3] = (200, 220, 255)
        img[wall] = (20, 20, 20)
        zero_l = int(round(-LATERAL_RANGE[0] / CELL_M))
        for fm in (0.0, 21.0, 55.0):
            img[:, int(round((fm - FORWARD_RANGE[0]) / CELL_M))] = (255, 60, 60)
        img[zero_l, :] = np.where(wall[zero_l][:, None], img[zero_l, :], (60, 200, 90))
        Image.fromarray(img).resize((nf * 2, nl * 2), Image.NEAREST).save(png)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    png = sys.argv[sys.argv.index("--png") + 1] if "--png" in sys.argv else None
    if png:
        args = [a for a in args if a != png]
    main(args[0], args[1], mirror="--no-mirror" not in sys.argv, png=png)
