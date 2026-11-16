# ferramentas/texturas/embelezar.py
"""Embeleza as texturas do protagonista e do zumbi (F6). Python + numpy + PIL.
Le base_jogador.png / base_zumbi.png (originais da F2, ficam nesta pasta) e grava as texturas finais em
android/assets/modelos/. Pode rodar quantas vezes quiser: sempre parte das bases.
Uso: python3 ferramentas/texturas/embelezar.py   (a partir da raiz do repositorio)
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

AQUI = os.path.dirname(os.path.abspath(__file__))
SAIDA = os.path.join(AQUI, "..", "..", "android", "assets", "modelos")


def ruido(rng, tam, escala):
    """Ruido suave (valor 0..1) por interpolacao de uma grade pequena."""
    g = rng.random((tam // escala + 2, tam // escala + 2)).astype(np.float32)
    im = Image.fromarray((g * 255).astype(np.uint8)).resize((tam, tam), Image.BICUBIC)
    return np.asarray(im).astype(np.float32) / 255.0


def mascara_bordas(arr):
    """1 perto de mudancas bruscas de cor (costuras entre partes do corpo)."""
    g = arr.mean(axis=2)
    dx = np.abs(np.diff(g, axis=1, prepend=g[:, :1]))
    dy = np.abs(np.diff(g, axis=0, prepend=g[:1, :]))
    b = ((dx + dy) > 10).astype(np.uint8) * 255
    im = Image.fromarray(b).filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(2.0))
    return np.asarray(im).astype(np.float32) / 255.0


def base_comum(img, rng, forca_sujeira):
    arr = np.asarray(img).astype(np.float32)
    n = arr.shape[0]
    # variacao de luz em manchas grandes + trama fina de tecido
    grande = (ruido(rng, n, 64) - 0.5) * 0.09
    media = (ruido(rng, n, 16) - 0.5) * 0.08
    fino = (rng.random((n, n)).astype(np.float32) - 0.5) * 0.05
    trama = (np.sin(np.arange(n)[None, :] * 1.6) * np.sin(np.arange(n)[:, None] * 1.6)) * 0.015
    mod = 1.0 + grande + media + fino + trama
    # escurece nas costuras (da relevo e separa as partes)
    mod -= mascara_bordas(arr) * 0.16
    # sujeira que acumula nas partes de baixo de cada bloco (pernas, barra)
    y = np.linspace(0, 1, n)[:, None]
    mod -= forca_sujeira * ruido(rng, n, 24) * (0.3 + 0.7 * y) * 0.25
    arr = arr * mod[:, :, None]
    return np.clip(arr, 0, 255)


def jogador():
    rng = np.random.default_rng(11)
    img = Image.open(os.path.join(AQUI, "base_jogador.png")).convert("RGB")
    arr = base_comum(img, rng, 0.5)
    im = Image.fromarray(arr.astype(np.uint8))
    d = ImageDraw.Draw(im, "RGBA")
    # jaqueta/colete no tronco (128..384 x 160..483): ziper, bolsos, cinto, joelheiras
    d.line([(256, 162), (256, 380)], fill=(20, 24, 40, 170), width=2)           # ziper
    for x0 in (144, 300):                                                       # bolsos do peito
        d.rectangle([x0, 190, x0 + 70, 232], outline=(18, 22, 36, 150), width=2)
        d.line([(x0, 196), (x0 + 70, 196)], fill=(120, 140, 175, 70), width=1)
    d.rectangle([128, 380, 384, 392], fill=(36, 28, 22, 230))                   # cinto
    d.rectangle([246, 380, 266, 392], fill=(176, 150, 70, 255))                 # fivela
    d.rectangle([246, 383, 266, 389], outline=(90, 70, 30, 255))
    for x0 in (150, 290):                                                       # joelheiras / dobras
        d.rectangle([x0, 420, x0 + 80, 440], fill=(20, 26, 44, 90))
    # ombreiras e punhos nas mangas
    for x0 in (0, 384):
        d.rectangle([x0, 160, x0 + 128, 172], fill=(70, 100, 140, 120))
        d.rectangle([x0, 262, x0 + 128, 274], fill=(24, 30, 46, 200))
    # luvas escuras nas maos
    for x0 in (0, 384):
        d.rectangle([x0, 470, x0 + 128, 512], fill=(36, 32, 30, 255))
    # rosto: sobrancelhas, sombra do nariz, olhos com brilho, boca, barba por fazer
    d.rectangle([97, 38, 112, 41], fill=(40, 26, 18, 255))
    d.rectangle([138, 38, 154, 41], fill=(40, 26, 18, 255))
    d.rectangle([122, 62, 134, 70], fill=(190, 140, 110, 150))
    d.rectangle([99, 42, 103, 47], fill=(255, 255, 255, 255))
    d.rectangle([141, 42, 145, 47], fill=(255, 255, 255, 255))
    d.rectangle([105, 99, 146, 103], fill=(120, 60, 60, 255))
    d.rectangle([70, 105, 180, 128], fill=(60, 46, 38, 45))
    # cabelo com mechas
    for i in range(40):
        x = rng.integers(0, 256)
        y = rng.integers(0, 34)
        d.line([(x, y), (x + rng.integers(-4, 5), y + rng.integers(8, 22))], fill=(26, 16, 10, 110), width=1)
    im = im.filter(ImageFilter.GaussianBlur(0.4))
    im.save(os.path.join(SAIDA, "textura_jogador.png"), optimize=True)


def zumbi():
    rng = np.random.default_rng(23)
    img = Image.open(os.path.join(AQUI, "base_zumbi.png")).convert("RGB")
    arr = base_comum(img, rng, 1.0)
    # pele: tom doente e veias esverdeadas (pixels "verdes" ficam mais pálidos e com manchas roxas)
    g = arr[:, :, 1] > arr[:, :, 0] + 10
    roxo = (ruido(rng, 512, 20) > 0.70) & g
    arr[roxo] = arr[roxo] * np.array([0.93, 0.90, 0.97])
    im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im, "RGBA")
    # roupas rasgadas: buracos mostrando a pele e fiapos
    for _ in range(26):
        x = int(rng.integers(130, 380))
        y = int(rng.integers(170, 470))
        w = int(rng.integers(8, 26))
        h = int(rng.integers(6, 16))
        d.polygon([(x, y), (x + w, y + int(rng.integers(-3, 4))), (x + w - 3, y + h), (x + 2, y + h - 2)],
                  fill=(120, 100, 84, 235))
        d.line([(x, y), (x + w, y + h)], fill=(30, 26, 22, 130), width=1)
    # sangue seco escorrendo (riscos verticais) no peito e nas mangas
    for _ in range(46):
        x = int(rng.integers(0, 512))
        y = int(rng.integers(0, 470))
        c = int(rng.integers(0, 3))
        cor = [(96, 12, 12, 200), (70, 8, 10, 210), (50, 10, 10, 190)][c]
        d.line([(x, y), (x + int(rng.integers(-2, 3)), y + int(rng.integers(12, 60)))], fill=cor, width=int(rng.integers(2, 5)))
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=cor)
    # boca ensanguentada e olhos leitosos/sombras fundas
    d.rectangle([100, 92, 152, 114], fill=(60, 6, 6, 235))
    d.ellipse([96, 30, 122, 56], fill=(18, 14, 14, 110))
    d.ellipse([138, 30, 164, 56], fill=(18, 14, 14, 110))
    d.line([(110, 112), (112, 140)], fill=(110, 14, 14, 220), width=3)               # saliva sangrenta
    d.line([(140, 112), (139, 132)], fill=(110, 14, 14, 200), width=2)
    # costuras de feridas (cicatrizes)
    for _ in range(7):
        x = int(rng.integers(0, 120))
        y = int(rng.integers(172, 420))
        d.line([(x, y), (x + 34, y + 4)], fill=(60, 30, 40, 220), width=2)
        for k in range(0, 34, 6):
            d.line([(x + k, y - 3), (x + k, y + 7)], fill=(40, 20, 30, 220), width=1)
    im = im.filter(ImageFilter.GaussianBlur(0.4))
    im.save(os.path.join(SAIDA, "textura_zumbi.png"), optimize=True)


if __name__ == "__main__":
    jogador()
    zumbi()
    print("texturas gravadas em", os.path.abspath(SAIDA))
