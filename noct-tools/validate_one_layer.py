#!/usr/bin/env python3
import os, sys, time
import numpy as np
import torch

root, model_path, out_dir = sys.argv[1:4]
sys.path.insert(0, os.path.join(root, "export"))

import MNN
import MNN.expr as F
import qwen_image21_mnn as Q
from export_mnn import SafeTensorConvRotWeights

def load(path, inputs, outputs, precision="high"):
    rt = MNN.nn.create_runtime_manager(({"backend":"CPU","precision":precision,"numThread":8},))
    return MNN.nn.load_module_from_file(path, inputs, outputs, runtime_manager=rt, shape_mutable=True, rearrange=True)

def var(t):
    a=np.ascontiguousarray(t.detach().float().numpy())
    return F.const(a,list(a.shape),F.NCHW)

def to_np(v):
    return np.array(F.convert(v,F.NCHW).read(),dtype=np.float32)

def rel(a,b):
    a=np.asarray(a,np.float64).ravel(); b=np.asarray(b,np.float64).ravel()
    return float(np.linalg.norm(a-b)/(np.linalg.norm(b)+1e-12)), float(np.corrcoef(a,b)[0,1])

W=SafeTensorConvRotWeights(model_path)
torch.manual_seed(1234)
L=12
h=w=8
N=h*w
text=torch.randn(1,L,Q.DIM)
lat=torch.randn(1,N,Q.IN_CH)
cos,sin=Q.rope_tables(L,h,w)
sigma=0.7

with torch.no_grad():
    ref_txt=Q.TxtIn(W,W.param)(text)
    ref_img=Q.ImgIn(W)(lat)
    dit=Q.DiT(W,W.param,layers=1)
    zero=[torch.zeros(2,1,Q.HEADS,Q.HEAD_DIM)]
    ref_kv=list(dit(ref_txt,torch.tensor([0.0]),cos[:L],sin[:L],Q.prefix_mask(L),*zero)[1:])
    ref_out=dit(ref_img,torch.tensor([sigma]),cos[L:],sin[L:],
                torch.zeros(1,1,N,L+N),*ref_kv)[0]

m=load(os.path.join(out_dir,"txt_in.mnn"),["txt"],["txt_h"])
m_txt=to_np(m.forward([var(text)])[0])
txt_rel=rel(m_txt,ref_txt)

m=load(os.path.join(out_dir,"img_in.mnn"),["lat"],["img_h"])
m_img=to_np(m.forward([var(lat)])[0])
img_rel=rel(m_img,ref_img)

names=["hidden","timestep","rope_cos","rope_sin","attn_mask","past_kv_0"]
pre=load(os.path.join(out_dir,"dit.mnn"),names,["present_kv_0"])
m_kv=to_np(pre.forward([var(ref_txt),var(torch.tensor([0.0])),var(cos[:L]),var(sin[:L]),
                         var(Q.prefix_mask(L)),var(zero[0])])[0]).reshape(ref_kv[0].shape)
kv_rel=rel(m_kv,ref_kv[0])

step=load(os.path.join(out_dir,"dit.mnn"),names,["out"])
m_out=to_np(step.forward([var(ref_img),var(torch.tensor([sigma])),var(cos[L:]),var(sin[L:]),
                          var(torch.zeros(1,1,N,L+N)),var(ref_kv[0])])[0])
out_rel=rel(m_out,ref_out)

print("txt_in rel/corr",txt_rel)
print("img_in rel/corr",img_rel)
print("prefix rel/corr",kv_rel)
print("step rel/corr",out_rel)

# int4 will not be bit-identical; correlation is the robust sanity signal.
checks=[("txt",txt_rel),("img",img_rel),("kv",kv_rel),("out",out_rel)]
bad=[(n,r,c) for n,(r,c) in checks if not np.isfinite(r) or not np.isfinite(c) or c < 0.97]
if bad:
    raise SystemExit("validation failed: "+repr(bad))
print("VALIDATION_OK")
