import sys; sys.path.insert(0,'.'); sys.path.insert(0,'../blender')
import garoto,kit,texturas_garoto as tg,previa_hd as pv
from gerar_garoto import salvar_png
from PIL import Image
anim=sys.argv[1]; q=float(sys.argv[2]); out=sys.argv[3]; angs=[float(a) for a in sys.argv[4].split(',')]; zoom=sys.argv[5] if len(sys.argv)>5 else 'corpo'
g=garoto.Garoto(False).tudo()
A=tg.pintar(g); tex='/tmp/claude-0/w/g_tex.png'; salvar_png(A,tex)
V,I,pts=pv.preparar(g.m.triangulos())
P,N,pose,W=pv.posar(V,pts,'jogador',anim,q)
jan={'corpo':((-0.5,-0.05,0.5,1.95),330),'cabeca':((-0.17,1.45,0.17,1.85),1500),'torso':((-0.32,0.40,0.32,1.55),700),'pe':((-0.18,-0.02,0.18,0.30),1600)}[zoom]
ims=[pv.desenhar(V,I,P,N,tex,ang=a,janela=jan[0],pxm=jan[1]) for a in angs]
f=Image.new('RGB',(sum(i.width for i in ims),ims[0].height)); x=0
for i in ims: f.paste(i,(x,0)); x+=i.width
f.save(out)
