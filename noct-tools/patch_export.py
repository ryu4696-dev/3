#!/usr/bin/env python3
import sys
from pathlib import Path

p = Path(sys.argv[1])
s = p.read_text()

provider = r'''
class SafeTensorConvRotWeights:
    """Lazy reader for ComfyUI int8_tensorwise + ConvRot safetensors.

    Noct-Q stores quantized linears as:
      <base>.weight       int8 [out,in]
      <base>.weight_scale float32 [out,1]
      <base>.comfy_quant  uint8 JSON
    with a regular normalized Hadamard H256 applied along the input dimension.
    The Hadamard is symmetric/orthogonal, so applying it again restores W.
    Non-quantized tensors are loaded as float32.
    """

    def __init__(self, path, gate_first=True):
        self.path = path
        self.gate_first = gate_first
        self._h = {}

    def _hadamard(self, size, dtype=torch.float32):
        key = (size, dtype)
        if key in self._h:
            return self._h[key]
        if size < 4:
            raise ValueError(size)
        h4 = torch.tensor([[1,1,1,-1],[1,1,-1,1],[1,-1,1,1],[-1,1,1,1]], dtype=dtype)
        h = h4
        cur = 4
        while cur < size:
            h = torch.kron(h, h4)
            cur *= 4
        h = h / (size ** 0.5)
        self._h[key] = h
        return h

    def _load(self, key):
        from safetensors import safe_open
        with safe_open(self.path, framework="pt", device="cpu") as f:
            keys = set(f.keys())
            t = f.get_tensor(key)
            if key.endswith(".weight"):
                base = key[:-len(".weight")]
                sk = base + ".weight_scale"
                ck = base + ".comfy_quant"
                if t.dtype == torch.int8 and sk in keys and ck in keys:
                    scale = f.get_tensor(sk).float().reshape(-1, 1)
                    wrot = t.float().mul_(scale)
                    # Noct-Q V1 uses exactly:
                    # {"format":"int8_tensorwise","convrot":true,"convrot_groupsize":256}
                    gs = 256
                    if wrot.shape[1] % gs:
                        raise ValueError(f"{key}: input dim {wrot.shape[1]} not divisible by {gs}")
                    h = self._hadamard(gs)
                    out = torch.empty_like(wrot)
                    groups = wrot.shape[1] // gs
                    # Row chunks bound peak temporary RAM for gate_up [24576,4096].
                    for st in range(0, wrot.shape[0], 512):
                        en = min(st + 512, wrot.shape[0])
                        x = wrot[st:en].reshape(en-st, groups, gs)
                        out[st:en] = torch.matmul(x, h).reshape(en-st, wrot.shape[1])
                    return out
            return t.float()

    def __call__(self, name):
        if name.endswith(".img_mlp.gate_layer") or name.endswith(".img_mlp.proj"):
            base = name.rsplit(".", 1)[0]
            w = self._load(base + ".gate_up.weight")
            a, b = w.chunk(2, dim=0)
            is_gate = name.endswith("gate_layer")
            return (a if is_gate == self.gate_first else b).contiguous()
        return self._load(name + ".weight")

    def param(self, name):
        return self._load(name)
'''

needle = "\nclass LoRAWeights:"
if needle not in s:
    raise SystemExit("LoRAWeights marker not found")
s = s.replace(needle, "\n" + provider + "\n\nclass LoRAWeights:", 1)

needle = '    ap.add_argument("--gguf")\n'
if needle not in s:
    raise SystemExit("arg marker not found")
s = s.replace(needle, needle + '    ap.add_argument("--safetensors", help="ComfyUI int8 ConvRot safetensors")\n', 1)

old = '''    else:
        W = GGUFWeights(a.gguf, gate_first=bool(a.gate_first))
        wp, params = W, W.param
        layers = a.layers
'''
new = '''    else:
        if a.safetensors:
            W = SafeTensorConvRotWeights(a.safetensors, gate_first=bool(a.gate_first))
        else:
            W = GGUFWeights(a.gguf, gate_first=bool(a.gate_first))
        wp, params = W, W.param
        layers = a.layers
'''
if old not in s:
    raise SystemExit("weight-provider block not found")
s = s.replace(old, new, 1)
p.write_text(s)
print("patched", p)
