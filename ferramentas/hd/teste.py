import sys,time
sys.path.insert(0,'.')
import personagem,texturas,previa_hd as pv
est=sys.argv[1]; anim=sys.argv[2]; q=float(sys.argv[3]); out=sys.argv[4]
vistas=[(float(a)) for a in sys.argv[5].split(',')]
zoom=sys.argv[6] if len(sys.argv)>6 else 'corpo'
p=personagem.Personagem(est).tudo()
A=texturas.pintar(p); tex='/tmp/claude-0/w/hd_%s.png'%est; texturas.salvar(A,tex)
V,I,pts=pv.preparar(p.m.triangulos())
P,N,pose,W=pv.posar(V,pts,est,anim,q)
jan={'corpo':((-0.5,-0.05,0.5,1.95),330),'cabeca':((-0.17,1.45,0.17,1.85),1500),'mao':((-0.12,0.60,0.12,0.90),2400),'pe':((-0.18,-0.02,0.18,0.30),1600),'torso':((-0.32,0.85,0.32,1.55),900)}[zoom]
from PIL import Image
ims=[pv.desenhar(V,I,P,N,tex,ang=a,janela=jan[0],pxm=jan[1]) for a in vistas]
W_=sum(i.width for i in ims); f=Image.new('RGB',(W_,ims[0].height))
x=0
for i in ims: f.paste(i,(x,0)); x+=i.width
f.save(out)
