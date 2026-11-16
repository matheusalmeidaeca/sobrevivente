import sys
sys.path.insert(0,'.')
import personagem,texturas,previa_hd as pv
from PIL import Image,ImageDraw
est=sys.argv[1]; out=sys.argv[2]; ang=float(sys.argv[3]); lista=sys.argv[4]  # anim:q,q,q;anim:q
p=personagem.Personagem(est).tudo()
tex='/tmp/claude-0/w/hd_%s.png'%est
A=texturas.pintar(p); texturas.salvar(A,tex)
V,I,pts=pv.preparar(p.m.triangulos())
frames=[]
for item in lista.split(';'):
    an_,qs=item.split(':')
    for q in qs.split(','):
        P,N,pose,W=pv.posar(V,pts,est,an_,float(q))
        im=pv.desenhar(V,I,P,N,tex,ang=ang,janela=(-0.75,-0.05,0.75,2.0),pxm=170)
        ImageDraw.Draw(im).text((6,4),"%s q%s"%(an_,q),fill=(0,0,0))
        frames.append(im)
cols=min(6,len(frames)); rows=(len(frames)+cols-1)//cols
f=Image.new('RGB',(frames[0].width*cols,frames[0].height*rows))
for i,im in enumerate(frames): f.paste(im,((i%cols)*im.width,(i//cols)*im.height))
f.save(out)
