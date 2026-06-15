from PIL import Image
from math import sqrt

ref = Image.open('c:/Users/iuuuu/AndroidStudioProjects/Studyapp/assets/科技地球.jpg')
out = Image.open('c:/Users/iuuuu/AndroidStudioProjects/Studyapp/screen_tex6.png')
pix_ref = ref.load()
pix_out = out.load()

# Reference globe info
rcx, rcy, rr = 839, 502, 695

# Output globe (density 2.0, sidebar 440px, status 72px)
view_w, view_h = 2120, 1464
ocx = int(view_w * 0.30) + 440  # 1076
ocy = int(view_h * 0.48) + 72   # 774
orad = int(min(view_w, view_h) * 0.60)  # 878

print(f'Ref globe: center=({rcx},{rcy}), radius={rr}')
print(f'Out globe: center=({ocx},{ocy}), radius={orad}')
print()

# Compare center color
out_center = pix_out[ocx, ocy][:3]
ref_center = pix_ref[rcx, rcy][:3]
print(f'Center color: ref=RGB{ref_center}  out=RGB{out_center}  diff={sum(abs(ref_center[i]-out_center[i]) for i in range(3))}')
print()

# Radial profile — bottom direction (cleanest)
print('=== Bottom direction ===')
for dp in [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]:
    rx = int(rcx + rr * dp)
    ry = int(rcy + rr * dp)
    ref_c = pix_ref[rx, ry][:3] if 0 <= rx < ref.width and 0 <= ry < ref.height else (0,0,0)

    ox = int(ocx + orad * dp)
    oy = int(ocy + orad * dp)
    out_c = pix_out[ox, oy][:3] if 0 <= ox < out.width and 0 <= oy < out.height else (0,0,0)

    diff = sum(abs(ref_c[i]-out_c[i]) for i in range(3))
    print(f'  {dp:.0%}: ref=RGB{ref_c}  out=RGB{out_c}  diff={diff}')

# Right direction
print()
print('=== Right direction ===')
for dp in [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]:
    rx = int(rcx + rr * dp)
    ry = rcy
    ref_c = pix_ref[rx, ry][:3] if 0 <= rx < ref.width else (0,0,0)

    ox = int(ocx + orad * dp)
    oy = ocy
    out_c = pix_out[ox, oy][:3] if 0 <= ox < out.width else (0,0,0)

    diff = sum(abs(ref_c[i]-out_c[i]) for i in range(3))
    print(f'  {dp:.0%}: ref=RGB{ref_c}  out=RGB{out_c}  diff={diff}')

# Full pixel diff for the globe area
print()
print('=== Full globe area comparison ===')
# Sample every 10th pixel within the output globe
total_diff = 0
samples = 0
for x in range(ocx - orad, ocx + orad, 10):
    for y in range(ocy - orad, ocy + orad, 10):
        dx, dy = x - ocx, y - ocy
        if dx*dx + dy*dy <= orad*orad and 0 <= x < out.width and 0 <= y < out.height:
            # Map output pixel to reference pixel
            rx = int(rcx + dx / orad * rr)
            ry = int(rcy + dy / orad * rr)
            if 0 <= rx < ref.width and 0 <= ry < ref.height:
                ref_p = pix_ref[rx, ry][:3]
                out_p = pix_out[x, y][:3]
                if out_p != (0,0,0) and sum(out_p) > 15:  # skip transparent/background
                    diff = sum(abs(ref_p[i]-out_p[i]) for i in range(3))
                    total_diff += diff
                    samples += 1

avg_diff = total_diff / max(samples, 1)
print(f'Sampled {samples} pixels in globe area')
print(f'Average per-pixel diff: {avg_diff:.1f} (out of 765 max)')
print(f'This represents how close the textured globe matches the reference pixel-for-pixel')