#!/usr/bin/env python3
import sys
from pathlib import Path

p = Path(sys.argv[1])
s = p.read_text()

# libQwenImage21's exporter references a utility from its developer-only ref tree.
# The public clone does not include that tree, so provide the exact 4/8-bit
# asymmetric quantization needed by this exporter locally.
s = s.replace(
    "from utils.torch_utils import quant as torch_quant",
    r"""
def torch_quant(weight, quant_bit, quant_block, symmetric, awq, hqq):
    if hqq:
        raise RuntimeError("HQQ is not used by the Noct-Q exporter")
    oc, ic = weight.shape
    block_size = ic if quant_block == 0 else quant_block
    while ic % block_size != 0:
        block_size //= 2
    block_num = ic // block_size
    offset = 1 << (quant_bit - 1)
    clip_max = offset - 1
    w = weight.float().reshape(oc, block_num, block_size)

    if symmetric:
        clip_min = -clip_max
        abs_max = torch.amax(torch.abs(w), dim=-1, keepdim=True)
        scale = abs_max / clip_max
        safe = torch.where(scale == 0, torch.ones_like(scale), scale)
        q = torch.round(w / safe).clamp(clip_min, clip_max)
        q = (q.flatten() + offset).to(torch.uint8)
        alpha = scale.flatten()
    else:
        clip_min = -offset
        max_val = torch.amax(w, dim=-1, keepdim=True)
        min_val = torch.amin(w, dim=-1, keepdim=True)
        scale = (max_val - min_val) / (clip_max - clip_min)
        safe = torch.where(scale == 0, torch.ones_like(scale), scale)
        if awq:
            q = torch.round(w / safe) - torch.round(min_val / safe) + clip_min
            zeros = (torch.round(min_val / safe) - clip_min) * safe
        else:
            q = torch.round((w - min_val) / safe) + clip_min
            zeros = min_val - safe * clip_min
        q = (q.clamp(clip_min, clip_max).flatten() + offset).to(torch.uint8)
        # Preserve constant blocks exactly: zero scale + constant as zero-point.
        zeros = torch.where(scale == 0, min_val, zeros)
        alpha = torch.stack([zeros.flatten(), scale.flatten()], dim=-1).flatten()

    if quant_bit == 8:
        return q.cpu(), alpha.float().cpu()
    if quant_bit == 4:
        q = q.reshape(-1, 2)
        packed = ((q[:, 0] << 4) | q[:, 1]).to(torch.uint8)
        return packed.cpu(), alpha.float().cpu()
    raise RuntimeError(f"unsupported quant bits: {quant_bit}")
"""
)

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

# qwen_image21_mnn.py also imports utils.custom_op only while exporting ONNX.
# The public libQwenImage21 clone omits that developer utility, so materialize
# the minimal FakeLinear op it needs.
utils_dir = p.parent / "utils"
utils_dir.mkdir(exist_ok=True)
(utils_dir / "__init__.py").write_text("")
(utils_dir / "custom_op.py").write_text(r"""
import torch

class FakeLinearOp(torch.autograd.Function):
    @staticmethod
    def symbolic(g, input, in_features, out_features, has_bias, name):
        kwargs = {
            "in_features_i": in_features,
            "out_features_i": out_features,
            "has_bias_i": has_bias,
            "name_s": name,
        }
        from torch.onnx.symbolic_helper import _get_tensor_sizes
        sizes = _get_tensor_sizes(input)
        out_sizes = (sizes[:-1] if sizes is not None else []) + [out_features]
        output_type = input.type().with_sizes(out_sizes)
        return g.op("LlmExporter::FakeLinear", input, **kwargs).setType(output_type)

    @staticmethod
    def forward(ctx, input, in_features, out_features, has_bias, name):
        return input.new_zeros(list(input.shape)[:-1] + [out_features])
""")

p.write_text(s)
print("patched", p)
