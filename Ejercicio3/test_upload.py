import base64
import re
from uuid import uuid4

import requests


URL = "http://localhost:8080"


def test_rechaza_html_aunque_diga_ser_png():
    nombre = f"prueba-{uuid4().hex}.png"
    contenido = b"<script>alert('upload')</script>"
    respuesta = requests.post(URL + "/upload/1",
                             files={"afiche": (nombre, contenido, "image/png")},
                             allow_redirects=False, timeout=10)
    assert respuesta.status_code == 302
    archivo = requests.get(URL + "/uploads/" + nombre, timeout=10)
    assert archivo.status_code == 404
    assert respuesta.headers["Location"].split(";")[0].endswith("/upload/1")


def test_imagen_png_se_puede_subir_y_descargar():
    imagen = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=")
    nombre = f"prueba-{uuid4().hex}.png"
    respuesta = requests.post(URL + "/upload/1",
                             files={"afiche": (nombre, imagen, "image/png")},
                             allow_redirects=False, timeout=10)
    assert respuesta.status_code == 302
    assert respuesta.headers["Location"].endswith("/")
    listado = requests.get(URL, params={"buscar": "Inception"}, timeout=10)
    assert listado.status_code == 200
    ruta = re.search(r'src="(/uploads/[^"]+)"', listado.text)
    assert ruta is not None
    archivo = requests.get(URL + ruta.group(1), timeout=10)
    assert archivo.status_code == 200
    assert archivo.headers["Content-Type"].startswith("image/png")
    assert archivo.content == imagen
